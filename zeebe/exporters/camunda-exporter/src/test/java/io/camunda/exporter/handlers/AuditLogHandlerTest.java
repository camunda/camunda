/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.AuditLogHandler.AuditLogBatch;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.template.AuditLogTemplate;
import io.camunda.webapps.schema.entities.auditlog.AuditLogActorType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogCleanupEntity;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntity;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntityType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationCategory;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogTenantScope;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogConfiguration;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogEntry;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformer.TransformerConfig;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceModificationRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuditLogHandlerTest {

  private static final String INDEX_NAME = "test-audit-log";
  private static final String CLEANUP_INDEX_NAME = "test-audit-log-cleanup";
  private static final String ENTITY_ID = "test-id";
  private static final String USERNAME = "test-user";
  private static final String TENANT = "test-tenant";

  private final ProtocolFactory factory = new ProtocolFactory();
  final ImmutableProcessInstanceModificationRecordValue value =
      ImmutableProcessInstanceModificationRecordValue.builder()
          .from(factory.generateObject(ImmutableProcessInstanceModificationRecordValue.class))
          .withTenantId(TENANT)
          .build();
  private final Record<RecordValue> record =
      factory.generateRecord(
          ValueType.PROCESS_INSTANCE_MODIFICATION,
          r ->
              r.withIntent(ProcessInstanceModificationIntent.MODIFIED)
                  .withValue(value)
                  .withAuthorizations(Map.of(Authorization.AUTHORIZED_USERNAME, USERNAME)));

  private AuditLogConfiguration config;
  private AuditLogTransformer<RecordValue> transformer;
  private AuditLogHandler<RecordValue> handler;

  @BeforeEach
  void setUp() {
    config = mock(AuditLogConfiguration.class);
    transformer = mock(AuditLogTransformer.class);
    handler = new AuditLogHandler<>(INDEX_NAME, CLEANUP_INDEX_NAME, transformer, config);
  }

  @Test
  void shouldNotHandleRecordWhenTransformerDoesNotSupport() {
    when(config.isEnabled(any())).thenReturn(true);
    when(transformer.supports(any())).thenReturn(false);

    assertThat(handler.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldNotHandleRecordWhenAuditLogIsNotEnabled() {
    when(transformer.supports(any())).thenReturn(true);
    when(config.isEnabled(any())).thenReturn(false);

    assertThat(handler.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldHandleRecord() {
    when(transformer.supports(any())).thenReturn(true);
    when(config.isEnabled(any())).thenReturn(true);

    assertThat(handler.handlesRecord(record)).isTrue();
  }

  @Test
  void shouldGetHandledValueType() {
    when(transformer.config())
        .thenReturn(TransformerConfig.with(ValueType.PROCESS_INSTANCE_MODIFICATION));

    assertThat(handler.getHandledValueType()).isEqualTo(ValueType.PROCESS_INSTANCE_MODIFICATION);
  }

  @Test
  void shouldGenerateIds() {
    final var idList = handler.generateIds(record);

    assertThat(idList).hasSize(1);
    assertThat(idList.getFirst()).isEqualTo(record.getPartitionId() + "-" + record.getPosition());
  }

  @Test
  void shouldCreateNewEntity() {
    final var entity = handler.createNewEntity(ENTITY_ID);

    assertThat(entity).isNotNull();
    assertThat(entity.getId()).isEqualTo(ENTITY_ID);
  }

  @Test
  void shouldAddEntityOnFlush() throws PersistenceException {
    final var batch = new AuditLogBatch(ENTITY_ID);
    batch.setAuditLogEntity(new AuditLogEntity());
    final var batchRequest = mock(BatchRequest.class);

    handler.flush(batch, batchRequest);

    verify(batchRequest).add(INDEX_NAME, batch.getAuditLogEntity());
    verify(batchRequest, never()).add(eq(CLEANUP_INDEX_NAME), any());
  }

  @Test
  void shouldAddEntityAndCleanupEntityOnFlush() throws PersistenceException {
    final var batch = new AuditLogBatch(ENTITY_ID);
    batch.setAuditLogEntity(new AuditLogEntity());
    batch.setAuditLogCleanupEntity(new AuditLogCleanupEntity().setId(ENTITY_ID));
    final var batchRequest = mock(BatchRequest.class);

    handler.flush(batch, batchRequest);

    verify(batchRequest).add(INDEX_NAME, batch.getAuditLogEntity());
    verify(batchRequest).add(CLEANUP_INDEX_NAME, batch.getAuditLogCleanupEntity());
  }

  @Test
  void shouldNotCreateCleanupEntityWhenNotTriggered() {
    when(transformer.triggersCleanUp(any(Record.class))).thenReturn(false);
    when(transformer.create(record)).thenReturn(createAuditLogEntry());
    final var batch = new AuditLogBatch(ENTITY_ID);

    handler.updateEntity(record, batch);

    assertThat(batch.getAuditLogEntity()).isNotNull();
    assertThat(batch.getAuditLogCleanupEntity()).isNull();
  }

  @Test
  void shouldUpdateEntity() {
    when(transformer.triggersCleanUp(any(Record.class))).thenReturn(true);

    final var batch = new AuditLogBatch(ENTITY_ID);

    // Create a properly populated AuditLogEntry that the transformer will return
    final var entry = createAuditLogEntry();

    when(transformer.create(record)).thenReturn(entry);
    handler.updateEntity(record, batch);

    // Verify all fields are properly mapped
    final var entity = batch.getAuditLogEntity();
    assertThat(entity.getId()).isEqualTo(ENTITY_ID);
    assertThat(entity.getEntityKey()).isEqualTo(String.valueOf(record.getKey()));
    assertThat(entity.getEntityType()).isEqualTo(AuditLogEntityType.PROCESS_INSTANCE);
    assertThat(entity.getOperationType()).isEqualTo(AuditLogOperationType.MODIFY);
    assertThat(entity.getCategory()).isEqualTo(AuditLogOperationCategory.DEPLOYED_RESOURCES);
    assertThat(entity.getActorType()).isEqualTo(AuditLogActorType.USER);
    assertThat(entity.getActorId()).isEqualTo(USERNAME);
    assertThat(entity.getTenantId()).isEqualTo(TENANT);
    assertThat(entity.getTenantScope()).isEqualTo(AuditLogTenantScope.TENANT);
    assertThat(entity.getBatchOperationKey()).isEqualTo(123L);
    assertThat(entity.getBatchOperationType())
        .isEqualTo(
            io.camunda.zeebe.protocol.record.value.BatchOperationType.CANCEL_PROCESS_INSTANCE);
    assertThat(entity.getProcessInstanceKey()).isEqualTo(value.getProcessInstanceKey());
    assertThat(entity.getEntityVersion()).isEqualTo(record.getRecordVersion());
    assertThat(entity.getEntityValueType())
        .isEqualTo(ValueType.PROCESS_INSTANCE_MODIFICATION.value());
    assertThat(entity.getEntityOperationIntent())
        .isEqualTo(ProcessInstanceModificationIntent.MODIFIED.value());
    assertThat(entity.getTimestamp())
        .isEqualTo(
            OffsetDateTime.ofInstant(Instant.ofEpochMilli(record.getTimestamp()), ZoneOffset.UTC));
    assertThat(entity.getAnnotation()).isEqualTo("test annotation");
    assertThat(entity.getResult())
        .isEqualTo(io.camunda.webapps.schema.entities.auditlog.AuditLogOperationResult.SUCCESS);
    assertThat(entity.getProcessDefinitionId()).isEqualTo("process-def-id");
    assertThat(entity.getProcessDefinitionKey()).isEqualTo(456L);
    assertThat(entity.getElementInstanceKey()).isEqualTo(789L);
    assertThat(entity.getJobKey()).isEqualTo(101L);
    assertThat(entity.getUserTaskKey()).isEqualTo(202L);
    assertThat(entity.getDecisionEvaluationKey()).isEqualTo(303L);
    assertThat(entity.getDecisionRequirementsId()).isEqualTo("drg-id");
    assertThat(entity.getDecisionRequirementsKey()).isEqualTo(404L);
    assertThat(entity.getDecisionDefinitionId()).isEqualTo("decision-id");
    assertThat(entity.getDecisionDefinitionKey()).isEqualTo(505L);
    assertThat(entity.getDeploymentKey()).isEqualTo(606L);
    assertThat(entity.getFormKey()).isEqualTo(707L);
    assertThat(entity.getResourceKey()).isEqualTo(808L);
    assertThat(entity.getRootProcessInstanceKey()).isEqualTo(909L);
    assertThat(entity.getRelatedEntityKey()).isEqualTo("related-entity-key");
    assertThat(entity.getRelatedEntityType()).isEqualTo(AuditLogEntityType.USER);
    assertThat(entity.getEntityDescription()).isEqualTo("entity-description");

    final var auditLogCleanupEntity = batch.getAuditLogCleanupEntity();
    assertThat(auditLogCleanupEntity.getId()).isEqualTo(ENTITY_ID);
    assertThat(auditLogCleanupEntity.getKey()).isEqualTo(String.valueOf(record.getKey()));
    assertThat(auditLogCleanupEntity.getKeyField()).isEqualTo(AuditLogTemplate.ENTITY_KEY);
    assertThat(auditLogCleanupEntity.getEntityType())
        .isEqualTo(AuditLogEntityType.PROCESS_INSTANCE);
    assertThat(auditLogCleanupEntity.getPartitionId()).isEqualTo(record.getPartitionId());
  }

  AuditLogEntry createAuditLogEntry() {
    return new AuditLogEntry()
        .setEntityKey(String.valueOf(record.getKey()))
        .setEntityType(
            io.camunda.search.entities.AuditLogEntity.AuditLogEntityType.PROCESS_INSTANCE)
        .setOperationType(io.camunda.search.entities.AuditLogEntity.AuditLogOperationType.MODIFY)
        .setCategory(
            io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory.DEPLOYED_RESOURCES)
        .setActor(io.camunda.zeebe.exporter.common.auditlog.AuditLogInfo.AuditLogActor.of(record))
        .setTenant(io.camunda.zeebe.exporter.common.auditlog.AuditLogInfo.AuditLogTenant.of(record))
        .setBatchOperationKey(123L)
        .setBatchOperationType(
            io.camunda.zeebe.protocol.record.value.BatchOperationType.CANCEL_PROCESS_INSTANCE)
        .setProcessInstanceKey(value.getProcessInstanceKey())
        .setEntityVersion(record.getRecordVersion())
        .setEntityValueType(ValueType.PROCESS_INSTANCE_MODIFICATION.value())
        .setEntityOperationIntent(ProcessInstanceModificationIntent.MODIFIED.value())
        .setTimestamp(
            OffsetDateTime.ofInstant(Instant.ofEpochMilli(record.getTimestamp()), ZoneOffset.UTC))
        .setAnnotation("test annotation")
        .setResult(io.camunda.search.entities.AuditLogEntity.AuditLogOperationResult.SUCCESS)
        .setProcessDefinitionId("process-def-id")
        .setProcessDefinitionKey(456L)
        .setElementInstanceKey(789L)
        .setJobKey(101L)
        .setUserTaskKey(202L)
        .setDecisionEvaluationKey(303L)
        .setDecisionRequirementsId("drg-id")
        .setDecisionRequirementsKey(404L)
        .setDecisionDefinitionId("decision-id")
        .setDecisionDefinitionKey(505L)
        .setDeploymentKey(606L)
        .setFormKey(707L)
        .setResourceKey(808L)
        .setRootProcessInstanceKey(909L)
        .setRelatedEntityKey("related-entity-key")
        .setRelatedEntityType(io.camunda.search.entities.AuditLogEntity.AuditLogEntityType.USER)
        .setEntityDescription("entity-description");
  }
}
