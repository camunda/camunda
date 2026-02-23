/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.db.rdbms.write.service.AuditLogWriter;
import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogConfiguration;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformer;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceModificationRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditLogExportHandlerTest {

  private static final String USERNAME = "test-user";
  private static final String TENANT = "test-tenant";

  private final ProtocolFactory factory = new ProtocolFactory();
  final ImmutableProcessInstanceModificationRecordValue value =
      ImmutableProcessInstanceModificationRecordValue.builder()
          .from(factory.generateObject(ImmutableProcessInstanceModificationRecordValue.class))
          .withTenantId(TENANT)
          .build();
  private final Record record =
      factory.generateRecord(
          ValueType.PROCESS_INSTANCE_MODIFICATION,
          r ->
              r.withIntent(ProcessInstanceModificationIntent.MODIFIED)
                  .withValue(value)
                  .withAuthorizations(Map.of(Authorization.AUTHORIZED_USERNAME, USERNAME)));

  private AuditLogWriter writer;
  private VendorDatabaseProperties databaseProperties;
  private AuditLogConfiguration config;
  private AuditLogTransformer transformer;
  private AuditLogExportHandler handler;
  @Captor private ArgumentCaptor<AuditLogDbModel> auditLogCaptor;

  @BeforeEach
  void setUp() {
    writer = mock(AuditLogWriter.class);
    databaseProperties = mock(VendorDatabaseProperties.class);
    config = mock(AuditLogConfiguration.class);
    transformer = mock(AuditLogTransformer.class);
    handler = new AuditLogExportHandler(writer, databaseProperties, transformer, config);
  }

  @Test
  void shouldNotHandleRecordWhenTransformerDoesNotSupport() {
    when(transformer.supports(any())).thenReturn(false);

    assertThat(handler.canExport(record)).isFalse();
  }

  @Test
  void shouldNotHandleRecordWhenAuditLogIsNotEnabled() {
    when(transformer.supports(any())).thenReturn(true);
    when(config.isEnabled(any())).thenReturn(false);

    assertThat(handler.canExport(record)).isFalse();
  }

  @Test
  void shouldHandleRecord() {
    when(transformer.supports(any())).thenReturn(true);
    when(config.isEnabled(any())).thenReturn(true);

    assertThat(handler.canExport(record)).isTrue();
  }

  @Test
  void shouldGenerateAuditLogKey() {
    final var key = AuditLogExportHandler.generateAuditLogKey(record);

    assertThat(key).isEqualTo(record.getPartitionId() + "-" + record.getPosition());
  }

  @Test
  void shouldExport() {
    // Create a properly populated AuditLogEntry that the transformer will return
    final var entry =
        new io.camunda.zeebe.exporter.common.auditlog.AuditLogEntry()
            .setEntityKey(String.valueOf(record.getKey()))
            .setEntityType(
                io.camunda.search.entities.AuditLogEntity.AuditLogEntityType.PROCESS_INSTANCE)
            .setOperationType(
                io.camunda.search.entities.AuditLogEntity.AuditLogOperationType.MODIFY)
            .setCategory(
                io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory
                    .DEPLOYED_RESOURCES)
            .setActor(
                io.camunda.zeebe.exporter.common.auditlog.AuditLogInfo.AuditLogActor.of(record))
            .setTenant(
                io.camunda.zeebe.exporter.common.auditlog.AuditLogInfo.AuditLogTenant.of(record))
            .setBatchOperationKey(123L)
            .setBatchOperationType(
                io.camunda.zeebe.protocol.record.value.BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .setProcessInstanceKey(value.getProcessInstanceKey())
            .setEntityVersion(record.getRecordVersion())
            .setEntityValueType(
                io.camunda.zeebe.protocol.record.ValueType.PROCESS_INSTANCE_MODIFICATION.value())
            .setEntityOperationIntent(
                io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent.MODIFIED
                    .value())
            .setTimestamp(
                java.time.OffsetDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(record.getTimestamp()),
                    java.time.ZoneOffset.UTC))
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
            .setRelatedEntityType(AuditLogEntityType.USER)
            .setEntityDescription("entity-description");

    when(transformer.create(record)).thenReturn(entry);
    handler.export(record);

    verify(writer).create(auditLogCaptor.capture());
    final AuditLogDbModel entity = auditLogCaptor.getValue();

    // Verify all fields are properly mapped
    assertThat(entity.auditLogKey()).isNotNull();
    assertThat(entity.entityKey()).isEqualTo(String.valueOf(record.getKey()));
    assertThat(entity.entityType())
        .isEqualTo(io.camunda.search.entities.AuditLogEntity.AuditLogEntityType.PROCESS_INSTANCE);
    assertThat(entity.operationType())
        .isEqualTo(io.camunda.search.entities.AuditLogEntity.AuditLogOperationType.MODIFY);
    assertThat(entity.category())
        .isEqualTo(
            io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory.DEPLOYED_RESOURCES);
    assertThat(entity.actorType())
        .isEqualTo(io.camunda.search.entities.AuditLogEntity.AuditLogActorType.USER);
    assertThat(entity.actorId()).isEqualTo(USERNAME);
    assertThat(entity.tenantId()).isEqualTo(TENANT);
    assertThat(entity.tenantScope())
        .isEqualTo(io.camunda.search.entities.AuditLogEntity.AuditLogTenantScope.TENANT);
    assertThat(entity.batchOperationKey()).isEqualTo(123L);
    assertThat(entity.batchOperationType())
        .isEqualTo(io.camunda.search.entities.BatchOperationType.CANCEL_PROCESS_INSTANCE);
    assertThat(entity.processInstanceKey()).isEqualTo(value.getProcessInstanceKey());
    assertThat(entity.entityVersion()).isEqualTo(record.getRecordVersion());
    assertThat(entity.entityValueType())
        .isEqualTo(
            io.camunda.zeebe.protocol.record.ValueType.PROCESS_INSTANCE_MODIFICATION.value());
    assertThat(entity.entityOperationIntent())
        .isEqualTo(
            io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent.MODIFIED
                .value());
    assertThat(entity.timestamp())
        .isEqualTo(
            java.time.OffsetDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(record.getTimestamp()), java.time.ZoneOffset.UTC));
    assertThat(entity.annotation()).isEqualTo("test annotation");
    assertThat(entity.result())
        .isEqualTo(io.camunda.search.entities.AuditLogEntity.AuditLogOperationResult.SUCCESS);
    assertThat(entity.processDefinitionId()).isEqualTo("process-def-id");
    assertThat(entity.processDefinitionKey()).isEqualTo(456L);
    assertThat(entity.elementInstanceKey()).isEqualTo(789L);
    assertThat(entity.jobKey()).isEqualTo(101L);
    assertThat(entity.userTaskKey()).isEqualTo(202L);
    assertThat(entity.decisionEvaluationKey()).isEqualTo(303L);
    assertThat(entity.decisionRequirementsId()).isEqualTo("drg-id");
    assertThat(entity.decisionRequirementsKey()).isEqualTo(404L);
    assertThat(entity.decisionDefinitionId()).isEqualTo("decision-id");
    assertThat(entity.decisionDefinitionKey()).isEqualTo(505L);
    assertThat(entity.deploymentKey()).isEqualTo(606L);
    assertThat(entity.formKey()).isEqualTo(707L);
    assertThat(entity.resourceKey()).isEqualTo(808L);
    assertThat(entity.rootProcessInstanceKey()).isEqualTo(909L);
    assertThat(entity.partitionId()).isEqualTo(record.getPartitionId());
    assertThat(entity.relatedEntityKey()).isEqualTo("related-entity-key");
    assertThat(entity.relatedEntityType()).isEqualTo(AuditLogEntityType.USER);
    assertThat(entity.entityDescription()).isEqualTo("entity-description");
  }

  @Test
  void shouldScheduleDataCleanup() {
    // given
    final var entry =
        new io.camunda.zeebe.exporter.common.auditlog.AuditLogEntry()
            .setEntityKey(String.valueOf(record.getKey()))
            .setEntityType(
                io.camunda.search.entities.AuditLogEntity.AuditLogEntityType.PROCESS_INSTANCE)
            .setOperationType(
                io.camunda.search.entities.AuditLogEntity.AuditLogOperationType.MODIFY)
            .setCategory(
                io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory
                    .DEPLOYED_RESOURCES)
            .setActor(
                io.camunda.zeebe.exporter.common.auditlog.AuditLogInfo.AuditLogActor.of(record))
            .setTenant(
                io.camunda.zeebe.exporter.common.auditlog.AuditLogInfo.AuditLogTenant.of(record))
            .setBatchOperationKey(123L)
            .setBatchOperationType(
                io.camunda.zeebe.protocol.record.value.BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .setProcessInstanceKey(value.getProcessInstanceKey())
            .setEntityVersion(record.getRecordVersion())
            .setEntityValueType(
                io.camunda.zeebe.protocol.record.ValueType.PROCESS_INSTANCE_MODIFICATION.value())
            .setEntityOperationIntent(
                io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent.MODIFIED
                    .value())
            .setTimestamp(
                java.time.OffsetDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(record.getTimestamp()),
                    java.time.ZoneOffset.UTC))
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
            .setRelatedEntityType(AuditLogEntityType.USER)
            .setEntityDescription("entity-description");
    when(transformer.create(record)).thenReturn(entry);
    when(transformer.triggersCleanUp(record)).thenReturn(true);

    // when
    handler.export(record);

    // then
    verify(writer).scheduleEntityRelatedAuditLogsHistoryCleanupByEndTime(any(), any(), any());
  }
}
