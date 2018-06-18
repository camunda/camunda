/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.client.event;

import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.*;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.subscription.TopicSubscription;
import io.zeebe.client.api.subscription.TopicSubscriptionBuilderStep1.TopicSubscriptionBuilderStep3;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.impl.subscription.topic.TopicSubscriberGroup;
import io.zeebe.client.impl.subscription.topic.TopicSubscriptionBuilderImpl;
import io.zeebe.client.util.ClientRule;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.*;
import io.zeebe.protocol.intent.SubscriberIntent;
import io.zeebe.test.broker.protocol.brokerapi.*;
import io.zeebe.test.broker.protocol.brokerapi.data.Topology;
import io.zeebe.transport.RemoteAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.*;
import org.junit.rules.RuleChain;

public class PartitionedTopicSubscriptionTest {

  public static final String TOPIC = "baz";
  public static final int PARTITION_1 = 1;
  public static final int PARTITION_2 = 2;

  public ClientRule clientRule = new ClientRule();
  public StubBrokerRule broker1 = new StubBrokerRule("localhost", 51015);
  public StubBrokerRule broker2 = new StubBrokerRule("localhost", 51016);

  protected ZeebeClient client;

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(broker1).around(broker2).around(clientRule);

  @Before
  public void setUp() {
    final Topology topology =
        new Topology()
            .addLeader(broker1, Protocol.SYSTEM_TOPIC, Protocol.SYSTEM_PARTITION)
            .addLeader(broker1, clientRule.getDefaultTopicName(), PARTITION_1)
            .addLeader(broker2, clientRule.getDefaultTopicName(), PARTITION_2);

    broker1.setCurrentTopology(topology);
    broker2.setCurrentTopology(topology);

    client = clientRule.getClient();
  }

  @Test
  public void shouldSubscribeToMultiplePartitionsOfATopic() {
    // given
    broker1.stubTopicSubscriptionApi(456);
    broker2.stubTopicSubscriptionApi(789);

    // when
    final TopicSubscription subscription =
        clientRule
            .topicClient()
            .newSubscription()
            .name("hohoho")
            .recordHandler(new RecordingHandler())
            .open();

    // then
    assertThat(subscription.isOpen()).isTrue();

    final List<ExecuteCommandRequest> subscribeRequestsBroker1 = getSubscribeRequests(broker1);
    assertThat(subscribeRequestsBroker1).hasSize(1);
    final ExecuteCommandRequest request1 = subscribeRequestsBroker1.get(0);
    assertThat(request1.partitionId()).isEqualTo(PARTITION_1);

    final List<ExecuteCommandRequest> subscribeRequestsBroker2 = getSubscribeRequests(broker2);
    assertThat(subscribeRequestsBroker2).hasSize(1);
    final ExecuteCommandRequest request2 = subscribeRequestsBroker2.get(0);
    assertThat(request2.partitionId()).isEqualTo(PARTITION_2);
  }

  protected List<ExecuteCommandRequest> getSubscribeRequests(StubBrokerRule broker) {
    return broker
        .getReceivedCommandRequests()
        .stream()
        .filter(
            r -> r.valueType() == ValueType.SUBSCRIBER && r.intent() == SubscriberIntent.SUBSCRIBE)
        .collect(Collectors.toList());
  }

