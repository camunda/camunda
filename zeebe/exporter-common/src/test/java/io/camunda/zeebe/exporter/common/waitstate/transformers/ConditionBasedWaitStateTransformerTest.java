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
import io.camunda.zeebe.protocol.record.intent.ConditionalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ConditionalSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableConditionalSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConditionBasedWaitStateTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final ConditionBasedWaitStateTransformer transformer =
      new ConditionBasedWaitStateTransformer();

  @Test
  void shouldExtractDetailsFromConditionalSubscriptionCreatedRecord() {
    // given
    final ConditionalSubscriptionRecordValue value =
        ImmutableConditionalSubscriptionRecordValue.builder()
            .from(factory.generateObject(ConditionalSubscriptionRecordValue.class))
            .withCondition("= x > 5")
            .withVariableEvents(List.of("create", "update"))
            .withElementType(BpmnElementType.INTERMEDIATE_CATCH_EVENT)
            .withElementId("catch-condition")
            .withElementInstanceKey(300L)
            .withProcessInstanceKey(200L)
            .withRootProcessInstanceKey(100L)
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .withInterrupting(true)
            .build();

    final Record<ConditionalSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.CONDITIONAL_SUBSCRIPTION,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(ConditionalSubscriptionIntent.CREATED)
                    .withValue(value));

    // when
    final var entry = transformer.transform(record);

    // then — identity fields from WaitStateRelated
    assertThat(entry.getRootProcessInstanceKey()).isEqualTo(100L);
    assertThat(entry.getProcessInstanceKey()).isEqualTo(200L);
    assertThat(entry.getElementInstanceKey()).isEqualTo(300L);
    assertThat(entry.getElementId()).isEqualTo("catch-condition");
    assertThat(entry.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(entry.getPartitionId()).isEqualTo(record.getPartitionId());

    // then — classification set by config and extract
    assertThat(entry.getWaitStateType()).isEqualTo(WaitStateType.CONDITION);
    assertThat(entry.getElementType()).isEqualTo(BpmnElementType.INTERMEDIATE_CATCH_EVENT);

    // then — condition-specific details
    assertThat(entry.getDetails()).isInstanceOf(ConditionWaitStateDetails.class);
    final var details = (ConditionWaitStateDetails) entry.getDetails();
    assertThat(details.expression()).isEqualTo("= x > 5");
    assertThat(details.events()).containsExactly("create", "update");
  }

  @Test
  void shouldTriggerAddOnCreatedWithNonRootProcessInstance() {
    // given
    final ConditionalSubscriptionRecordValue value =
        ImmutableConditionalSubscriptionRecordValue.builder()
            .from(factory.generateObject(ConditionalSubscriptionRecordValue.class))
            .withProcessInstanceKey(200L)
            .build();

    final Record<ConditionalSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.CONDITIONAL_SUBSCRIPTION,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(ConditionalSubscriptionIntent.CREATED)
                    .withValue(value));

    // when / then
    assertThat(transformer.triggersAdd(record)).isTrue();
    assertThat(transformer.triggersRemoval(record)).isFalse();
    assertThat(transformer.triggersUpdate(record)).isFalse();
  }

  @Test
  void shouldTriggerUpdateOnMigrated() {
    // given
    final ConditionalSubscriptionRecordValue value =
        ImmutableConditionalSubscriptionRecordValue.builder()
            .from(factory.generateObject(ConditionalSubscriptionRecordValue.class))
            .withProcessInstanceKey(200L)
            .build();

    final Record<ConditionalSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.CONDITIONAL_SUBSCRIPTION,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(ConditionalSubscriptionIntent.MIGRATED)
                    .withValue(value));

    // when / then
    assertThat(transformer.triggersUpdate(record)).isTrue();
    assertThat(transformer.triggersAdd(record)).isFalse();
    assertThat(transformer.triggersRemoval(record)).isFalse();
  }

  @Test
  void shouldTriggerRemovalOnDeleted() {
    // given
    final ConditionalSubscriptionRecordValue value =
        ImmutableConditionalSubscriptionRecordValue.builder()
            .from(factory.generateObject(ConditionalSubscriptionRecordValue.class))
            .withProcessInstanceKey(200L)
            .build();

    final Record<ConditionalSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.CONDITIONAL_SUBSCRIPTION,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(ConditionalSubscriptionIntent.DELETED)
                    .withValue(value));

    // when / then
    assertThat(transformer.triggersRemoval(record)).isTrue();
    assertThat(transformer.triggersAdd(record)).isFalse();
    assertThat(transformer.triggersUpdate(record)).isFalse();
  }

  @Test
  void shouldTriggerRemovalOnTriggeredWhenInterrupting() {
    // given
    final ConditionalSubscriptionRecordValue value =
        ImmutableConditionalSubscriptionRecordValue.builder()
            .from(factory.generateObject(ConditionalSubscriptionRecordValue.class))
            .withProcessInstanceKey(200L)
            .withInterrupting(true)
            .build();

    final Record<ConditionalSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.CONDITIONAL_SUBSCRIPTION,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                    .withValue(value));

    // when / then
    assertThat(transformer.triggersRemoval(record)).isTrue();
    assertThat(transformer.triggersAdd(record)).isFalse();
    assertThat(transformer.triggersUpdate(record)).isFalse();
  }

  @Test
  void shouldNotTriggerRemovalOnTriggeredWhenNonInterrupting() {
    // given
    final ConditionalSubscriptionRecordValue value =
        ImmutableConditionalSubscriptionRecordValue.builder()
            .from(factory.generateObject(ConditionalSubscriptionRecordValue.class))
            .withProcessInstanceKey(200L)
            .withInterrupting(false)
            .build();

    final Record<ConditionalSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.CONDITIONAL_SUBSCRIPTION,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                    .withValue(value));

    // when / then
    assertThat(transformer.triggersRemoval(record)).isFalse();
    assertThat(transformer.triggersAdd(record)).isFalse();
    assertThat(transformer.triggersUpdate(record)).isFalse();
  }

  @Test
  void shouldNotTriggerAnyActionForRootStartEvent() {
    // given — processInstanceKey == -1 marks a root-level start event
    final ConditionalSubscriptionRecordValue value =
        ImmutableConditionalSubscriptionRecordValue.builder()
            .from(factory.generateObject(ConditionalSubscriptionRecordValue.class))
            .withProcessInstanceKey(-1L)
            .withInterrupting(true)
            .build();

    final Record<ConditionalSubscriptionRecordValue> createdRecord =
        factory.generateRecord(
            ValueType.CONDITIONAL_SUBSCRIPTION,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(ConditionalSubscriptionIntent.CREATED)
                    .withValue(value));
    final Record<ConditionalSubscriptionRecordValue> migratedRecord =
        factory.generateRecord(
            ValueType.CONDITIONAL_SUBSCRIPTION,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(ConditionalSubscriptionIntent.MIGRATED)
                    .withValue(value));
    final Record<ConditionalSubscriptionRecordValue> deletedRecord =
        factory.generateRecord(
            ValueType.CONDITIONAL_SUBSCRIPTION,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(ConditionalSubscriptionIntent.DELETED)
                    .withValue(value));
    final Record<ConditionalSubscriptionRecordValue> triggeredRecord =
        factory.generateRecord(
            ValueType.CONDITIONAL_SUBSCRIPTION,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                    .withValue(value));

    // when / then — all intents should be suppressed for root start events
    assertThat(transformer.triggersAdd(createdRecord)).isFalse();
    assertThat(transformer.triggersUpdate(migratedRecord)).isFalse();
    assertThat(transformer.triggersRemoval(deletedRecord)).isFalse();
    assertThat(transformer.triggersRemoval(triggeredRecord)).isFalse();
  }
}
