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
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableTimerRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.TimerRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

class TimerBasedWaitStateTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final TimerBasedWaitStateTransformer transformer = new TimerBasedWaitStateTransformer();

  @Test
  void shouldExtractDetailsFromTimerCreatedRecord() {
    // given
    final TimerRecordValue value =
        ImmutableTimerRecordValue.builder()
            .from(factory.generateObject(TimerRecordValue.class))
            .withRootProcessInstanceKey(100L)
            .withProcessInstanceKey(200L)
            .withElementInstanceKey(300L)
            .withTargetElementId("timer-catch")
            .withBpmnProcessId("my-process")
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .withDueDate(1_000_000L)
            .withRepetitions(2)
            .withProcessDefinitionKey(42L)
            .build();

    final Record<TimerRecordValue> record =
        factory.generateRecord(
            ValueType.TIMER,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(TimerIntent.CREATED)
                    .withValue(value));

    // when
    final var entry = transformer.transform(record);

    // then — identity fields from WaitStateRelated
    assertThat(entry.getRootProcessInstanceKey()).isEqualTo(100L);
    assertThat(entry.getProcessInstanceKey()).isEqualTo(200L);
    assertThat(entry.getElementInstanceKey()).isEqualTo(300L);
    assertThat(entry.getElementId()).isEqualTo("timer-catch");
    assertThat(entry.getBpmnProcessId()).isEqualTo("my-process");
    assertThat(entry.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(entry.getPartitionId()).isEqualTo(record.getPartitionId());

    // then — classification
    assertThat(entry.getWaitStateType()).isEqualTo(WaitStateType.TIMER);
    assertThat(entry.getElementType()).isNull();

    // then — timer-specific details
    assertThat(entry.getDetails()).isInstanceOf(TimerWaitStateDetails.class);
    final var details = (TimerWaitStateDetails) entry.getDetails();
    assertThat(details.dueDate()).isEqualTo(1_000_000L);
    assertThat(details.repetitions()).isEqualTo(2);
  }

  @Test
  void shouldRemoveOnTriggeredAndCanceled() {
    // given
    final TimerRecordValue value =
        ImmutableTimerRecordValue.builder()
            .from(factory.generateObject(TimerRecordValue.class))
            .withProcessInstanceKey(200L)
            .withRootProcessInstanceKey(200L)
            .withBpmnProcessId("my-process")
            .build();

    final Record<TimerRecordValue> triggered =
        factory.generateRecord(
            ValueType.TIMER,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(TimerIntent.TRIGGERED)
                    .withValue(value));
    final Record<TimerRecordValue> canceled =
        factory.generateRecord(
            ValueType.TIMER,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(TimerIntent.CANCELED)
                    .withValue(value));

    // when / then
    assertThat(transformer.triggersAdd(triggered)).isFalse();
    assertThat(transformer.triggersUpdate(triggered)).isFalse();
    assertThat(transformer.triggersRemoval(triggered)).isTrue();

    assertThat(transformer.triggersAdd(canceled)).isFalse();
    assertThat(transformer.triggersUpdate(canceled)).isFalse();
    assertThat(transformer.triggersRemoval(canceled)).isTrue();
  }

  @Test
  void shouldTriggerUpdateOnMigratedWithoutChangingTimerState() {
    // given
    final TimerRecordValue value =
        ImmutableTimerRecordValue.builder()
            .from(factory.generateObject(TimerRecordValue.class))
            .withProcessInstanceKey(200L)
            .withRootProcessInstanceKey(200L)
            .withBpmnProcessId("my-process")
            .withDueDate(2_000_000L)
            .withRepetitions(1)
            .build();

    final Record<TimerRecordValue> migrated =
        factory.generateRecord(
            ValueType.TIMER,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(TimerIntent.MIGRATED)
                    .withValue(value));

    // when / then
    assertThat(transformer.triggersAdd(migrated)).isFalse();
    assertThat(transformer.triggersUpdate(migrated)).isTrue();
    assertThat(transformer.triggersRemoval(migrated)).isFalse();

    final var details = (TimerWaitStateDetails) transformer.transform(migrated).getDetails();
    assertThat(details.dueDate()).isEqualTo(2_000_000L);
    assertThat(details.repetitions()).isEqualTo(1);
  }

  @Test
  void shouldSkipTimerStartEventSubscriptions() {
    // given — processInstanceKey == -1L means this is a start-event subscription
    final TimerRecordValue value =
        ImmutableTimerRecordValue.builder()
            .from(factory.generateObject(TimerRecordValue.class))
            .withProcessInstanceKey(-1L)
            .withRootProcessInstanceKey(-1L)
            .withBpmnProcessId("my-process")
            .build();

    final Record<TimerRecordValue> created =
        factory.generateRecord(
            ValueType.TIMER,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(TimerIntent.CREATED)
                    .withValue(value));
    final Record<TimerRecordValue> triggered =
        factory.generateRecord(
            ValueType.TIMER,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(TimerIntent.TRIGGERED)
                    .withValue(value));

    // when / then
    assertThat(transformer.triggersAdd(created)).isFalse();
    assertThat(transformer.triggersUpdate(triggered)).isFalse();
    assertThat(transformer.triggersRemoval(triggered)).isFalse();
  }

  @Test
  void shouldSkipV1TimerRecordsMissingProcessContext() {
    // given — V1 records from before 8.10 have rootProcessInstanceKey=-1 and bpmnProcessId=""
    final TimerRecordValue value =
        ImmutableTimerRecordValue.builder()
            .from(factory.generateObject(TimerRecordValue.class))
            .withProcessInstanceKey(200L)
            .withRootProcessInstanceKey(-1L)
            .withBpmnProcessId("")
            .build();

    final Record<TimerRecordValue> created =
        factory.generateRecord(
            ValueType.TIMER,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(TimerIntent.CREATED)
                    .withValue(value));

    // when / then
    assertThat(transformer.triggersAdd(created)).isFalse();
    assertThat(transformer.triggersUpdate(created)).isFalse();
    assertThat(transformer.triggersRemoval(created)).isFalse();
  }

  @Test
  void shouldTriggerAddOnTimerCreatedForProcessInstance() {
    // given
    final TimerRecordValue value =
        ImmutableTimerRecordValue.builder()
            .from(factory.generateObject(TimerRecordValue.class))
            .withProcessInstanceKey(200L)
            .withRootProcessInstanceKey(200L)
            .withBpmnProcessId("my-process")
            .build();

    final Record<TimerRecordValue> record =
        factory.generateRecord(
            ValueType.TIMER,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(TimerIntent.CREATED)
                    .withValue(value));

    // when / then
    assertThat(transformer.supports(record)).isTrue();
    assertThat(transformer.triggersAdd(record)).isTrue();
    assertThat(transformer.triggersUpdate(record)).isFalse();
    assertThat(transformer.triggersRemoval(record)).isFalse();
  }
}
