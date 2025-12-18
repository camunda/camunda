/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.auditlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.auditlog.AuditLogActorType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntity;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntityType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationCategory;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationResult;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationType;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogConfiguration;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformer.TransformerConfig;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuditLogHandlerTest {

  private static final String INDEX_NAME = "test-audit-log";
  private static final String ENTITY_ID = "test-id";
  private static final String USERNAME = "test-user";

  private final ProtocolFactory factory = new ProtocolFactory();

  private AuditLogConfiguration config;
  private AuditLogTransformer transformer;
  private AuditLogHandler handler;

  private final Record authorizedRecord =
      factory.generateRecord(
          ValueType.PROCESS_INSTANCE_MODIFICATION,
          r ->
              r.withIntent(ProcessInstanceModificationIntent.MODIFIED)
                  .withAuthorizations(Map.of(Authorization.AUTHORIZED_USERNAME, USERNAME)));

  private final Record unauthorizedRecord =
      factory.generateRecordWithIntent(
          ValueType.PROCESS_INSTANCE_MODIFICATION, ProcessInstanceModificationIntent.MODIFIED);

  @BeforeEach
  void setUp() {
    config = mock(AuditLogConfiguration.class);
    transformer = mock(AuditLogTransformer.class);
    handler = new AuditLogHandler(INDEX_NAME, transformer, config);
  }

  @Test
  void shouldNotHandleRecordWhenTransformerDoesNotSupport() {
    when(config.isEnabled(any())).thenReturn(true);
    when(transformer.supports(any())).thenReturn(false);

    assertThat(handler.handlesRecord(authorizedRecord)).isFalse();
  }

  @Test
  void shouldNotHandleRecordWhenAuditLogIsNotEnabled() {
    when(transformer.supports(any())).thenReturn(true);
    when(config.isEnabled(any())).thenReturn(false);

    assertThat(handler.handlesRecord(authorizedRecord)).isFalse();
  }

  @Test
  void shouldHandleRecord() {
    when(transformer.supports(any())).thenReturn(true);
    when(config.isEnabled(any())).thenReturn(true);

    assertThat(handler.handlesRecord(authorizedRecord)).isTrue();
  }

  @Test
  void shouldGetHandledValueType() {
    when(transformer.config())
        .thenReturn(TransformerConfig.with(ValueType.PROCESS_INSTANCE_MODIFICATION));

    assertThat(handler.getHandledValueType()).isEqualTo(ValueType.PROCESS_INSTANCE_MODIFICATION);
  }

  @Test
  void shouldGenerateIds() {
    final var idList = handler.generateIds(authorizedRecord);

    assertThat(idList).hasSize(1);
    assertThat((String) idList.getFirst()).hasSize(AuditLogHandler.ID_LENGTH);
  }

  @Test
  void shouldCreateNewEntity() {
    final var entity = handler.createNewEntity(ENTITY_ID);

    assertThat(entity).isNotNull();
    assertThat(entity.getId()).isEqualTo(ENTITY_ID);
  }

  @Test
  void shouldAddEntityOnFlush() throws PersistenceException {
    final var entity = new AuditLogEntity().setId(ENTITY_ID);
    final var batchRequest = mock(BatchRequest.class);

    handler.flush(entity, batchRequest);

    verify(batchRequest).add(INDEX_NAME, entity);
  }

  @Test
  void shouldUpdateEntityForSuccessfulOperation() {
    final var entity = new AuditLogEntity().setId(ENTITY_ID);

    handler.updateEntity(authorizedRecord, entity);

    assertCommonEntityFields(entity, authorizedRecord, ENTITY_ID);
    assertThat(entity.getResult()).isEqualTo(AuditLogOperationResult.SUCCESS);
    verify(transformer).transform(authorizedRecord, entity);
  }

  @Test
  void shouldUpdateEntityForRejection() {
    final var rejectedRecord =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE_MODIFICATION,
            r ->
                r.withRecordType(RecordType.COMMAND_REJECTION)
                    .withRejectionType(RejectionType.INVALID_STATE)
                    .withIntent(ProcessInstanceModificationIntent.MODIFIED)
                    .withAuthorizations(Map.of(Authorization.AUTHORIZED_USERNAME, USERNAME)));

    final var entity = new AuditLogEntity().setId(ENTITY_ID);

    handler.updateEntity(rejectedRecord, entity);

    assertCommonEntityFields(entity, rejectedRecord, ENTITY_ID);
    assertThat(entity.getResult()).isEqualTo(AuditLogOperationResult.FAIL);
    verify(transformer, never()).transform(any(), any());
  }

  private void assertCommonEntityFields(
      final AuditLogEntity entity, final Record record, final String expectedId) {
    assertThat(entity.getId()).isEqualTo(expectedId);
    assertThat(entity.getEntityKey()).isEqualTo(String.valueOf(record.getKey()));
    assertThat(entity.getEntityType()).isEqualTo(AuditLogEntityType.PROCESS_INSTANCE);
    assertThat(entity.getOperationType()).isEqualTo(AuditLogOperationType.MODIFY);
    assertThat(entity.getCategory()).isEqualTo(AuditLogOperationCategory.DEPLOYED_RESOURCES);
    assertThat(entity.getActorType()).isEqualTo(AuditLogActorType.USER);
    assertThat(entity.getActorId()).isEqualTo(USERNAME);
    assertThat(entity.getEntityVersion()).isEqualTo(record.getRecordVersion());
    assertThat(entity.getEntityValueType())
        .isEqualTo(ValueType.PROCESS_INSTANCE_MODIFICATION.value());
    assertThat(entity.getEntityOperationIntent())
        .isEqualTo(ProcessInstanceModificationIntent.MODIFIED.value());
    assertThat(entity.getTimestamp())
        .isEqualTo(
            OffsetDateTime.ofInstant(Instant.ofEpochMilli(record.getTimestamp()), ZoneOffset.UTC));
  }
}
