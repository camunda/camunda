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

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.DeploymentCommand;
import io.zeebe.client.api.commands.TopicCommand;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.client.api.record.Record;
import io.zeebe.client.api.record.RecordMetadata;
import io.zeebe.client.api.subscription.*;
import io.zeebe.client.util.ClientRule;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.*;
import io.zeebe.protocol.intent.*;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.test.util.Conditions;
import io.zeebe.transport.RemoteAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class ManagementSubscriptionTest {
  private static final long SUBSCRIPTION_KEY = 123L;
  private static final String SUBSCRIPTION_NAME = "foo";

  private static final RecordHandler DO_NOTHING = e -> {};

  public ClientRule clientRule = new ClientRule();
  public StubBrokerRule broker = new StubBrokerRule();

  @Rule public RuleChain ruleChain = RuleChain.outerRule(broker).around(clientRule);

  @Rule public ExpectedException exception = ExpectedException.none();

  @Rule public AutoCloseableRule closeables = new AutoCloseableRule();

  protected ZeebeClient client;

  @Before
  public void setUp() {
    this.client = clientRule.getClient();

    broker.stubTopicSubscriptionApi(SUBSCRIPTION_KEY);
  }

  @Test
  public void shouldOpenSubscription() {
    // when
    client.newManagementSubscription().name(SUBSCRIPTION_NAME).recordHandler(DO_NOTHING).open();

    // then
    final ExecuteCommandRequest subscribeRequest =
        broker
            .getReceivedCommandRequests()
            .stream()
            .filter((e) -> e.valueType() == ValueType.SUBSCRIBER)
            .findFirst()
            .get();

    assertThat(subscribeRequest.intent()).isEqualTo(SubscriberIntent.SUBSCRIBE);
    assertThat(subscribeRequest.partitionId()).isEqualTo(Protocol.SYSTEM_PARTITION);
    assertThat(subscribeRequest.getCommand())
        .containsEntry("startPosition", 0)
        .containsEntry("bufferSize", 1024)
        .containsEntry("name", SUBSCRIPTION_NAME)
        .doesNotContainEntry("forceStart", true);
  }

  @Test
  public void shouldOpenSubscriptionAndForceStart() {
    // when
    client
        .newManagementSubscription()
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
    // when
    client
        .newManagementSubscription()
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
    assertThat(subscribeRequest.partitionId()).isEqualTo(Protocol.SYSTEM_PARTITION);
    assertThat(subscribeRequest.getCommand()).containsEntry("startPosition", 0);
  }

  @Test
  public void shouldOpenSubscriptionAtTailOfTopic() {
    // when
    client
        .newManagementSubscription()
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

    assertThat(subscribeRequest.getCommand())
        .hasEntrySatisfying("startPosition", Conditions.isLowerThan(0))
        .doesNotContainEntry("forceStart", true);
  }

  @Test
  public void shouldOpenSubscriptionAtPosition() {
    // when
    client
        .newManagementSubscription()
        .name(SUBSCRIPTION_NAME)
        .recordHandler(DO_NOTHING)
        .startAtPosition(654L)
        .open();

    // then
    final ExecuteCommandRequest subscribeRequest =
        broker
            .getReceivedCommandRequests()
            .stream()
            .filter((e) -> e.valueType() == ValueType.SUBSCRIBER)
            .findFirst()
            .get();

    assertThat(subscribeRequest.getCommand())
        .containsEntry("startPosition", 654)
        .doesNotContainEntry("forceStart", true);
  }

  @Test
  public void shouldValidateRecordHandlerNotNull() {
    // then
    exception.expect(RuntimeException.class);
    exception.expectMessage("recordHandler must not be null");

    // when
    client.newManagementSubscription().name(SUBSCRIPTION_NAME).recordHandler(null).open();
  }

  @Test
  public void shouldValidateNameNotNull() {
    // then
    exception.expect(RuntimeException.class);
    exception.expectMessage("name must not be null");

    // when
    client.newManagementSubscription().name(null).recordHandler(DO_NOTHING).open();
  }

  @Test
  public void shouldValidateBufferSizeGreaterThanZero() {
    // then
    exception.expect(RuntimeException.class);
    exception.expectMessage("bufferSize must be greater than 0");

    // when
    client.newManagementSubscription().name("foo").recordHandler(DO_NOTHING).bufferSize(-1).open();
  }

  @Test
  public void shouldSendBufferSizeAsDefinedInClientProperties() {
    // given
    final int bufferSize = 999;

    final ZeebeClient configuredClient =
        ZeebeClient.newClientBuilder().defaultTopicSubscriptionBufferSize(bufferSize).build();
    closeables.manage(configuredClient);

    configuredClient
        .newManagementSubscription()
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

    assertThat(addSubscriptionRequest.getCommand()).containsEntry("bufferSize", bufferSize);
  }

  @Test
  public void shouldSendBufferSizeAsDefinedViaBuilder() {
    // given
    final int bufferSize = 123;

    client
        .newManagementSubscription()
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

    assertThat(addSubscriptionRequest.getCommand()).containsEntry("bufferSize", bufferSize);
  }

  @Test
  public void shouldInvokeDefaultHandler() {
    // given
    final List<Record> records = new ArrayList<>();
    client
        .newManagementSubscription()
        .name(SUBSCRIPTION_NAME)
        .recordHandler(records::add)
        .startAtHeadOfTopic()
        .open();

    final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

    final Instant expectedEventTimestamp = Instant.now();

    // when pushing two events
    pushRecord(
        clientAddress,
        21L,
        1L,
        expectedEventTimestamp,
        RecordType.COMMAND,
        ValueType.DEPLOYMENT,
        DeploymentIntent.CREATE);
    pushRecord(
        clientAddress,
        22L,
        2L,
        expectedEventTimestamp,
        RecordType.EVENT,
        ValueType.DEPLOYMENT,
        DeploymentIntent.CREATED);

    // then
    waitUntil(() -> records.size() == 2);

    assertMetadata(
        records.get(0),
        21L,
        1L,
        expectedEventTimestamp,
        RecordType.COMMAND,
        ValueType.DEPLOYMENT,
        "CREATE");
    assertMetadata(
        records.get(1),
        22L,
        2L,
        expectedEventTimestamp,
        RecordType.EVENT,
        ValueType.DEPLOYMENT,
        "CREATED");
  }

  @Test
  public void shouldInvokeDeploymentEventHandler() {
    // given
    final List<Record> records = new ArrayList<>();
    client
        .newManagementSubscription()
        .name(SUBSCRIPTION_NAME)
        .deploymentEventHandler(records::add)
        .startAtHeadOfTopic()
        .open();

    final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

    final Instant expectedEventTimestamp = Instant.now();

    // when pushing two events
    pushRecord(
        clientAddress,
        21L,
        1L,
        expectedEventTimestamp,
        RecordType.EVENT,
        ValueType.DEPLOYMENT,
        DeploymentIntent.CREATED);
    pushRecord(
        clientAddress,
        22L,
        2L,
        expectedEventTimestamp,
        RecordType.EVENT,
        ValueType.DEPLOYMENT,
        DeploymentIntent.CREATED);

    // then
    waitUntil(() -> records.size() == 2);

    assertMetadata(
        records.get(0),
        21L,
        1L,
        expectedEventTimestamp,
        RecordType.EVENT,
        ValueType.DEPLOYMENT,
        "CREATED");
    assertMetadata(
        records.get(1),
        22L,
        2L,
        expectedEventTimestamp,
        RecordType.EVENT,
        ValueType.DEPLOYMENT,
        "CREATED");
  }

  @Test
  public void shouldInvokeDeploymentCommandHandler() {
    // given
    final List<Record> records = new ArrayList<>();
    client
        .newManagementSubscription()
        .name(SUBSCRIPTION_NAME)
        .deploymentCommandHandler(
            new DeploymentCommandHandler() {
              @Override
              public void onDeploymentCommand(DeploymentCommand deploymentCommand) {
                records.add(deploymentCommand);
              }

              @Override
              public void onDeploymentCommandRejection(DeploymentCommand deploymentCommand) {
                records.add(deploymentCommand);
              }
            })
        .startAtHeadOfTopic()
        .open();

    final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

    final Instant expectedEventTimestamp = Instant.now();

    // when pushing two events
    pushRecord(
        clientAddress,
        21L,
        1L,
        expectedEventTimestamp,
        RecordType.COMMAND,
        ValueType.DEPLOYMENT,
        DeploymentIntent.CREATE);
    pushRecord(
        clientAddress,
        22L,
        2L,
        expectedEventTimestamp,
        RecordType.COMMAND_REJECTION,
        ValueType.DEPLOYMENT,
        DeploymentIntent.CREATE);

    // then
    waitUntil(() -> records.size() == 2);

    assertMetadata(
        records.get(0),
        21L,
        1L,
        expectedEventTimestamp,
        RecordType.COMMAND,
        ValueType.DEPLOYMENT,
        "CREATE");
    assertMetadata(
        records.get(1),
        22L,
        2L,
        expectedEventTimestamp,
        RecordType.COMMAND_REJECTION,
        ValueType.DEPLOYMENT,
        "CREATE");
  }

  @Test
  public void shouldInvokeTopicEventHandler() {
    // given
    final List<Record> records = new ArrayList<>();
    client
        .newManagementSubscription()
        .name(SUBSCRIPTION_NAME)
        .topicEventHandler(records::add)
        .startAtHeadOfTopic()
        .open();

    final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

    final Instant expectedEventTimestamp = Instant.now();

    // when pushing two events
    pushRecord(
        clientAddress,
        21L,
        1L,
        expectedEventTimestamp,
        RecordType.EVENT,
        ValueType.TOPIC,
        TopicIntent.CREATING);
    pushRecord(
        clientAddress,
        22L,
        2L,
        expectedEventTimestamp,
        RecordType.EVENT,
        ValueType.TOPIC,
        TopicIntent.CREATED);

    // then
    waitUntil(() -> records.size() == 2);

    assertMetadata(
        records.get(0),
        21L,
        1L,
        expectedEventTimestamp,
        RecordType.EVENT,
        ValueType.TOPIC,
        "CREATING");
    assertMetadata(
        records.get(1),
        22L,
        2L,
        expectedEventTimestamp,
        RecordType.EVENT,
        ValueType.TOPIC,
        "CREATED");
  }

  @Test
  public void shouldInvokeTopicCommandHandler() {
    // given
    final List<Record> records = new ArrayList<>();
    client
        .newManagementSubscription()
        .name(SUBSCRIPTION_NAME)
        .topicCommandHandler(
            new TopicCommandHandler() {

              @Override
              public void onTopicCommand(TopicCommand topicCommand) {
                records.add(topicCommand);
              }

              @Override
              public void onTopicCommandRejection(TopicCommand topicCommand) {
                records.add(topicCommand);
              }
            })
        .startAtHeadOfTopic()
        .open();

    final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

    final Instant expectedEventTimestamp = Instant.now();

    // when pushing two events
    pushRecord(
        clientAddress,
        21L,
        1L,
        expectedEventTimestamp,
        RecordType.COMMAND,
        ValueType.TOPIC,
        TopicIntent.CREATE);
    pushRecord(
        clientAddress,
        22L,
        2L,
        expectedEventTimestamp,
        RecordType.COMMAND_REJECTION,
        ValueType.TOPIC,
        TopicIntent.CREATE);

    // then
    waitUntil(() -> records.size() == 2);

    assertMetadata(
        records.get(0),
        21L,
        1L,
        expectedEventTimestamp,
        RecordType.COMMAND,
        ValueType.TOPIC,
        "CREATE");
    assertMetadata(
        records.get(1),
        22L,
        2L,
        expectedEventTimestamp,
        RecordType.COMMAND_REJECTION,
        ValueType.TOPIC,
        "CREATE");
  }

  @Test
  public void shouldInvokeRaftEventHandler() {
    // given
    final List<Record> records = new ArrayList<>();
    client
        .newManagementSubscription()
        .name(SUBSCRIPTION_NAME)
        .raftEventHandler(records::add)
        .startAtHeadOfTopic()
        .open();

    final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

    final Instant expectedEventTimestamp = Instant.now();

    // when pushing two events
    pushRecord(
        clientAddress,
        21L,
        1L,
        expectedEventTimestamp,
        RecordType.EVENT,
        ValueType.RAFT,
        RaftIntent.MEMBER_ADDED);
    pushRecord(
        clientAddress,
        22L,
        2L,
        expectedEventTimestamp,
        RecordType.EVENT,
        ValueType.RAFT,
        RaftIntent.MEMBER_REMOVED);

    // then
    waitUntil(() -> records.size() == 2);

    assertMetadata(
        records.get(0),
        21L,
        1L,
        expectedEventTimestamp,
        RecordType.EVENT,
        ValueType.RAFT,
        "MEMBER_ADDED");
    assertMetadata(
        records.get(1),
        22L,
        2L,
        expectedEventTimestamp,
        RecordType.EVENT,
        ValueType.RAFT,
        "MEMBER_REMOVED");
  }

  @Test
  public void shouldInvokeDefaultHandlerIfNoHandlerIsRegistered() {
    // given
    final List<DeploymentEvent> deploymentEvents = new ArrayList<>();
    final List<Record> records = new ArrayList<>();
    client
        .newManagementSubscription()
        .name(SUBSCRIPTION_NAME)
        .deploymentEventHandler(deploymentEvents::add)
        .recordHandler(records::add)
        .startAtHeadOfTopic()
        .open();

    final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

    final Instant expectedEventTimestamp = Instant.now();

    // when pushing two events
    pushRecord(
        clientAddress,
        21L,
        1L,
        expectedEventTimestamp,
        RecordType.COMMAND,
        ValueType.DEPLOYMENT,
        DeploymentIntent.CREATE);
    pushRecord(
        clientAddress,
        22L,
        2L,
        expectedEventTimestamp,
        RecordType.EVENT,
        ValueType.DEPLOYMENT,
        DeploymentIntent.CREATED);

    // then
    waitUntil(() -> (records.size() + deploymentEvents.size()) == 2);

    assertThat(records).hasSize(1);
    assertThat(deploymentEvents).hasSize(1);

    assertMetadata(
        records.get(0),
        21L,
        1L,
        expectedEventTimestamp,
        RecordType.COMMAND,
        ValueType.DEPLOYMENT,
        "CREATE");
    assertMetadata(
        deploymentEvents.get(0),
        22L,
        2L,
        expectedEventTimestamp,
        RecordType.EVENT,
        ValueType.DEPLOYMENT,
        "CREATED");
  }

  private void assertMetadata(
      Record actualRecord,
      long expectedKey,
      long expectedPosition,
      Instant expectedTimestamp,
      RecordType expectedRecordType,
      ValueType expectedValueType,
      String expectedIntent) {

    final io.zeebe.client.api.record.RecordType clientRecordType =
        io.zeebe.client.api.record.RecordType.valueOf(expectedRecordType.name());
    final io.zeebe.client.api.record.ValueType clientValueType =
        io.zeebe.client.api.record.ValueType.valueOf(expectedValueType.name());

    final RecordMetadata metadata = actualRecord.getMetadata();
    assertThat(metadata.getKey()).isEqualTo(expectedKey);
    assertThat(metadata.getPosition()).isEqualTo(expectedPosition);
    assertThat(metadata.getTimestamp()).isEqualTo(expectedTimestamp);
    assertThat(metadata.getValueType()).isEqualTo(clientValueType);
    assertThat(metadata.getTopicName()).isEqualTo(Protocol.SYSTEM_TOPIC);
    assertThat(metadata.getPartitionId()).isEqualTo(Protocol.SYSTEM_PARTITION);
    assertThat(metadata.getRecordType()).isEqualTo(clientRecordType);
    assertThat(metadata.getIntent()).isEqualTo(expectedIntent);
  }

  private void pushRecord(
      RemoteAddress remote,
      long key,
      long position,
      Instant timestamp,
      RecordType recordType,
      ValueType valueType,
      Intent intent) {
    final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

    broker
        .newSubscribedEvent()
        .partitionId(Protocol.SYSTEM_PARTITION)
        .key(key)
        .position(position)
        .recordType(recordType)
        .valueType(valueType)
        .intent(intent)
        .subscriberKey(SUBSCRIPTION_KEY)
        .subscriptionType(SubscriptionType.TOPIC_SUBSCRIPTION)
        .timestamp(timestamp)
        .value()
        .done()
        .push(clientAddress);
  }
}
