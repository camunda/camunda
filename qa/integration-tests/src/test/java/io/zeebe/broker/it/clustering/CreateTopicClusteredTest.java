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
package io.zeebe.broker.it.clustering;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.clustering.impl.TopologyBroker;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.event.TaskEvent;
import io.zeebe.client.topic.Partition;
import io.zeebe.client.topic.Topic;
import io.zeebe.client.topic.Topics;
import io.zeebe.client.impl.clustering.BrokerInfoImpl;
import io.zeebe.client.impl.topic.Topic;
import io.zeebe.client.impl.topic.Topics;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.SocketAddress;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CreateTopicClusteredTest
{
    private static final int PARTITION_COUNT = 5;

    public AutoCloseableRule closeables = new AutoCloseableRule();
    public Timeout testTimeout = Timeout.seconds(30);
    public ClientRule clientRule = new ClientRule(false);
    public ClusteringRule clusteringRule = new ClusteringRule(closeables, clientRule);

    @Rule
    public RuleChain ruleChain =
        RuleChain.outerRule(closeables)
                 .around(testTimeout)
                 .around(clientRule)
                 .around(clusteringRule);

    private ZeebeClient client;

    @Before
    public void setUp()
    {
        client = clientRule.getClient();
    }

    @Test
    public void shouldCreateTopic()
    {
        // given

        // when
        final Topic topic = clusteringRule.createTopic("foo", PARTITION_COUNT);

        // then
        assertThat(topic.getName()).isEqualTo("foo");
        assertThat(topic.getPartitions().size()).isEqualTo(PARTITION_COUNT);
    }

    @Test
    public void shouldRequestTopicsAfterTopicCreation()
    {
        // given
        clusteringRule.createTopic("foo", PARTITION_COUNT);

        // when
        final Topics topicsResponse =
            doRepeatedly(() -> client.topics().getTopics().execute())
                .until((topics -> topics.getTopics().size() == 2));

        final List<Topic> topics = topicsResponse.getTopics();

        // then
        final List<Integer> partitions = topics.stream().filter(t -> t.getName().equals("foo"))
              .flatMap(t -> t.getPartitions().stream())
              .map(Partition::getId)
              .collect(Collectors.toList());

        assertThat(partitions.size()).isEqualTo(5);
        assertThat(partitions).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
    }

    @Test
    public void shouldCreateTaskAfterTopicCreation()
    {
        // given
        clusteringRule.createTopic("foo", PARTITION_COUNT);

        // then
        final TaskEvent taskEvent = client.tasks().create("foo", "bar").execute();

        assertThat(taskEvent).isNotNull();
        assertThat(taskEvent.getState()).isEqualTo("CREATED");
    }

    @Test
    public void shouldChooseNewLeaderForCreatedTopicAfterLeaderDies()
    {
        // given
        final int partitionsCount = 1;
        clusteringRule.createTopic("foo", partitionsCount, 3);
        final TaskEvent taskEvent = client.tasks().create("foo", "bar").execute();
        final int partitionId = taskEvent.getMetadata().getPartitionId();

        final BrokerInfoImpl leaderForPartition = clusteringRule.getLeaderForPartition(partitionId);
        final SocketAddress currentLeaderAddress = leaderForPartition.getSocketAddress();

        // when
        clusteringRule.stopBroker(currentLeaderAddress);

        // then
        final BrokerInfoImpl newLeader = clusteringRule.getLeaderForPartition(partitionId);
        assertThat(newLeader.getSocketAddress()).isNotEqualTo(leaderForPartition.getSocketAddress());
    }

    @Test
    @Ignore("https://github.com/zeebe-io/zeebe/issues/844")
    public void shouldCompleteTaskAfterNewLeaderWasChosen() throws Exception
    {
        // given
        final int partitionsCount = 1;
        final String topicName = "foo";
        final int replicationFactor = 3;
        clusteringRule.createTopic(topicName, partitionsCount, replicationFactor);

        final TaskEvent taskEvent = client.tasks().create(topicName, "bar").execute();
        final int partitionId = taskEvent.getMetadata().getPartitionId();

        final BrokerInfoImpl leaderForPartition = clusteringRule.getLeaderForPartition(partitionId);
        final SocketAddress currentLeaderAddress = leaderForPartition.getSocketAddress();

        // when
        clusteringRule.stopBroker(currentLeaderAddress);

        // then
        final BrokerInfoImpl newLeader = clusteringRule.getLeaderForPartition(partitionId);
        assertThat(newLeader.getSocketAddress()).isNotEqualTo(leaderForPartition.getSocketAddress());

        final CompletableFuture<TaskEvent> taskCompleted = new CompletableFuture<>();
        client.tasks()
              .newTaskSubscription(topicName)
              .handler((taskClient, lockedEvent) ->
              {
                  final TaskEvent completedTask = taskClient.complete(lockedEvent)
                                                      .execute();
                  taskCompleted.complete(completedTask);
              })
              .taskType("bar")
              .lockOwner("owner")
              .lockTime(5000)
              .open();

        waitUntil(taskCompleted::isDone);

        assertThat(taskCompleted).isCompleted();
        final TaskEvent completedTask = taskCompleted.get();
        assertThat(completedTask.getState()).isEqualTo("COMPLETED");
    }

    @Test
    public void shouldNotBeAbleToRequestTopicsAfterGoingUnderReplicationFactor()
    {
        // given
        final TopologyBroker leaderForSystemPartition = clusteringRule.getLeaderForPartition(Protocol.SYSTEM_PARTITION);
        final SocketAddress[] otherBrokers = clusteringRule.getOtherBrokers(leaderForSystemPartition.getSocketAddress());

        // when
        // stop broker which is no leader to decrement member size correctly
        clusteringRule.stopBroker(otherBrokers[0]);

        // then topology contains still topic
        clusteringRule.checkTopology((topologyResponse ->
        {
            return topologyResponse.getBrokers()
                .stream()
                .flatMap(broker -> broker.getPartitions().stream())
                .anyMatch(partition -> partition.getTopicName().equalsIgnoreCase(Protocol.SYSTEM_TOPIC));
        }));

        // but requesting topics is not possible since replication factor is not reached -> leader partition was removed
        final Future<Topics> topicRequestFuture = client.topics().getTopics().executeAsync();
        doRepeatedly(() -> clientRule.getActorClock().addTime(Duration.ofSeconds(1)))
            .until((v) -> topicRequestFuture.isDone());
        assertThatThrownBy(() -> topicRequestFuture.get())
            .hasMessageContaining("Request timed out (PT15S). Request was: [ target topic = null, target partition = 0, type = REQUEST_PARTITIONS]")
            .hasCauseInstanceOf(ClientException.class);
    }

    @Test
    public void shouldNotBeAbleToCreateTaskAfterGoingUnderReplicationFactor()
    {
        // given
        final int partitionCount = 1;
        final int replicationFactor = 3;
        final String topicName = "topicName";
        final Topic topic = clusteringRule.createTopic(topicName, partitionCount, replicationFactor);

        final TopologyBroker leaderForSystemPartition = clusteringRule.getLeaderForPartition(Protocol.SYSTEM_PARTITION);
        final int partitionId = topic.getPartitions().get(0).getId();
        final TopologyBroker leaderForTopicPartition = clusteringRule.getLeaderForPartition(partitionId);
        final SocketAddress[] otherBrokers = clusteringRule.getOtherBrokers(leaderForTopicPartition.getSocketAddress());

        // when
        // stop broker which is no leader to decrement member size correctly
        final SocketAddress brokerWhichIsNoLeader = Arrays.stream(otherBrokers).filter(broker -> !broker.equals(leaderForSystemPartition.getSocketAddress())).findAny().get();
        clusteringRule.stopBroker(brokerWhichIsNoLeader);

        // then topology contains still topic
        clusteringRule.checkTopology((topologyResponse ->
        {
            return topologyResponse.getBrokers()
                .stream()
                .flatMap(broker -> broker.getPartitions().stream())
                .anyMatch(partition -> partition.getTopicName().equalsIgnoreCase(topicName));
        }));

        // but creating task on topic is no longer possible since replication factor is not reached -> leader partition was removed
        final String taskType = "taskType";
        final Future<TaskEvent> taskCreationFuture = client.tasks().create(topicName, taskType).executeAsync();
        doRepeatedly(() -> clientRule.getActorClock().addTime(Duration.ofSeconds(1)))
            .until((v) -> taskCreationFuture.isDone());
        assertThatThrownBy(() -> taskCreationFuture.get())
            .hasMessageContaining("Request timed out (PT15S). Request was: [ topic = topicName, partition = 1, event type = TASK, state = CREATE ]")
            .hasCauseInstanceOf(ClientException.class);
    }
}
