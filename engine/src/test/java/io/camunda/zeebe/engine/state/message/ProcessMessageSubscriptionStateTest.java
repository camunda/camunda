/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.message;

import static io.camunda.zeebe.util.buffer.BufferUtil.cloneBuffer;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.util.ProcessingStateRule;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class ProcessMessageSubscriptionStateTest {

  @Rule public final ProcessingStateRule stateRule = new ProcessingStateRule();

  private MutableProcessMessageSubscriptionState state;

  @Before
  public void setUp() {
    state = stateRule.getProcessingState().getProcessMessageSubscriptionState();
  }

  @Test
  public void shouldNotExist() {
    // given
    final ProcessMessageSubscriptionRecord record = subscriptionRecordWithElementInstanceKey(1);
    state.put(1L, record);

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
    state.put(1L, record);

    // when
    final boolean exist =
        state.existSubscriptionForElementInstance(1, record.getMessageNameBuffer());

    // then
    assertThat(exist).isTrue();
  }

  @Test
  public void shouldGetSubscription() {
    // given
    final ProcessMessageSubscriptionRecord record = subscriptionRecordWithElementInstanceKey(1);
    state.put(1L, record);

    // when
    final var subscription =
        state.getSubscription(record.getElementInstanceKey(), record.getMessageNameBuffer());

    // then
    assertThat(subscription).isNotNull();
    assertThat(subscription.getKey()).isEqualTo(1L);
    assertThat(subscription.getRecord()).isEqualTo(record);
  }

  @Test
  public void shouldNotFailOnRemoveSubscriptionTwice() {
    // given
    final ProcessMessageSubscriptionRecord record = subscriptionRecordWithElementInstanceKey(1L);
    state.put(1L, record);

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
    state.put(1L, subscriptionRecord("messageName", "correlationKey", 1L));
    state.put(2L, subscriptionRecord("messageName", "correlationKey", 2L));

    // when
    state.remove(2L, wrapString("messageName"));

    // then
    assertThat(state.existSubscriptionForElementInstance(1L, wrapString("messageName"))).isTrue();
  }

  @Test
  public void shouldVisitAllSubscriptionsInTheState() {
    // given
    state.put(1L, subscriptionRecord("message1", "correlationKey", 1L));
    state.put(2L, subscriptionRecord("message2", "correlationKey", 1L));
    state.put(3L, subscriptionRecord("message3", "correlationKey", 2L));

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

  @Test
  public void shouldRemoveSubscription() {
    // given
    final ProcessMessageSubscriptionRecord record = subscriptionRecordWithElementInstanceKey(1L);
    state.put(1L, record);

    // when
    state.remove(1L, record.getMessageNameBuffer());

    final var subscription =
        state.getSubscription(record.getElementInstanceKey(), record.getMessageNameBuffer());

    // then
    assertThat(subscription).isNull();
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