  @Test
  public void shouldReceiveEventsFromMultiplePartitions() {
    // given
    final int subscriberKey1 = 456;
    broker1.stubTopicSubscriptionApi(subscriberKey1);

    final int subscriberKey2 = 789;
    broker2.stubTopicSubscriptionApi(subscriberKey2);

    final RecordingHandler eventHandler = new RecordingHandler();
    clientRule.topicClient().newSubscription().name("hohoho").recordHandler(eventHandler).open();

    final RemoteAddress clientAddressFromBroker1 =
        broker1.getReceivedCommandRequests().get(0).getSource();
    final RemoteAddress clientAddressFromBroker2 =
        broker2.getReceivedCommandRequests().get(0).getSource();

    // when
    final long key1 = 3;
    broker1.pushTopicEvent(
        clientAddressFromBroker1,
        b -> b.partitionId(PARTITION_1).subscriberKey(subscriberKey1).key(key1));
    final long key2 = 4;
    broker2.pushTopicEvent(
        clientAddressFromBroker2,
        b -> b.partitionId(PARTITION_2).subscriberKey(subscriberKey2).key(key2));

    // then
    waitUntil(() -> eventHandler.numRecordedRecords() == 2);
    assertThat(eventHandler.getRecordedRecords())
        .extracting("metadata.key")
        .containsExactlyInAnyOrder(key1, key2);
  }

  @Test
  public void shouldCloseAllOpenSubscriptions() {
    // given
    final int subscriberKey1 = 456;
    broker1.stubTopicSubscriptionApi(subscriberKey1);
    final int subscriberKey2 = 789;
    broker2.stubTopicSubscriptionApi(subscriberKey2);

    final TopicSubscription subscription =
        clientRule
            .topicClient()
            .newSubscription()
            .name("hohoho")
            .recordHandler(new RecordingHandler())
            .open();

    // when
    subscription.close();

    // then
    final List<ControlMessageRequest> closeRequestsBroker1 = getCloseSubscriptionRequests(broker1);
    assertThat(closeRequestsBroker1).hasSize(1);
    assertThat(closeRequestsBroker1.get(0).getData().get("subscriberKey"))
        .isEqualTo(subscriberKey1);

    final List<ControlMessageRequest> closeRequestsBroker2 = getCloseSubscriptionRequests(broker2);
    assertThat(closeRequestsBroker2).hasSize(1);
    assertThat(closeRequestsBroker2.get(0).getData().get("subscriberKey"))
        .isEqualTo(subscriberKey2);
  }

  @Test
  public void shouldNotOpenSubscriptionGroupWhenSingleSubscriptionCannotBeOpened() {
    // given
    final int subscriberKey1 = 456;
    broker1.stubTopicSubscriptionApi(subscriberKey1);

    broker2
        .onExecuteCommandRequest(ValueType.SUBSCRIBER, SubscriberIntent.SUBSCRIBE)
        .respondWithError()
        .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
        .errorData("foo")
        .register();

    final TopicSubscriptionBuilderStep3 builder =
        clientRule
            .topicClient()
            .newSubscription()
            .name("hohoho")
            .recordHandler(new RecordingHandler());

    // when
    final Throwable failure = catchThrowable(() -> builder.open());

    // then
    assertThat(failure)
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("Could not open subscriber group");

    assertThat(failure.getCause()).hasMessageContaining("Could not subscribe to all partitions");
  }

  @Test
  public void shouldCloseSuccessfulSubscriptionIfAnotherSubscriptionCannotBeOpened()
      throws Exception {
    // given
    final int subscriberKey1 = 456;
    broker1.stubTopicSubscriptionApi(subscriberKey1);

    final ResponseController responseController =
        broker2
            .onExecuteCommandRequest(ValueType.SUBSCRIBER, SubscriberIntent.SUBSCRIBE)
            .respondWithError()
            .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
            .errorData("foo")
            .registerControlled();

    // assuming that subscription to broker 1 is successful
    final TopicSubscriptionBuilderImpl builder =
        (TopicSubscriptionBuilderImpl)
            clientRule
                .topicClient()
                .newSubscription()
                .name("hohoho")
                .recordHandler(new RecordingHandler());

    final Future<TopicSubscriberGroup> groupFuture = builder.buildSubscriberGroup();
    waitUntil(() -> getOpenSubscriptionRequests(broker1).size() == 1);

    // when
    responseController.unblockNextResponse(); // triggering the error response and continuing

    // then
    waitUntil(() -> groupFuture.isDone());
    assertFailed(groupFuture);

    final List<ControlMessageRequest> closeRequestsBroker1 = getCloseSubscriptionRequests(broker1);
    assertThat(closeRequestsBroker1).hasSize(1);
    assertThat(closeRequestsBroker1.get(0).getData().get("subscriberKey"))
        .isEqualTo(subscriberKey1);
  }

