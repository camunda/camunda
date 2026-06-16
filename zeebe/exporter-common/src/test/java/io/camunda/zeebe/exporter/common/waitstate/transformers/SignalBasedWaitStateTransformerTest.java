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
import io.camunda.zeebe.protocol.record.intent.SignalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableSignalSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.SignalSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

class SignalBasedWaitStateTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final SignalBasedWaitStateTransformer transformer = new SignalBasedWaitStateTransformer();

  @Test
  void shouldExtractDetailsFromSignalCreatedRecord() {
    // given
    final SignalSubscriptionRecordValue value =
        ImmutableSignalSubscriptionRecordValue.builder()
            .from(factory.generateObject(SignalSubscriptionRecordValue.class))
            .withRootProcessInstanceKey(100L)
            .withProcessInstanceKey(200L)
            .withCatchEventInstanceKey(300L)
            .withCatchEventId("signal-catch")
            .withBpmnProcessId("my-process")
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .withSignalName("mySignal")
            .withBpmnElementType(BpmnElementType.INTERMEDIATE_CATCH_EVENT.name())
            .build();

    final Record<SignalSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.SIGNAL_SUBSCRIPTION,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(SignalSubscriptionIntent.CREATED)
                    .withValue(value));

    // when
    final var entry = transformer.transform(record);

    // then — identity fields
    assertThat(entry.getRootProcessInstanceKey()).isEqualTo(100L);
    assertThat(entry.getProcessInstanceKey()).isEqualTo(200L);
    assertThat(entry.getElementInstanceKey()).isEqualTo(300L);
    assertThat(entry.getElementId()).isEqualTo("signal-catch");
    assertThat(entry.getBpmnProcessId()).isEqualTo("my-process");
    assertThat(entry.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(entry.getPartitionId()).isEqualTo(record.getPartitionId());

    // then — classification
    assertThat(entry.getWaitStateType()).isEqualTo(WaitStateType.SIGNAL);
    assertThat(entry.getElementType()).isEqualTo(BpmnElementType.INTERMEDIATE_CATCH_EVENT);

    // then — signal-specific details
    assertThat(entry.getDetails()).isInstanceOf(SignalWaitStateDetails.class);
    final var details = (SignalWaitStateDetails) entry.getDetails();
    assertThat(details.signalName()).isEqualTo("mySignal");
  }

  @Test
  void shouldTriggerAddOnCreatedForIntermediateCatchEvent() {
    // given
    final Record<SignalSubscriptionRecordValue> record =
        buildRecord(SignalSubscriptionIntent.CREATED, BpmnElementType.INTERMEDIATE_CATCH_EVENT);

    // when / then
    assertThat(transformer.triggersAdd(record)).isTrue();
    assertThat(transformer.triggersUpdate(record)).isFalse();
    assertThat(transformer.triggersRemoval(record)).isFalse();
  }

  @Test
  void shouldTriggerRemovalOnDeleted() {
    // given
    final Record<SignalSubscriptionRecordValue> record =
        buildRecord(SignalSubscriptionIntent.DELETED, BpmnElementType.INTERMEDIATE_CATCH_EVENT);

    // when / then
    assertThat(transformer.triggersAdd(record)).isFalse();
    assertThat(transformer.triggersUpdate(record)).isFalse();
    assertThat(transformer.triggersRemoval(record)).isTrue();
  }

  @Test
  void shouldTriggerUpdateOnMigrated() {
    // given
    final Record<SignalSubscriptionRecordValue> record =
        buildRecord(SignalSubscriptionIntent.MIGRATED, BpmnElementType.INTERMEDIATE_CATCH_EVENT);

    // when / then
    assertThat(transformer.triggersAdd(record)).isFalse();
    assertThat(transformer.triggersUpdate(record)).isTrue();
    assertThat(transformer.triggersRemoval(record)).isFalse();
  }

  @Test
  void shouldSkipStartEventSubscriptions() {
    // given — start event subscriptions have no running instance; bpmnElementType = START_EVENT
    final Record<SignalSubscriptionRecordValue> created =
        buildRecord(SignalSubscriptionIntent.CREATED, BpmnElementType.START_EVENT);
    final Record<SignalSubscriptionRecordValue> deleted =
        buildRecord(SignalSubscriptionIntent.DELETED, BpmnElementType.START_EVENT);

    // when / then
    assertThat(transformer.triggersAdd(created)).isFalse();
    assertThat(transformer.triggersUpdate(created)).isFalse();
    assertThat(transformer.triggersRemoval(created)).isFalse();
    assertThat(transformer.triggersRemoval(deleted)).isFalse();
  }

  @Test
  void shouldSkipBoundaryEventSubscriptions() {
    // given — boundary events interrupt the host element and must not appear as their own wait
    // state
    final Record<SignalSubscriptionRecordValue> created =
        buildRecord(SignalSubscriptionIntent.CREATED, BpmnElementType.BOUNDARY_EVENT);
    final Record<SignalSubscriptionRecordValue> deleted =
        buildRecord(SignalSubscriptionIntent.DELETED, BpmnElementType.BOUNDARY_EVENT);

    // when / then
    assertThat(transformer.triggersAdd(created)).isFalse();
    assertThat(transformer.triggersUpdate(created)).isFalse();
    assertThat(transformer.triggersRemoval(created)).isFalse();
    assertThat(transformer.triggersRemoval(deleted)).isFalse();
  }

  @Test
  void shouldSkipPreV810RecordsWithEmptyBpmnElementType() {
    // given — records exported before 8.10 have an empty bpmnElementType string
    final SignalSubscriptionRecordValue value =
        ImmutableSignalSubscriptionRecordValue.builder()
            .from(factory.generateObject(SignalSubscriptionRecordValue.class))
            .withProcessInstanceKey(200L)
            .withRootProcessInstanceKey(200L)
            .withBpmnProcessId("my-process")
            .withBpmnElementType("")
            .build();

    final Record<SignalSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.SIGNAL_SUBSCRIPTION,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(SignalSubscriptionIntent.CREATED)
                    .withValue(value));

    // when / then
    assertThat(transformer.triggersAdd(record)).isFalse();
    assertThat(transformer.triggersUpdate(record)).isFalse();
    assertThat(transformer.triggersRemoval(record)).isFalse();
  }

  private Record<SignalSubscriptionRecordValue> buildRecord(
      final SignalSubscriptionIntent intent, final BpmnElementType elementType) {
    final SignalSubscriptionRecordValue value =
        ImmutableSignalSubscriptionRecordValue.builder()
            .from(factory.generateObject(SignalSubscriptionRecordValue.class))
            .withProcessInstanceKey(200L)
            .withRootProcessInstanceKey(200L)
            .withBpmnProcessId("my-process")
            .withSignalName("mySignal")
            .withBpmnElementType(elementType.name())
            .build();

    return factory.generateRecord(
        ValueType.SIGNAL_SUBSCRIPTION,
        r -> r.withRecordType(RecordType.EVENT).withIntent(intent).withValue(value));
  }
}
