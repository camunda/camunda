/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.message;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.mutable.MutableMessageState;
import io.zeebe.engine.util.ZeebeStateRule;
import io.zeebe.util.sched.clock.ActorClock;
import java.util.ArrayList;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class MessageStateTest {

  @Rule public final ZeebeStateRule stateRule = new ZeebeStateRule();

  private MutableMessageState messageState;
  private ZeebeState zeebeState;

  @Before
  public void setUp() {
    zeebeState = stateRule.getZeebeState();
    messageState = zeebeState.getMessageState();
  }

  @Test
  public void shouldNotExistIfNameDoesntMatch() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey", "{}", "id");
    messageState.put(message);

    // when
    final boolean exist =
        messageState.exist(wrapString("otherName"), wrapString("correlationKey"), wrapString("id"));

    // then
    assertThat(exist).isFalse();
  }

  @Test
  public void shouldNotExistIfCorrelationKeyDoesntMatch() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey", "{}", "id");
    messageState.put(message);

    // when
    final boolean exist =
        messageState.exist(wrapString("name"), wrapString("otherCorrelationKey"), wrapString("id"));

    // then
    assertThat(exist).isFalse();
  }

  @Test
  public void shouldNotExistIfMessageIdDoesntMatch() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey", "{}", "id");
    messageState.put(message);

    // when
    final boolean exist =
        messageState.exist(
            wrapString("name"), wrapString("otherCorrelationKey"), wrapString("otherId"));

    // then
    assertThat(exist).isFalse();
  }

  @Test
  public void shouldExist() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey", "{}", "id");
    messageState.put(message);

    // when
    final boolean exist =
        messageState.exist(wrapString("name"), wrapString("correlationKey"), wrapString("id"));

    // then
    assertThat(exist).isTrue();
  }

  @Test
  public void shouldVisitMessages() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey");
    messageState.put(message);

    // when
    final List<Message> messages = new ArrayList<>();
    messageState.visitMessages(wrapString("name"), wrapString("correlationKey"), messages::add);

    // then
    assertThat(messages).hasSize(1);
    assertThat(messages.get(0).getKey()).isEqualTo(message.getKey());
    assertThat(messages.get(0).getName()).isEqualTo(message.getName());
    assertThat(messages.get(0).getCorrelationKey()).isEqualTo(message.getCorrelationKey());
  }

  @Test
  public void shouldVisitMessagesInOrder() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey");
    messageState.put(message);

    final Message message2 = createMessage(2L, "name", "correlationKey");
    messageState.put(message2);

    // when
    final List<Long> keys = new ArrayList<>();
    messageState.visitMessages(
        wrapString("name"), wrapString("correlationKey"), m -> keys.add(m.getKey()));

    // then
    assertThat(keys).hasSize(2).containsExactly(1L, 2L);
  }

  @Test
  public void shouldVisitMessagesUntilStop() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey");
    messageState.put(message);

    final Message message2 = createMessage(2L, "name", "correlationKey");
    messageState.put(message2);

    // when
    final List<Long> keys = new ArrayList<>();
    messageState.visitMessages(
        wrapString("name"),
        wrapString("correlationKey"),
        m -> {
          keys.add(m.getKey());
          return false;
        });

    // then
    assertThat(keys).hasSize(1).contains(1L);
  }

  @Test
  public void shouldNotVisitMessagesIfNameDoesntMatch() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey");
    messageState.put(message);

    // when
    final List<Long> keys = new ArrayList<>();
    messageState.visitMessages(
        wrapString("otherName"), wrapString("correlationKey"), m -> keys.add(m.getKey()));

    // then
    assertThat(keys).isEmpty();
  }

  @Test
  public void shouldNotVisitMessageIfCorrelationKeyDoesntMatch() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey");
    messageState.put(message);

    // when
    final List<Long> keys = new ArrayList<>();
    messageState.visitMessages(
        wrapString("name"), wrapString("otherCorrelationKey"), m -> keys.add(m.getKey()));

    // then
    assertThat(keys).isEmpty();
  }

  @Test
  public void shouldNotVisitMessagesBeforeTime() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey", "{}", "nr1", 1234);
    final Message message2 = createMessage(2L, "name", "correlationKey", "{}", "nr2", 4567);

    messageState.put(message);
    messageState.put(message2);

    // then
    final List<Message> readMessage = new ArrayList<>();
    messageState.visitMessagesWithDeadlineBefore(1_000, readMessage::add);

    assertThat(readMessage).isEmpty();
  }

  @Test
  public void shouldVisitMessagesBeforeTime() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey", "{}", "nr1", 1234);
    final Message message2 = createMessage(2L, "otherName", "correlationKey", "{}", "nr2", 2000);

    messageState.put(message);
    messageState.put(message2);

    // then
    final List<Message> readMessage = new ArrayList<>();
    messageState.visitMessagesWithDeadlineBefore(1_999, readMessage::add);

    assertThat(readMessage.size()).isEqualTo(1);
    assertThat(readMessage.get(0).getKey()).isEqualTo(1L);
  }

  @Test
  public void shouldVisitMessagesBeforeTimeInOrder() {
    // given
    final long now = ActorClock.currentTimeMillis();

    final Message message = createMessage(1L, "name", "correlationKey", "{}", "nr1", 1234);
    final Message message2 = createMessage(2L, "name", "correlationKey", "{}", "nr1", 2000);

    messageState.put(message);
    messageState.put(message2);

    // when
    final long deadline = now + 3_000L;

    // then
    final List<Long> readMessage = new ArrayList<>();
    messageState.visitMessagesWithDeadlineBefore(deadline, m -> readMessage.add(m.getKey()));

    assertThat(readMessage.size()).isEqualTo(2);
    assertThat(readMessage).containsExactly(1L, 2L);
  }

  @Test
  public void shouldRemoveMessage() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey", "{}", "id", 1234);
    messageState.put(message);

    messageState.putMessageCorrelation(1L, wrapString("a"));
    messageState.putMessageCorrelation(1L, wrapString("b"));

    // when
    messageState.remove(message.getKey());

    // then
    final List<Message> readMessages = new ArrayList<>();
    messageState.visitMessagesWithDeadlineBefore(2000, readMessages::add);

    assertThat(readMessages.size()).isEqualTo(0);

    // and
    final List<Long> keys = new ArrayList<>();
    messageState.visitMessages(
        wrapString("name"), wrapString("correlationKey"), m -> keys.add(m.getKey()));

    assertThat(keys).isEmpty();

    // and
    final boolean exist =
        messageState.exist(
            wrapString("messageName"), wrapString("correlationKey"), wrapString("id"));
    assertThat(exist).isFalse();

    // and
    assertThat(messageState.existMessageCorrelation(1L, wrapString("a"))).isFalse();
    assertThat(messageState.existMessageCorrelation(1L, wrapString("b"))).isFalse();
  }

  @Test
  public void shouldRemoveMessageWithoutId() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey");

    messageState.put(message);

    // when
    messageState.remove(message.getKey());

    // then
    final List<Message> readMessages = new ArrayList<>();
    messageState.visitMessagesWithDeadlineBefore(2000, readMessages::add);

    assertThat(readMessages.size()).isEqualTo(0);

    // and
    final List<Long> keys = new ArrayList<>();
    messageState.visitMessages(
        wrapString("name"), wrapString("correlationKey"), m -> keys.add(m.getKey()));

    assertThat(keys).isEmpty();
  }

  @Test
  public void shouldNotFailOnRemoveMessageTwice() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey", "{}", "id", 1234);

    messageState.put(message);

    // when
    messageState.remove(message.getKey());
    messageState.remove(message.getKey());

    // then
    final List<Message> readMessages = new ArrayList<>();
    messageState.visitMessagesWithDeadlineBefore(2000, readMessages::add);

    assertThat(readMessages.size()).isEqualTo(0);

    // and
    final List<Long> keys = new ArrayList<>();
    messageState.visitMessages(
        wrapString("name"), wrapString("correlationKey"), m -> keys.add(m.getKey()));

    assertThat(keys).isEmpty();

    // and
    final boolean exist =
        messageState.exist(
            wrapString("messageName"), wrapString("correlationKey"), wrapString("id"));
    assertThat(exist).isFalse();
  }

  @Test
  public void shouldNotRemoveDifferentMessage() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey", "{}", "id1", 1234);
    final Message message2 = createMessage(2L, "name", "correlationKey", "{}", "id2", 4567);

    messageState.put(message);

    messageState.putMessageCorrelation(1L, wrapString("a"));
    messageState.putMessageCorrelation(2L, wrapString("b"));

    // when
    messageState.remove(message2.getKey());

    // then
    final long deadline = ActorClock.currentTimeMillis() + 2_000L;
    final List<Message> readMessages = new ArrayList<>();
    messageState.visitMessagesWithDeadlineBefore(deadline, readMessages::add);

    assertThat(readMessages.size()).isEqualTo(1);

    // and
    final List<Long> keys = new ArrayList<>();
    messageState.visitMessages(
        wrapString("name"), wrapString("correlationKey"), m -> keys.add(m.getKey()));

    assertThat(keys).hasSize(1).contains(1L);

    // and
    final boolean exist =
        messageState.exist(wrapString("name"), wrapString("correlationKey"), wrapString("id1"));
    assertThat(exist).isTrue();

    // and
    assertThat(messageState.existMessageCorrelation(1L, wrapString("a"))).isTrue();
  }

  @Test
  public void shouldExistCorrelatedMessage() {
    // when
    messageState.putMessageCorrelation(1L, wrapString("a"));

    // then
    assertThat(messageState.existMessageCorrelation(1L, wrapString("a"))).isTrue();

    assertThat(messageState.existMessageCorrelation(3L, wrapString("a"))).isFalse();
    assertThat(messageState.existMessageCorrelation(1L, wrapString("b"))).isFalse();
  }

  @Test
  public void shouldRemoveMessageCorrelation() {
    // given
    final long messageKey = 6L;
    final long workflowInstanceKey = 9L;
    messageState.putMessageCorrelation(messageKey, wrapString("a"));

    // when
    messageState.removeMessageCorrelation(messageKey, wrapString("a"));

    // then
    assertThat(messageState.existMessageCorrelation(messageKey, wrapString("a"))).isFalse();
  }

  @Test
  public void shouldExistActiveWorkflowInstance() {
    // when
    messageState.putActiveWorkflowInstance(wrapString("wf-1"), wrapString("key-1"));

    // then
    assertThat(messageState.existActiveWorkflowInstance(wrapString("wf-1"), wrapString("key-1")))
        .isTrue();

    assertThat(messageState.existActiveWorkflowInstance(wrapString("wf-2"), wrapString("key-1")))
        .isFalse();
    assertThat(messageState.existActiveWorkflowInstance(wrapString("wf-1"), wrapString("key-2")))
        .isFalse();
  }

  @Test
  public void shouldRemoveActiveWorkflowInstance() {
    // given
    messageState.putActiveWorkflowInstance(wrapString("wf-1"), wrapString("key-1"));
    messageState.putActiveWorkflowInstance(wrapString("wf-2"), wrapString("key-1"));
    messageState.putActiveWorkflowInstance(wrapString("wf-1"), wrapString("key-2"));

    // when
    messageState.removeActiveWorkflowInstance(wrapString("wf-1"), wrapString("key-1"));

    // then
    assertThat(messageState.existActiveWorkflowInstance(wrapString("wf-1"), wrapString("key-1")))
        .isFalse();
    assertThat(messageState.existActiveWorkflowInstance(wrapString("wf-2"), wrapString("key-1")))
        .isTrue();
    assertThat(messageState.existActiveWorkflowInstance(wrapString("wf-1"), wrapString("key-2")))
        .isTrue();
  }

  @Test
  public void shouldGetWorkflowInstanceCorrelationKey() {
    // when
    messageState.putWorkflowInstanceCorrelationKey(1L, wrapString("key-1"));

    // then
    assertThat(messageState.getWorkflowInstanceCorrelationKey(1L)).isEqualTo(wrapString("key-1"));

    assertThat(messageState.getWorkflowInstanceCorrelationKey(2L)).isNull();
  }

  @Test
  public void shouldRemoveWorkflowInstanceCorrelationKey() {
    // given
    messageState.putWorkflowInstanceCorrelationKey(1L, wrapString("key-1"));
    messageState.putWorkflowInstanceCorrelationKey(2L, wrapString("key-2"));

    // when
    messageState.removeWorkflowInstanceCorrelationKey(1L);

    // then
    assertThat(messageState.getWorkflowInstanceCorrelationKey(1L)).isNull();
    assertThat(messageState.getWorkflowInstanceCorrelationKey(2L)).isEqualTo(wrapString("key-2"));
  }

  private Message createMessage(final long key, final String name, final String correlationKey) {
    return new Message(
        key,
        wrapString(name),
        wrapString(correlationKey),
        wrapString(""),
        new UnsafeBuffer(0, 0),
        10_000,
        0L);
  }

  private Message createMessage(
      final long key,
      final String name,
      final String correlationKey,
      final String variables,
      final String id) {
    return new Message(
        key,
        wrapString(name),
        wrapString(correlationKey),
        wrapString(variables),
        wrapString(id),
        10_000,
        0L);
  }

  private Message createMessage(
      final long key,
      final String name,
      final String correlationKey,
      final String variables,
      final String id,
      final long deadline) {
    return new Message(
        key,
        wrapString(name),
        wrapString(correlationKey),
        wrapString(variables),
        wrapString(id),
        10_000L,
        deadline);
  }
}
