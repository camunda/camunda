/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.message;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableTransientProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.util.ZeebeStateRule;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class TransientProcessMessageSubscriptionStateTest {

  @Rule public final ZeebeStateRule stateRule = new ZeebeStateRule();

  private MutableProcessMessageSubscriptionState persistentState;
  private MutableTransientProcessMessageSubscriptionState transientState;

  @Before
  public void setUp() {
    persistentState = stateRule.getZeebeState().getProcessMessageSubscriptionState();
    transientState = stateRule.getZeebeState().getTransientProcessMessageSubscriptionState();
  }

  @Test
  public void shouldNoVisitSubscriptionBeforeTime() {
    // given
    final ProcessMessageSubscriptionRecord record1 = subscriptionRecordWithElementInstanceKey(1L);
    persistentState.put(1L, record1);
    transientState.updateCommandSentTime(record1, 1_000L);

    final ProcessMessageSubscriptionRecord record2 = subscriptionRecordWithElementInstanceKey(2L);
    transientState.updateCommandSentTime(record2, 3_000L);

    // then
    final List<Long> keys = new ArrayList<>();
    transientState.visitSubscriptionBefore(
        1_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).isEmpty();
  }

  @Test
  public void shouldVisitSubscriptionBeforeTime() {
    // given
    final ProcessMessageSubscriptionRecord record1 = subscriptionRecordWithElementInstanceKey(1L);
    persistentState.put(1L, record1);
    transientState.updateCommandSentTime(record1, 1_000L);

    final ProcessMessageSubscriptionRecord record2 = subscriptionRecordWithElementInstanceKey(2L);
    persistentState.put(2L, record2);
    transientState.updateCommandSentTime(record2, 3_000L);

    // then
    final List<Long> keys = new ArrayList<>();
    transientState.visitSubscriptionBefore(
        2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);
  }

  @Test
  public void shouldFindSubscriptionBeforeTimeInOrder() {
    // given
    final ProcessMessageSubscriptionRecord record1 = subscriptionRecordWithElementInstanceKey(1L);
    persistentState.put(1L, record1);
    transientState.updateCommandSentTime(record1, 1_000L);

    final ProcessMessageSubscriptionRecord record2 = subscriptionRecordWithElementInstanceKey(2L);
    persistentState.put(2L, record2);
    transientState.updateCommandSentTime(record2, 2_000L);

    // then
    final List<Long> keys = new ArrayList<>();
    transientState.visitSubscriptionBefore(
        3_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(2).containsExactly(1L, 2L);
  }

  @Test
  public void shouldNotVisitSubscriptionIfOpened() {
    // given
    final ProcessMessageSubscriptionRecord record1 = subscriptionRecordWithElementInstanceKey(1L);
    persistentState.put(1L, record1);
    transientState.updateCommandSentTime(record1, 1_000L);

    final ProcessMessageSubscriptionRecord record2 = subscriptionRecordWithElementInstanceKey(2L);
    persistentState.put(2L, record2);
    transientState.updateCommandSentTime(record2, 2_000L);

    persistentState.updateToOpenedState(record2.setSubscriptionPartitionId(3));

    // then
    final List<Long> keys = new ArrayList<>();
    transientState.visitSubscriptionBefore(
        2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);
  }

  @Test
  public void shouldUpdateSubscriptionSentTime() {
    // given
    final ProcessMessageSubscriptionRecord record = subscriptionRecordWithElementInstanceKey(1L);

    // when
    persistentState.put(1L, record);
    transientState.updateCommandSentTime(record, 1_000L);

    // then
    final List<Long> keys = new ArrayList<>();
    transientState.visitSubscriptionBefore(
        2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);

    // and
    final ProcessMessageSubscription existingSubscription =
        persistentState.getSubscription(
            record.getElementInstanceKey(), record.getMessageNameBuffer());
    transientState.updateCommandSentTime(existingSubscription.getRecord(), 1_500);

    keys.clear();
    transientState.visitSubscriptionBefore(
        2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);
  }

  @Test
  public void shouldUpdateOpenState() {
    // given
    final ProcessMessageSubscriptionRecord record = subscriptionRecordWithElementInstanceKey(1L);
    persistentState.put(1L, record);
    transientState.updateCommandSentTime(record, 1_000L);

    final ProcessMessageSubscription subscription =
        persistentState.getSubscription(
            record.getElementInstanceKey(), record.getMessageNameBuffer());

    Assertions.assertThat(subscription.isOpening()).isTrue();

    // when
    persistentState.updateToOpenedState(record.setSubscriptionPartitionId(3));

    // then
    final ProcessMessageSubscription updatedSubscription =
        persistentState.getSubscription(
            record.getElementInstanceKey(), record.getMessageNameBuffer());
    Assertions.assertThat(updatedSubscription.isOpening()).isFalse();

    // and
    assertThat(updatedSubscription.getRecord().getSubscriptionPartitionId()).isEqualTo(3);

    // and
    final List<Long> keys = new ArrayList<>();
    transientState.visitSubscriptionBefore(
        2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).isEmpty();
  }

  @Test
  public void shouldUpdateCloseState() {
    // given
    final ProcessMessageSubscriptionRecord record = subscriptionRecordWithElementInstanceKey(1L);
    persistentState.put(1L, record);
    transientState.updateCommandSentTime(record, 1_000L);

    persistentState.updateToOpenedState(record.setSubscriptionPartitionId(3));
    final ProcessMessageSubscription subscription =
        persistentState.getSubscription(
            record.getElementInstanceKey(), record.getMessageNameBuffer());

    Assertions.assertThat(subscription.isClosing()).isFalse();

    // when
    persistentState.updateToClosingState(record);
    transientState.updateCommandSentTime(record, 1_000L);

    // then
    final ProcessMessageSubscription updatedSubscription =
        persistentState.getSubscription(
            record.getElementInstanceKey(), record.getMessageNameBuffer());
    assertThat(updatedSubscription.isClosing()).isTrue();

    // and
    final List<Long> keys = new ArrayList<>();
    transientState.visitSubscriptionBefore(
        2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);
  }

  @Test
  public void shouldRemoveSubscription() {
    // given
    final ProcessMessageSubscriptionRecord record = subscriptionRecordWithElementInstanceKey(1L);
    persistentState.put(1L, record);
    transientState.updateCommandSentTime(record, 1_000L);

    // when
    persistentState.remove(1L, record.getMessageNameBuffer());

    // then
    final List<Long> keys = new ArrayList<>();
    transientState.visitSubscriptionBefore(
        2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).isEmpty();

    // and
    assertThat(
            persistentState.existSubscriptionForElementInstance(1L, record.getMessageNameBuffer()))
        .isFalse();
  }

  private ProcessMessageSubscriptionRecord subscriptionRecordWithElementInstanceKey(
      final long elementInstanceKey) {
    return subscriptionRecord("handler", "messageName", "correlationKey", elementInstanceKey);
  }

  private ProcessMessageSubscriptionRecord subscriptionRecord(
      final String name, final String correlationKey, final long elementInstanceKey) {
    return subscriptionRecord("handler", name, correlationKey, elementInstanceKey);
  }

  private ProcessMessageSubscriptionRecord subscriptionRecord(
      final String handlerId,
      final String name,
      final String correlationKey,
      final long elementInstanceKey) {
    return new ProcessMessageSubscriptionRecord()
        .setProcessInstanceKey(1L)
        .setElementInstanceKey(elementInstanceKey)
        .setBpmnProcessId(wrapString("process"))
        .setElementId(wrapString(handlerId))
        .setMessageName(wrapString(name))
        .setCorrelationKey(wrapString(correlationKey))
        .setInterrupting(true)
        .setSubscriptionPartitionId(1);
  }
}
