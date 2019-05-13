/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.state.message;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.util.ZeebeStateRule;
import io.zeebe.util.sched.clock.ActorClock;
import java.util.ArrayList;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class MessageStateTest {

  @Rule public ZeebeStateRule stateRule = new ZeebeStateRule();

  private MessageState messageState;
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
    final List<Message> messages = new ArrayList<Message>();
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

    messageState.putMessageCorrelation(1L, 2L);
    messageState.putMessageCorrelation(1L, 3L);

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
    assertThat(messageState.existMessageCorrelation(1L, 2L)).isFalse();
    assertThat(messageState.existMessageCorrelation(1L, 3L)).isFalse();
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

    messageState.putMessageCorrelation(1L, 3L);
    messageState.putMessageCorrelation(2L, 4L);

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
    assertThat(messageState.existMessageCorrelation(1L, 3L)).isTrue();
  }

  @Test
  public void shouldExistCorrelatedMessage() {
    // when
    messageState.putMessageCorrelation(1L, 2L);

    // then
    assertThat(messageState.existMessageCorrelation(1L, 2L)).isTrue();

    assertThat(messageState.existMessageCorrelation(3L, 2L)).isFalse();
    assertThat(messageState.existMessageCorrelation(1L, 3L)).isFalse();
  }

  @Test
  public void shouldRemoveMessageCorrelation() {
    // given
    final long messageKey = 6L;
    final long workflowInstanceKey = 9L;
    messageState.putMessageCorrelation(messageKey, workflowInstanceKey);

    // when
    messageState.removeMessageCorrelation(messageKey, workflowInstanceKey);

    // then
    assertThat(messageState.existMessageCorrelation(messageKey, workflowInstanceKey)).isFalse();
  }

  private Message createMessage(long key, String name, String correlationKey) {
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
      long key, String name, String correlationKey, String variables, String id) {
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
      long key, String name, String correlationKey, String variables, String id, long deadline) {
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
