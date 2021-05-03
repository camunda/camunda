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
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.engine.util.ZeebeStateRule;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.test.util.MsgPackUtil;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class MessageSubscriptionStateTest {

  @Rule public final ZeebeStateRule stateRule = new ZeebeStateRule();

  private MutableMessageSubscriptionState state;

  @Before
  public void setUp() {

    final MutableZeebeState zeebeState = stateRule.getZeebeState();
    state = zeebeState.getMessageSubscriptionState();
  }

  @Test
  public void shouldNotExistWithDifferentElementKey() {
    // given
    final var subscription = subscriptionWithElementInstanceKey(1);
    state.put(1L, subscription);

    // when
    final boolean exist =
        state.existSubscriptionForElementInstance(2, subscription.getMessageNameBuffer());

    // then
    assertThat(exist).isFalse();
  }

  @Test
  public void shouldNotExistWithDifferentMessageName() {
    // given
    final var subscription = subscriptionWithElementInstanceKey(1);
    state.put(1L, subscription);

    // when
    final boolean exist =
        state.existSubscriptionForElementInstance(
            subscription.getElementInstanceKey(), wrapString("\0"));

    // then
    assertThat(exist).isFalse();
  }

  @Test
  public void shouldExistSubscription() {
    // given
    final var subscription = subscriptionWithElementInstanceKey(1);
    state.put(1L, subscription);

    // when
    final boolean exist =
        state.existSubscriptionForElementInstance(
            subscription.getElementInstanceKey(), subscription.getMessageNameBuffer());

    // then
    assertThat(exist).isTrue();
  }

  @Test
  public void shouldVisitSubscription() {
    // given
    final var subscription = subscription("messageName", "correlationKey", 1);
    state.put(1L, subscription);

    // when
    final List<MessageSubscription> subscriptions = new ArrayList<>();
    state.visitSubscriptions(
        wrapString("messageName"), wrapString("correlationKey"), subscriptions::add);

    // then
    assertThat(subscriptions).hasSize(1);
    assertThat(subscriptions.get(0).getRecord().getProcessInstanceKey())
        .isEqualTo(subscription.getProcessInstanceKey());
    assertThat(subscriptions.get(0).getRecord().getElementInstanceKey())
        .isEqualTo(subscription.getElementInstanceKey());
    assertThat(subscriptions.get(0).getRecord().getMessageName())
        .isEqualTo(subscription.getMessageName());
    assertThat(subscriptions.get(0).getRecord().getCorrelationKey())
        .isEqualTo(subscription.getCorrelationKey());
    assertThat(subscriptions.get(0).getRecord().getVariables())
        .isEqualTo(subscription.getVariables());
    assertThat(subscriptions.get(0).getCommandSentTime()).isZero();
  }

  @Test
  public void shouldVisitSubscriptionsInOrder() {
    // given
    state.put(1L, subscription("messageName", "correlationKey", 1));
    state.put(2L, subscription("messageName", "correlationKey", 2));
    state.put(3L, subscription("otherMessageName", "correlationKey", 3));
    state.put(4L, subscription("messageName", "otherCorrelationKey", 4));

    // when
    final List<Long> keys = new ArrayList<>();
    state.visitSubscriptions(
        wrapString("messageName"),
        wrapString("correlationKey"),
        s -> keys.add(s.getRecord().getElementInstanceKey()));

    // then
    assertThat(keys).hasSize(2).containsExactly(1L, 2L);
  }

  @Test
  public void shouldVisitSubsctionsUntilStop() {
    // given
    state.put(1L, subscription("messageName", "correlationKey", 1));
    state.put(2L, subscription("messageName", "correlationKey", 2));

    // when
    final List<Long> keys = new ArrayList<>();
    state.visitSubscriptions(
        wrapString("messageName"),
        wrapString("correlationKey"),
        s -> {
          keys.add(s.getRecord().getElementInstanceKey());
          return false;
        });

    // then
    assertThat(keys).hasSize(1).contains(1L);
  }

  @Test
  public void shouldNoVisitMessageSubscriptionBeforeTime() {
    // given
    final var subscription1 = subscriptionWithElementInstanceKey(1L);
    state.put(1L, subscription1);
    state.updateToCorrelatingState(subscription1, 1_000);

    final var subscription2 = subscriptionWithElementInstanceKey(2L);
    state.put(2L, subscription2);
    state.updateToCorrelatingState(subscription2, 3_000);

    // then
    final List<Long> keys = new ArrayList<>();
    state.visitSubscriptionBefore(1_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).isEmpty();
  }

  @Test
  public void shouldVisitMessageSubscriptionBeforeTime() {
    // given
    final var subscription1 = subscriptionWithElementInstanceKey(1L);
    state.put(1L, subscription1);
    state.updateToCorrelatingState(subscription1, 1_000);

    final var subscription2 = subscriptionWithElementInstanceKey(2L);
    state.put(2L, subscription2);
    state.updateToCorrelatingState(subscription2, 3_000);

    // then
    final List<Long> keys = new ArrayList<>();
    state.visitSubscriptionBefore(2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);
  }

  @Test
  public void shouldFindMessageSubscriptionBeforeTimeInOrder() {
    // given
    final var subscription1 = subscriptionWithElementInstanceKey(1L);
    state.put(1L, subscription1);
    state.updateToCorrelatingState(subscription1, 1_000);

    final var subscription2 = subscriptionWithElementInstanceKey(2L);
    state.put(2L, subscription2);
    state.updateToCorrelatingState(subscription2, 2_000);

    // then
    final List<Long> keys = new ArrayList<>();
    state.visitSubscriptionBefore(3_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(2).containsExactly(1L, 2L);
  }

  @Test
  public void shouldNotVisitMessageSubscriptionIfSentTimeNotSet() {
    // given
    final var subscription1 = subscriptionWithElementInstanceKey(1L);
    state.put(1L, subscription1);
    state.updateToCorrelatingState(subscription1, 1_000);

    final var subscription2 = subscriptionWithElementInstanceKey(2L);
    state.put(2L, subscription2);

    // then
    final List<Long> keys = new ArrayList<>();
    state.visitSubscriptionBefore(2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);
  }

  @Test
  public void shouldUpdateMessageSubscriptionSentTime() {
    // given
    final var record = subscriptionWithElementInstanceKey(1L);
    state.put(1L, record);

    final var subscription =
        state.get(record.getElementInstanceKey(), record.getMessageNameBuffer());

    // when
    state.updateSentTime(subscription, 1_000);

    // then
    final List<Long> keys = new ArrayList<>();
    state.visitSubscriptionBefore(2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);

    // and
    state.updateSentTime(subscription, 1_500);

    keys.clear();
    state.visitSubscriptionBefore(2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);
  }

  @Test
  public void shouldUpdateCorrelationState() {
    // given
    final var subscription = subscriptionWithElementInstanceKey(1L);
    state.put(1L, subscription);

    assertThat(
            state
                .get(subscription.getElementInstanceKey(), subscription.getMessageNameBuffer())
                .isCorrelating())
        .isFalse();

    // when
    subscription.setVariables(MsgPackUtil.asMsgPack("{\"foo\":\"bar\"}")).setMessageKey(5L);
    state.updateToCorrelatingState(subscription, 1_000);

    // then
    assertThat(
            state
                .get(subscription.getElementInstanceKey(), subscription.getMessageNameBuffer())
                .isCorrelating())
        .isTrue();

    // and
    final List<MessageSubscription> subscriptions = new ArrayList<>();
    state.visitSubscriptions(
        subscription.getMessageNameBuffer(),
        subscription.getCorrelationKeyBuffer(),
        subscriptions::add);

    assertThat(subscriptions).hasSize(1);
    assertThat(subscriptions.get(0).getRecord().getVariables())
        .isEqualTo(subscription.getVariables());
    assertThat(subscriptions.get(0).getRecord().getMessageKey())
        .isEqualTo(subscription.getMessageKey());

    // and
    final List<Long> keys = new ArrayList<>();
    state.visitSubscriptionBefore(2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);
  }

  @Test
  public void shouldRemoveSubscription() {
    // given
    final var subscription = subscriptionWithElementInstanceKey(1L);
    state.put(1L, subscription);
    state.updateToCorrelatingState(subscription, 1_000);

    // when
    state.remove(1L, subscription.getMessageNameBuffer());

    // then
    final List<Long> keys = new ArrayList<>();
    state.visitSubscriptions(
        subscription.getMessageNameBuffer(),
        subscription.getCorrelationKeyBuffer(),
        s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).isEmpty();

    // and
    state.visitSubscriptionBefore(2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).isEmpty();

    // and
    assertThat(state.existSubscriptionForElementInstance(1L, subscription.getMessageNameBuffer()))
        .isFalse();
  }

  @Test
  public void shouldNotFailOnRemoveSubscriptionTwice() {
    // given
    final var subscription = subscriptionWithElementInstanceKey(1L);
    state.put(1L, subscription);

    // when
    state.remove(1L, subscription.getMessageNameBuffer());
    state.remove(1L, subscription.getMessageNameBuffer());

    // then
    assertThat(state.existSubscriptionForElementInstance(1L, subscription.getMessageNameBuffer()))
        .isFalse();
  }

  @Test
  public void shouldNotRemoveSubscriptionOnDifferentKey() {
    // given
    state.put(1L, subscription("messageName", "correlationKey", 1L));
    state.put(2L, subscription("messageName", "correlationKey", 2L));

    // when
    state.remove(2L, wrapString("messageName"));

    // then
    final List<Long> keys = new ArrayList<>();
    state.visitSubscriptions(
        wrapString("messageName"),
        wrapString("correlationKey"),
        s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);
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
