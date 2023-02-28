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
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateRule;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class MessageSubscriptionStateTest {

  @Rule public final ProcessingStateRule stateRule = new ProcessingStateRule();

  private MutableMessageSubscriptionState state;

  @Before
  public void setUp() {

    final MutableProcessingState processingState = stateRule.getProcessingState();
    state = processingState.getMessageSubscriptionState();
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
    assertThat(subscriptions.get(0).isCorrelating()).isFalse();
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
