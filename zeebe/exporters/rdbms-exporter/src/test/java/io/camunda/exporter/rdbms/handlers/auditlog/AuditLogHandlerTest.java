/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.auditlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.db.rdbms.write.service.AuditLogWriter;
import io.camunda.webapps.schema.entities.auditlog.AuditLogActorType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntityType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationCategory;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationResult;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationType;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogConfiguration;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformer;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditLogHandlerTest {

  private static final String USERNAME = "test-user";

  private final ProtocolFactory factory = new ProtocolFactory();

  private AuditLogWriter writer;
  private VendorDatabaseProperties databaseProperties;
  private AuditLogConfiguration config;
  private AuditLogTransformer transformer;
  private AuditLogExportHandler handler;

  @Captor private ArgumentCaptor<AuditLogDbModel> auditLogCaptor;

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
    writer = mock(AuditLogWriter.class);
    databaseProperties = mock(VendorDatabaseProperties.class);
    config = mock(AuditLogConfiguration.class);
    transformer = mock(AuditLogTransformer.class);
    handler = new AuditLogExportHandler(writer, databaseProperties, transformer, config);
  }

  @Test
  void shouldNotHandleRecordWhenTransformerDoesNotSupport() {
    when(transformer.supports(any())).thenReturn(false);

    assertThat(handler.canExport(authorizedRecord)).isFalse();
  }

  @Test
  void shouldNotHandleRecordWhenAuditLogIsNotEnabled() {
    when(transformer.supports(any())).thenReturn(true);
    when(config.isEnabled(any())).thenReturn(false);

    assertThat(handler.canExport(authorizedRecord)).isFalse();
  }

  @Test
  void shouldNotHandleRecordWhenAuthorizationIsMissing() {

    assertThat(handler.canExport(unauthorizedRecord)).isFalse();
  }

  @Test
  void shouldHandleRecord() {
    when(transformer.supports(any())).thenReturn(true);
    when(config.isEnabled(any())).thenReturn(true);

    assertThat(handler.canExport(authorizedRecord)).isTrue();
  }

  @Test
  void shouldUpdateEntityForSuccessfulOperation() {
    handler.export(authorizedRecord);

    verify(writer).create(auditLogCaptor.capture());
    final AuditLogDbModel entity = auditLogCaptor.getValue();

    assertCommonEntityFields(entity, authorizedRecord);
    assertThat(entity.result()).isEqualTo(AuditLogOperationResult.SUCCESS);
    verify(transformer).transform(any(), any());
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

    handler.export(rejectedRecord);

    verify(writer).create(auditLogCaptor.capture());
    final AuditLogDbModel entity = auditLogCaptor.getValue();

    assertCommonEntityFields(entity, rejectedRecord);
    assertThat(entity.result()).isEqualTo(AuditLogOperationResult.FAIL);
    verify(transformer, never()).transform(any(), any());
  }

  private void assertCommonEntityFields(final AuditLogDbModel entity, final Record record) {
    assertThat(entity.auditLogKey()).isNotNull();
    assertThat(entity.entityKey()).isEqualTo(String.valueOf(record.getKey()));
    assertThat(entity.entityType()).isEqualTo(AuditLogEntityType.PROCESS_INSTANCE);
    assertThat(entity.operationType()).isEqualTo(AuditLogOperationType.MODIFY);
    assertThat(entity.category()).isEqualTo(AuditLogOperationCategory.OPERATOR);
    assertThat(entity.actorType()).isEqualTo(AuditLogActorType.USER);
    assertThat(entity.actorId()).isEqualTo(USERNAME);
    assertThat(entity.entityVersion()).isEqualTo(record.getRecordVersion());
    assertThat(entity.entityValueType()).isEqualTo(ValueType.PROCESS_INSTANCE_MODIFICATION.value());
    assertThat(entity.entityOperationIntent())
        .isEqualTo(ProcessInstanceModificationIntent.MODIFIED.value());
    assertThat(entity.timestamp())
        .isEqualTo(
            OffsetDateTime.ofInstant(Instant.ofEpochMilli(record.getTimestamp()), ZoneOffset.UTC));
  }
}
