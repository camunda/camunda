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
package io.zeebe.client.task.subscription;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.task.PollableTaskSubscription;
import io.zeebe.client.task.TaskSubscription;
import io.zeebe.client.task.impl.subscription.SubscriberGroup;
import io.zeebe.client.util.ClientRule;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.test.broker.protocol.brokerapi.ControlMessageRequest;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import io.zeebe.test.broker.protocol.brokerapi.data.Topology;
import io.zeebe.transport.RemoteAddress;

public class PartitionedTaskSubscriptionTest
{

    public static final String TOPIC = "baz";
    public static final int PARTITION_1 = 1;
    public static final int PARTITION_2 = 2;

    public static final String TASK_TYPE = "foo";

    public ClientRule clientRule = new ClientRule();
    public StubBrokerRule broker1 = new StubBrokerRule("localhost", 51015);
    public StubBrokerRule broker2 = new StubBrokerRule("localhost", 51016);

    protected ZeebeClient client;

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(broker1)
        .around(broker2)
        .around(clientRule);

    @Before
    public void setUp()
    {
        final Topology topology = new Topology()
            .addLeader(broker1, Protocol.SYSTEM_TOPIC, Protocol.SYSTEM_PARTITION)
            .addLeader(broker1, TOPIC, PARTITION_1)
            .addLeader(broker2, TOPIC, PARTITION_2);

        broker1.setCurrentTopology(topology);
        broker2.setCurrentTopology(topology);

        client = clientRule.getClient();
    }

    @Test
    public void shouldSubscribeToMultiplePartitionsOfATopic()
    {
        // given
        broker1.stubTaskSubscriptionApi(456);
        broker2.stubTaskSubscriptionApi(789);

        // when
        final TaskSubscription subscription = client.tasks().newTaskSubscription(TOPIC)
            .handler(new RecordingTaskHandler())
            .taskType(TASK_TYPE)
            .lockOwner("bumbum")
            .lockTime(Duration.ofSeconds(6))
            .open();

        // then
        assertThat(subscription.isOpen()).isTrue();

        final List<ControlMessageRequest> subscribeRequestsBroker1 = getSubscribeRequests(broker1);
        assertThat(subscribeRequestsBroker1).hasSize(1);
        final ControlMessageRequest request1 = subscribeRequestsBroker1.get(0);
        assertThat(request1.partitionId()).isEqualTo(PARTITION_1);

        final List<ControlMessageRequest> subscribeRequestsBroker2 = getSubscribeRequests(broker2);
        assertThat(subscribeRequestsBroker2).hasSize(1);
        final ControlMessageRequest request2 = subscribeRequestsBroker2.get(0);
        assertThat(request2.partitionId()).isEqualTo(PARTITION_2);
    }

    protected List<ControlMessageRequest> getSubscribeRequests(StubBrokerRule broker)
    {
        return broker.getReceivedControlMessageRequests().stream()
                .filter(r -> r.messageType() == ControlMessageType.ADD_TASK_SUBSCRIPTION)
                .collect(Collectors.toList());
    }

    @Test
    public void shouldReceiveEventsFromMultiplePartitions()
    {
        // given
        final int subscriberKey1 = 456;
        broker1.stubTaskSubscriptionApi(subscriberKey1);

        final int subscriberKey2 = 789;
        broker2.stubTaskSubscriptionApi(subscriberKey2);

        final RecordingTaskHandler eventHandler = new RecordingTaskHandler();
        client.tasks().newTaskSubscription(TOPIC)
            .handler(eventHandler)
            .taskType(TASK_TYPE)
            .lockOwner("bumbum")
            .lockTime(Duration.ofSeconds(6))
            .open();

        final RemoteAddress clientAddressFromBroker1 = broker1.getReceivedControlMessageRequests().get(0).getSource();
        final RemoteAddress clientAddressFromBroker2 = broker2.getReceivedControlMessageRequests().get(0).getSource();

        // when
        final long key1 = 3;
        broker1.newSubscribedEvent()
            .eventType(EventType.TASK_EVENT)
            .partitionId(PARTITION_1)
            .subscriberKey(subscriberKey1)
            .key(key1)
            .subscriptionType(SubscriptionType.TASK_SUBSCRIPTION)
            .value().done()
            .push(clientAddressFromBroker1);

        final long key2 = 4;
        broker2.newSubscribedEvent()
            .eventType(EventType.TASK_EVENT)
            .partitionId(PARTITION_1)
            .subscriberKey(subscriberKey1)
            .key(key2)
            .subscriptionType(SubscriptionType.TASK_SUBSCRIPTION)
            .value().done()
            .push(clientAddressFromBroker2);

        // then
        waitUntil(() -> eventHandler.numHandledTasks() == 2);
        assertThat(eventHandler.getHandledTasks()).extracting("metadata.key").containsExactlyInAnyOrder(key1, key2);
    }

    @Test
    public void shouldSumWorkCountOfPollableSubscription()
    {
        // given
        final int subscriberKey1 = 456;
        broker1.stubTaskSubscriptionApi(subscriberKey1);

        final int subscriberKey2 = 789;
        broker2.stubTaskSubscriptionApi(subscriberKey2);

        final RecordingTaskHandler eventHandler = new RecordingTaskHandler();
        final PollableTaskSubscription subscription = client.tasks().newPollableTaskSubscription(TOPIC)
            .taskType(TASK_TYPE)
            .lockOwner("bumbum")
            .lockTime(Duration.ofSeconds(6))
            .open();

        final RemoteAddress clientAddressFromBroker1 = broker1.getReceivedControlMessageRequests().get(0).getSource();
        final RemoteAddress clientAddressFromBroker2 = broker2.getReceivedControlMessageRequests().get(0).getSource();

        final long key1 = 3;
        broker1.newSubscribedEvent()
            .eventType(EventType.TASK_EVENT)
            .partitionId(PARTITION_1)
            .subscriberKey(subscriberKey1)
            .key(key1)
            .subscriptionType(SubscriptionType.TASK_SUBSCRIPTION)
            .value().done()
            .push(clientAddressFromBroker1);

        final long key2 = 4;
        broker2.newSubscribedEvent()
            .eventType(EventType.TASK_EVENT)
            .partitionId(PARTITION_1)
            .subscriberKey(subscriberKey1)
            .key(key2)
            .subscriptionType(SubscriptionType.TASK_SUBSCRIPTION)
            .value().done()
            .push(clientAddressFromBroker2);

        waitUntil(() -> ((SubscriberGroup<?>) subscription).size() == 2);

        // when
        final int polledEvents = subscription.poll(eventHandler);

        // then
        assertThat(polledEvents).isEqualTo(2);
    }
}
