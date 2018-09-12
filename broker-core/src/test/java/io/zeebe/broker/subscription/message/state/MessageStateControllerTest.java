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

import io.zeebe.util.collection.Tuple;
import io.zeebe.util.sched.clock.ActorClock;
import java.util.ArrayList;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
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
  public void shouldStoreMessage() {
    // given
    final Message message = new Message("messageName", "correlationKey", "{\"foo\":\"bar\"}", 1234);

    // when
    stateController.put(message);

    // then no error
  }

  @Test
  public void shouldStoreMessageWithId() {
    // given
    final Message message =
        new Message("idOfMessage", "messageName", "correlationKey", "{\"foo\":\"bar\"}", 1234);

    // when
    stateController.put(message);

    // then no error
  }

  @Test
  public void shouldStoreSubscription() {
    // given
    final MessageSubscription subscription =
        new MessageSubscription(
            "messageName", "correlationKey", "{\"foo\":\"bar\"}", 1, 2, 3, 1234);

    // when
    stateController.put(subscription);

    // then no error
  }

  @Test
  public void shouldNotExistIfNotStored() {
    // given
    final Message message =
        new Message("idOfMessage", "messageName", "correlationKey", "{\"foo\":\"bar\"}", 1234);

    // when
    final boolean exist = stateController.exist(message);

    // then
    assertThat(exist).isFalse();
  }

  @Test
  public void shouldNotExistIfNoId() {
    // given
    final Message message = new Message("messageName", "correlationKey", "{\"foo\":\"bar\"}", 1234);
    stateController.put(message);

    // when
    final boolean exist = stateController.exist(message);

    // then
    assertThat(exist).isFalse();
  }

  @Test
  public void shouldExist() {
    // given
    final Message message =
        new Message("idOfMessage", "messageName", "correlationKey", "{\"foo\":\"bar\"}", 1234);
    stateController.put(message);

    // when
    final boolean exist = stateController.exist(message);

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
  public void shouldFindMessage() {
    // given
    final long now = ActorClock.currentTimeMillis();
    final Message message = new Message("messageName", "correlationKey", "{\"foo\":\"bar\"}", 1234);
    stateController.put(message);

    // when
    final Message readMessage =
        stateController.findMessage(wrapString("messageName"), wrapString("correlationKey"));

    // then
    assertThat(readMessage.getName()).isEqualTo(message.getName());
    assertThat(readMessage.getCorrelationKey()).isEqualTo(message.getCorrelationKey());
    assertThat(readMessage.getPayload()).isEqualTo(message.getPayload());
    assertThat(readMessage.getTimeToLive()).isEqualTo(1234);
    assertThat(readMessage.getDeadline()).isGreaterThanOrEqualTo(now + 1234);
  }

  @Test
  public void shouldNotFindMessageWhichNotExist() {
    // given

    // when
    final Message readMessage =
        stateController.findMessage(wrapString("messageName"), wrapString("correlationKey"));

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
        new MessageSubscription(
            "messageName", "correlationKey", "{\"foo\":\"bar\"}", 3, 2, 3, 1234);
    stateController.put(subscription);
    stateController.put(subscription2);
    stateController.put(subscription3);

    // when
    final List<MessageSubscription> readSubscriptions =
        stateController.findSubscriptions(wrapString("messageName"), wrapString("correlationKey"));

    // then
    assertThat(readSubscriptions.size()).isEqualTo(3);

    MessageSubscription readSubscription = readSubscriptions.get(0);
    assertSubscription(subscription, readSubscription, 1234, 1, 2, 3);
    readSubscription = readSubscriptions.get(1);
    assertSubscription(subscription, readSubscription, 1234, 2, 2, 3);
    readSubscription = readSubscriptions.get(2);
    assertSubscription(subscription, readSubscription, 1234, 3, 2, 3);
  }

  @Test
  public void shouldFindSubscriptionWithMessageStored() {
    // given
    final MessageSubscription subscription =
        new MessageSubscription(
            "messageName", "correlationKey", "{\"foo\":\"bar\"}", 1, 2, 3, 1234);
    final Message message = new Message("messageName", "correlationKey", "{\"foo\":\"bar\"}", 1234);
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

    final Message message = new Message("messageName", "correlationKey", "{\"foo\":\"bar\"}", 1234);
    final Message message2 =
        new Message("messageName", "correlationKey", "{\"foo\":\"bar\"}", 4567);
    stateController.put(message);
    stateController.put(message2);

    // when
    final long deadline = now + 1_000L;

    // then
    final List<Message> readMessage = stateController.findMessageBefore(deadline);
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
    final Message message = new Message("messageName", "correlationKey", "{\"foo\":\"bar\"}", 1234);

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

    final Message message = new Message("messageName", "correlationKey", "{\"foo\":\"bar\"}", 1234);
    final Message message2 = new Message("otherName", "otherKey", "{\"foo\":\"bar\"}", 4567);
    stateController.put(message);
    stateController.put(message2);

    // when
    final long deadline = now + 2_000L;

    // then
    final List<Message> readMessage = stateController.findMessageBefore(deadline);
    assertThat(readMessage.size()).isEqualTo(1);
    assertThat(readMessage.get(0).getName()).isEqualTo(wrapString("messageName"));
    assertThat(readMessage.get(0).getCorrelationKey()).isEqualTo(wrapString("correlationKey"));
    assertThat(readMessage.get(0).getPayload()).isEqualTo(wrapString("{\"foo\":\"bar\"}"));
  }

  @Test
  public void shouldFindMessageSubscriptionBeforeTime() {
    // given
    final MessageSubscription subscription =
        new MessageSubscription(
            "messageName", "correlationKey", "{\"foo\":\"bar\"}", 1, 2, 3, 1234);
    final MessageSubscription subscription2 =
        new MessageSubscription("otherName", "otherKey", "{\"foo\":\"bar\"}", 1, 3, 3, 4567);
    final Message message = new Message("messageName", "correlationKey", "{\"foo\":\"bar\"}", 1234);

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
    final Message message =
        new Message("idOfMessage", "messageName", "correlationKey", "{\"foo\":\"bar\"}", 1234);
    stateController.put(message);

    // when
    stateController.remove(message);

    // then
    final List<Message> readMessages = stateController.findMessageBefore(2000);
    assertThat(readMessages.size()).isEqualTo(0);

    // and
    final Message readMessage =
        stateController.findMessage(wrapString("messageName"), wrapString("correlationKey"));
    assertThat(readMessage).isNull();

    // and
    final boolean exist = stateController.exist(message);
    assertThat(exist).isFalse();
  }

  @Test
  @Ignore("https://github.com/zeebe-io/zeebe/issues/1323")
  public void shouldRemoveAllEntriesIfMessageIsRemoved() {
    // given
    final Message message = new Message("messageName", "correlationKey", "{\"foo\":\"bar\"}", 1234);
    final Message message2 =
        new Message("messageName", "correlationKey", "{\"foo\":\"bar\"}", 4500);
    stateController.put(message);
    stateController.put(message2);

    // when
    stateController.remove(message2);

    // then
    final List<Message> readMessages = stateController.findMessageBefore(2000);
    assertThat(readMessages.size()).isEqualTo(0);

    // and
    final Message readMessage =
        stateController.findMessage(wrapString("messageName"), wrapString("correlationKey"));
    assertThat(readMessage).isNull();

    // and
    final UnsafeBuffer keyBuffer = new UnsafeBuffer(new byte[message.getKeyLength()]);
    message.writeKey(keyBuffer, 0);

    final boolean exists =
        stateController.exist(
            stateController.timeToLiveHandle, keyBuffer.byteArray(), 0, keyBuffer.capacity());
    assertThat(exists).isFalse();

    final List<Tuple<byte[], byte[]>> keyValues = new ArrayList<>();
    stateController.foreach(
        stateController.timeToLiveHandle,
        (k, v) -> {
          keyValues.add(new Tuple<>(k, v));
        });
    assertThat(keyValues).isEmpty();
  }

  @Test
  public void shouldRemoveMessageWithoutId() {
    // given
    final Message message = new Message("messageName", "correlationKey", "{\"foo\":\"bar\"}", 1234);
    stateController.put(message);

    // when
    stateController.remove(message);

    // then
    final List<Message> readMessages = stateController.findMessageBefore(2000);
    assertThat(readMessages.size()).isEqualTo(0);

    // and
    final Message readMessage =
        stateController.findMessage(wrapString("messageName"), wrapString("correlationKey"));
    assertThat(readMessage).isNull();

    // and
    final boolean exist = stateController.exist(message);
    assertThat(exist).isFalse();
  }

  @Test
  public void shouldNotFailOnRemoveMessageTwice() {
    // given
    final Message message =
        new Message("idOfMessage", "messageName", "correlationKey", "{\"foo\":\"bar\"}", 1234);
    stateController.put(message);

    // when
    stateController.remove(message);
    stateController.remove(message);

    // then
    final List<Message> readMessages = stateController.findMessageBefore(2000);
    assertThat(readMessages.size()).isEqualTo(0);

    // and
    final Message readMessage =
        stateController.findMessage(wrapString("messageName"), wrapString("correlationKey"));
    assertThat(readMessage).isNull();

    // and
    final boolean exist = stateController.exist(message);
    assertThat(exist).isFalse();
  }

  @Test
  public void shouldNotRemoveDifferenMessage() {
    // given
    final Message message =
        new Message("idOfMessage", "messageName", "correlationKey", "{\"foo\":\"bar\"}", 1234);
    final Message message2 =
        new Message("otherId", "otherName", "correlationKey", "{\"foo\":\"bar\"}", 1234);
    stateController.put(message);

    // when
    stateController.remove(message2);

    // then
    final long deadline = ActorClock.currentTimeMillis() + 2_000L;
    final List<Message> readMessages = stateController.findMessageBefore(deadline);
    assertThat(readMessages.size()).isEqualTo(1);

    // and
    final Message readMessage =
        stateController.findMessage(wrapString("messageName"), wrapString("correlationKey"));
    assertThat(readMessage).isNotNull();

    // and
    final boolean exist = stateController.exist(message);
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
}
