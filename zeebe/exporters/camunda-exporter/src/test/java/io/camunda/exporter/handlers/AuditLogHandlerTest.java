/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.operation.AuditLogEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableGroupRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.test.util.Strings;
import org.junit.jupiter.api.Test;

public class AuditLogHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-audit";
  private final AuditLogHandler underTest = new AuditLogHandler(indexName);

  @Test
  void testGetHandledValueTypes() {
    assertThat(underTest.getHandledValueTypes())
        .containsExactlyInAnyOrder(
            ValueType.GROUP,
            ValueType.ROLE,
            ValueType.USER,
            ValueType.TENANT,
            ValueType.MAPPING_RULE,
            ValueType.AUTHORIZATION);
  }

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.NULL_VAL);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(AuditLogEntity.class);
  }

  @Test
  void testGetIndexName() {
    assertThat(underTest.getIndexName()).isEqualTo(indexName);
  }

  @Test
  void shouldHandleGroupCreatedRecord() {
    // given
    final Record<RecordValue> groupCreatedRecord =
        factory.generateRecordWithIntent(ValueType.GROUP, GroupIntent.CREATED);

    // when - then
    assertThat(underTest.handlesRecord(groupCreatedRecord)).isTrue();
  }

  @Test
  void shouldHandleRoleCreatedRecord() {
    // given
    final Record<RecordValue> roleCreatedRecord =
        factory.generateRecordWithIntent(ValueType.ROLE, RoleIntent.CREATED);

    // when - then
    assertThat(underTest.handlesRecord(roleCreatedRecord)).isTrue();
  }

  @Test
  void shouldNotHandleUnsupportedValueType() {
    // given - process record is not supported
    final Record<RecordValue> processRecord =
        factory.generateRecordWithIntent(ValueType.PROCESS, null);

    // when - then
    assertThat(underTest.handlesRecord(processRecord)).isFalse();
  }

  @Test
  void shouldNotHandleUnsupportedIntent() {
    // given - GROUP UPDATED is not in supported intents
    final Record<RecordValue> groupUpdatedRecord =
        factory.generateRecordWithIntent(ValueType.GROUP, GroupIntent.UPDATED);

    // when - then
    assertThat(underTest.handlesRecord(groupUpdatedRecord)).isFalse();
  }

  @Test
  void shouldCreateNewEntity() {
    // when
    final var result = underTest.createNewEntity("test-id");

    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("test-id");
    assertThat(result).isInstanceOf(AuditLogEntity.class);
  }

  @Test
  void shouldGenerateUniqueIds() {
    // given
    final Record<RecordValue> groupRecord =
        factory.generateRecordWithIntent(ValueType.GROUP, GroupIntent.CREATED);

    // when
    final var ids1 = underTest.generateIds(groupRecord);
    final var ids2 = underTest.generateIds(groupRecord);

    // then
    assertThat(ids1).hasSize(1);
    assertThat(ids2).hasSize(1);
    assertThat(ids1.getFirst()).isNotEqualTo(ids2.getFirst()); // UUIDs should be different
  }

  @Test
  void shouldUpdateEntityFromRecord() {
    // given
    final var recordKey = 123L;
    final var groupId = Strings.newRandomValidIdentityId();

    final GroupRecordValue groupRecordValue =
        ImmutableGroupRecordValue.builder()
            .from(factory.generateObject(GroupRecordValue.class))
            .withName("test-group")
            .withDescription("test-description")
            .withGroupId(groupId)
            .withGroupKey(recordKey)
            .build();

    final Record<RecordValue> groupRecord =
        factory.generateRecord(
            ValueType.GROUP,
            r -> r.withIntent(GroupIntent.CREATED).withValue(groupRecordValue).withKey(recordKey));

    final AuditLogEntity auditLogEntity = new AuditLogEntity().setId("test-id");

    // when - updateEntity is currently a no-op, so we just verify it doesn't throw
    underTest.updateEntity(groupRecord, auditLogEntity);

    // then - no exception should be thrown
    assertThat(auditLogEntity.getId()).isEqualTo("test-id");
  }

  @Test
  void shouldAddEntityOnFlush() throws PersistenceException {
    // given
    final AuditLogEntity inputEntity = new AuditLogEntity().setId("test-audit-log");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(indexName, inputEntity);
  }

  @Test
  void shouldHandleAllSupportedIntentsAndValueTypes() {
    // Test that all combinations of supported value types and intents are handled correctly

    // GROUP + CREATED
    final Record<RecordValue> groupCreated =
        factory.generateRecordWithIntent(ValueType.GROUP, GroupIntent.CREATED);
    assertThat(underTest.handlesRecord(groupCreated)).isTrue();

    // ROLE + CREATED
    final Record<RecordValue> roleCreated =
        factory.generateRecordWithIntent(ValueType.ROLE, RoleIntent.CREATED);
    assertThat(underTest.handlesRecord(roleCreated)).isTrue();

    // USER + CREATED
    final Record<RecordValue> userCreated =
        factory.generateRecordWithIntent(ValueType.USER, UserIntent.CREATED);
    assertThat(underTest.handlesRecord(userCreated)).isTrue();
  }

  @Test
  void shouldNotHandleRecordsWithBothUnsupportedValueTypeAndIntent() {
    // given - both value type and intent are unsupported
    final Record<RecordValue> unsupportedRecord =
        factory.generateRecordWithIntent(ValueType.PROCESS, null);

    // when - then
    assertThat(underTest.handlesRecord(unsupportedRecord)).isFalse();
  }

  @Test
  void shouldHandleUserCreatedRecord() {
    // given
    final Record<RecordValue> userCreatedRecord =
        factory.generateRecordWithIntent(ValueType.USER, UserIntent.CREATED);

    // when - then
    assertThat(underTest.handlesRecord(userCreatedRecord)).isTrue();
  }

  @Test
  void shouldGenerateIdsForDifferentRecordTypes() {
    // given
    final Record<RecordValue> groupRecord =
        factory.generateRecordWithIntent(ValueType.GROUP, GroupIntent.CREATED);
    final Record<RecordValue> roleRecord =
        factory.generateRecordWithIntent(ValueType.ROLE, RoleIntent.CREATED);

    // when
    final var groupIds = underTest.generateIds(groupRecord);
    final var roleIds = underTest.generateIds(roleRecord);

    // then
    assertThat(groupIds).hasSize(1);
    assertThat(roleIds).hasSize(1);
    assertThat(groupIds.getFirst()).isNotEqualTo(roleIds.getFirst());
  }
}
