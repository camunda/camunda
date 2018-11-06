/*
 * Zeebe Broker Core
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
package io.zeebe.broker.subscription.message.state;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.logstreams.state.ZeebeState;
import io.zeebe.broker.subscription.message.data.MessageSubscriptionRecord;
import io.zeebe.util.sched.clock.ActorClock;
import java.util.ArrayList;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MessageStateTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private MessageState stateController;
  private ZeebeState zeebeState;

  @Before
  public void setUp() throws Exception {
    zeebeState = new ZeebeState();
    zeebeState.open(folder.newFolder("rocksdb"), false);
    stateController = zeebeState.getMessageState();
  }

  @After
  public void close() {
    zeebeState.close();
  }

  @Test
  public void shouldNotExistIfNameDoesntMatch() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey", "{}", "id");
    stateController.put(message);

    // when
    final boolean exist =
        stateController.exist(
            wrapString("otherName"), wrapString("correlationKey"), wrapString("id"));

    // then
    assertThat(exist).isFalse();
  }

  @Test
  public void shouldNotExistIfCorrelationKeyDoesntMatch() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey", "{}", "id");
    stateController.put(message);

    // when
    final boolean exist =
        stateController.exist(
            wrapString("name"), wrapString("otherCorrelationKey"), wrapString("id"));

    // then
    assertThat(exist).isFalse();
  }

  @Test
  public void shouldNotExistIfMessageIdDoesntMatch() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey", "{}", "id");
    stateController.put(message);

    // when
    final boolean exist =
        stateController.exist(
            wrapString("name"), wrapString("otherCorrelationKey"), wrapString("otherId"));

    // then
    assertThat(exist).isFalse();
  }

  @Test
  public void shouldExist() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey", "{}", "id");
    stateController.put(message);

    // when
    final boolean exist =
        stateController.exist(wrapString("name"), wrapString("correlationKey"), wrapString("id"));

    // then
    assertThat(exist).isTrue();
  }

  @Test
  public void shouldNotExistIfSubscriptionNotStored() {
    // given
    final MessageSubscription subscription =
        new MessageSubscription("messageName", "correlationKey", "{\"foo\":\"bar\"}", 1, 2, 1234);

    // when
    final boolean exist = stateController.exist(subscription);

    // then
    assertThat(exist).isFalse();
  }

  @Test
  public void shouldExistSubscription() {
    // given
    final MessageSubscription subscription =
        new MessageSubscription("messageName", "correlationKey", "{\"foo\":\"bar\"}", 1, 2, 1234);
    stateController.put(subscription);

    // when
    final boolean exist = stateController.exist(subscription);

    // then
    assertThat(exist).isTrue();
  }

  @Test
  public void shouldVisitMessages() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey");
    stateController.put(message);

    // when
    final List<Message> messages = new ArrayList<Message>();
    stateController.visitMessages(wrapString("name"), wrapString("correlationKey"), messages::add);

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
    stateController.put(message);

    final Message message2 = createMessage(2L, "name", "correlationKey");
    stateController.put(message2);

    // when
    final List<Long> keys = new ArrayList<>();
    stateController.visitMessages(
        wrapString("name"), wrapString("correlationKey"), m -> keys.add(m.getKey()));

    // then
    assertThat(keys).hasSize(2).containsExactly(1L, 2L);
  }

  @Test
  public void shouldVisitMessagesUntilStop() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey");
    stateController.put(message);

    final Message message2 = createMessage(2L, "name", "correlationKey");
    stateController.put(message2);

    // when
    final List<Long> keys = new ArrayList<>();
    stateController.visitMessages(
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
    stateController.put(message);

    // when
    final List<Long> keys = new ArrayList<>();
    stateController.visitMessages(
        wrapString("otherName"), wrapString("correlationKey"), m -> keys.add(m.getKey()));

    // then
    assertThat(keys).isEmpty();
  }

  @Test
  public void shouldNotVisitMessageIfCorrelationKeyDoesntMatch() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey");
    stateController.put(message);

    // when
    final List<Long> keys = new ArrayList<>();
    stateController.visitMessages(
        wrapString("name"), wrapString("otherCorrelationKey"), m -> keys.add(m.getKey()));

    // then
    assertThat(keys).isEmpty();
  }

  @Test
  public void shouldFindSubscription() {
    // given
    final MessageSubscription subscription =
        new MessageSubscription("messageName", "correlationKey", "{\"foo\":\"bar\"}", 1, 2, 0);
    stateController.put(subscription);

    // when
    final List<MessageSubscription> readSubscriptions =
        stateController.findSubscriptions(wrapString("messageName"), wrapString("correlationKey"));

    // then
    assertThat(readSubscriptions.size()).isEqualTo(1);

    final MessageSubscription readSubscription = readSubscriptions.get(0);
    assertSubscription(subscription, readSubscription);
  }

  @Test
  public void shouldFindMoreSubscriptions() {
    // given
    final MessageSubscription subscription =
        new MessageSubscription("messageName", "correlationKey", "{\"foo\":\"bar\"}", 1, 2, 0);
    final MessageSubscription subscription2 =
        new MessageSubscription("messageName", "correlationKey", "{\"foo\":\"bar\"}", 2, 3, 0);
    final MessageSubscription subscription3 =
        new MessageSubscription("otherName", "correlationKey", "{\"foo\":\"bar\"}", 3, 4, 0);
    stateController.put(subscription);
    stateController.put(subscription2);
    stateController.put(subscription3);

    // when
    final List<MessageSubscription> readSubscriptions =
        stateController.findSubscriptions(wrapString("messageName"), wrapString("correlationKey"));

    // then
    assertThat(readSubscriptions.size()).isEqualTo(2);

    MessageSubscription readSubscription = readSubscriptions.get(0);
    assertSubscription(subscription, readSubscription, 0, 1, 2);
    readSubscription = readSubscriptions.get(1);
    assertSubscription(subscription2, readSubscription, 0, 2, 3);

    // and
    final List<MessageSubscription> otherSubscriptions =
        stateController.findSubscriptions(wrapString("otherName"), wrapString("correlationKey"));

    assertThat(otherSubscriptions.size()).isEqualTo(1);
    assertSubscription(subscription3, otherSubscriptions.get(0), 0, 3, 4);
  }

  @Test
  public void shouldFindSubscriptionWithMessageStored() {
    // given
    final MessageSubscription subscription =
        new MessageSubscription("messageName", "correlationKey", "{\"foo\":\"bar\"}", 1, 2, 0);
    final Message message = createMessage(1L, "name", "correlationKey");

    stateController.put(message);
    stateController.put(subscription);

    // when
    final List<MessageSubscription> readSubscriptions =
        stateController.findSubscriptions(wrapString("messageName"), wrapString("correlationKey"));

    // then
    assertThat(readSubscriptions.size()).isEqualTo(1);

    final MessageSubscription readSubscription = readSubscriptions.get(0);
    assertSubscription(subscription, readSubscription);
  }

  @Test
  public void shouldFindOnlySubscriptionsWithoutSendTime() {
    // given
    final MessageSubscription subscription =
        new MessageSubscription("messageName", "correlationKey", "{\"foo\":\"bar\"}", 1, 2, 1234);
    final MessageSubscription subscription2 =
        new MessageSubscription("messageName", "correlationKey", "{\"foo\":\"bar\"}", 3, 4, 0);
    stateController.put(subscription);
    stateController.put(subscription2);

    // when
    final List<MessageSubscription> readSubscriptions =
        stateController.findSubscriptions(wrapString("messageName"), wrapString("correlationKey"));

    // then
    assertThat(readSubscriptions.size()).isEqualTo(1);
    assertThat(readSubscriptions.get(0).getWorkflowInstanceKey()).isEqualTo(3);
  }

  private void assertSubscription(
      MessageSubscription subscription, MessageSubscription readSubscription) {
    assertSubscription(subscription, readSubscription, 0, 1, 2);
  }

  private void assertSubscription(
      MessageSubscription subscription,
      MessageSubscription readSubscription,
      long sendTime,
      int wfInstanceKey,
      int actInstanceKey) {
    assertThat(readSubscription.getMessageName()).isEqualTo(subscription.getMessageName());
    assertThat(readSubscription.getCorrelationKey()).isEqualTo(subscription.getCorrelationKey());
    assertThat(readSubscription.getMessagePayload()).isEqualTo(subscription.getMessagePayload());

    assertThat(readSubscription.getCommandSentTime()).isEqualTo(sendTime);
    assertThat(readSubscription.getWorkflowInstanceKey()).isEqualTo(wfInstanceKey);
    assertThat(readSubscription.getElementInstanceKey()).isEqualTo(actInstanceKey);
  }

  @Test
  public void shouldNotVisitMessagesBeforeTime() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey", "{}", "nr1", 1234);
    final Message message2 = createMessage(2L, "name", "correlationKey", "{}", "nr2", 4567);

    stateController.put(message);
    stateController.put(message2);

    // then
    final List<Message> readMessage = new ArrayList<>();
    stateController.visitMessagesWithDeadlineBefore(1_000, readMessage::add);

    assertThat(readMessage).isEmpty();
  }

  @Test
  public void shouldNotFindMessageSubscriptionBeforeTime() {
    // given
    final MessageSubscription subscription =
        new MessageSubscription("messageName", "correlationKey", "{\"foo\":\"bar\"}", 1, 2, 1234);
    final MessageSubscription subscription2 =
        new MessageSubscription("messageName", "correlationKey", "{\"foo\":\"bar\"}", 2, 3, 4567);
    final Message message = createMessage(1L, "name", "correlationKey", "{}", "nr1", 1234);

    stateController.put(message);
    stateController.put(subscription);
    stateController.put(subscription2);

    // when
    final long deadline = 1_000L;

    // then
    final List<MessageSubscription> readSubscriptions =
        stateController.findSubscriptionBefore(deadline);
    assertThat(readSubscriptions).isEmpty();
  }

  @Test
  public void shouldVisitMessagesBeforeTime() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey", "{}", "nr1", 1234);
    final Message message2 = createMessage(2L, "otherName", "correlationKey", "{}", "nr2", 2000);

    stateController.put(message);
    stateController.put(message2);

    // then
    final List<Message> readMessage = new ArrayList<>();
    stateController.visitMessagesWithDeadlineBefore(1_999, readMessage::add);

    assertThat(readMessage.size()).isEqualTo(1);
    assertThat(readMessage.get(0).getKey()).isEqualTo(1L);
  }

  @Test
  public void shouldVisitMessagesBeforeTimeInOrder() {
    // given
    final long now = ActorClock.currentTimeMillis();

    final Message message = createMessage(1L, "name", "correlationKey", "{}", "nr1", 1234);
    final Message message2 = createMessage(2L, "name", "correlationKey", "{}", "nr1", 2000);

    stateController.put(message);
    stateController.put(message2);

    // when
    final long deadline = now + 3_000L;

    // then
    final List<Long> readMessage = new ArrayList<>();
    stateController.visitMessagesWithDeadlineBefore(deadline, m -> readMessage.add(m.getKey()));

    assertThat(readMessage.size()).isEqualTo(2);
    assertThat(readMessage).containsExactly(1L, 2L);
  }

  @Test
  public void shouldFindMessageSubscriptionBeforeTime() {
    // given
    final MessageSubscription subscription =
        new MessageSubscription("messageName", "correlationKey", "{\"foo\":\"bar\"}", 1, 2, 1234);
    final MessageSubscription subscription2 =
        new MessageSubscription("otherName", "otherKey", "{\"foo\":\"bar\"}", 2, 3, 2000);
    final Message message = createMessage(1L, "name", "correlationKey", "{}", "nr1", 1234);

    stateController.put(message);
    stateController.put(subscription);
    stateController.put(subscription2);

    // when
    final long deadline = 2_000L;

    // then
    final List<MessageSubscription> readSubscriptions =
        stateController.findSubscriptionBefore(deadline);
    assertThat(readSubscriptions.size()).isEqualTo(1);
    final MessageSubscription readSubscription = readSubscriptions.get(0);
    assertThat(readSubscription.getMessageName()).isEqualTo(wrapString("messageName"));
    assertThat(readSubscription.getCorrelationKey()).isEqualTo(wrapString("correlationKey"));
    assertThat(readSubscription.getMessagePayload()).isEqualTo(wrapString("{\"foo\":\"bar\"}"));
    assertThat(readSubscription.getCommandSentTime()).isEqualTo(1234);
  }

  @Test
  public void shouldFindMessageSubscriptionBeforeTimeInOrder() {
    // given
    final MessageSubscription subscription =
        new MessageSubscription("messageName", "correlationKey", "{\"foo\":\"bar\"}", 1, 2, 1234);
    final MessageSubscription subscription2 =
        new MessageSubscription("otherName", "otherKey", "{\"foo\":\"bar\"}", 3, 4, 2000);
    final Message message = createMessage(1L, "name", "correlationKey", "{}", "nr1", 1234);

    stateController.put(message);
    stateController.put(subscription);
    stateController.put(subscription2);

    // when
    final long deadline = 3_000L;

    // then
    final List<MessageSubscription> readSubscriptions =
        stateController.findSubscriptionBefore(deadline);
    assertThat(readSubscriptions.size()).isEqualTo(2);
    assertThat(readSubscriptions)
        .extracting(s -> s.getWorkflowInstanceKey())
        .containsExactly(1L, 3L);
  }

  @Test
  public void shouldRemoveMessage() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey", "{}", "id", 1234);
    stateController.put(message);

    stateController.putMessageCorrelation(1L, 2L);
    stateController.putMessageCorrelation(1L, 3L);

    // when
    stateController.remove(message.getKey());

    // then
    final List<Message> readMessages = new ArrayList<>();
    stateController.visitMessagesWithDeadlineBefore(2000, readMessages::add);

    assertThat(readMessages.size()).isEqualTo(0);

    // and
    final List<Long> keys = new ArrayList<>();
    stateController.visitMessages(
        wrapString("name"), wrapString("correlationKey"), m -> keys.add(m.getKey()));

    assertThat(keys).isEmpty();

    // and
    final boolean exist =
        stateController.exist(
            wrapString("messageName"), wrapString("correlationKey"), wrapString("id"));
    assertThat(exist).isFalse();

    // and
    assertThat(stateController.existMessageCorrelation(1L, 2L)).isFalse();
    assertThat(stateController.existMessageCorrelation(1L, 3L)).isFalse();
  }

  @Test
  public void shouldRemoveMessageWithoutId() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey");

    stateController.put(message);

    // when
    stateController.remove(message.getKey());

    // then
    final List<Message> readMessages = new ArrayList<>();
    stateController.visitMessagesWithDeadlineBefore(2000, readMessages::add);

    assertThat(readMessages.size()).isEqualTo(0);

    // and
    final List<Long> keys = new ArrayList<>();
    stateController.visitMessages(
        wrapString("name"), wrapString("correlationKey"), m -> keys.add(m.getKey()));

    assertThat(keys).isEmpty();
  }

  @Test
  public void shouldNotFailOnRemoveMessageTwice() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey", "{}", "id", 1234);

    stateController.put(message);

    // when
    stateController.remove(message.getKey());
    stateController.remove(message.getKey());

    // then
    final List<Message> readMessages = new ArrayList<>();
    stateController.visitMessagesWithDeadlineBefore(2000, readMessages::add);

    assertThat(readMessages.size()).isEqualTo(0);

    // and
    final List<Long> keys = new ArrayList<>();
    stateController.visitMessages(
        wrapString("name"), wrapString("correlationKey"), m -> keys.add(m.getKey()));

    assertThat(keys).isEmpty();

    // and
    final boolean exist =
        stateController.exist(
            wrapString("messageName"), wrapString("correlationKey"), wrapString("id"));
    assertThat(exist).isFalse();
  }

  @Test
  public void shouldNotRemoveDifferenMessage() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey", "{}", "id1", 1234);
    final Message message2 = createMessage(2L, "name", "correlationKey", "{}", "id2", 4567);

    stateController.put(message);

    stateController.putMessageCorrelation(1L, 3L);
    stateController.putMessageCorrelation(2L, 4L);

    // when
    stateController.remove(message2.getKey());

    // then
    final long deadline = ActorClock.currentTimeMillis() + 2_000L;
    final List<Message> readMessages = new ArrayList<>();
    stateController.visitMessagesWithDeadlineBefore(deadline, readMessages::add);

    assertThat(readMessages.size()).isEqualTo(1);

    // and
    final List<Long> keys = new ArrayList<>();
    stateController.visitMessages(
        wrapString("name"), wrapString("correlationKey"), m -> keys.add(m.getKey()));

    assertThat(keys).hasSize(1).contains(1L);

    // and
    final boolean exist =
        stateController.exist(wrapString("name"), wrapString("correlationKey"), wrapString("id1"));
    assertThat(exist).isTrue();

    // and
    assertThat(stateController.existMessageCorrelation(1L, 3L));
  }

  @Test
  public void shouldExistCorrelatedMessage() {
    // when
    stateController.putMessageCorrelation(1L, 2L);

    // then
    assertThat(stateController.existMessageCorrelation(1L, 2L)).isTrue();

    assertThat(stateController.existMessageCorrelation(3L, 2L)).isFalse();
    assertThat(stateController.existMessageCorrelation(1L, 3L)).isFalse();
  }

  @Test
  public void shouldRemoveSubscription() {
    // given
    final MessageSubscription subscription =
        new MessageSubscription("messageName", "correlationKey", "{\"foo\":\"bar\"}", 1, 2, 1234);

    stateController.put(subscription);

    // when
    stateController.remove(subscription);

    // then
    List<MessageSubscription> readSubscriptions = stateController.findSubscriptionBefore(2000);
    assertThat(readSubscriptions.size()).isEqualTo(0);

    // and
    readSubscriptions =
        stateController.findSubscriptions(wrapString("messageName"), wrapString("correlationKey"));
    assertThat(readSubscriptions.size()).isEqualTo(0);

    // and
    final boolean exist = stateController.exist(subscription);
    assertThat(exist).isFalse();
  }

  @Test
  public void shouldRemoveSubscriptionWithRecord() {
    // given
    final MessageSubscription subscription =
        new MessageSubscription("messageName", "correlationKey", "{\"foo\":\"bar\"}", 1, 2, 1234);

    stateController.put(subscription);

    // when
    final MessageSubscriptionRecord messageSubscriptionRecord = new MessageSubscriptionRecord();
    messageSubscriptionRecord.setWorkflowInstanceKey(1);
    messageSubscriptionRecord.setElementInstanceKey(2);
    final boolean removed = stateController.remove(messageSubscriptionRecord);

    // then
    assertThat(removed).isTrue();
    List<MessageSubscription> readSubscriptions = stateController.findSubscriptionBefore(2000);
    assertThat(readSubscriptions.size()).isEqualTo(0);

    // and
    readSubscriptions =
        stateController.findSubscriptions(wrapString("messageName"), wrapString("correlationKey"));
    assertThat(readSubscriptions.size()).isEqualTo(0);

    // and
    final boolean exist = stateController.exist(subscription);
    assertThat(exist).isFalse();
  }

  @Test
  public void shouldNotFailOnRemoveSubscriptionTwice() {
    // given
    final MessageSubscription subscription =
        new MessageSubscription("messageName", "correlationKey", "{\"foo\":\"bar\"}", 1, 2, 1234);

    stateController.put(subscription);

    // when
    stateController.remove(subscription);
    stateController.remove(subscription);

    // then
    List<MessageSubscription> readSubscriptions = stateController.findSubscriptionBefore(2000);
    assertThat(readSubscriptions.size()).isEqualTo(0);

    // and
    readSubscriptions =
        stateController.findSubscriptions(wrapString("messageName"), wrapString("correlationKey"));
    assertThat(readSubscriptions.size()).isEqualTo(0);
  }

  @Test
  public void shouldNotRemoveSubscriptionOnDifferentKey() {
    // given
    final MessageSubscription subscription =
        new MessageSubscription("messageName", "correlationKey", "{\"foo\":\"bar\"}", 1, 2, 0);
    final MessageSubscription subscription2 =
        new MessageSubscription("messageName", "correlationKey", "{\"foo\":\"bar\"}", 2, 3, 0);

    stateController.put(subscription);

    // when
    stateController.remove(subscription2);

    // then
    final List<MessageSubscription> readSubscriptions =
        stateController.findSubscriptions(wrapString("messageName"), wrapString("correlationKey"));
    assertThat(readSubscriptions.size()).isEqualTo(1);
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
      long key, String name, String correlationKey, String payload, String id) {
    return new Message(
        key,
        wrapString(name),
        wrapString(correlationKey),
        wrapString(payload),
        wrapString(id),
        10_000,
        0L);
  }

  private Message createMessage(
      long key, String name, String correlationKey, String payload, String id, long deadline) {
    return new Message(
        key,
        wrapString(name),
        wrapString(correlationKey),
        wrapString(payload),
        wrapString(id),
        10_000L,
        deadline);
  }
}
