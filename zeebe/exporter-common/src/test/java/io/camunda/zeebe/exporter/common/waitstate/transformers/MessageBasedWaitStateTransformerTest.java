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
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableMessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

class MessageBasedWaitStateTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final MessageBasedWaitStateTransformer transformer =
      new MessageBasedWaitStateTransformer();

  @Test
  void shouldExtractDetailsFromMessageSubscriptionCreatedRecord() {
    // given
    final MessageSubscriptionRecordValue value =
        ImmutableMessageSubscriptionRecordValue.builder()
            .from(factory.generateObject(MessageSubscriptionRecordValue.class))
            .withMessageName("order-received")
            .withCorrelationKey("order-123")
            .withElementType(BpmnElementType.RECEIVE_TASK)
            .withElementId("receive-task")
            .withElementInstanceKey(300L)
            .withProcessInstanceKey(200L)
            .withRootProcessInstanceKey(100L)
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .build();

    final Record<MessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_SUBSCRIPTION,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(MessageSubscriptionIntent.CREATED)
                    .withValue(value));

    // when
    final var entry = transformer.transform(record);

    // then — identity fields from WaitStateRelated
    assertThat(entry.getRootProcessInstanceKey()).isEqualTo(100L);
    assertThat(entry.getProcessInstanceKey()).isEqualTo(200L);
    assertThat(entry.getElementInstanceKey()).isEqualTo(300L);
    assertThat(entry.getElementId()).isEqualTo("receive-task");
    assertThat(entry.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(entry.getPartitionId()).isEqualTo(record.getPartitionId());

    // then — classification
    assertThat(entry.getWaitStateType()).isEqualTo(WaitStateType.MESSAGE);
    assertThat(entry.getElementType()).isEqualTo(BpmnElementType.RECEIVE_TASK);

    // then — message-specific details
    assertThat(entry.getDetails()).isInstanceOf(MessageWaitStateDetails.class);
    final var details = (MessageWaitStateDetails) entry.getDetails();
    assertThat(details.messageName()).isEqualTo("order-received");
    assertThat(details.correlationKey()).isEqualTo("order-123");
  }

  @Test
  void shouldTriggerAddOnCreatedForReceiveTask() {
    // given / when / then
    assertThat(
            transformer.triggersAdd(
                recordWithElementType(
                    MessageSubscriptionIntent.CREATED, BpmnElementType.RECEIVE_TASK)))
        .isTrue();
  }

  @Test
  void shouldTriggerAddOnCreatedForIntermediateCatchEvent() {
    // given / when / then
    assertThat(
            transformer.triggersAdd(
                recordWithElementType(
                    MessageSubscriptionIntent.CREATED, BpmnElementType.INTERMEDIATE_CATCH_EVENT)))
        .isTrue();
  }

  @Test
  void shouldNotTriggerAddForUnsupportedElementType() {
    // given / when / then
    assertThat(
            transformer.triggersAdd(
                recordWithElementType(
                    MessageSubscriptionIntent.CREATED, BpmnElementType.BOUNDARY_EVENT)))
        .isFalse();
  }

  @Test
  void shouldTriggerRemovalOnCorrelated() {
    // given
    final var record =
        recordWithElementType(MessageSubscriptionIntent.CORRELATED, BpmnElementType.RECEIVE_TASK);

    // when / then
    assertThat(transformer.triggersRemoval(record)).isTrue();
    assertThat(transformer.triggersAdd(record)).isFalse();
  }

  @Test
  void shouldTriggerRemovalOnDeleted() {
    // given / when / then
    assertThat(
            transformer.triggersRemoval(
                recordWithElementType(
                    MessageSubscriptionIntent.DELETED, BpmnElementType.RECEIVE_TASK)))
        .isTrue();
  }

  @Test
  void shouldTriggerUpdateOnMigrated() {
    // given
    final var record =
        recordWithElementType(MessageSubscriptionIntent.MIGRATED, BpmnElementType.RECEIVE_TASK);

    // when / then
    assertThat(transformer.triggersUpdate(record)).isTrue();
    assertThat(transformer.triggersAdd(record)).isFalse();
    assertThat(transformer.triggersRemoval(record)).isFalse();
  }

  @Test
  void shouldNotTriggerRemovalForUnsupportedElementTypeOnCorrelated() {
    // given / when / then
    assertThat(
            transformer.triggersRemoval(
                recordWithElementType(
                    MessageSubscriptionIntent.CORRELATED, BpmnElementType.BOUNDARY_EVENT)))
        .isFalse();
  }

  @SuppressWarnings("unchecked")
  private Record<MessageSubscriptionRecordValue> recordWithElementType(
      final MessageSubscriptionIntent intent, final BpmnElementType elementType) {
    final MessageSubscriptionRecordValue value =
        ImmutableMessageSubscriptionRecordValue.builder()
            .from(factory.generateObject(MessageSubscriptionRecordValue.class))
            .withElementType(elementType)
            .build();
    return (Record<MessageSubscriptionRecordValue>)
        (Record<?>)
            factory.generateRecord(
                ValueType.MESSAGE_SUBSCRIPTION,
                r -> r.withRecordType(RecordType.EVENT).withIntent(intent).withValue(value));
  }
}
