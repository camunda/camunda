/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.message;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.state.mutable.MutableMessageStartEventSubscriptionState;
import io.camunda.zeebe.engine.util.ProcessingStateRule;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.agrona.collections.MutableReference;
import org.assertj.core.api.Assertions;
import org.assertj.core.groups.Tuple;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class MessageStartEventSubscriptionStateTest {

  @Rule public final ProcessingStateRule stateRule = new ProcessingStateRule();

  private MutableMessageStartEventSubscriptionState state;

  @Before
  public void setUp() {
    state = stateRule.getProcessingState().getMessageStartEventSubscriptionState();
  }

  @Test
  public void shouldExistAfterPut() {
    final MessageStartEventSubscriptionRecord subscription =
        createSubscription("messageName", "startEventID", 1);
    state.put(1L, subscription);
    assertThat(state.exists(subscription)).isTrue();
  }

  @Test
  public void shouldNotExistForDifferentKey() {
    final MessageStartEventSubscriptionRecord subscription =
        createSubscription("messageName", "startEventID", 1);
    state.put(1L, subscription);

    subscription.setProcessDefinitionKey(2);
    assertThat(state.exists(subscription)).isFalse();
  }

  @Test
  public void shouldStoreSubscriptionWithKey() {
    // given
    final MessageStartEventSubscriptionRecord subscription =
        createSubscription("messageName", "startEventID", 1);

    // then
    state.put(1L, subscription);

    // when
    final var storedSubscription = new MutableReference<MessageStartEventSubscription>();
    state.visitSubscriptionsByMessageName(
        subscription.getMessageNameBuffer(), storedSubscription::set);

    assertThat(storedSubscription).isNotNull();
    assertThat(storedSubscription.get().getKey()).isEqualTo(1L);
    assertThat(storedSubscription.get().getRecord()).isEqualTo(subscription);
  }

  @Test
  public void shouldVisitForMessageNames() {
    final MessageStartEventSubscriptionRecord subscription1 =
        createSubscription("message", "startEvent1", 1);
    state.put(1L, subscription1);

    // more subscriptions for same message
    final MessageStartEventSubscriptionRecord subscription2 =
        createSubscription("message", "startEvent2", 2);
    state.put(2L, subscription2);

    final MessageStartEventSubscriptionRecord subscription3 =
        createSubscription("message", "startEvent3", 3);
    state.put(3L, subscription3);

    // should not visit other message
    final MessageStartEventSubscriptionRecord subscription4 =
        createSubscription("message-other", "startEvent4", 3);
    state.put(4L, subscription4);

    final List<String> visitedStartEvents = new ArrayList<>();

    state.visitSubscriptionsByMessageName(
        wrapString("message"),
        subscription -> {
          visitedStartEvents.add(bufferAsString(subscription.getRecord().getStartEventIdBuffer()));
        });

    assertThat(visitedStartEvents.size()).isEqualTo(3);
    assertThat(visitedStartEvents)
        .containsExactlyInAnyOrder("startEvent1", "startEvent2", "startEvent3");
  }

  @Test
  public void shouldVisitForProcessDefinitionKey() {
    final MessageStartEventSubscriptionRecord subscription1 =
        createSubscription("message1", "startEvent1", 1);
    state.put(1L, subscription1);

    // more subscriptions for same process definition
    final MessageStartEventSubscriptionRecord subscription2 =
        createSubscription("message2", "startEvent2", 1);
    state.put(2L, subscription2);

    // should not visit with other process definition
    final MessageStartEventSubscriptionRecord subscription3 =
        createSubscription("message3", "startEvent3", 2);
    state.put(3L, subscription3);

    final List<Tuple> visitedSubscriptions = new ArrayList<>();

    state.visitSubscriptionsByProcessDefinition(
        1,
        subscription -> {
          visitedSubscriptions.add(
              tuple(subscription.getKey(), subscription.getRecord().getMessageName()));
        });

    assertThat(visitedSubscriptions)
        .hasSize(2)
        .containsExactlyInAnyOrder(tuple(1L, "message1"), tuple(2L, "message2"));
  }

  @Test
  public void shouldNotExistAfterRemove() {
    final MessageStartEventSubscriptionRecord subscription1 =
        createSubscription("message1", "startEvent1", 1);
    state.put(1L, subscription1);

    final MessageStartEventSubscriptionRecord subscription2 =
        createSubscription("message2", "startEvent2", 2);
    state.put(2L, subscription2);

    state.remove(1L, wrapString("message1"));
    state.remove(2L, wrapString("message2"));

    assertThat(state.exists(subscription1)).isFalse();
    assertThat(state.exists(subscription2)).isFalse();
  }

  @Test
  public void shouldNotRemoveOtherSubscriptions() {
    final MessageStartEventSubscriptionRecord subscription1 =
        createSubscription("message1", "startEvent1", 1);
    state.put(1L, subscription1);

    final MessageStartEventSubscriptionRecord subscription2 =
        createSubscription("message2", "startEvent2", 1);
    state.put(2L, subscription2);

    final MessageStartEventSubscriptionRecord subscription3 =
        createSubscription("message1", "startEvent1", 2);
    state.put(3L, subscription3);

    state.remove(1L, wrapString("message1"));

    assertThat(state.exists(subscription1)).isFalse();
    assertThat(state.exists(subscription2)).isTrue();
    assertThat(state.exists(subscription3)).isTrue();
  }

  @Test
  public void shouldNotOverwritePreviousRecord() {
    // given
    final long key = 1L;
    final MessageStartEventSubscriptionRecord writtenRecord =
        createSubscription("msg", "start", key);

    // when
    state.put(key, writtenRecord);
    writtenRecord.setMessageName(BufferUtil.wrapString("foo"));

    // then
    state.visitSubscriptionsByMessageName(
        BufferUtil.wrapString("msg"),
        readRecord -> {
          assertThat(readRecord.getRecord().getMessageNameBuffer())
              .isNotEqualTo(writtenRecord.getMessageNameBuffer());
          assertThat(readRecord.getRecord().getMessageNameBuffer())
              .isEqualTo(BufferUtil.wrapString("msg"));
          Assertions.assertThat(writtenRecord.getMessageNameBuffer())
              .isEqualTo(BufferUtil.wrapString("foo"));
        });

    final MessageStartEventSubscriptionRecord secondSub = createSubscription("msg", "start", 23);
    state.exists(secondSub);
    Assertions.assertThat(writtenRecord.getMessageNameBuffer())
        .isEqualTo(BufferUtil.wrapString("foo"));
  }

  private MessageStartEventSubscriptionRecord createSubscription(
      final String messageName, final String startEventId, final long key) {
    return new MessageStartEventSubscriptionRecord()
        .setStartEventId(wrapString(startEventId))
        .setMessageName(wrapString(messageName))
        .setProcessDefinitionKey(key);
  }
}
