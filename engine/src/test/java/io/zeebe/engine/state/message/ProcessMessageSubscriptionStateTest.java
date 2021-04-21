/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.message;

import static io.zeebe.util.buffer.BufferUtil.cloneBuffer;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.state.mutable.MutableProcessMessageSubscriptionState;
import io.zeebe.engine.util.ZeebeStateRule;
import io.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.zeebe.util.collection.Tuple;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class ProcessMessageSubscriptionStateTest {

  @Rule public final ZeebeStateRule stateRule = new ZeebeStateRule();

  private MutableProcessMessageSubscriptionState state;

  @Before
  public void setUp() {
    state = stateRule.getZeebeState().getProcessMessageSubscriptionState();
  }

  @Test
  public void shouldNotExist() {
    // given
    final ProcessMessageSubscriptionRecord record = subscriptionRecordWithElementInstanceKey(1);
    state.put(record, 1_000L);

    // when
    final boolean exist =
        state.existSubscriptionForElementInstance(2, record.getMessageNameBuffer());

    // then
    assertThat(exist).isFalse();
  }

  @Test
  public void shouldExistSubscription() {
    // given
    final ProcessMessageSubscriptionRecord record = subscriptionRecordWithElementInstanceKey(1);
    state.put(record, 1_000L);

    // when
    final boolean exist =
        state.existSubscriptionForElementInstance(1, record.getMessageNameBuffer());

    // then
    assertThat(exist).isTrue();
  }

  @Test
  public void shouldNoVisitSubscriptionBeforeTime() {
    // given
    final ProcessMessageSubscriptionRecord record1 = subscriptionRecordWithElementInstanceKey(1L);
    state.put(record1, 1_000L);

    final ProcessMessageSubscriptionRecord record2 = subscriptionRecordWithElementInstanceKey(2L);
    state.put(record2, 3_000L);

    // then
    final List<Long> keys = new ArrayList<>();
    state.visitSubscriptionBefore(1_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).isEmpty();
  }

  @Test
  public void shouldVisitSubscriptionBeforeTime() {
    // given
    final ProcessMessageSubscriptionRecord record1 = subscriptionRecordWithElementInstanceKey(1L);
    state.put(record1, 1_000L);

    final ProcessMessageSubscriptionRecord record2 = subscriptionRecordWithElementInstanceKey(2L);
    state.put(record2, 3_000L);

    // then
    final List<Long> keys = new ArrayList<>();
    state.visitSubscriptionBefore(2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);
  }

  @Test
  public void shouldFindSubscriptionBeforeTimeInOrder() {
    // given
    final ProcessMessageSubscriptionRecord record1 = subscriptionRecordWithElementInstanceKey(1L);
    state.put(record1, 1_000L);

    final ProcessMessageSubscriptionRecord record2 = subscriptionRecordWithElementInstanceKey(2L);
    state.put(record2, 2_000L);

    // then
    final List<Long> keys = new ArrayList<>();
    state.visitSubscriptionBefore(3_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(2).containsExactly(1L, 2L);
  }

  @Test
  public void shouldNotVisitSubscriptionIfOpened() {
    // given
    final ProcessMessageSubscriptionRecord record1 = subscriptionRecordWithElementInstanceKey(1L);
    state.put(record1, 1_000L);

    final ProcessMessageSubscriptionRecord record2 = subscriptionRecordWithElementInstanceKey(2L);
    state.put(record2, 2_000L);
    state.updateToOpenedState(record2.setSubscriptionPartitionId(3));

    // then
    final List<Long> keys = new ArrayList<>();
    state.visitSubscriptionBefore(2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);
  }

  @Test
  public void shouldUpdateSubscriptionSentTime() {
    // given
    final ProcessMessageSubscriptionRecord record = subscriptionRecordWithElementInstanceKey(1L);

    // when
    state.put(record, 1_000L);

    // then
    final List<Long> keys = new ArrayList<>();
    state.visitSubscriptionBefore(2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);

    // and
    final ProcessMessageSubscription existingSubscription =
        state.getSubscription(record.getElementInstanceKey(), record.getMessageNameBuffer());
    state.updateSentTimeInTransaction(existingSubscription, 1_500);

    keys.clear();
    state.visitSubscriptionBefore(2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);
  }

  @Test
  public void shouldUpdateOpenState() {
    // given
    final ProcessMessageSubscriptionRecord record = subscriptionRecordWithElementInstanceKey(1L);
    state.put(record, 1_000L);
    final ProcessMessageSubscription subscription =
        state.getSubscription(record.getElementInstanceKey(), record.getMessageNameBuffer());

    Assertions.assertThat(subscription.isOpening()).isTrue();

    // when
    state.updateToOpenedState(record.setSubscriptionPartitionId(3));

    // then
    final ProcessMessageSubscription updatedSubscription =
        state.getSubscription(record.getElementInstanceKey(), record.getMessageNameBuffer());
    Assertions.assertThat(updatedSubscription.isOpening()).isFalse();

    // and
    assertThat(updatedSubscription.getRecord().getSubscriptionPartitionId()).isEqualTo(3);

    // and
    final List<Long> keys = new ArrayList<>();
    state.visitSubscriptionBefore(2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).isEmpty();
  }

  @Test
  public void shouldUpdateCloseState() {
    // given
    final ProcessMessageSubscriptionRecord record = subscriptionRecordWithElementInstanceKey(1L);
    state.put(record, 1_000L);
    state.updateToOpenedState(record.setSubscriptionPartitionId(3));
    final ProcessMessageSubscription subscription =
        state.getSubscription(record.getElementInstanceKey(), record.getMessageNameBuffer());

    Assertions.assertThat(subscription.isClosing()).isFalse();

    // when
    state.updateToClosingState(record, 1_000);

    // then
    final ProcessMessageSubscription updatedSubscription =
        state.getSubscription(record.getElementInstanceKey(), record.getMessageNameBuffer());
    assertThat(updatedSubscription.isClosing()).isTrue();

    // and
    final List<Long> keys = new ArrayList<>();
    state.visitSubscriptionBefore(2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);
  }

  @Test
  public void shouldRemoveSubscription() {
    // given
    final ProcessMessageSubscriptionRecord record = subscriptionRecordWithElementInstanceKey(1L);
    state.put(record, 1_000L);

    // when
    state.remove(1L, record.getMessageNameBuffer());

    // then
    final List<Long> keys = new ArrayList<>();
    state.visitSubscriptionBefore(2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).isEmpty();

    // and
    assertThat(state.existSubscriptionForElementInstance(1L, record.getMessageNameBuffer()))
        .isFalse();
  }

  @Test
  public void shouldNotFailOnRemoveSubscriptionTwice() {
    // given
    final ProcessMessageSubscriptionRecord record = subscriptionRecordWithElementInstanceKey(1L);
    state.put(record, 1_000L);

    // when
    state.remove(1L, record.getMessageNameBuffer());
    state.remove(1L, record.getMessageNameBuffer());

    // then
    assertThat(state.existSubscriptionForElementInstance(1L, record.getMessageNameBuffer()))
        .isFalse();
  }

  @Test
  public void shouldNotRemoveSubscriptionOnDifferentKey() {
    // given
    state.put(subscriptionRecord("messageName", "correlationKey", 1L), 1_000L);
    state.put(subscriptionRecord("messageName", "correlationKey", 2L), 1_000L);

    // when
    state.remove(2L, wrapString("messageName"));

    // then
    assertThat(state.existSubscriptionForElementInstance(1L, wrapString("messageName"))).isTrue();
  }

  @Test
  public void shouldVisitAllSubscriptionsInTheState() {
    // given
    state.put(subscriptionRecord("message1", "correlationKey", 1L), 1_000L);
    state.put(subscriptionRecord("message2", "correlationKey", 1L), 1_000L);
    state.put(subscriptionRecord("message3", "correlationKey", 2L), 1_000L);

    // when
    final List<Tuple<Long, DirectBuffer>> visited = new ArrayList<>();
    state.visitElementSubscriptions(
        1L,
        s ->
            visited.add(
                new Tuple<>(
                    s.getRecord().getElementInstanceKey(),
                    cloneBuffer(s.getRecord().getMessageNameBuffer()))));

    // then
    assertThat(visited)
        .containsExactly(
            new Tuple<>(1L, wrapString("message1")), new Tuple<>(1L, wrapString("message2")));
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
