/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.auditlog;

import static io.camunda.zeebe.protocol.record.RecordMetadataDecoder.batchOperationReferenceNullValue;
import static org.assertj.core.api.Assertions.assertThat;
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
import io.camunda.webapps.schema.entities.auditlog.AuditLogTenantScope;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceModificationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.util.DateUtil;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessInstanceModifiedAuditLogExportHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();

  @Mock private AuditLogWriter auditLogWriter;

  @Mock(lenient = true)
  private VendorDatabaseProperties vendorDatabaseProperties;

  @Captor private ArgumentCaptor<AuditLogDbModel> auditLogCaptor;

  private AuditLogExportHandler<ProcessInstanceModificationRecordValue> underTest;

  @BeforeEach
  void setUp() {
    underTest =
        new AuditLogExportHandler<>(
            auditLogWriter,
            vendorDatabaseProperties,
            new ProcessInstanceModificationAuditLogTransformer());
    when(vendorDatabaseProperties.userCharColumnSize()).thenReturn(50);
  }

  @Test
  void shouldExportEventRecord() {
    // given
    final Record<ProcessInstanceModificationRecordValue> record = createEventRecord();

    // when - then
    assertThat(underTest.canExport(record)).isTrue();
  }

  @Test
  void shouldNotExportRecordWithoutUsernameOrClientId() {
    // given
    final ProcessInstanceModificationRecordValue recordValue =
        ImmutableProcessInstanceModificationRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceModificationRecordValue.class))
            .withProcessInstanceKey(123456789L)
            .build();

    final Record<ProcessInstanceModificationRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE_MODIFICATION,
            r ->
                r.withIntent(ProcessInstanceModificationIntent.MODIFIED)
                    .withValue(recordValue)
                    .withBatchOperationReference(batchOperationReferenceNullValue())
                    .withAuthorizations(Map.of())); // No username or clientId

    // when - then
    assertThat(underTest.canExport(record)).isFalse();
  }

  @Test
  void shouldCreateAuditLogFromRecord() {
    // given
    final long recordKey = 123L;
    final long processInstanceKey = 456789L;
    final String tenantId = "tenant-1";
    final String username = "testuser";

    final ProcessInstanceModificationRecordValue recordValue =
        ImmutableProcessInstanceModificationRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceModificationRecordValue.class))
            .withProcessInstanceKey(processInstanceKey)
            .withTenantId(tenantId)
            .build();

    final Record<ProcessInstanceModificationRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE_MODIFICATION,
            r ->
                r.withIntent(ProcessInstanceModificationIntent.MODIFIED)
                    .withValue(recordValue)
                    .withKey(recordKey)
                    .withBatchOperationReference(batchOperationReferenceNullValue())
                    .withAuthorizations(Map.of(Authorization.AUTHORIZED_USERNAME, username)));

    // when
    underTest.export(record);

    // then
    verify(auditLogWriter).create(auditLogCaptor.capture());
    final AuditLogDbModel auditLog = auditLogCaptor.getValue();

    assertThat(auditLog.auditLogKey()).isNotNull();
    assertThat(auditLog.entityKey()).isEqualTo(String.valueOf(recordKey));
    assertThat(auditLog.entityType()).isEqualTo(AuditLogEntityType.PROCESS_INSTANCE);
    assertThat(auditLog.operationType()).isEqualTo(AuditLogOperationType.MODIFY);
    assertThat(auditLog.entityVersion()).isEqualTo(record.getRecordVersion());
    assertThat(auditLog.entityValueType())
        .isEqualTo(ValueType.PROCESS_INSTANCE_MODIFICATION.value());
    assertThat(auditLog.entityOperationIntent())
        .isEqualTo(ProcessInstanceModificationIntent.MODIFIED.value());
    assertThat(auditLog.timestamp()).isEqualTo(DateUtil.toOffsetDateTime(record.getTimestamp()));
    assertThat(auditLog.category()).isEqualTo(AuditLogOperationCategory.OPERATOR);
    assertThat(auditLog.actorId()).isEqualTo(username);
    assertThat(auditLog.actorType()).isEqualTo(AuditLogActorType.USER);
    assertThat(auditLog.result()).isEqualTo(AuditLogOperationResult.SUCCESS);
    assertThat(auditLog.processInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(auditLog.tenantId()).isEqualTo(tenantId);
    assertThat(auditLog.tenantScope()).isEqualTo(AuditLogTenantScope.TENANT);
    assertThat(auditLog.batchOperationKey()).isNull();
  }

  @Test
  void shouldCreateAuditLogFromRecordWithClientId() {
    // given
    final long recordKey = 123L;
    final long processInstanceKey = 456789L;
    final String clientId = "test-client-id";

    final ProcessInstanceModificationRecordValue recordValue =
        ImmutableProcessInstanceModificationRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceModificationRecordValue.class))
            .withProcessInstanceKey(processInstanceKey)
            .build();

    final Record<ProcessInstanceModificationRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE_MODIFICATION,
            r ->
                r.withIntent(ProcessInstanceModificationIntent.MODIFIED)
                    .withValue(recordValue)
                    .withKey(recordKey)
                    .withBatchOperationReference(batchOperationReferenceNullValue())
                    .withAuthorizations(Map.of(Authorization.AUTHORIZED_CLIENT_ID, clientId)));

    // when
    underTest.export(record);

    // then
    verify(auditLogWriter).create(auditLogCaptor.capture());
    final AuditLogDbModel auditLog = auditLogCaptor.getValue();

    assertThat(auditLog.actorId()).isEqualTo(clientId);
    assertThat(auditLog.actorType()).isEqualTo(AuditLogActorType.CLIENT);
  }

  @Test
  void shouldIncludeBatchOperationKeyWhenPresent() {
    // given
    final var batchOperationKey = 99999L;
    final var username = "testuser";
    final var value =
        ImmutableProcessInstanceModificationRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceModificationRecordValue.class))
            .withProcessInstanceKey(123456789L)
            .build();
    final Record<ProcessInstanceModificationRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE_MODIFICATION,
            b ->
                b.withIntent(ProcessInstanceModificationIntent.MODIFIED)
                    .withAuthorizations(Map.of(Authorization.AUTHORIZED_USERNAME, username))
                    .withBatchOperationReference(batchOperationKey)
                    .withValue(value));

    // when
    underTest.export(record);

    // then
    verify(auditLogWriter).create(auditLogCaptor.capture());
    final AuditLogDbModel auditLog = auditLogCaptor.getValue();

    assertThat(auditLog.batchOperationKey()).isEqualTo(batchOperationKey);
  }

  protected Record<ProcessInstanceModificationRecordValue> createEventRecord() {
    final var username = "testuser";
    final var value =
        ImmutableProcessInstanceModificationRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceModificationRecordValue.class))
            .withProcessInstanceKey(123456789L)
            .build();
    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE_MODIFICATION,
        b ->
            b.withIntent(ProcessInstanceModificationIntent.MODIFIED)
                .withBatchOperationReference(batchOperationReferenceNullValue())
                .withAuthorizations(Map.of(Authorization.AUTHORIZED_USERNAME, username))
                .withValue(value));
  }
}