  @Test
  public void shouldCloseSubscriptionIfASingleSubscriptionIsAborted() throws InterruptedException {
    // given
    broker1.stubTopicSubscriptionApi(456);
    final int subscriberKey2 = 789;
    broker2.stubTopicSubscriptionApi(subscriberKey2);

    final TopicSubscription subscription =
        clientRule
            .topicClient()
            .newSubscription()
            .name("hohoho")
            .recordHandler(new RecordingHandler())
            .open();

    // when
    broker1.closeTransport();
    Thread.sleep(500L); // let subscriber attempt reopening
    clientRule.getClock().addTime(Duration.ofSeconds(60)); // make request time out immediately

    // then
    waitUntil(() -> subscription.isClosed());

    final List<ControlMessageRequest> closeRequestsBroker2 = getCloseSubscriptionRequests(broker2);
    assertThat(closeRequestsBroker2).hasSize(1);
    assertThat(closeRequestsBroker2.get(0).getData().get("subscriberKey"))
        .isEqualTo(subscriberKey2);
  }

  @Test
  public void shouldReopenIndividualSubscriptions() {
    // given
    broker1.stubTopicSubscriptionApi(456);
    broker2.stubTopicSubscriptionApi(789);

    final String subscriptionName = "hohoho";
    final TopicSubscription subscription =
        clientRule
            .topicClient()
            .newSubscription()
            .name(subscriptionName)
            .recordHandler(new RecordingHandler())
            .open();

    // when
    broker1.interruptAllServerChannels();

    // then
    final List<ExecuteCommandRequest> subscribeRequestsBroker1 =
        doRepeatedly(() -> getSubscribeRequests(broker1)).until(r -> r.size() == 2);
    assertThat(subscription.isOpen()).isTrue();

    final ExecuteCommandRequest request1 = subscribeRequestsBroker1.get(0);
    assertThat(request1.partitionId()).isEqualTo(PARTITION_1);
    assertThat(request1.getCommand().get("name")).isEqualTo(subscriptionName);

    final ExecuteCommandRequest request2 = subscribeRequestsBroker1.get(1);
    assertThat(request2.partitionId()).isEqualTo(PARTITION_1);
    assertThat(request2.getCommand().get("name")).isEqualTo(subscriptionName);
  }

  @Test
  public void shouldNotOpenSubscriptionWhenPartitionsRequestFails() {
    // given
    broker1
        .onControlMessageRequest(r -> r.messageType() == ControlMessageType.REQUEST_PARTITIONS)
        .respondWithError()
        .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
        .errorData("foo")
        .register();

    final TopicSubscriptionBuilderStep3 builder =
        clientRule
            .topicClient()
            .newSubscription()
            .name("hohoho")
            .recordHandler(new RecordingHandler());

    // when
    final Throwable failure = catchThrowable(() -> builder.open());

    // then
    assertThat(failure)
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("Could not open subscriber group");

    assertThat(failure.getCause()).hasMessageContaining("Requesting partitions failed");

    assertThat(getSubscribeRequests(broker1)).isEmpty();
    assertThat(getSubscribeRequests(broker2)).isEmpty();
  }

  @Test
  public void shouldNotOpenSubscriptionToNonExistentTopic() {
    // given
    final String nonExistingTopic = "jj";

    final TopicSubscriptionBuilderStep3 builder =
        client
            .topicClient(nonExistingTopic)
            .newSubscription()
            .name("hohoho")
            .recordHandler(new RecordingHandler());

    // when
    final Throwable failure = catchThrowable(() -> builder.open());

    // then
    assertThat(failure)
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("Could not open subscriber group");

    assertThat(failure.getCause())
        .hasMessageContaining("Topic " + nonExistingTopic + " is not known");

    assertThat(getSubscribeRequests(broker1)).isEmpty();
    assertThat(getSubscribeRequests(broker2)).isEmpty();
  }

