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
package io.zeebe.test.broker.protocol.clientapi;

import static io.zeebe.protocol.Protocol.DEFAULT_TOPIC;
import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.test.util.TestUtil.waitUntil;

import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ExecuteCommandResponseDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.protocol.clientapi.SubscribedRecordDecoder;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.SubscriberIntent;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.Transports;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.clock.ControlledActorClock;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.junit.rules.ExternalResource;

public class ClientApiRule extends ExternalResource {

  public static final long DEFAULT_LOCK_DURATION = 10000L;

  protected ClientTransport transport;

  protected final Supplier<SocketAddress> brokerAddressSupplier;
  protected RemoteAddress streamAddress;

  protected MsgPackHelper msgPackHelper;
  protected RawMessageCollector incomingMessageCollector;

  private Int2ObjectHashMap<TestTopicClient> testTopicClients = new Int2ObjectHashMap<>();
  private ControlledActorClock controlledActorClock = new ControlledActorClock();
  private ActorScheduler scheduler;

  protected int defaultPartitionId = -1;

  public ClientApiRule(Supplier<SocketAddress> brokerAddressSupplier) {
    this.brokerAddressSupplier = brokerAddressSupplier;
  }

  @Override
  protected void before() throws Throwable {
    scheduler =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(1)
            .setActorClock(controlledActorClock)
            .build();
    scheduler.start();

    incomingMessageCollector = new RawMessageCollector();

    transport =
        Transports.newClientTransport()
            .inputListener(incomingMessageCollector)
            .scheduler(scheduler)
            .build();

    msgPackHelper = new MsgPackHelper();
    streamAddress = transport.registerRemoteAddress(brokerAddressSupplier.get());

    final List<Integer> partitionIds = doRepeatedly(this::getPartitionIds).until(p -> !p.isEmpty());
    defaultPartitionId = partitionIds.get(0);
  }

  @Override
  protected void after() {
    if (transport != null) {
      transport.close();
    }

    if (scheduler != null) {
      scheduler.stop();
    }
  }

  /** targets the default partition by default */
  public ExecuteCommandRequestBuilder createCmdRequest() {
    return new ExecuteCommandRequestBuilder(transport.getOutput(), streamAddress, msgPackHelper)
        .partitionId(defaultPartitionId);
  }

  public ControlMessageRequestBuilder createControlMessageRequest() {
    return new ControlMessageRequestBuilder(transport.getOutput(), streamAddress, msgPackHelper);
  }

  public ClientApiRule moveMessageStreamToTail() {
    incomingMessageCollector.moveToTail();
    return this;
  }

  public ClientApiRule moveMessageStreamToHead() {
    incomingMessageCollector.moveToHead();
    return this;
  }

  public int numSubscribedEventsAvailable() {
    return (int) incomingMessageCollector.getNumMessagesFulfilling(this::isSubscribedEvent);
  }

  public TestTopicClient topic() {
    return topic(defaultPartitionId);
  }

  public TestTopicClient topic(final int partitionId) {
    if (!testTopicClients.containsKey(partitionId)) {
      testTopicClients.put(partitionId, new TestTopicClient(this, partitionId));
    }
    return testTopicClients.get(partitionId);
  }

  public ExecuteCommandRequest openTopicSubscription(final String name, final long startPosition) {
    return openTopicSubscription(defaultPartitionId, name, startPosition);
  }

  public ExecuteCommandRequest openTopicSubscription(
      final int partitionId, final String name, final long startPosition) {
    return createCmdRequest()
        .partitionId(partitionId)
        .type(ValueType.SUBSCRIBER, SubscriberIntent.SUBSCRIBE)
        .command()
        .put("startPosition", startPosition)
        .put("name", name)
        .put("bufferSize", 1024)
        .done()
        .send();
  }

  public ControlMessageRequest closeTopicSubscription(long subscriberKey) {
    return createControlMessageRequest()
        .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
        .data()
        .put("topicName", DEFAULT_TOPIC)
        .put("partitionId", defaultPartitionId)
        .put("subscriberKey", subscriberKey)
        .done()
        .send();
  }

