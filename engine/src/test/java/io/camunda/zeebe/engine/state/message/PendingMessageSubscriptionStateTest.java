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

import io.camunda.zeebe.engine.state.mutable.MutableMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutablePendingMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateRule;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.test.util.MsgPackUtil;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class PendingMessageSubscriptionStateTest {

  @Rule public final ProcessingStateRule stateRule = new ProcessingStateRule();

  private MutableMessageSubscriptionState persistentState;
  private MutablePendingMessageSubscriptionState transientState;

  @Before
  public void setUp() {

    final MutableProcessingState processingState = stateRule.getProcessingState();
    persistentState = processingState.getMessageSubscriptionState();
    transientState = processingState.getPendingMessageSubscriptionState();
  }

  @Test
  public void shouldNoVisitMessageSubscriptionBeforeTime() {
    // given
    final var subscription1 = subscriptionWithElementInstanceKey(1L);
    persistentState.put(1L, subscription1);
    persistentState.updateToCorrelatingState(subscription1);
    transientState.updateCommandSentTime(subscription1, 1_000);

    final var subscription2 = subscriptionWithElementInstanceKey(2L);
    persistentState.put(2L, subscription2);
    persistentState.updateToCorrelatingState(subscription2);
    transientState.updateCommandSentTime(subscription2, 3_000);

    // then
    final List<Long> keys = new ArrayList<>();
    transientState.visitSubscriptionBefore(
        1_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).isEmpty();
  }

  @Test
  public void shouldVisitMessageSubscriptionBeforeTime() {
    // given
    final var subscription1 = subscriptionWithElementInstanceKey(1L);
    persistentState.put(1L, subscription1);
    persistentState.updateToCorrelatingState(subscription1);
    transientState.updateCommandSentTime(subscription1, 1_000);

    final var subscription2 = subscriptionWithElementInstanceKey(2L);
    persistentState.put(2L, subscription2);
    persistentState.updateToCorrelatingState(subscription2);
    transientState.updateCommandSentTime(subscription2, 3_000);

    // then
    final List<Long> keys = new ArrayList<>();
    transientState.visitSubscriptionBefore(
        2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);
  }

  @Test
  public void shouldFindMessageSubscriptionBeforeTimeInOrder() {
    // given
    final var subscription1 = subscriptionWithElementInstanceKey(1L);
    persistentState.put(1L, subscription1);
    persistentState.updateToCorrelatingState(subscription1);
    transientState.updateCommandSentTime(subscription1, 1_000);

    final var subscription2 = subscriptionWithElementInstanceKey(2L);
    persistentState.put(2L, subscription2);
    persistentState.updateToCorrelatingState(subscription2);
    transientState.updateCommandSentTime(subscription2, 2_000);

    // then
    final List<Long> keys = new ArrayList<>();
    transientState.visitSubscriptionBefore(
        3_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(2).containsExactly(1L, 2L);
  }

  @Test
  public void shouldNotVisitMessageSubscriptionIfSentTimeNotSet() {
    // given
    final var subscription1 = subscriptionWithElementInstanceKey(1L);
    persistentState.put(1L, subscription1);
    persistentState.updateToCorrelatingState(subscription1);
    transientState.updateCommandSentTime(subscription1, 1_000);

    final var subscription2 = subscriptionWithElementInstanceKey(2L);
    persistentState.put(2L, subscription2);

    // then
    final List<Long> keys = new ArrayList<>();
    transientState.visitSubscriptionBefore(
        2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);
  }

  @Test
  public void shouldUpdateMessageSubscriptionSentTime() {
    // given
    final var record = subscriptionWithElementInstanceKey(1L);
    persistentState.put(1L, record);

    final var subscription =
        persistentState.get(record.getElementInstanceKey(), record.getMessageNameBuffer());

    // when
    persistentState.updateToCorrelatingState(subscription.getRecord());
    transientState.updateCommandSentTime(subscription.getRecord(), 1_000);

    // then
    final List<Long> keys = new ArrayList<>();
    transientState.visitSubscriptionBefore(
        2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);

    // and
    transientState.updateCommandSentTime(subscription.getRecord(), 1_500);

    keys.clear();
    transientState.visitSubscriptionBefore(
        2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);
  }

  @Test
  public void shouldUpdateCorrelationState() {
    // given
    final var subscription = subscriptionWithElementInstanceKey(1L);
    persistentState.put(1L, subscription);

    assertThat(
            persistentState
                .get(subscription.getElementInstanceKey(), subscription.getMessageNameBuffer())
                .isCorrelating())
        .isFalse();

    // when
    subscription.setVariables(MsgPackUtil.asMsgPack("{\"foo\":\"bar\"}")).setMessageKey(5L);
    persistentState.updateToCorrelatingState(subscription);
    transientState.updateCommandSentTime(subscription, 1_000);

    // then
    assertThat(
            persistentState
                .get(subscription.getElementInstanceKey(), subscription.getMessageNameBuffer())
                .isCorrelating())
        .isTrue();

    // and
    final List<MessageSubscription> subscriptions = new ArrayList<>();
    persistentState.visitSubscriptions(
        subscription.getMessageNameBuffer(),
        subscription.getCorrelationKeyBuffer(),
        subscriptions::add);

    Assertions.assertThat(subscriptions).hasSize(1);
    assertThat(subscriptions.get(0).getRecord().getVariables())
        .isEqualTo(subscription.getVariables());
    assertThat(subscriptions.get(0).getRecord().getMessageKey())
        .isEqualTo(subscription.getMessageKey());

    // and
    final List<Long> keys = new ArrayList<>();
    transientState.visitSubscriptionBefore(
        2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);
  }

  @Test
  public void shouldRemoveSubscription() {
    // given
    final var subscription = subscriptionWithElementInstanceKey(1L);
    persistentState.put(1L, subscription);
    persistentState.updateToCorrelatingState(subscription);
    transientState.updateCommandSentTime(subscription, 1_000);

    // when
    persistentState.remove(1L, subscription.getMessageNameBuffer());

    // then
    final List<Long> keys = new ArrayList<>();
    persistentState.visitSubscriptions(
        subscription.getMessageNameBuffer(),
        subscription.getCorrelationKeyBuffer(),
        s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).isEmpty();

    // and
    transientState.visitSubscriptionBefore(
        2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).isEmpty();

    // and
    assertThat(
            persistentState.existSubscriptionForElementInstance(
                1L, subscription.getMessageNameBuffer()))
        .isFalse();
  }

  private MessageSubscriptionRecord subscriptionWithElementInstanceKey(
      final long elementInstanceKey) {
    return subscription("messageName", "correlationKey", elementInstanceKey);
  }

  private MessageSubscriptionRecord subscription(
      final String name, final String correlationKey, final long elementInstanceKey) {
    return new MessageSubscriptionRecord()
        .setProcessInstanceKey(1L)
        .setElementInstanceKey(elementInstanceKey)
        .setBpmnProcessId(wrapString("process"))
        .setMessageName(wrapString(name))
        .setCorrelationKey(wrapString(correlationKey))
        .setInterrupting(true);
  }
}
