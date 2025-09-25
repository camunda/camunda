/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.message;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.common.state.immutable.MessageState;
import io.camunda.zeebe.engine.common.state.message.StoredMessage;
import io.camunda.zeebe.engine.common.state.mutable.MutableMessageState;
import io.camunda.zeebe.engine.common.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateRule;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.MsgPackUtil;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class MessageStateTest {

  private static final String DEFAULT_TENANT = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
  @Rule public final ProcessingStateRule stateRule = new ProcessingStateRule();

  private MutableMessageState messageState;
  private MutableProcessingState processingState;

  @Before
  public void setUp() {
    processingState = stateRule.getProcessingState();
    messageState = processingState.getMessageState();
  }

  @Test
  public void shouldNotExistIfNameDoesntMatch() {
    // given
    final var message = createMessage("name", "correlationKey", "{}", "id");
    messageState.put(1L, message);

    // when
    final boolean exist =
        messageState.exist(
            wrapString("otherName"),
            wrapString("correlationKey"),
            wrapString("id"),
            TenantOwned.DEFAULT_TENANT_IDENTIFIER);

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
        messageState.exist(
            wrapString("name"),
            wrapString("otherCorrelationKey"),
            wrapString("id"),
            TenantOwned.DEFAULT_TENANT_IDENTIFIER);

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
            wrapString("name"),
            wrapString("otherCorrelationKey"),
            wrapString("otherId"),
            TenantOwned.DEFAULT_TENANT_IDENTIFIER);

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
        messageState.exist(
            wrapString("name"),
            wrapString("correlationKey"),
            wrapString("id"),
            TenantOwned.DEFAULT_TENANT_IDENTIFIER);

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
    messageState.visitMessages(
        TenantOwned.DEFAULT_TENANT_IDENTIFIER,
        wrapString("name"),
        wrapString("correlationKey"),
        messages::add);

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
        TenantOwned.DEFAULT_TENANT_IDENTIFIER,
        wrapString("name"),
        wrapString("correlationKey"),
        m -> keys.add(m.getMessageKey()));

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
        TenantOwned.DEFAULT_TENANT_IDENTIFIER,
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
        TenantOwned.DEFAULT_TENANT_IDENTIFIER,
        wrapString("otherName"),
        wrapString("correlationKey"),
        m -> keys.add(m.getMessageKey()));

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
        TenantOwned.DEFAULT_TENANT_IDENTIFIER,
        wrapString("name"),
        wrapString("otherCorrelationKey"),
        m -> keys.add(m.getMessageKey()));

    // then
    assertThat(keys).isEmpty();
  }

  @Test
  public void shouldVisitMessagesUntilVisitorReturnsFalse() {
    // given
    final var message = createMessage("name", "correlationKey", "{}", "nr1", 1234);
    final var message2 = createMessage("otherName", "correlationKey", "{}", "nr2", 2000);

    messageState.put(1L, message);
    messageState.put(2L, message2);

    // then
    final List<Long> readMessage = new ArrayList<>();
    final boolean isStoppedByVisitor =
        messageState.visitMessagesWithDeadlineBeforeTimestamp(
            3456,
            null,
            (deadline, e) -> {
              readMessage.add(e);
              return false;
            });

    assertThat(readMessage).hasSize(1).containsExactly(1L);
    assertThat(isStoppedByVisitor)
        .describedAs("Expect that the visiting stopped because of the visitor")
        .isTrue();
  }

  @Test
  public void shouldVisitMessagesWhileVisitorReturnsTrue() {
    // given
    final var message = createMessage("name", "correlationKey", "{}", "nr1", 1234);
    final var message2 = createMessage("otherName", "correlationKey", "{}", "nr2", 2000);

    messageState.put(1L, message);
    messageState.put(2L, message2);

    // then
    final List<Long> readMessage = new ArrayList<>();
    final boolean isStoppedByVisitor =
        messageState.visitMessagesWithDeadlineBeforeTimestamp(
            1_900,
            null,
            (deadline, e) -> {
              readMessage.add(e);
              return true;
            });

    assertThat(readMessage).hasSize(1).containsExactly(1L);
    assertThat(isStoppedByVisitor)
        .describedAs(
            "Expect that the visiting is stopped because there are no more entries before the timestamp")
        .isFalse();
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
    messageState.visitMessagesWithDeadlineBeforeTimestamp(
        1_000, null, (deadline, e) -> readMessage.add(e));

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
    messageState.visitMessagesWithDeadlineBeforeTimestamp(
        1_999, null, (deadline, e) -> readMessage.add(e));

    assertThat(readMessage.size()).isEqualTo(1);
    assertThat(readMessage.get(0)).isEqualTo(1L);
  }

  @Test
  public void shouldVisitMessagesBeforeTimeInOrder() {
    // given
    final long now = InstantSource.system().millis();

    final var message = createMessage("name", "correlationKey", "{}", "nr1", 1234);
    final var message2 = createMessage("name", "correlationKey", "{}", "nr1", 2000);

    messageState.put(1L, message);
    messageState.put(2L, message2);

    // when
    final long deadline = now + 3_000L;

    // then
    final List<Long> readMessage = new ArrayList<>();
    messageState.visitMessagesWithDeadlineBeforeTimestamp(
        deadline, null, (d, m) -> readMessage.add(m));

    assertThat(readMessage.size()).isEqualTo(2);
    assertThat(readMessage).containsExactly(1L, 2L);
  }

  @Test
  public void shouldVisitMessagesBeforeTimeStartingAtIndex() {
    // given four messages
    final var message = createMessage("name", "correlationKey", "{}", "nr1", 1234);
    final var message2 = createMessage("otherName", "correlationKey", "{}", "nr2", 2000);
    final var message3 = createMessage("anotherName", "correlationKey", "{}", "nr3", 2500);
    final var message4 = createMessage("yetAnotherName", "correlationKey", "{}", "nr4", 3456);

    messageState.put(1L, message);
    messageState.put(2L, message2);
    messageState.put(3L, message3);
    messageState.put(4L, message4);

    // and we've visited two
    final MutableObject<MessageState.Index> lastIndex = new MutableObject<>();
    messageState.visitMessagesWithDeadlineBeforeTimestamp(
        2999,
        null,
        (deadline, key) -> {
          final boolean shouldContinue = lastIndex.getValue() == null;
          lastIndex.setValue(new MessageState.Index(key, deadline));
          return shouldContinue;
        });

    // when we visit from the last index
    final List<Long> readMessage = new ArrayList<>();
    messageState.visitMessagesWithDeadlineBeforeTimestamp(
        2999, lastIndex.getValue(), (deadline, e) -> readMessage.add(e));

    // then we encounter the message from after that index and before the deadline
    assertThat(readMessage).hasSize(2).containsExactly(2L, 3L).doesNotContain(1L, 4L);
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
    messageState.visitMessagesWithDeadlineBeforeTimestamp(
        2000, null, (deadline, e) -> readMessages.add(e));

    assertThat(readMessages.size()).isEqualTo(0);

    // and
    final List<Long> keys = new ArrayList<>();
    messageState.visitMessages(
        TenantOwned.DEFAULT_TENANT_IDENTIFIER,
        wrapString("name"),
        wrapString("correlationKey"),
        m -> keys.add(m.getMessageKey()));

    assertThat(keys).isEmpty();

    // and
    final boolean exist =
        messageState.exist(
            wrapString("messageName"),
            wrapString("correlationKey"),
            wrapString("id"),
            TenantOwned.DEFAULT_TENANT_IDENTIFIER);
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
    messageState.visitMessagesWithDeadlineBeforeTimestamp(
        2000, null, (deadline, e) -> readMessages.add(e));

    assertThat(readMessages.size()).isEqualTo(0);

    // and
    final List<Long> keys = new ArrayList<>();
    messageState.visitMessages(
        TenantOwned.DEFAULT_TENANT_IDENTIFIER,
        wrapString("name"),
        wrapString("correlationKey"),
        m -> keys.add(m.getMessageKey()));

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
    messageState.visitMessagesWithDeadlineBeforeTimestamp(
        2000, null, (deadline, e) -> readMessages.add(e));

    assertThat(readMessages.size()).isEqualTo(0);

    // and
    final List<Long> keys = new ArrayList<>();
    messageState.visitMessages(
        TenantOwned.DEFAULT_TENANT_IDENTIFIER,
        wrapString("name"),
        wrapString("correlationKey"),
        m -> keys.add(m.getMessageKey()));

    assertThat(keys).isEmpty();

    // and
    final boolean exist =
        messageState.exist(
            wrapString("messageName"),
            wrapString("correlationKey"),
            wrapString("id"),
            TenantOwned.DEFAULT_TENANT_IDENTIFIER);
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
    final long deadline = InstantSource.system().millis() + 2_000L;
    final List<Long> readMessages = new ArrayList<>();
    messageState.visitMessagesWithDeadlineBeforeTimestamp(
        deadline, null, (d, e) -> readMessages.add(e));

    assertThat(readMessages.size()).isEqualTo(1);

    // and
    final List<Long> keys = new ArrayList<>();
    messageState.visitMessages(
        TenantOwned.DEFAULT_TENANT_IDENTIFIER,
        wrapString("name"),
        wrapString("correlationKey"),
        m -> keys.add(m.getMessageKey()));

    assertThat(keys).hasSize(1).contains(1L);

    // and
    final boolean exist =
        messageState.exist(
            wrapString("name"),
            wrapString("correlationKey"),
            wrapString("id1"),
            TenantOwned.DEFAULT_TENANT_IDENTIFIER);
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
    assertThat(
            messageState.existActiveProcessInstance(
                DEFAULT_TENANT, wrapString("wf-1"), wrapString("key-1")))
        .isTrue();

    assertThat(
            messageState.existActiveProcessInstance(
                DEFAULT_TENANT, wrapString("wf-2"), wrapString("key-1")))
        .isFalse();
    assertThat(
            messageState.existActiveProcessInstance(
                DEFAULT_TENANT, wrapString("wf-1"), wrapString("key-2")))
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
    assertThat(
            messageState.existActiveProcessInstance(
                DEFAULT_TENANT, wrapString("wf-1"), wrapString("key-1")))
        .isFalse();
    assertThat(
            messageState.existActiveProcessInstance(
                DEFAULT_TENANT, wrapString("wf-2"), wrapString("key-1")))
        .isTrue();
    assertThat(
            messageState.existActiveProcessInstance(
                DEFAULT_TENANT, wrapString("wf-1"), wrapString("key-2")))
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
