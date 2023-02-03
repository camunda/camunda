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

import io.camunda.zeebe.engine.state.mutable.MutableMessageState;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.engine.util.ZeebeStateRule;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.test.util.MsgPackUtil;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class MessageStateTest {

  @Rule public final ZeebeStateRule stateRule = new ZeebeStateRule();

  private MutableMessageState messageState;
  private MutableZeebeState zeebeState;

  @Before
  public void setUp() {
    zeebeState = stateRule.getZeebeState();
    messageState = zeebeState.getMessageState();
  }

  @Test
  public void shouldNotExistIfNameDoesntMatch() {
    // given
    final var message = createMessage("name", "correlationKey", "{}", "id");
    messageState.put(1L, message);

    // when
    final boolean exist =
        messageState.exist(wrapString("otherName"), wrapString("correlationKey"), wrapString("id"));

    // then
    assertThat(exist).isFalse();
  }

  @Test
  public void shouldNotExistIfCorrelationKeyDoesntMatch() {
    // given
    final var message = createMessage("name", "correlationKey", "{}", "id");
    messageState.put(1L, message);

    // when
    final boolean exist =
        messageState.exist(wrapString("name"), wrapString("otherCorrelationKey"), wrapString("id"));

    // then
    assertThat(exist).isFalse();
  }

  @Test
  public void shouldNotExistIfMessageIdDoesntMatch() {
    // given
    final var message = createMessage("name", "correlationKey", "{}", "id");
    messageState.put(1L, message);

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
    final var message = createMessage("name", "correlationKey", "{}", "id");
    messageState.put(1L, message);

    // when
    final boolean exist =
        messageState.exist(wrapString("name"), wrapString("correlationKey"), wrapString("id"));

    // then
    assertThat(exist).isTrue();
  }

  @Test
  public void shouldVisitMessages() {
    // given
    final var message = createMessage("name", "correlationKey");
    messageState.put(1L, message);

    // when
    final List<StoredMessage> messages = new ArrayList<>();
    messageState.visitMessages(wrapString("name"), wrapString("correlationKey"), messages::add);

    // then
    assertThat(messages).hasSize(1);
    assertThat(messages.get(0).getMessageKey()).isEqualTo(1L);
    assertThat(messages.get(0).getMessage().getName()).isEqualTo(message.getName());
    assertThat(messages.get(0).getMessage().getCorrelationKey())
        .isEqualTo(message.getCorrelationKey());
  }

  @Test
  public void shouldVisitMessagesInOrder() {
    // given
    final var message = createMessage("name", "correlationKey");
    messageState.put(1L, message);

    final var message2 = createMessage("name", "correlationKey");
    messageState.put(2L, message2);

    // when
    final List<Long> keys = new ArrayList<>();
    messageState.visitMessages(
        wrapString("name"), wrapString("correlationKey"), m -> keys.add(m.getMessageKey()));

    // then
    assertThat(keys).hasSize(2).containsExactly(1L, 2L);
  }

  @Test
  public void shouldVisitMessagesUntilStop() {
    // given
    final var message = createMessage("name", "correlationKey");
    messageState.put(1L, message);

    final var message2 = createMessage("name", "correlationKey");
    messageState.put(2L, message2);

    // when
    final List<Long> keys = new ArrayList<>();
    messageState.visitMessages(
        wrapString("name"),
        wrapString("correlationKey"),
        m -> {
          keys.add(m.getMessageKey());
          return false;
        });

    // then
    assertThat(keys).hasSize(1).contains(1L);
  }

  @Test
  public void shouldNotVisitMessagesIfNameDoesntMatch() {
    // given
    final var message = createMessage("name", "correlationKey");
    messageState.put(1L, message);

    // when
    final List<Long> keys = new ArrayList<>();
    messageState.visitMessages(
        wrapString("otherName"), wrapString("correlationKey"), m -> keys.add(m.getMessageKey()));

    // then
    assertThat(keys).isEmpty();
  }

  @Test
  public void shouldNotVisitMessageIfCorrelationKeyDoesntMatch() {
    // given
    final var message = createMessage("name", "correlationKey");
    messageState.put(1L, message);

    // when
    final List<Long> keys = new ArrayList<>();
    messageState.visitMessages(
        wrapString("name"), wrapString("otherCorrelationKey"), m -> keys.add(m.getMessageKey()));

    // then
    assertThat(keys).isEmpty();
  }

  @Test
  public void shouldNotVisitMessagesBeforeTime() {
    // given
    final var message = createMessage("name", "correlationKey", "{}", "nr1", 1234);
    final var message2 = createMessage("name", "correlationKey", "{}", "nr2", 4567);

    messageState.put(1L, message);
    messageState.put(2L, message2);

    // then
    final List<Long> readMessage = new ArrayList<>();
    messageState.visitMessagesWithDeadlineBefore(1_000, readMessage::add);

    assertThat(readMessage).isEmpty();
  }

  @Test
  public void shouldVisitMessagesBeforeTime() {
    // given
    final var message = createMessage("name", "correlationKey", "{}", "nr1", 1234);
    final var message2 = createMessage("otherName", "correlationKey", "{}", "nr2", 2000);

    messageState.put(1L, message);
    messageState.put(2L, message2);

    // then
    final List<Long> readMessage = new ArrayList<>();
    messageState.visitMessagesWithDeadlineBefore(1_999, readMessage::add);

    assertThat(readMessage.size()).isEqualTo(1);
    assertThat(readMessage.get(0)).isEqualTo(1L);
  }

  @Test
  public void shouldVisitMessagesBeforeTimeInOrder() {
    // given
    final long now = ActorClock.currentTimeMillis();

    final var message = createMessage("name", "correlationKey", "{}", "nr1", 1234);
    final var message2 = createMessage("name", "correlationKey", "{}", "nr1", 2000);

    messageState.put(1L, message);
    messageState.put(2L, message2);

    // when
    final long deadline = now + 3_000L;

    // then
    final List<Long> readMessage = new ArrayList<>();
    messageState.visitMessagesWithDeadlineBefore(deadline, m -> readMessage.add(m));

    assertThat(readMessage.size()).isEqualTo(2);
    assertThat(readMessage).containsExactly(1L, 2L);
  }

  @Test
  public void shouldRemoveMessage() {
    // given
    final var message = createMessage("name", "correlationKey", "{}", "id", 1234);
    messageState.put(1L, message);

    messageState.putMessageCorrelation(1L, wrapString("a"));
    messageState.putMessageCorrelation(1L, wrapString("b"));

    // when
    messageState.remove(1L);

    // then
    final List<Long> readMessages = new ArrayList<>();
    messageState.visitMessagesWithDeadlineBefore(2000, readMessages::add);

    assertThat(readMessages.size()).isEqualTo(0);

    // and
    final List<Long> keys = new ArrayList<>();
    messageState.visitMessages(
        wrapString("name"), wrapString("correlationKey"), m -> keys.add(m.getMessageKey()));

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
    final var message = createMessage("name", "correlationKey");

    messageState.put(1L, message);

    // when
    messageState.remove(1L);

    // then
    final List<Long> readMessages = new ArrayList<>();
    messageState.visitMessagesWithDeadlineBefore(2000, readMessages::add);

    assertThat(readMessages.size()).isEqualTo(0);

    // and
    final List<Long> keys = new ArrayList<>();
    messageState.visitMessages(
        wrapString("name"), wrapString("correlationKey"), m -> keys.add(m.getMessageKey()));

    assertThat(keys).isEmpty();
  }

  @Test
  public void shouldNotFailOnRemoveMessageTwice() {
    // given
    final var message = createMessage("name", "correlationKey", "{}", "id", 1234);

    messageState.put(1L, message);

    // when
    messageState.remove(1L);
    messageState.remove(1L);

    // then
    final List<Long> readMessages = new ArrayList<>();
    messageState.visitMessagesWithDeadlineBefore(2000, readMessages::add);

    assertThat(readMessages.size()).isEqualTo(0);

    // and
    final List<Long> keys = new ArrayList<>();
    messageState.visitMessages(
        wrapString("name"), wrapString("correlationKey"), m -> keys.add(m.getMessageKey()));

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
    final var message = createMessage("name", "correlationKey", "{}", "id1", 1234);
    final var message2 = createMessage("name", "correlationKey", "{}", "id2", 4567);

    messageState.put(1L, message);
    messageState.put(2L, message2);

    messageState.putMessageCorrelation(1L, wrapString("a"));
    messageState.putMessageCorrelation(2L, wrapString("b"));

    // when
    messageState.remove(2L);

    // then
    final long deadline = ActorClock.currentTimeMillis() + 2_000L;
    final List<Long> readMessages = new ArrayList<>();
    messageState.visitMessagesWithDeadlineBefore(deadline, readMessages::add);

    assertThat(readMessages.size()).isEqualTo(1);

    // and
    final List<Long> keys = new ArrayList<>();
    messageState.visitMessages(
        wrapString("name"), wrapString("correlationKey"), m -> keys.add(m.getMessageKey()));

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
    final var messageKey = 1L;
    final var message = createMessage("name", "correlationKey", "{}", "id1", 1234);
    messageState.put(messageKey, message);
    messageState.putMessageCorrelation(messageKey, wrapString("a"));

    // then
    assertThat(messageState.existMessageCorrelation(messageKey, wrapString("a"))).isTrue();

    assertThat(messageState.existMessageCorrelation(messageKey + 1, wrapString("a"))).isFalse();
    assertThat(messageState.existMessageCorrelation(messageKey, wrapString("b"))).isFalse();
  }

  @Test
  public void shouldRemoveMessageCorrelation() {
    // given
    final long messageKey = 6L;
    final var message = createMessage("name", "correlationKey", "{}", "id1", 1234);
    messageState.put(messageKey, message);

    messageState.putMessageCorrelation(messageKey, wrapString("a"));

    // when
    messageState.removeMessageCorrelation(messageKey, wrapString("a"));

    // then
    assertThat(messageState.existMessageCorrelation(messageKey, wrapString("a"))).isFalse();
  }

  @Test
  public void shouldExistActiveProcessInstance() {
    // when
    messageState.putActiveProcessInstance(wrapString("wf-1"), wrapString("key-1"));

    // then
    assertThat(messageState.existActiveProcessInstance(wrapString("wf-1"), wrapString("key-1")))
        .isTrue();

    assertThat(messageState.existActiveProcessInstance(wrapString("wf-2"), wrapString("key-1")))
        .isFalse();
    assertThat(messageState.existActiveProcessInstance(wrapString("wf-1"), wrapString("key-2")))
        .isFalse();
  }

  @Test
  public void shouldRemoveActiveProcessInstance() {
    // given
    messageState.putActiveProcessInstance(wrapString("wf-1"), wrapString("key-1"));
    messageState.putActiveProcessInstance(wrapString("wf-2"), wrapString("key-1"));
    messageState.putActiveProcessInstance(wrapString("wf-1"), wrapString("key-2"));

    // when
    messageState.removeActiveProcessInstance(wrapString("wf-1"), wrapString("key-1"));

    // then
    assertThat(messageState.existActiveProcessInstance(wrapString("wf-1"), wrapString("key-1")))
        .isFalse();
    assertThat(messageState.existActiveProcessInstance(wrapString("wf-2"), wrapString("key-1")))
        .isTrue();
    assertThat(messageState.existActiveProcessInstance(wrapString("wf-1"), wrapString("key-2")))
        .isTrue();
  }

  @Test
  public void shouldGetProcessInstanceCorrelationKey() {
    // when
    messageState.putProcessInstanceCorrelationKey(1L, wrapString("key-1"));

    // then
    assertThat(messageState.getProcessInstanceCorrelationKey(1L)).isEqualTo(wrapString("key-1"));

    assertThat(messageState.getProcessInstanceCorrelationKey(2L)).isNull();
  }

  @Test
  public void shouldRemoveProcessInstanceCorrelationKey() {
    // given
    messageState.putProcessInstanceCorrelationKey(1L, wrapString("key-1"));
    messageState.putProcessInstanceCorrelationKey(2L, wrapString("key-2"));

    // when
    messageState.removeProcessInstanceCorrelationKey(1L);

    // then
    assertThat(messageState.getProcessInstanceCorrelationKey(1L)).isNull();
    assertThat(messageState.getProcessInstanceCorrelationKey(2L)).isEqualTo(wrapString("key-2"));
  }

  private MessageRecord createMessage(final String name, final String correlationKey) {
    return new MessageRecord()
        .setName(name)
        .setCorrelationKey(correlationKey)
        .setTimeToLive(10_000L)
        .setDeadline(0L);
  }

  private MessageRecord createMessage(
      final String name, final String correlationKey, final String variables, final String id) {
    return new MessageRecord()
        .setName(name)
        .setCorrelationKey(correlationKey)
        .setTimeToLive(10_000L)
        .setVariables(MsgPackUtil.asMsgPack(variables))
        .setMessageId(id);
  }

  private MessageRecord createMessage(
      final String name,
      final String correlationKey,
      final String variables,
      final String id,
      final long deadline) {
    return new MessageRecord()
        .setName(name)
        .setCorrelationKey(correlationKey)
        .setVariables(MsgPackUtil.asMsgPack(variables))
        .setMessageId(id)
        .setDeadline(deadline)
        .setTimeToLive(10_000L);
  }
}
