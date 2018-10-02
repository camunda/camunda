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

import io.zeebe.util.sched.clock.ActorClock;
import java.util.ArrayList;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MessageStateControllerTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private MessageStateController stateController;

  @Before
  public void setUp() throws Exception {
    stateController = new MessageStateController();
    stateController.open(folder.newFolder("rocksdb"), false);
  }

  @After
  public void close() {
    stateController.close();
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
        new MessageSubscription(
            "messageName", "correlationKey", "{\"foo\":\"bar\"}", 1, 2, 3, 1234);

    // when
    final boolean exist = stateController.exist(subscription);

    // then
    assertThat(exist).isFalse();
  }

  @Test
  public void shouldExistSubscription() {
    // given
    final MessageSubscription subscription =
        new MessageSubscription(
            "messageName", "correlationKey", "{\"foo\":\"bar\"}", 1, 2, 3, 1234);
    stateController.put(subscription);

    // when
    final boolean exist = stateController.exist(subscription);

    // then
    assertThat(exist).isTrue();
  }

  @Test
  public void shouldFindFirstMessage() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey");
    stateController.put(message);

    final Message message2 = createMessage(2L, "name", "correlationKey");
    stateController.put(message2);

    // when
    final Message readMessage =
        stateController.findFirstMessage(wrapString("name"), wrapString("correlationKey"));

    // then
    assertThat(readMessage).isNotNull();
    assertThat(readMessage.getKey()).isEqualTo(message.getKey());
    assertThat(readMessage.getName()).isEqualTo(message.getName());
    assertThat(readMessage.getCorrelationKey()).isEqualTo(message.getCorrelationKey());
  }

  @Test
  public void shouldNotFindMessageIfNameDoesntMatch() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey");
    stateController.put(message);

    // when
    final Message readMessage =
        stateController.findFirstMessage(wrapString("otherName"), wrapString("correlationKey"));

    // then
    assertThat(readMessage).isNull();
  }

  @Test
  public void shouldNotFindMessageIfCorrelationKeyDoesntMatch() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey");
    stateController.put(message);

    // when
    final Message readMessage =
        stateController.findFirstMessage(wrapString("name"), wrapString("otherCorrelationKey"));

    // then
    assertThat(readMessage).isNull();
  }

  @Test
  public void shouldFindSubscription() {
    // given
    final MessageSubscription subscription =
        new MessageSubscription(
            "messageName", "correlationKey", "{\"foo\":\"bar\"}", 1, 2, 3, 1234);
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
        new MessageSubscription(
            "messageName", "correlationKey", "{\"foo\":\"bar\"}", 1, 2, 3, 1234);
    final MessageSubscription subscription2 =
        new MessageSubscription(
            "messageName", "correlationKey", "{\"foo\":\"bar\"}", 2, 2, 3, 1234);
    final MessageSubscription subscription3 =
        new MessageSubscription("otherName", "correlationKey", "{\"foo\":\"bar\"}", 3, 2, 3, 1234);
    stateController.put(subscription);
    stateController.put(subscription2);
    stateController.put(subscription3);

    // when
    final List<MessageSubscription> readSubscriptions =
        stateController.findSubscriptions(wrapString("messageName"), wrapString("correlationKey"));

    // then
    assertThat(readSubscriptions.size()).isEqualTo(2);

    MessageSubscription readSubscription = readSubscriptions.get(0);
    assertSubscription(subscription, readSubscription, 1234, 1, 2, 3);
    readSubscription = readSubscriptions.get(1);
    assertSubscription(subscription2, readSubscription, 1234, 2, 2, 3);

    // and
    final List<MessageSubscription> otherSubscriptions =
        stateController.findSubscriptions(wrapString("otherName"), wrapString("correlationKey"));

    assertThat(otherSubscriptions.size()).isEqualTo(1);
    assertSubscription(subscription3, otherSubscriptions.get(0), 1234, 3, 2, 3);
  }

  @Test
  public void shouldFindSubscriptionWithMessageStored() {
    // given
    final MessageSubscription subscription =
        new MessageSubscription(
            "messageName", "correlationKey", "{\"foo\":\"bar\"}", 1, 2, 3, 1234);
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

  private void assertSubscription(
      MessageSubscription subscription, MessageSubscription readSubscription) {
    assertSubscription(subscription, readSubscription, 1234, 1, 2, 3);
  }

  private void assertSubscription(
      MessageSubscription subscription,
      MessageSubscription readSubscription,
      long sendTime,
      int partitionId,
      int wfInstanceKey,
      int actInstanceKey) {
    assertThat(readSubscription.getMessageName()).isEqualTo(subscription.getMessageName());
    assertThat(readSubscription.getCorrelationKey()).isEqualTo(subscription.getCorrelationKey());
    assertThat(readSubscription.getMessagePayload()).isEqualTo(subscription.getMessagePayload());

    assertThat(readSubscription.getCommandSentTime()).isEqualTo(sendTime);
    assertThat(readSubscription.getWorkflowInstancePartitionId()).isEqualTo(partitionId);
    assertThat(readSubscription.getWorkflowInstanceKey()).isEqualTo(wfInstanceKey);
    assertThat(readSubscription.getActivityInstanceKey()).isEqualTo(actInstanceKey);
  }

  @Test
  public void shouldNotFindMessageBeforeTime() {
    // given
    final long now = ActorClock.currentTimeMillis();

    final Message message = createMessage(1L, "name", "correlationKey", "{}", "nr1", 1234);
    final Message message2 = createMessage(2L, "name", "correlationKey", "{}", "nr2", 4567);

    stateController.put(message);
    stateController.put(message2);

    // when
    final long deadline = now + 1_000L;

    // then
    final List<Message> readMessage = new ArrayList<>();
    stateController.findMessagesWithDeadlineBefore(deadline, readMessage::add);

    assertThat(readMessage).isEmpty();
  }

  @Test
  public void shouldNotFindMessageSubscriptionBeforeTime() {
    // given
    final MessageSubscription subscription =
        new MessageSubscription(
            "messageName", "correlationKey", "{\"foo\":\"bar\"}", 1, 2, 3, 1234);
    final MessageSubscription subscription2 =
        new MessageSubscription(
            "messageName", "correlationKey", "{\"foo\":\"bar\"}", 1, 3, 3, 4567);
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
  public void shouldFindMessageBeforeTime() {
    // given
    final long now = ActorClock.currentTimeMillis();

    final Message message = createMessage(1L, "name", "correlationKey", "{}", "nr1", 1234);
    final Message message2 = createMessage(2L, "otherName", "correlationKey", "{}", "nr2", 4567);

    stateController.put(message);
    stateController.put(message2);

    // when
    final long deadline = now + 2_000L;

    // then
    final List<Message> readMessage = new ArrayList<>();
    stateController.findMessagesWithDeadlineBefore(deadline, readMessage::add);

    assertThat(readMessage.size()).isEqualTo(1);
    assertThat(readMessage.get(0).getKey()).isEqualTo(1L);
  }

  @Test
  public void shouldFindMessageSubscriptionBeforeTime() {
    // given
    final MessageSubscription subscription =
        new MessageSubscription(
            "messageName", "correlationKey", "{\"foo\":\"bar\"}", 1, 2, 3, 1234);
    final MessageSubscription subscription2 =
        new MessageSubscription("otherName", "otherKey", "{\"foo\":\"bar\"}", 1, 3, 3, 4567);
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
  public void shouldRemoveMessage() {
    // given
    final Message message = createMessage(1L, "name", "correlationKey", "{}", "id", 1234);
    stateController.put(message);

    // when
    stateController.remove(message.getKey());

    // then
    final List<Message> readMessages = new ArrayList<>();
    stateController.findMessagesWithDeadlineBefore(2000, readMessages::add);

    assertThat(readMessages.size()).isEqualTo(0);

    // and
    final Message readMessage =
        stateController.findFirstMessage(wrapString("messageName"), wrapString("correlationKey"));
    assertThat(readMessage).isNull();

    // and
    final boolean exist =
        stateController.exist(
            wrapString("messageName"), wrapString("correlationKey"), wrapString("id"));
    assertThat(exist).isFalse();
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
    stateController.findMessagesWithDeadlineBefore(2000, readMessages::add);

    assertThat(readMessages.size()).isEqualTo(0);

    // and
    final Message readMessage =
        stateController.findFirstMessage(wrapString("name"), wrapString("correlationKey"));
    assertThat(readMessage).isNull();
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
    stateController.findMessagesWithDeadlineBefore(2000, readMessages::add);

    assertThat(readMessages.size()).isEqualTo(0);

    // and
    final Message readMessage =
        stateController.findFirstMessage(wrapString("messageName"), wrapString("correlationKey"));
    assertThat(readMessage).isNull();

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

    // when
    stateController.remove(message2.getKey());

    // then
    final long deadline = ActorClock.currentTimeMillis() + 2_000L;
    final List<Message> readMessages = new ArrayList<>();
    stateController.findMessagesWithDeadlineBefore(deadline, readMessages::add);

    assertThat(readMessages.size()).isEqualTo(1);

    // and
    final Message readMessage =
        stateController.findFirstMessage(wrapString("name"), wrapString("correlationKey"));
    assertThat(readMessage).isNotNull();

    // and
    final boolean exist =
        stateController.exist(wrapString("name"), wrapString("correlationKey"), wrapString("id1"));
    assertThat(exist).isTrue();
  }

  @Test
  public void shouldRemoveSubscription() {
    // given
    final MessageSubscription subscription =
        new MessageSubscription(
            "messageName", "correlationKey", "{\"foo\":\"bar\"}", 1, 2, 3, 1234);

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
  public void shouldNotFailOnRemoveSubscriptionTwice() {
    // given
    final MessageSubscription subscription =
        new MessageSubscription(
            "messageName", "correlationKey", "{\"foo\":\"bar\"}", 1, 2, 3, 1234);

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
        new MessageSubscription(
            "messageName", "correlationKey", "{\"foo\":\"bar\"}", 1, 2, 3, 1234);
    final MessageSubscription subscription2 =
        new MessageSubscription(
            "messageName", "correlationKey", "{\"foo\":\"bar\"}", 2, 2, 3, 1234);

    stateController.put(subscription);

    // when
    stateController.remove(subscription2);

    // then
    List<MessageSubscription> readSubscriptions = stateController.findSubscriptionBefore(2000);
    assertThat(readSubscriptions.size()).isEqualTo(1);

    // and
    readSubscriptions =
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
        0L);
  }

  private Message createMessage(
      long key, String name, String correlationKey, String payload, String id) {
    return new Message(
        key, wrapString(name), wrapString(correlationKey), wrapString(payload), wrapString(id), 0L);
  }

  private Message createMessage(
      long key, String name, String correlationKey, String payload, String id, long deadline) {
    return new Message(
        key,
        wrapString(name),
        wrapString(correlationKey),
        wrapString(payload),
        wrapString(id),
        deadline);
  }
}
