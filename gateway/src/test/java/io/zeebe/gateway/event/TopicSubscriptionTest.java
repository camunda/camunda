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
package io.zeebe.gateway.event;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.ZeebeClient;
import io.zeebe.gateway.api.commands.IncidentCommand;
import io.zeebe.gateway.api.commands.JobCommand;
import io.zeebe.gateway.api.commands.WorkflowInstanceCommand;
import io.zeebe.gateway.api.events.IncidentEvent;
import io.zeebe.gateway.api.events.JobEvent;
import io.zeebe.gateway.api.events.RaftEvent;
import io.zeebe.gateway.api.events.WorkflowInstanceEvent;
import io.zeebe.gateway.api.record.Record;
import io.zeebe.gateway.api.record.RecordMetadata;
import io.zeebe.gateway.api.subscription.IncidentCommandHandler;
import io.zeebe.gateway.api.subscription.IncidentEventHandler;
import io.zeebe.gateway.api.subscription.JobCommandHandler;
import io.zeebe.gateway.api.subscription.JobEventHandler;
import io.zeebe.gateway.api.subscription.RaftEventHandler;
import io.zeebe.gateway.api.subscription.RecordHandler;
import io.zeebe.gateway.api.subscription.TopicSubscription;
import io.zeebe.gateway.api.subscription.WorkflowInstanceCommandHandler;
import io.zeebe.gateway.api.subscription.WorkflowInstanceEventHandler;
import io.zeebe.gateway.impl.ZeebeClientImpl;
import io.zeebe.gateway.impl.subscription.SubscriptionManager;
import io.zeebe.gateway.impl.subscription.topic.TopicSubscriberGroup;
import io.zeebe.gateway.impl.subscription.topic.TopicSubscriptionBuilderImpl;
import io.zeebe.gateway.util.ClientRule;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.SubscriberIntent;
import io.zeebe.protocol.intent.SubscriptionIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.brokerapi.ControlMessageRequest;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.brokerapi.ResponseController;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.test.util.Conditions;
import io.zeebe.test.util.TestUtil;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class TopicSubscriptionTest {

  protected static final String SUBSCRIPTION_NAME = "foo";

  protected static final RecordHandler DO_NOTHING = e -> {};

  public StubBrokerRule broker = new StubBrokerRule();
  public ClientRule clientRule = new ClientRule(broker);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(broker).around(clientRule);

  @Rule public ExpectedException exception = ExpectedException.none();

  @Rule public AutoCloseableRule closeables = new AutoCloseableRule();

  protected ZeebeClient client;

  @Before
  public void setUp() {
    this.client = clientRule.getClient();
  }

  @Test
  public void shouldOpenSubscription() {
    // given
    broker.stubTopicSubscriptionApi(123L);

    // when
    clientRule
        .topicClient()
        .newSubscription()
        .name(SUBSCRIPTION_NAME)
        .recordHandler(DO_NOTHING)
        .open();

    // then
    final ExecuteCommandRequest subscribeRequest =
        broker
            .getReceivedCommandRequests()
            .stream()
            .filter((e) -> e.valueType() == ValueType.SUBSCRIBER)
            .findFirst()
            .get();

    assertThat(subscribeRequest.intent()).isEqualTo(SubscriberIntent.SUBSCRIBE);

    assertThat(subscribeRequest.getCommand())
        .containsEntry("startPosition", 0L) // default start position
        .containsEntry("bufferSize", 1024L)
        .containsEntry("name", SUBSCRIPTION_NAME)
        .doesNotContainEntry("forceStart", true);
  }

  @Test
  public void shouldOpenSubscriptionAndForceStart() {
    // given
    broker.stubTopicSubscriptionApi(123L);

    // when
    clientRule
        .topicClient()
        .newSubscription()
        .name(SUBSCRIPTION_NAME)
        .recordHandler(DO_NOTHING)
        .forcedStart()
        .open();

    // then
    final ExecuteCommandRequest subscribeRequest =
        broker
            .getReceivedCommandRequests()
            .stream()
            .filter((e) -> e.valueType() == ValueType.SUBSCRIBER)
            .findFirst()
            .get();

    assertThat(subscribeRequest.getCommand()).containsEntry("forceStart", true);
  }

  @Test
  public void shouldOpenSubscriptionAtHeadOfTopic() {
    // given
    broker.stubTopicSubscriptionApi(123L);

    // when
    clientRule
        .topicClient()
        .newSubscription()
        .name(SUBSCRIPTION_NAME)
        .recordHandler(DO_NOTHING)
        .startAtHeadOfTopic()
        .open();

    // then
    final ExecuteCommandRequest subscribeRequest =
        broker
            .getReceivedCommandRequests()
            .stream()
            .filter((e) -> e.valueType() == ValueType.SUBSCRIBER)
            .findFirst()
            .get();

    assertThat(subscribeRequest.intent()).isEqualTo(SubscriberIntent.SUBSCRIBE);
    assertThat(subscribeRequest.getCommand()).containsEntry("startPosition", 0L);
  }

  @Test
  public void shouldOpenSubscriptionAtTailOfTopic() {
    // given
    broker.stubTopicSubscriptionApi(123L);

    // when
    clientRule
        .topicClient()
        .newSubscription()
        .name(SUBSCRIPTION_NAME)
        .recordHandler(DO_NOTHING)
        .startAtTailOfTopic()
        .open();

    // then
    final ExecuteCommandRequest subscribeRequest =
        broker
            .getReceivedCommandRequests()
            .stream()
            .filter((e) -> e.valueType() == ValueType.SUBSCRIBER)
            .findFirst()
            .get();

    assertThat(subscribeRequest.intent()).isEqualTo(SubscriberIntent.SUBSCRIBE);

    assertThat(subscribeRequest.getCommand())
        .hasEntrySatisfying("startPosition", Conditions.isLowerThan(0))
        .containsEntry("bufferSize", 1024L)
        .containsEntry("name", SUBSCRIPTION_NAME)
        .doesNotContainEntry("forceStart", true);
  }

  @Test
  public void shouldOpenSubscriptionAtPosition() {
    // given
    broker.stubTopicSubscriptionApi(123L);

    // when
    clientRule
        .topicClient()
        .newSubscription()
        .name(SUBSCRIPTION_NAME)
        .recordHandler(DO_NOTHING)
        .startAtPosition(clientRule.getDefaultPartitionId(), 654L)
        .open();

    // then
    final ExecuteCommandRequest subscribeRequest =
        broker
            .getReceivedCommandRequests()
            .stream()
            .filter((e) -> e.valueType() == ValueType.SUBSCRIBER)
            .findFirst()
            .get();

    assertThat(subscribeRequest.intent()).isEqualTo(SubscriberIntent.SUBSCRIBE);

    assertThat(subscribeRequest.getCommand())
        .containsEntry("startPosition", 654L)
        .containsEntry("bufferSize", 1024L)
        .containsEntry("name", SUBSCRIPTION_NAME)
        .doesNotContainEntry("forceStart", true);
  }

  @Test
  public void shouldValidateEventHandlerNotNull() {
    // then
    exception.expect(RuntimeException.class);
    exception.expectMessage("recordHandler must not be null");

    // when
    clientRule.topicClient().newSubscription().name(SUBSCRIPTION_NAME).recordHandler(null).open();
  }

  @Test
  public void shouldValidateNameNotNull() {
    // then
    exception.expect(RuntimeException.class);
    exception.expectMessage("name must not be null");

    // when
    clientRule.topicClient().newSubscription().name(null).recordHandler(DO_NOTHING).open();
  }

  @Test
  public void shouldValidateBufferSizeGreaterThanZero() {
    // then
    exception.expect(RuntimeException.class);
    exception.expectMessage("bufferSize must be greater than 0");

    // when
    clientRule
        .topicClient()
        .newSubscription()
        .name("foo")
        .recordHandler(DO_NOTHING)
        .bufferSize(-1)
        .open();
  }

  @Test
  public void shouldRetryThreeTimesOnHandlerFailure() throws InterruptedException {
    // given
    broker.stubTopicSubscriptionApi(123L);

    final FailingHandler handler = new FailingHandler();
    final TopicSubscription subscription =
        clientRule
            .topicClient()
            .newSubscription()
            .name(SUBSCRIPTION_NAME)
            .recordHandler(handler)
            .startAtHeadOfTopic()
            .open();

    final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

    // when pushing two events
    broker.pushRaftEvent(clientAddress, 123L, 1L, 1L);
    broker.pushRaftEvent(clientAddress, 123L, 1L, 2L);

    // then
    waitUntil(() -> !subscription.isOpen());
    Thread.sleep(
        1000L); // wait an extra second as we might receive more events if this feature is broken

    assertThat(handler.getRecordedRecords()).hasSize(3);

    final Set<Long> eventPositions =
        handler
            .getRecordedRecords()
            .stream()
            .map((re) -> re.getMetadata().getPosition())
            .collect(Collectors.toSet());

    assertThat(eventPositions).containsExactly(1L);
  }

  @Test
  public void shouldResumeSubscriptionBeforeFailedEventAfterHandlerFailure() {
    // given
    broker.stubTopicSubscriptionApi(123L);

    final FailingHandler handler = new FailingHandler(e -> e.getMetadata().getPosition() == 2L);
    final TopicSubscription subscription =
        clientRule
            .topicClient()
            .newSubscription()
            .name(SUBSCRIPTION_NAME)
            .recordHandler(handler)
            .startAtHeadOfTopic()
            .open();

    final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();
    broker.pushRaftEvent(clientAddress, 123L, 1L, 1L);

    // when
    broker.pushRaftEvent(clientAddress, 123L, 1L, 2L);

    // then
    TestUtil.waitUntil(() -> subscription.isClosed());

    final List<ExecuteCommandRequest> commandRequests = broker.getReceivedCommandRequests();

    final List<ExecuteCommandRequest> acknowledgements =
        commandRequests
            .stream()
            .filter((c) -> c.valueType() == ValueType.SUBSCRIPTION)
            .filter((c) -> c.intent() == SubscriptionIntent.ACKNOWLEDGE)
            .collect(Collectors.toList());

    assertThat(acknowledgements).isNotEmpty();

    final ExecuteCommandRequest lastAck = acknowledgements.get(acknowledgements.size() - 1);
    assertThat(lastAck.getCommand().get("name")).isEqualTo(SUBSCRIPTION_NAME);
    assertThat(lastAck.getCommand().get("ackPosition")).isEqualTo(1L);

    final ControlMessageRequest removeRequest =
        broker
            .getReceivedControlMessageRequests()
            .stream()
            .filter((c) -> c.messageType() == ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .findFirst()
            .get();

    final List<Object> requests = broker.getAllReceivedRequests();
    assertThat(requests).contains(lastAck);
    assertThat(requests.indexOf(lastAck)).isLessThan(requests.indexOf(removeRequest));
  }

  @Test
  public void shouldContinueEventHandlingAfterSuccessfulRetry() {
    // given
    broker.stubTopicSubscriptionApi(123L);

    final AtomicInteger counter = new AtomicInteger(3);
    final FailingHandler handler =
        new FailingHandler(
            e -> e.getMetadata().getPosition() == 1L && counter.decrementAndGet() > 0);

    clientRule
        .topicClient()
        .newSubscription()
        .name(SUBSCRIPTION_NAME)
        .recordHandler(handler)
        .startAtHeadOfTopic()
        .open();

    final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();
    broker.pushRaftEvent(clientAddress, 123L, 1L, 1L);

    // when
    broker.pushRaftEvent(clientAddress, 123L, 1L, 2L);

    // then
    TestUtil.waitUntil(
        () ->
            handler
                .getRecordedRecords()
                .stream()
                .anyMatch(re -> re.getMetadata().getPosition() == 2L));

    final List<Long> handledEventPositions =
        handler
            .getRecordedRecords()
            .stream()
            .map((re) -> re.getMetadata().getPosition())
            .collect(Collectors.toList());

    assertThat(handledEventPositions).containsExactly(1L, 1L, 1L, 2L);
  }

  @Test
  public void shouldSendBufferSizeAsDefinedInClientProperties() {
    // given
    final int bufferSize = 999;
    broker.stubTopicSubscriptionApi(123L);

    final ZeebeClient configuredClient =
        ZeebeClient.newClientBuilder()
            .defaultTopicSubscriptionBufferSize(bufferSize)
            .brokerContactPoint(broker.getSocketAddress().toString())
            .build();
    closeables.manage(configuredClient);

    configuredClient
        .topicClient()
        .newSubscription()
        .name(SUBSCRIPTION_NAME)
        .recordHandler(DO_NOTHING)
        .startAtHeadOfTopic()
        .open();

    // then
    final ExecuteCommandRequest addSubscriptionRequest =
        broker
            .getReceivedCommandRequests()
            .stream()
            .filter(
                (r) ->
                    r.valueType() == ValueType.SUBSCRIBER
                        && r.intent() == SubscriberIntent.SUBSCRIBE)
            .findFirst()
            .get();

    assertThat(addSubscriptionRequest.getCommand()).containsEntry("bufferSize", (long) bufferSize);
  }

  @Test
  public void shouldSendBufferSizeAsDefinedViaBuilder() {
    // given
    final int bufferSize = 123;

    broker.stubTopicSubscriptionApi(123L);

    clientRule
        .topicClient()
        .newSubscription()
        .name(SUBSCRIPTION_NAME)
        .recordHandler(DO_NOTHING)
        .startAtHeadOfTopic()
        .bufferSize(bufferSize)
        .open();

    // then
    final ExecuteCommandRequest addSubscriptionRequest =
        broker
            .getReceivedCommandRequests()
            .stream()
            .filter(
                (r) ->
                    r.valueType() == ValueType.SUBSCRIBER
                        && r.intent() == SubscriberIntent.SUBSCRIBE)
            .findFirst()
            .get();

    assertThat(addSubscriptionRequest.getCommand()).containsEntry("bufferSize", (long) bufferSize);
  }

  @Test
  public void shouldOnlyAcknowledgeEventAndCloseSubscriptionAfterLastEventHasBeenHandled()
      throws InterruptedException, ExecutionException, TimeoutException {
    // given
    broker.stubTopicSubscriptionApi(123L);
    final ControllableHandler handler = new ControllableHandler();

    final TopicSubscriberGroup subscription =
        (TopicSubscriberGroup)
            clientRule
                .topicClient()
                .newSubscription()
                .name(SUBSCRIPTION_NAME)
                .recordHandler(handler)
                .startAtHeadOfTopic()
                .open();

    final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

    broker.pushRaftEvent(clientAddress, 123L, 1L, 1L);
    TestUtil.waitUntil(() -> handler.isWaiting());

    // when
    final ActorFuture<?> closeFuture = subscription.closeAsync();

    // then
    Thread.sleep(1000L);
    assertThat(closeFuture).isNotDone();

    boolean hasSentAck =
        broker
            .getReceivedCommandRequests()
            .stream()
            .anyMatch((r) -> r.valueType() == ValueType.SUBSCRIPTION);

    assertThat(hasSentAck).isFalse();

    // and when
    handler.signal();

    // then
    closeFuture.get(1L, TimeUnit.SECONDS);

    // and
    hasSentAck =
        broker
            .getReceivedCommandRequests()
            .stream()
            .anyMatch((r) -> r.valueType() == ValueType.SUBSCRIPTION);

    assertThat(hasSentAck).isTrue();
  }

  @Test
  public void shouldCloseSubscriptionOnChannelClose() throws InterruptedException {
    // given
    broker.stubTopicSubscriptionApi(123L);

    final TopicSubscription subscription =
        clientRule
            .topicClient()
            .newSubscription()
            .name(SUBSCRIPTION_NAME)
            .recordHandler(DO_NOTHING)
            .startAtHeadOfTopic()
            .open();

    // when
    broker.closeTransport();
    Thread.sleep(500L); // let subscriber attempt reopening
    clientRule.getClock().addTime(Duration.ofSeconds(60)); // make request time out immediately

    // then
    TestUtil.waitUntil(() -> subscription.isClosed());
    assertThat(subscription.isClosed()).isTrue();
  }

  @Test
  public void shouldCloseSubscriptionOnClientClose() {
    // given
    broker.stubTopicSubscriptionApi(123L);

    final TopicSubscription subscription =
        clientRule
            .topicClient()
            .newSubscription()
            .name(SUBSCRIPTION_NAME)
            .recordHandler(DO_NOTHING)
            .startAtHeadOfTopic()
            .open();

    // when
    client.close();

    // then
    assertThat(subscription.isClosed()).isTrue();
  }

  @Test
  public void shouldAllowReopeningSubscriptionAfterChannelClose() throws InterruptedException {
    // given
    broker.stubTopicSubscriptionApi(123L);

    final TopicSubscription firstSubscription =
        clientRule
            .topicClient()
            .newSubscription()
            .name(SUBSCRIPTION_NAME)
            .recordHandler(DO_NOTHING)
            .startAtHeadOfTopic()
            .open();

    firstSubscription.close();

    broker.closeTransport();

    broker.bindTransport();

    // when
    final TopicSubscription secondSubscription =
        clientRule
            .topicClient()
            .newSubscription()
            .name(SUBSCRIPTION_NAME)
            .recordHandler(DO_NOTHING)
            .startAtHeadOfTopic()
            .open();

    // then
    assertThat(secondSubscription.isOpen()).isTrue();
  }

  @Test
  public void shouldInvokeDefaultHandlerForTopicEvent() {
    // given
    broker.stubTopicSubscriptionApi(123L);

    final RecordingTopicEventHandler eventHandler = new RecordingTopicEventHandler();

    clientRule
        .topicClient()
        .newSubscription()
        .name(SUBSCRIPTION_NAME)
        .recordHandler(eventHandler)
        .startAtHeadOfTopic()
        .open();

    final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

    final Instant expectedEventTimestamp = Instant.now();

    // when pushing two events
    broker.pushRecord(
        clientAddress,
        123L,
        1L,
        1L,
        expectedEventTimestamp,
        RecordType.EVENT,
        ValueType.JOB,
        JobIntent.CREATED);
    broker.pushRecord(
        clientAddress,
        123L,
        1L,
        2L,
        expectedEventTimestamp,
        RecordType.COMMAND,
        ValueType.JOB,
        JobIntent.ACTIVATED);

    // then
    waitUntil(() -> eventHandler.numTopicEvents() == 2);

    final Record event1 = eventHandler.topicEvents.get(0);
    final Record event2 = eventHandler.topicEvents.get(1);

    assertMetadata(
        event1, 1L, 1L, expectedEventTimestamp, RecordType.EVENT, ValueType.JOB, "CREATED");
    assertMetadata(
        event2, 1L, 2L, expectedEventTimestamp, RecordType.COMMAND, ValueType.JOB, "ACTIVATED");

    assertThat(eventHandler.numTopicEvents()).isEqualTo(2);
    assertThat(eventHandler.numJobEvents()).isEqualTo(0);
    assertThat(eventHandler.numJobCommands()).isEqualTo(0);
    assertThat(eventHandler.numWorkflowInstanceCommands()).isEqualTo(0);
    assertThat(eventHandler.numWorkflowInstanceEvents()).isEqualTo(0);
    assertThat(eventHandler.numIncidentEvents()).isEqualTo(0);
    assertThat(eventHandler.numIncidentCommands()).isEqualTo(0);
    assertThat(eventHandler.numRaftEvents()).isEqualTo(0);
  }

  @Test
  public void shouldInvokeJobEventHandlerForJobEvent() {
    // given
    broker.stubTopicSubscriptionApi(123L);

    final RecordingTopicEventHandler eventHandler = subscribeToAllEvents();

    final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

    final Instant expectedEventTimestamp = Instant.now();

    // when pushing two events
    broker.pushRecord(
        clientAddress,
        123L,
        1L,
        1L,
        expectedEventTimestamp,
        RecordType.EVENT,
        ValueType.JOB,
        JobIntent.CREATED);
    broker.pushRecord(
        clientAddress,
        123L,
        1L,
        2L,
        expectedEventTimestamp,
        RecordType.EVENT,
        ValueType.JOB,
        JobIntent.ACTIVATED);

    // then
    waitUntil(() -> eventHandler.numJobEvents() >= 2);

    final JobEvent event1 = eventHandler.jobEvents.get(0);
    final JobEvent event2 = eventHandler.jobEvents.get(1);

    assertMetadata(
        event1, 1L, 1L, expectedEventTimestamp, RecordType.EVENT, ValueType.JOB, "CREATED");
    assertMetadata(
        event2, 1L, 2L, expectedEventTimestamp, RecordType.EVENT, ValueType.JOB, "ACTIVATED");

    assertThat(eventHandler.numTopicEvents()).isEqualTo(0);
    assertThat(eventHandler.numJobEvents()).isEqualTo(2);
    assertThat(eventHandler.numJobCommands()).isEqualTo(0);
    assertThat(eventHandler.numWorkflowInstanceCommands()).isEqualTo(0);
    assertThat(eventHandler.numWorkflowInstanceEvents()).isEqualTo(0);
    assertThat(eventHandler.numIncidentEvents()).isEqualTo(0);
    assertThat(eventHandler.numIncidentCommands()).isEqualTo(0);
    assertThat(eventHandler.numRaftEvents()).isEqualTo(0);
  }

  @Test
  public void shouldInvokeJobCommandHandlerForJobCommand() {
    // given
    broker.stubTopicSubscriptionApi(123L);

    final RecordingTopicEventHandler eventHandler = subscribeToAllEvents();

    final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

    final Instant expectedEventTimestamp = Instant.now();

    // when pushing two events
    broker.pushRecord(
        clientAddress,
        123L,
        1L,
        1L,
        expectedEventTimestamp,
        RecordType.COMMAND,
        ValueType.JOB,
        JobIntent.CREATE);
    broker.pushRecord(
        clientAddress,
        123L,
        1L,
        2L,
        expectedEventTimestamp,
        RecordType.COMMAND,
        ValueType.JOB,
        JobIntent.ACTIVATE);

    // then
    waitUntil(() -> eventHandler.numJobCommands() >= 2);

    final JobCommand command1 = eventHandler.jobCommands.get(0);
    final JobCommand command2 = eventHandler.jobCommands.get(1);

    assertMetadata(
        command1, 1L, 1L, expectedEventTimestamp, RecordType.COMMAND, ValueType.JOB, "CREATE");
    assertMetadata(
        command2, 1L, 2L, expectedEventTimestamp, RecordType.COMMAND, ValueType.JOB, "ACTIVATE");

    assertThat(eventHandler.numTopicEvents()).isEqualTo(0);
    assertThat(eventHandler.numJobEvents()).isEqualTo(0);
    assertThat(eventHandler.numJobCommands()).isEqualTo(2);
    assertThat(eventHandler.numWorkflowInstanceCommands()).isEqualTo(0);
    assertThat(eventHandler.numWorkflowInstanceEvents()).isEqualTo(0);
    assertThat(eventHandler.numIncidentEvents()).isEqualTo(0);
    assertThat(eventHandler.numIncidentCommands()).isEqualTo(0);
    assertThat(eventHandler.numRaftEvents()).isEqualTo(0);
  }

  @Test
  public void shouldInvokeWorkflowInstanceHandlerForWorkflowInstanceEvent() {
    // given
    broker.stubTopicSubscriptionApi(123L);

    final RecordingTopicEventHandler eventHandler = subscribeToAllEvents();

    final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

    final Instant expectedEventTimestamp = Instant.now();

    // when pushing two events
    broker.pushRecord(
        clientAddress,
        123L,
        1L,
        1L,
        expectedEventTimestamp,
        RecordType.EVENT,
        ValueType.WORKFLOW_INSTANCE,
        WorkflowInstanceIntent.CREATED);
    broker.pushRecord(
        clientAddress,
        123L,
        1L,
        2L,
        expectedEventTimestamp,
        RecordType.EVENT,
        ValueType.WORKFLOW_INSTANCE,
        WorkflowInstanceIntent.ELEMENT_COMPLETED);

    // then
    waitUntil(() -> eventHandler.numWorkflowInstanceEvents() >= 2);

    final WorkflowInstanceEvent event1 = eventHandler.workflowInstanceEvents.get(0);
    final WorkflowInstanceEvent event2 = eventHandler.workflowInstanceEvents.get(1);

    assertMetadata(
        event1,
        1L,
        1L,
        expectedEventTimestamp,
        RecordType.EVENT,
        ValueType.WORKFLOW_INSTANCE,
        "CREATED");
    assertMetadata(
        event2,
        1L,
        2L,
        expectedEventTimestamp,
        RecordType.EVENT,
        ValueType.WORKFLOW_INSTANCE,
        "ELEMENT_COMPLETED");

    assertThat(eventHandler.numTopicEvents()).isEqualTo(0);
    assertThat(eventHandler.numJobEvents()).isEqualTo(0);
    assertThat(eventHandler.numJobCommands()).isEqualTo(0);
    assertThat(eventHandler.numWorkflowInstanceCommands()).isEqualTo(0);
    assertThat(eventHandler.numWorkflowInstanceEvents()).isEqualTo(2);
    assertThat(eventHandler.numIncidentEvents()).isEqualTo(0);
    assertThat(eventHandler.numIncidentCommands()).isEqualTo(0);
    assertThat(eventHandler.numRaftEvents()).isEqualTo(0);
  }

  @Test
  public void shouldInvokeWorkflowInstanceHandlerForWorkflowInstanceCommand() {
    // given
    broker.stubTopicSubscriptionApi(123L);

    final RecordingTopicEventHandler eventHandler = subscribeToAllEvents();

    final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

    final Instant expectedEventTimestamp = Instant.now();

    // when pushing two events
    broker.pushRecord(
        clientAddress,
        123L,
        1L,
        1L,
        expectedEventTimestamp,
        RecordType.COMMAND,
        ValueType.WORKFLOW_INSTANCE,
        WorkflowInstanceIntent.CREATE);
    broker.pushRecord(
        clientAddress,
        123L,
        1L,
        2L,
        expectedEventTimestamp,
        RecordType.COMMAND,
        ValueType.WORKFLOW_INSTANCE,
        WorkflowInstanceIntent.UPDATE_PAYLOAD);

    // then
    waitUntil(() -> eventHandler.numWorkflowInstanceCommands() >= 2);

    final WorkflowInstanceCommand command1 = eventHandler.workflowInstanceCommands.get(0);
    final WorkflowInstanceCommand command2 = eventHandler.workflowInstanceCommands.get(1);

    assertMetadata(
        command1,
        1L,
        1L,
        expectedEventTimestamp,
        RecordType.COMMAND,
        ValueType.WORKFLOW_INSTANCE,
        "CREATE");
    assertMetadata(
        command2,
        1L,
        2L,
        expectedEventTimestamp,
        RecordType.COMMAND,
        ValueType.WORKFLOW_INSTANCE,
        "UPDATE_PAYLOAD");

    assertThat(eventHandler.numTopicEvents()).isEqualTo(0);
    assertThat(eventHandler.numJobEvents()).isEqualTo(0);
    assertThat(eventHandler.numJobCommands()).isEqualTo(0);
    assertThat(eventHandler.numWorkflowInstanceCommands()).isEqualTo(2);
    assertThat(eventHandler.numWorkflowInstanceEvents()).isEqualTo(0);
    assertThat(eventHandler.numIncidentEvents()).isEqualTo(0);
    assertThat(eventHandler.numIncidentCommands()).isEqualTo(0);
    assertThat(eventHandler.numRaftEvents()).isEqualTo(0);
  }

  @Test
  public void shouldDeserializeWorkflowInstanceEvent() {
    // given
    broker.stubTopicSubscriptionApi(123L);

    final RecordingTopicEventHandler eventHandler = subscribeToAllEvents();

    final Map<String, Object> value = new HashMap<>();
    value.put("scopeInstanceKey", 42);

    final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

    // when
    broker.pushTopicEvent(
        clientAddress,
        b ->
            b.subscriberKey(123L)
                .recordType(RecordType.EVENT)
                .valueType(ValueType.WORKFLOW_INSTANCE)
                .intent(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
                .value()
                .put("scopeInstanceKey", 42)
                .done());

    // then
    waitUntil(() -> eventHandler.numWorkflowInstanceEvents() > 0);

    final WorkflowInstanceEvent workflowInstanceEvent = eventHandler.workflowInstanceEvents.get(0);
    assertThat(workflowInstanceEvent.getScopeInstanceKey()).isEqualTo(42L);
  }

  @Test
  public void shouldInvokeIncidentEventHandlerForIncidentEvent() {
    // given
    broker.stubTopicSubscriptionApi(123L);

    final RecordingTopicEventHandler eventHandler = subscribeToAllEvents();

    final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

    final Instant expectedEventTimestamp = Instant.now();

    // when pushing two events
    broker.pushRecord(
        clientAddress,
        123L,
        1L,
        1L,
        expectedEventTimestamp,
        RecordType.EVENT,
        ValueType.INCIDENT,
        IncidentIntent.CREATED);
    broker.pushRecord(
        clientAddress,
        123L,
        1L,
        2L,
        expectedEventTimestamp,
        RecordType.EVENT,
        ValueType.INCIDENT,
        IncidentIntent.DELETED);

    // then
    waitUntil(() -> eventHandler.numIncidentEvents() >= 2);

    final IncidentEvent event1 = eventHandler.incidentEvents.get(0);
    final IncidentEvent event2 = eventHandler.incidentEvents.get(1);

    assertMetadata(
        event1, 1L, 1L, expectedEventTimestamp, RecordType.EVENT, ValueType.INCIDENT, "CREATED");
    assertMetadata(
        event2, 1L, 2L, expectedEventTimestamp, RecordType.EVENT, ValueType.INCIDENT, "DELETED");

    assertThat(eventHandler.numTopicEvents()).isEqualTo(0);
    assertThat(eventHandler.numJobEvents()).isEqualTo(0);
    assertThat(eventHandler.numJobCommands()).isEqualTo(0);
    assertThat(eventHandler.numWorkflowInstanceCommands()).isEqualTo(0);
    assertThat(eventHandler.numWorkflowInstanceEvents()).isEqualTo(0);
    assertThat(eventHandler.numIncidentEvents()).isEqualTo(2);
    assertThat(eventHandler.numIncidentCommands()).isEqualTo(0);
    assertThat(eventHandler.numRaftEvents()).isEqualTo(0);
  }

  @Test
  public void shouldInvokeIncidentCommandHandlerForIncidentCommand() {
    // given
    broker.stubTopicSubscriptionApi(123L);

    final RecordingTopicEventHandler eventHandler = subscribeToAllEvents();

    final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

    final Instant expectedEventTimestamp = Instant.now();

    // when pushing two events
    broker.pushRecord(
        clientAddress,
        123L,
        1L,
        1L,
        expectedEventTimestamp,
        RecordType.COMMAND,
        ValueType.INCIDENT,
        IncidentIntent.CREATE);
    broker.pushRecord(
        clientAddress,
        123L,
        1L,
        2L,
        expectedEventTimestamp,
        RecordType.COMMAND,
        ValueType.INCIDENT,
        IncidentIntent.DELETE);

    // then
    waitUntil(() -> eventHandler.numIncidentCommands() >= 2);

    final IncidentCommand command1 = eventHandler.incidentCommands.get(0);
    final IncidentCommand command2 = eventHandler.incidentCommands.get(1);

    assertMetadata(
        command1, 1L, 1L, expectedEventTimestamp, RecordType.COMMAND, ValueType.INCIDENT, "CREATE");
    assertMetadata(
        command2, 1L, 2L, expectedEventTimestamp, RecordType.COMMAND, ValueType.INCIDENT, "DELETE");

    assertThat(eventHandler.numTopicEvents()).isEqualTo(0);
    assertThat(eventHandler.numJobEvents()).isEqualTo(0);
    assertThat(eventHandler.numJobCommands()).isEqualTo(0);
    assertThat(eventHandler.numWorkflowInstanceCommands()).isEqualTo(0);
    assertThat(eventHandler.numWorkflowInstanceEvents()).isEqualTo(0);
    assertThat(eventHandler.numIncidentEvents()).isEqualTo(0);
    assertThat(eventHandler.numIncidentCommands()).isEqualTo(2);
    assertThat(eventHandler.numRaftEvents()).isEqualTo(0);
  }

  @Test
  public void shouldInvokeRaftEventHandlerForRaftEvent() {
    // given
    broker.stubTopicSubscriptionApi(123L);

    final RecordingTopicEventHandler eventHandler = subscribeToAllEvents();

    final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

    final Instant expectedEventTimestamp = Instant.now();

    // when pushing two events
    broker.pushRecord(
        clientAddress,
        123L,
        1L,
        1L,
        expectedEventTimestamp,
        RecordType.EVENT,
        ValueType.RAFT,
        Intent.UNKNOWN);
    broker.pushRecord(
        clientAddress,
        123L,
        1L,
        2L,
        expectedEventTimestamp,
        RecordType.EVENT,
        ValueType.RAFT,
        Intent.UNKNOWN);

    // then
    waitUntil(() -> eventHandler.numRaftEvents() >= 2);

    final RaftEvent event1 = eventHandler.raftEvents.get(0);
    final RaftEvent event2 = eventHandler.raftEvents.get(1);

    assertMetadata(
        event1, 1L, 1L, expectedEventTimestamp, RecordType.EVENT, ValueType.RAFT, "UNKNOWN");
    assertMetadata(
        event2, 1L, 2L, expectedEventTimestamp, RecordType.EVENT, ValueType.RAFT, "UNKNOWN");

    assertThat(eventHandler.numTopicEvents()).isEqualTo(0);
    assertThat(eventHandler.numJobEvents()).isEqualTo(0);
    assertThat(eventHandler.numJobCommands()).isEqualTo(0);
    assertThat(eventHandler.numWorkflowInstanceCommands()).isEqualTo(0);
    assertThat(eventHandler.numWorkflowInstanceEvents()).isEqualTo(0);
    assertThat(eventHandler.numIncidentEvents()).isEqualTo(0);
    assertThat(eventHandler.numIncidentCommands()).isEqualTo(0);
    assertThat(eventHandler.numRaftEvents()).isEqualTo(2);
  }

  protected RecordingTopicEventHandler subscribeToAllEvents() {

    final RecordingTopicEventHandler eventHandler = new RecordingTopicEventHandler();

    clientRule
        .topicClient()
        .newSubscription()
        .name(SUBSCRIPTION_NAME)
        .recordHandler(eventHandler)
        .jobEventHandler(eventHandler)
        .jobCommandHandler(eventHandler)
        .workflowInstanceEventHandler(eventHandler)
        .workflowInstanceCommandHandler(eventHandler)
        .incidentEventHandler(eventHandler)
        .incidentCommandHandler(eventHandler)
        .raftEventHandler(eventHandler)
        .startAtHeadOfTopic()
        .open();

    return eventHandler;
  }

  @Test
  public void shouldInvokeDefaultHandlerIfNoHandlerIsRegistered() {
    // given
    broker.stubTopicSubscriptionApi(123L);

    final RecordingTopicEventHandler defaultEventHandler = new RecordingTopicEventHandler();

    clientRule
        .topicClient()
        .newSubscription()
        .name(SUBSCRIPTION_NAME)
        .recordHandler(defaultEventHandler)
        .startAtHeadOfTopic()
        .open();

    final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

    // when pushing two events
    broker.pushRecord(
        clientAddress, 123L, 1L, 1L, RecordType.EVENT, ValueType.JOB, JobIntent.CREATED);
    broker.pushRecord(
        clientAddress,
        123L,
        1L,
        2L,
        RecordType.EVENT,
        ValueType.WORKFLOW_INSTANCE,
        WorkflowInstanceIntent.CREATED);
    broker.pushRecord(
        clientAddress, 123L, 1L, 3L, RecordType.EVENT, ValueType.INCIDENT, IncidentIntent.RESOLVED);

    // then
    waitUntil(() -> defaultEventHandler.numTopicEvents() == 3);

    assertThat(defaultEventHandler.numTopicEvents()).isEqualTo(3);
  }

  @Test
  public void shouldIgnoreEventWithoutHandler() {
    // given
    broker.stubTopicSubscriptionApi(123L);

    final RecordingTopicEventHandler defaultEventHandler = new RecordingTopicEventHandler();

    final TopicSubscription subscription =
        clientRule
            .topicClient()
            .newSubscription()
            .name(SUBSCRIPTION_NAME)
            .jobEventHandler(defaultEventHandler)
            .startAtHeadOfTopic()
            .open();

    final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

    // when
    broker.pushRecord(
        clientAddress,
        123L,
        1L,
        1L,
        RecordType.EVENT,
        ValueType.WORKFLOW_INSTANCE,
        WorkflowInstanceIntent.CREATED);
    broker.pushRecord(
        clientAddress, 123L, 1L, 2L, RecordType.EVENT, ValueType.JOB, JobIntent.CREATED);

    // then
    waitUntil(() -> defaultEventHandler.numJobEvents() == 1);
    assertThat(subscription.isOpen()).isTrue();
  }

  @Test
  public void shouldReopenSubscriptionOnChannelInterruption() throws InterruptedException {
    // given
    broker.stubTopicSubscriptionApi(123L);

    clientRule
        .topicClient()
        .newSubscription()
        .name(SUBSCRIPTION_NAME)
        .recordHandler(DO_NOTHING)
        .startAtHeadOfTopic()
        .open();

    // when
    broker.interruptAllServerChannels();

    // then
    final ExecuteCommandRequest reopenRequest =
        TestUtil.doRepeatedly(() -> receivedSubscribeCommands().skip(1).findFirst())
            .until(v -> v.isPresent())
            .get();

    assertThat(reopenRequest.getCommand())
        .containsEntry("startPosition", 0L)
        .containsEntry("bufferSize", 1024L)
        .containsEntry("name", SUBSCRIPTION_NAME)
        .doesNotContainEntry("forceStart", true);

    assertThat(reopenRequest.intent()).isEqualTo(SubscriberIntent.SUBSCRIBE);
  }

  @Test
  public void shouldReceiveEventsAfterChannelInterruption() {
    // given
    broker.stubTopicSubscriptionApi(123L);
    final RecordingHandler recordingHandler = new RecordingHandler();

    clientRule
        .topicClient()
        .newSubscription()
        .name(SUBSCRIPTION_NAME)
        .recordHandler(recordingHandler)
        .startAtHeadOfTopic()
        .open();

    broker.interruptAllServerChannels();

    TestUtil.waitUntil(() -> receivedSubscribeCommands().count() >= 2);

    final RemoteAddress clientAddress =
        receivedSubscribeCommands().skip(1).findFirst().get().getSource();

    // when
    broker.pushRaftEvent(clientAddress, 124L, 1L, 2L);

    // then
    TestUtil.waitUntil(() -> recordingHandler.getRecordedRecords().size() > 0);
    assertThat(recordingHandler.getRecordedRecords()).hasSize(1);
  }

  @Test
  public void shouldDiscardEventsReceivedBeforeReopening() throws InterruptedException {
    // given
    broker.stubTopicSubscriptionApi(123L);
    final ControllableHandler handler = new ControllableHandler();

    clientRule
        .topicClient()
        .newSubscription()
        .name(SUBSCRIPTION_NAME)
        .recordHandler(handler)
        .startAtHeadOfTopic()
        .open();

    final RemoteAddress clientAddress = receivedSubscribeCommands().findFirst().get().getSource();

    broker.pushRaftEvent(clientAddress, 123L, 1L, 2L);
    broker.pushRaftEvent(clientAddress, 123L, 1L, 3L);

    TestUtil.waitUntil(() -> handler.isWaiting());

    // when
    broker.interruptAllServerChannels();

    TestUtil.waitUntil(() -> receivedSubscribeCommands().count() >= 2);

    handler.disableWait();
    handler.signal();

    // then
    // give client some time to invoke handler with second event
    Thread.sleep(1000L);

    assertThat(handler.getNumHandledRecords()).isEqualTo(1);
  }

  @Test
  public void shouldCloseSubscriptionWhileOpeningSubscriber() {
    // given
    final long subscriberKey = 123;

    broker.stubTopicSubscriptionApi(0L);
    final ResponseController responseController =
        broker
            .onExecuteCommandRequest(ValueType.SUBSCRIBER, SubscriberIntent.SUBSCRIBE)
            .respondWith()
            .event()
            .intent(SubscriberIntent.SUBSCRIBED)
            .key(subscriberKey)
            .value()
            .allOf((r) -> r.getCommand())
            .done()
            .registerControlled();

    final TopicSubscriptionBuilderImpl builder =
        (TopicSubscriptionBuilderImpl)
            clientRule.topicClient().newSubscription().name("foo").recordHandler(DO_NOTHING);

    final Future<TopicSubscriberGroup> future = builder.buildSubscriberGroup();

    waitUntil(
        () ->
            broker
                    .getReceivedCommandRequests()
                    .stream()
                    .filter(
                        r ->
                            r.valueType() == ValueType.SUBSCRIBER
                                && r.intent() == SubscriberIntent.SUBSCRIBE)
                    .count()
                == 1);

    final Thread closingThread = new Thread(client::close);
    closingThread.start();

    final SubscriptionManager subscriptionManager =
        ((ZeebeClientImpl) client).getSubscriptionManager();
    waitUntil(() -> subscriptionManager.isClosing());
    // when
    responseController.unblockNextResponse();

    // then
    waitUntil(() -> future.isDone());

    assertThat(future).isDone();

    final Optional<ControlMessageRequest> closeRequest =
        broker
            .getReceivedControlMessageRequests()
            .stream()
            .filter(c -> c.messageType() == ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .findFirst();

    assertThat(closeRequest).isPresent();
    final ControlMessageRequest request = closeRequest.get();
    assertThat(request.getData()).containsEntry("subscriberKey", subscriberKey);
  }

  @Test
  public void shouldCloseClientAfterSubscriptionCloseIsCalled() throws Exception {
    // given
    broker.stubTopicSubscriptionApi(0L);

    final ResponseController responseController =
        broker
            .onControlMessageRequest(
                (r) -> r.messageType() == ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .respondWith()
            .data()
            .allOf((r) -> r.getData())
            .done()
            .registerControlled();

    final TopicSubscription foo =
        clientRule
            .topicClient()
            .newSubscription()
            .name(SUBSCRIPTION_NAME)
            .recordHandler(DO_NOTHING)
            .open();

    final ActorFuture<Void> future = ((TopicSubscriberGroup) foo).closeAsync();

    waitUntil(
        () ->
            broker
                    .getReceivedControlMessageRequests()
                    .stream()
                    .filter(r -> r.messageType() == ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
                    .count()
                == 1);

    final Thread closingThread = new Thread(client::close);
    closingThread.start();

    // when
    responseController.unblockNextResponse();

    // then
    closingThread.join(Duration.ofSeconds(10).toMillis());
    waitUntil(future::isDone);

    assertThat(future).isDone();
  }

  @Test
  public void shouldCloseSubscriptionWhenAckFails() {
    // given
    final long subscriberKey = 123L;
    final int bufferSize = 16;
    broker.stubTopicSubscriptionApi(subscriberKey);
    broker
        .onExecuteCommandRequest(ValueType.SUBSCRIPTION, SubscriptionIntent.ACKNOWLEDGE)
        .respondWithError()
        .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
        .errorData("foo")
        .register();

    final TopicSubscription subscription =
        clientRule
            .topicClient()
            .newSubscription()
            .name(SUBSCRIPTION_NAME)
            .recordHandler(DO_NOTHING)
            .startAtHeadOfTopic()
            .bufferSize(bufferSize)
            .open();

    final RemoteAddress clientAddress = receivedSubscribeCommands().findFirst().get().getSource();

    // when
    for (int i = 0; i < bufferSize; i++) {
      broker.pushRaftEvent(clientAddress, subscriberKey, i, i);
    }

    // then
    waitUntil(() -> subscription.isClosed());

    assertThat(subscription.isClosed()).isTrue();
  }

  @Test
  public void shouldCloseSubscriptionWhenClosingFails() {
    // given
    final long subscriberKey = 123L;
    broker.stubTopicSubscriptionApi(subscriberKey);
    broker
        .onControlMessageRequest(
            r -> r.messageType() == ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
        .respondWithError()
        .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
        .errorData("foo")
        .register();

    final TopicSubscription subscription =
        clientRule
            .topicClient()
            .newSubscription()
            .name(SUBSCRIPTION_NAME)
            .recordHandler(DO_NOTHING)
            .startAtHeadOfTopic()
            .open();

    // when
    subscription.close();

    // then
    assertThat(subscription.isClosed()).isTrue();

    final long numCloseRequests =
        broker
            .getReceivedControlMessageRequests()
            .stream()
            .filter(r -> r.messageType() == ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .count();

    assertThat(numCloseRequests).isEqualTo(1); // did not attempt to close more than once
  }

  @Test
  public void shouldExposeRejectionReason() {
    // given
    final long subscriberKey = 123L;
    broker.stubTopicSubscriptionApi(subscriberKey);

    final RecordingTopicEventHandler eventHandler = new RecordingTopicEventHandler();

    clientRule
        .topicClient()
        .newSubscription()
        .name(SUBSCRIPTION_NAME)
        .jobCommandHandler(eventHandler)
        .startAtHeadOfTopic()
        .open();

    final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

    // when pushing a rejection record
    final String rejectionReason = "foooo";
    broker
        .newSubscribedEvent()
        .partitionId(StubBrokerRule.TEST_PARTITION_ID)
        .key(123)
        .position(345)
        .recordType(RecordType.COMMAND_REJECTION)
        .valueType(ValueType.JOB)
        .intent(JobIntent.COMPLETE)
        .subscriberKey(subscriberKey)
        .subscriptionType(SubscriptionType.TOPIC_SUBSCRIPTION)
        .rejectionType(RejectionType.NOT_APPLICABLE)
        .rejectionReason(rejectionReason)
        .value()
        .done()
        .push(clientAddress);

    // then
    waitUntil(() -> eventHandler.jobCommandRejections.size() == 1);

    final Record record = eventHandler.jobCommandRejections.get(0);

    final RecordMetadata metadata = record.getMetadata();
    assertThat(metadata.getRejectionType())
        .isEqualByComparingTo(io.zeebe.gateway.api.record.RejectionType.NOT_APPLICABLE);
    assertThat(metadata.getRejectionReason()).isEqualTo(rejectionReason);
  }

  protected void assertMetadata(
      Record actualRecord,
      long expectedKey,
      long expectedPosition,
      Instant expectedTimestamp,
      RecordType expectedRecordType,
      ValueType expectedValueType,
      String expectedIntent) {

    final io.zeebe.gateway.api.record.RecordType clientRecordType =
        io.zeebe.gateway.api.record.RecordType.valueOf(expectedRecordType.name());
    final io.zeebe.gateway.api.record.ValueType clientValueType =
        io.zeebe.gateway.api.record.ValueType.valueOf(expectedValueType.name());

    final RecordMetadata metadata = actualRecord.getMetadata();
    assertThat(metadata.getKey()).isEqualTo(expectedKey);
    assertThat(metadata.getSourceRecordPosition()).isEqualTo(expectedPosition - 1);
    assertThat(metadata.getPosition()).isEqualTo(expectedPosition);
    assertThat(metadata.getTimestamp()).isEqualTo(expectedTimestamp);
    assertThat(metadata.getValueType()).isEqualTo(clientValueType);
    assertThat(metadata.getTopicName()).isEqualTo(clientRule.getDefaultTopicName());
    assertThat(metadata.getPartitionId()).isEqualTo(clientRule.getDefaultPartitionId());
    assertThat(metadata.getRecordType()).isEqualTo(clientRecordType);
    assertThat(metadata.getIntent()).isEqualTo(expectedIntent);
    assertThat(metadata.getRejectionReason()).isNull();
    assertThat(metadata.getRejectionType()).isNull();
  }

  protected Stream<ExecuteCommandRequest> receivedSubscribeCommands() {
    return broker
        .getReceivedCommandRequests()
        .stream()
        .filter(
            (e) ->
                e.valueType() == ValueType.SUBSCRIBER && e.intent() == SubscriberIntent.SUBSCRIBE);
  }

  private static class RecordingTopicEventHandler
      implements RecordHandler,
          JobEventHandler,
          JobCommandHandler,
          WorkflowInstanceEventHandler,
          WorkflowInstanceCommandHandler,
          IncidentEventHandler,
          IncidentCommandHandler,
          RaftEventHandler {
    protected List<Record> topicEvents = new CopyOnWriteArrayList<>();
    protected List<JobEvent> jobEvents = new CopyOnWriteArrayList<>();
    protected List<JobCommand> jobCommands = new CopyOnWriteArrayList<>();
    protected List<JobCommand> jobCommandRejections = new CopyOnWriteArrayList<>();
    protected List<WorkflowInstanceEvent> workflowInstanceEvents = new CopyOnWriteArrayList<>();
    protected List<WorkflowInstanceCommand> workflowInstanceCommands = new CopyOnWriteArrayList<>();
    protected List<IncidentEvent> incidentEvents = new CopyOnWriteArrayList<>();
    protected List<IncidentCommand> incidentCommands = new CopyOnWriteArrayList<>();
    protected List<RaftEvent> raftEvents = new CopyOnWriteArrayList<>();

    @Override
    public void onRecord(Record event) throws Exception {
      topicEvents.add(event);
    }

    @Override
    public void onJobEvent(JobEvent jobEvent) {
      jobEvents.add(jobEvent);
    }

    @Override
    public void onJobCommand(JobCommand jobCommand) {
      jobCommands.add(jobCommand);
    }

    @Override
    public void onJobCommandRejection(JobCommand jobCommand) throws Exception {
      this.jobCommandRejections.add(jobCommand);
    }

    @Override
    public void onWorkflowInstanceEvent(WorkflowInstanceEvent workflowInstanceEvent) {
      workflowInstanceEvents.add(workflowInstanceEvent);
    }

    @Override
    public void onWorkflowInstanceCommand(WorkflowInstanceCommand workflowInstanceCommand) {
      workflowInstanceCommands.add(workflowInstanceCommand);
    }

    @Override
    public void onIncidentEvent(IncidentEvent incidentEvent) {
      incidentEvents.add(incidentEvent);
    }

    @Override
    public void onIncidentCommand(IncidentCommand incidentCommand) {
      incidentCommands.add(incidentCommand);
    }

    @Override
    public void onRaftEvent(RaftEvent raftEvent) {
      raftEvents.add(raftEvent);
    }

    public int numTopicEvents() {
      return topicEvents.size();
    }

    public int numJobEvents() {
      return jobEvents.size();
    }

    public int numJobCommands() {
      return jobCommands.size();
    }

    public int numWorkflowInstanceEvents() {
      return workflowInstanceEvents.size();
    }

    public int numWorkflowInstanceCommands() {
      return workflowInstanceCommands.size();
    }

    public int numIncidentEvents() {
      return incidentEvents.size();
    }

    public int numIncidentCommands() {
      return incidentCommands.size();
    }

    public int numRaftEvents() {
      return raftEvents.size();
    }
  }
}
