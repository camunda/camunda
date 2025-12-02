/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.auditlog;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.auditlog.AuditLogActorType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntity;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntityType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationCategory;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationResult;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogTenantScope;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceModificationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ProcessInstanceModifiedAuditLogHandlerTest {

  private static final String INDEX_NAME = "test-pi-modified-audit-log";
  private final ProtocolFactory factory = new ProtocolFactory();
  private final AuditLogHandler<ProcessInstanceModificationRecordValue> underTest =
      new AuditLogHandler<>(INDEX_NAME, new ProcessInstanceModificationAuditLogTransformer());

  @Test
  void shouldGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.PROCESS_INSTANCE_MODIFICATION);
  }

  @Test
  void shouldGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(AuditLogEntity.class);
  }

  @Test
  void shouldHandleEventRecord() {
    // given
    final Record<ProcessInstanceModificationRecordValue> record = createEventRecord();

    // when - then
    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  @Test
  void shouldNotHandleRecordWithoutUsernameOrClientId() {
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
                    .withAuthorizations(Map.of())); // No username or clientId

    // when - then
    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final Record<ProcessInstanceModificationRecordValue> record = createEventRecord();

    // when
    final var idList = underTest.generateIds(record);

    // then
    assertThat(idList).hasSize(1);
    assertThat(idList.get(0)).isNotNull();
    assertThat(idList.get(0)).hasSize(AuditLogHandler.ID_LENGTH);
  }

  @Test
  void shouldCreateNewEntity() {
    // when
    final var result = underTest.createNewEntity("id");

    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  void shouldAddEntityOnFlush() throws PersistenceException {
    // given
    final var entity = new AuditLogEntity().setId("test-id");
    final var batchRequest = Mockito.mock(BatchRequest.class);

    // when
    underTest.flush(entity, batchRequest);

    // then
    Mockito.verify(batchRequest).add(INDEX_NAME, entity);
  }

  @Test
  void shouldCreateEntityFromRecord() {
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
                    .withAuthorizations(Map.of(Authorization.AUTHORIZED_USERNAME, username)));

    // when
    final AuditLogEntity entity = new AuditLogEntity().setId(String.valueOf(record.getPosition()));
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getId()).isEqualTo(String.valueOf(record.getPosition()));
    assertThat(entity.getEntityKey()).isEqualTo(String.valueOf(recordKey));
    assertThat(entity.getEntityType()).isEqualTo(AuditLogEntityType.PROCESS_INSTANCE);
    assertThat(entity.getOperationType()).isEqualTo(AuditLogOperationType.MODIFY);
    assertThat(entity.getEntityVersion()).isEqualTo(record.getRecordVersion());
    assertThat(entity.getEntityValueType())
        .isEqualTo(ValueType.PROCESS_INSTANCE_MODIFICATION.value());
    assertThat(entity.getEntityOperationIntent())
        .isEqualTo(ProcessInstanceModificationIntent.MODIFIED.value());
    assertThat(entity.getTimestamp())
        .isEqualTo(
            OffsetDateTime.ofInstant(Instant.ofEpochMilli(record.getTimestamp()), ZoneOffset.UTC));
    assertThat(entity.getCategory()).isEqualTo(AuditLogOperationCategory.OPERATOR);
    assertThat(entity.getActorId()).isEqualTo(username);
    assertThat(entity.getActorType()).isEqualTo(AuditLogActorType.USER);
    assertThat(entity.getResult()).isEqualTo(AuditLogOperationResult.SUCCESS);
    assertThat(entity.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(entity.getTenantId()).isEqualTo(tenantId);
    assertThat(entity.getTenantScope()).isEqualTo(AuditLogTenantScope.TENANT);
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
                .withAuthorizations(Map.of(Authorization.AUTHORIZED_USERNAME, username))
                .withValue(value));
  }

  protected Record<ProcessInstanceModificationRecordValue> createRejectedRecord() {
    final var value =
        ImmutableProcessInstanceModificationRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceModificationRecordValue.class))
            .withProcessInstanceKey(123456789L)
            .build();
    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE_MODIFICATION,
        b ->
            b.withRecordType(RecordType.COMMAND_REJECTION)
                .withRejectionType(RejectionType.INVALID_STATE)
                .withIntent(ProcessInstanceModificationIntent.MODIFY)
                .withValue(value));
  }
}