  @Test
  public void shouldSerializeHandlerInvocationForDifferentSubscribers() {
    // given
    final int subscriberKey1 = 456;
    broker1.stubTopicSubscriptionApi(subscriberKey1);

    final int subscriberKey2 = 789;
    broker2.stubTopicSubscriptionApi(subscriberKey2);

    final ParallelismDetectionHandler eventHandler =
        new ParallelismDetectionHandler(Duration.ofMillis(500));

    clientRule.topicClient().newSubscription().name("hohoho").recordHandler(eventHandler).open();

    final RemoteAddress clientAddressFromBroker1 =
        broker1.getReceivedCommandRequests().get(0).getSource();
    final RemoteAddress clientAddressFromBroker2 =
        broker2.getReceivedCommandRequests().get(0).getSource();

    // when
    broker1.pushTopicEvent(
        clientAddressFromBroker1,
        b -> b.partitionId(PARTITION_1).subscriberKey(subscriberKey1).key(3));
    broker2.pushTopicEvent(
        clientAddressFromBroker2,
        b -> b.partitionId(PARTITION_2).subscriberKey(subscriberKey2).key(4));
    waitUntil(() -> eventHandler.numInvocations() == 2);

    // then
    assertThat(eventHandler.hasDetectedParallelism()).isFalse();
  }

  @Test
  public void shouldApplyStartPositionPerSubscriber() {
    // given
    broker1.stubTopicSubscriptionApi(456);
    broker2.stubTopicSubscriptionApi(789);

    final int position1 = 987;
    final int position2 = 546;

    clientRule
        .topicClient()
        .newSubscription()
        .name("hohoho")
        .recordHandler(new RecordingHandler())
        .startAtPosition(PARTITION_1, position1)
        .startAtPosition(PARTITION_2, position2)
        .open();

    // then
    final ExecuteCommandRequest broker1Request = getSubscribeRequests(broker1).get(0);
    assertThat(broker1Request.getCommand()).containsEntry("startPosition", position1);

    final ExecuteCommandRequest broker2Request = getSubscribeRequests(broker2).get(0);
    assertThat(broker2Request.getCommand()).containsEntry("startPosition", position2);
  }

  @Test
  public void shouldOverrideDefaultStartPosition() {
    // given
    broker1.stubTopicSubscriptionApi(456);
    broker2.stubTopicSubscriptionApi(789);

    final int position1 = 987;

    clientRule
        .topicClient()
        .newSubscription()
        .name("hohoho")
        .recordHandler(new RecordingHandler())
        .startAtTailOfTopic()
        .startAtPosition(PARTITION_1, position1)
        .open();

    // then
    final ExecuteCommandRequest broker1Request = getSubscribeRequests(broker1).get(0);
    assertThat(broker1Request.getCommand()).containsEntry("startPosition", position1);

    final ExecuteCommandRequest broker2Request = getSubscribeRequests(broker2).get(0);
    assertThat(broker2Request.getCommand()).containsEntry("startPosition", -1);
  }

  protected List<ExecuteCommandRequest> getOpenSubscriptionRequests(StubBrokerRule broker) {
    return broker
        .getReceivedCommandRequests()
        .stream()
        .filter(
            r -> r.valueType() == ValueType.SUBSCRIBER && r.intent() == SubscriberIntent.SUBSCRIBE)
        .collect(Collectors.toList());
  }

  protected List<ControlMessageRequest> getCloseSubscriptionRequests(StubBrokerRule broker) {
    return broker
        .getReceivedControlMessageRequests()
        .stream()
        .filter(r -> r.messageType() == ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
        .collect(Collectors.toList());
  }

  private void assertFailed(Future<?> future) {
    assertThatThrownBy(() -> future.get()).isInstanceOf(ExecutionException.class);
  }
}