  public ControlMessageRequest openJobSubscription(final String type) {
    return openJobSubscription(defaultPartitionId, type, DEFAULT_LOCK_DURATION);
  }

  public ControlMessageRequest closeJobSubscription(long subscriberKey) {
    return createControlMessageRequest()
        .messageType(ControlMessageType.REMOVE_JOB_SUBSCRIPTION)
        .data()
        .put("subscriberKey", subscriberKey)
        .done()
        .send();
  }

  public ControlMessageRequest openJobSubscription(
      final int partitionId, final String type, long lockDuration, int credits) {
    return createControlMessageRequest()
        .messageType(ControlMessageType.ADD_JOB_SUBSCRIPTION)
        .partitionId(partitionId)
        .data()
        .put("jobType", type)
        .put("timeout", lockDuration)
        .put("worker", "test")
        .put("credits", credits)
        .done()
        .send();
  }

  public ControlMessageRequest openJobSubscription(
      final int partitionId, final String type, long lockDuration) {
    return openJobSubscription(partitionId, type, lockDuration, 10);
  }

  public Stream<RawMessage> incomingMessages() {
    return Stream.generate(incomingMessageCollector);
  }

  /**
   * @return an infinite stream of received subscribed events; make sure to use short-circuiting
   *     operations to reduce it to a finite stream
   */
  public Stream<SubscribedRecord> subscribedEvents() {
    return incomingMessages().filter(this::isSubscribedEvent).map(this::asSubscribedEvent);
  }

  public Stream<RawMessage> commandResponses() {
    return incomingMessages().filter(this::isCommandResponse);
  }

  public void interruptAllChannels() {
    transport.interruptAllChannels();
  }

  public SocketAddress getBrokerAddress() {
    return brokerAddressSupplier.get();
  }

  protected SubscribedRecord asSubscribedEvent(RawMessage message) {
    final SubscribedRecord event = new SubscribedRecord(message);
    event.wrap(message.getMessage(), 0, message.getMessage().capacity());
    return event;
  }

  protected boolean isCommandResponse(RawMessage message) {
    return message.isResponse()
        && isMessageOfType(message.getMessage(), ExecuteCommandResponseDecoder.TEMPLATE_ID);
  }

  protected boolean isSubscribedEvent(RawMessage message) {
    return message.isMessage()
        && isMessageOfType(message.getMessage(), SubscribedRecordDecoder.TEMPLATE_ID);
  }

  protected boolean isMessageOfType(DirectBuffer message, int type) {
    final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    headerDecoder.wrap(message, 0);

    return headerDecoder.templateId() == type;
  }

  public void waitForTopic(int partitions) {

    waitUntil(() -> getPartitionIds().size() >= partitions);
  }

  @SuppressWarnings("unchecked")
  public List<Integer> getPartitionIds() {
    try {
      final ControlMessageResponse response = requestPartitions();

      final Map<String, Object> data = response.getData();
      final List<Map<String, Object>> partitions =
          (List<Map<String, Object>>) data.get("partitions");

      return partitions
          .stream()
          .filter(p -> DEFAULT_TOPIC.equals(p.get("topic")))
          .map(p -> ((Number) p.get("id")).intValue())
          .collect(Collectors.toList());
    } catch (Exception e) {
      return Collections.EMPTY_LIST;
    }
  }

  public ControlMessageResponse requestPartitions() {
    return createControlMessageRequest()
        .partitionId(Protocol.SYSTEM_PARTITION)
        .messageType(ControlMessageType.REQUEST_PARTITIONS)
        .data()
        .done()
        .sendAndAwait();
  }

  public int getDefaultPartitionId() {
    return defaultPartitionId;
  }

  public ClientTransport getTransport() {
    return transport;
  }

  public ControlledActorClock getClock() {
    return controlledActorClock;
  }
}
