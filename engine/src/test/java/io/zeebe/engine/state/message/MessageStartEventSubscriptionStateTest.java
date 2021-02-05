/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.message;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.state.mutable.MutableMessageStartEventSubscriptionState;
import io.zeebe.engine.util.ZeebeStateRule;
import io.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class MessageStartEventSubscriptionStateTest {

  @Rule public final ZeebeStateRule stateRule = new ZeebeStateRule();

  private MutableMessageStartEventSubscriptionState state;

  @Before
  public void setUp() {
    state = stateRule.getZeebeState().getMessageStartEventSubscriptionState();
  }

  @Test
  public void shouldExistAfterPut() {
    final MessageStartEventSubscriptionRecord subscription =
        createSubscription("messageName", "startEventID", 1);
    state.put(subscription);
    assertThat(state.exists(subscription)).isTrue();
  }

  @Test
  public void shouldNotExistForDifferentKey() {
    final MessageStartEventSubscriptionRecord subscription =
        createSubscription("messageName", "startEventID", 1);
    state.put(subscription);

    subscription.setWorkflowKey(2);
    assertThat(state.exists(subscription)).isFalse();
  }

  @Test
  public void shouldVisitForMessageNames() {
    final MessageStartEventSubscriptionRecord subscription1 =
        createSubscription("message", "startEvent1", 1);
    state.put(subscription1);

    // more subscriptions for same message
    final MessageStartEventSubscriptionRecord subscription2 =
        createSubscription("message", "startEvent2", 2);
    state.put(subscription2);

    final MessageStartEventSubscriptionRecord subscription3 =
        createSubscription("message", "startEvent3", 3);
    state.put(subscription3);

    // should not visit other message
    final MessageStartEventSubscriptionRecord subscription4 =
        createSubscription("message-other", "startEvent4", 3);
    state.put(subscription4);

    final List<String> visitedStartEvents = new ArrayList<>();

    state.visitSubscriptionsByMessageName(
        wrapString("message"),
        subscription -> {
          visitedStartEvents.add(bufferAsString(subscription.getStartEventIdBuffer()));
        });

    assertThat(visitedStartEvents.size()).isEqualTo(3);
    assertThat(visitedStartEvents)
        .containsExactlyInAnyOrder("startEvent1", "startEvent2", "startEvent3");
  }

  @Test
  public void shouldNotExistAfterRemove() {
    final MessageStartEventSubscriptionRecord subscription1 =
        createSubscription("message1", "startEvent1", 1);
    state.put(subscription1);

    // more subscriptions for same workflow
    final MessageStartEventSubscriptionRecord subscription2 =
        createSubscription("message2", "startEvent2", 1);
    state.put(subscription2);

    final MessageStartEventSubscriptionRecord subscription3 =
        createSubscription("message3", "startEvent3", 1);
    state.put(subscription3);

    state.removeSubscriptionsOfWorkflow(1);

    assertThat(state.exists(subscription1)).isFalse();
    assertThat(state.exists(subscription2)).isFalse();
    assertThat(state.exists(subscription3)).isFalse();
  }

  @Test
  public void shouldNotRemoveOtherKeys() {
    final MessageStartEventSubscriptionRecord subscription1 =
        createSubscription("message1", "startEvent1", 1);
    state.put(subscription1);

    final MessageStartEventSubscriptionRecord subscription2 =
        createSubscription("message1", "startEvent1", 4);
    state.put(subscription2);

    state.removeSubscriptionsOfWorkflow(1);

    assertThat(state.exists(subscription1)).isFalse();
    assertThat(state.exists(subscription2)).isTrue();
  }

  @Test
  public void shouldNotOverwritePreviousRecord() {
    // given
    final long key = 1L;
    final MessageStartEventSubscriptionRecord writtenRecord =
        createSubscription("msg", "start", key);

    // when
    state.put(writtenRecord);
    writtenRecord.setMessageName(BufferUtil.wrapString("foo"));

    // then
    state.visitSubscriptionsByMessageName(
        BufferUtil.wrapString("msg"),
        readRecord -> {
          assertThat(readRecord.getMessageNameBuffer())
              .isNotEqualTo(writtenRecord.getMessageNameBuffer());
          assertThat(readRecord.getMessageNameBuffer()).isEqualTo(BufferUtil.wrapString("msg"));
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
        .setWorkflowKey(key);
  }
}
