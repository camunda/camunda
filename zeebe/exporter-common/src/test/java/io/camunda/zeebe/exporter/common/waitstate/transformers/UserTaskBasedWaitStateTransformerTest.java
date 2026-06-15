/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.waitstate.transformers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.common.waitstate.WaitStateEntry.WaitStateType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableUserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

class UserTaskBasedWaitStateTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final UserTaskBasedWaitStateTransformer transformer =
      new UserTaskBasedWaitStateTransformer();

  @Test
  void shouldExtractDetailsFromUserTaskCreatedRecord() {
    // given
    final UserTaskRecordValue value =
        ImmutableUserTaskRecordValue.builder()
            .from(factory.generateObject(UserTaskRecordValue.class))
            .withUserTaskKey(999L)
            .withDueDate("2026-10-13T10:00:00+01:00")
            .withElementId("approve-task")
            .withElementInstanceKey(300L)
            .withProcessInstanceKey(200L)
            .withRootProcessInstanceKey(100L)
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .build();

    final Record<UserTaskRecordValue> record =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withKey(999L)
                    .withRecordType(RecordType.EVENT)
                    .withIntent(UserTaskIntent.CREATED)
                    .withValue(value));

    // when
    final var entry = transformer.transform(record);

    // then — identity fields from WaitStateRelated
    assertThat(entry.getRootProcessInstanceKey()).isEqualTo(100L);
    assertThat(entry.getProcessInstanceKey()).isEqualTo(200L);
    assertThat(entry.getElementInstanceKey()).isEqualTo(300L);
    assertThat(entry.getElementId()).isEqualTo("approve-task");
    assertThat(entry.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(entry.getPartitionId()).isEqualTo(record.getPartitionId());

    // then — classification set by config and extract
    assertThat(entry.getWaitStateType()).isEqualTo(WaitStateType.USER_TASK);
    assertThat(entry.getElementType()).isEqualTo(BpmnElementType.USER_TASK);

    // then — user-task-specific details
    assertThat(entry.getDetails()).isInstanceOf(UserTaskWaitStateDetails.class);
    final var details = (UserTaskWaitStateDetails) entry.getDetails();
    assertThat(details.taskKey()).isEqualTo(999L);
    assertThat(details.dueDate()).isEqualTo("2026-10-13T10:00:00+01:00");
  }

  @Test
  void shouldTriggerAddOnUserTaskCreated() {
    // given
    final Record<UserTaskRecordValue> record = userTaskRecord(UserTaskIntent.CREATED);

    // when / then
    assertThat(transformer.supports(record)).isTrue();
    assertThat(transformer.triggersAdd(record)).isTrue();
    assertThat(transformer.triggersUpdate(record)).isFalse();
    assertThat(transformer.triggersRemoval(record)).isFalse();
  }

  @Test
  void shouldTriggerUpdateOnUserTaskMigratedAndUpdated() {
    // given
    final Record<UserTaskRecordValue> migrated = userTaskRecord(UserTaskIntent.MIGRATED);
    final Record<UserTaskRecordValue> updated = userTaskRecord(UserTaskIntent.UPDATED);

    // when / then
    assertThat(transformer.triggersUpdate(migrated)).isTrue();
    assertThat(transformer.triggersUpdate(updated)).isTrue();
    assertThat(transformer.triggersAdd(migrated)).isFalse();
    assertThat(transformer.triggersRemoval(migrated)).isFalse();
  }

  @Test
  void shouldTriggerRemovalOnUserTaskCompletedAndCanceled() {
    // given
    final Record<UserTaskRecordValue> completed = userTaskRecord(UserTaskIntent.COMPLETED);
    final Record<UserTaskRecordValue> canceled = userTaskRecord(UserTaskIntent.CANCELED);

    // when / then
    assertThat(transformer.triggersRemoval(completed)).isTrue();
    assertThat(transformer.triggersRemoval(canceled)).isTrue();
    assertThat(transformer.triggersAdd(completed)).isFalse();
    assertThat(transformer.triggersAdd(canceled)).isFalse();
  }

  @Test
  void shouldNormalizeEmptyDueDateToNull() {
    // given — protocol default for dueDate is "" when not configured
    final UserTaskRecordValue value =
        ImmutableUserTaskRecordValue.builder()
            .from(factory.generateObject(UserTaskRecordValue.class))
            .withUserTaskKey(999L)
            .withDueDate("")
            .withElementId("no-due-date-task")
            .withElementInstanceKey(300L)
            .withProcessInstanceKey(200L)
            .withRootProcessInstanceKey(100L)
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .build();

    final Record<UserTaskRecordValue> record =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withKey(999L)
                    .withRecordType(RecordType.EVENT)
                    .withIntent(UserTaskIntent.CREATED)
                    .withValue(value));

    // when
    final var entry = transformer.transform(record);

    // then — empty string must be normalised to null so consumers can safely parse dueDate
    final var details = (UserTaskWaitStateDetails) entry.getDetails();
    assertThat(details.dueDate()).isNull();
  }

  private Record<UserTaskRecordValue> userTaskRecord(final UserTaskIntent intent) {
    return factory.generateRecord(
        ValueType.USER_TASK, r -> r.withRecordType(RecordType.EVENT).withIntent(intent));
  }
}
