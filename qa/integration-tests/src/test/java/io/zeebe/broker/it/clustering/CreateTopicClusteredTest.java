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

import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.*;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.api.events.JobState;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.protocol.Protocol;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.SocketAddress;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class CreateTopicClusteredTest {
  private static final int PARTITION_COUNT = 5;

  public AutoCloseableRule closeables = new AutoCloseableRule();
  public Timeout testTimeout = Timeout.seconds(30);
  public ClientRule clientRule = new ClientRule();
  public ClusteringRule clusteringRule = new ClusteringRule(closeables, clientRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(closeables).around(testTimeout).around(clientRule).around(clusteringRule);

  private ZeebeClient client;

  @Before
  public void setUp() {
    client = clientRule.getClient();
  }

  @Test
  public void shouldCreateTopic() {
    // given

    // when
    final Topic topic = clusteringRule.createTopic("foo", PARTITION_COUNT);

    // then
    assertThat(topic.getName()).isEqualTo("foo");
    assertThat(topic.getPartitions().size()).isEqualTo(PARTITION_COUNT);
  }

  @Test
  public void shouldRequestTopicsAfterTopicCreation() {
    // given
    clusteringRule.createTopic("foo", PARTITION_COUNT);

    // when
    final Topics topicsResponse =
        doRepeatedly(() -> client.newTopicsRequest().send().join())
            .until((topics -> topics.getTopics().size() == 2));

    final List<Topic> topics = topicsResponse.getTopics();

    // then
    final List<Integer> partitions =
        topics
            .stream()
            .filter(t -> t.getName().equals("foo"))
            .flatMap(t -> t.getPartitions().stream())
            .map(Partition::getId)
            .collect(Collectors.toList());

    assertThat(partitions.size()).isEqualTo(5);
    assertThat(partitions).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
  }

  @Test
  public void shouldCreateJobAfterTopicCreation() {
    // given
    clusteringRule.createTopic("foo", PARTITION_COUNT);

    // then
    final JobEvent jobEvent =
        client.topicClient("foo").jobClient().newCreateCommand().jobType("bar").send().join();

    assertThat(jobEvent.getState()).isEqualTo(JobState.CREATED);
  }

  @Test
  public void shouldChooseNewLeaderForCreatedTopicAfterLeaderDies() {
    // given
    final int partitionsCount = 1;
    clusteringRule.createTopic("foo", partitionsCount, 3);

    final JobEvent jobEvent =
        client.topicClient("foo").jobClient().newCreateCommand().jobType("bar").send().join();

    final int partitionId = jobEvent.getMetadata().getPartitionId();

    final BrokerInfo leaderForPartition = clusteringRule.getLeaderForPartition(partitionId);
    final String currentLeaderAddress = leaderForPartition.getAddress();

    // when
    clusteringRule.stopBroker(currentLeaderAddress);

    // then
    final BrokerInfo newLeader = clusteringRule.getLeaderForPartition(partitionId);
    assertThat(newLeader.getAddress()).isNotEqualTo(leaderForPartition.getAddress());
  }

  @Test
  @Ignore("https://github.com/zeebe-io/zeebe/issues/844")
  public void shouldCompleteJobAfterNewLeaderWasChosen() throws Exception {
    // given
    final int partitionsCount = 1;
    final String topicName = "foo";
    final int replicationFactor = 3;
    clusteringRule.createTopic(topicName, partitionsCount, replicationFactor);

    final JobEvent jobEvent =
        client.topicClient(topicName).jobClient().newCreateCommand().jobType("bar").send().join();

    final int partitionId = jobEvent.getMetadata().getPartitionId();

    final BrokerInfo leaderForPartition = clusteringRule.getLeaderForPartition(partitionId);
    final String currentLeaderAddress = leaderForPartition.getAddress();

    // when
    clusteringRule.stopBroker(currentLeaderAddress);

    // then
    final BrokerInfo newLeader = clusteringRule.getLeaderForPartition(partitionId);
    assertThat(newLeader.getAddress()).isNotEqualTo(leaderForPartition.getAddress());

    final CompletableFuture<JobEvent> jobCompleted = new CompletableFuture<>();
    client
        .topicClient(topicName)
        .jobClient()
        .newWorker()
        .jobType("bar")
        .handler(
            (client, event) -> {
              final JobEvent completedJob = client.newCompleteCommand(event).send().join();

              jobCompleted.complete(completedJob);
            })
        .open();

    waitUntil(jobCompleted::isDone);

    assertThat(jobCompleted).isCompleted();
    final JobEvent completedTask = jobCompleted.get();
    assertThat(completedTask.getState()).isEqualTo(JobState.COMPLETED);
  }

  @Test
  public void shouldNotBeAbleToRequestTopicsAfterGoingUnderReplicationFactor() {
    // given
    final BrokerInfo leaderForSystemPartition =
        clusteringRule.getLeaderForPartition(Protocol.SYSTEM_PARTITION);
    final SocketAddress[] otherBrokers =
        clusteringRule.getOtherBrokers(leaderForSystemPartition.getAddress());

    // when
    // stop broker which is no leader to decrement member size correctly
    clusteringRule.stopBroker(otherBrokers[0]);

    // then topology contains still topic
    clusteringRule.waitForTopology(
        (topology ->
            topology
                .stream()
                .flatMap(broker -> broker.getPartitions().stream())
                .anyMatch(
                    partition ->
                        partition.getTopicName().equalsIgnoreCase(Protocol.SYSTEM_TOPIC))));

    // but requesting topics is not possible since replication factor is not reached -> leader
    // partition was removed
    final Future<Topics> topicRequestFuture = client.newTopicsRequest().send();
    doRepeatedly(() -> clientRule.getActorClock().addTime(Duration.ofSeconds(1)))
        .until((v) -> topicRequestFuture.isDone());
    assertThatThrownBy(() -> topicRequestFuture.get())
        .hasMessageContaining(
            "Request timed out (PT15S). Request was: [ target topic = null, target partition = 0, type = REQUEST_PARTITIONS]")
        .hasCauseInstanceOf(ClientException.class);
  }

  @Test
  public void shouldNotBeAbleToCreateJobAfterGoingUnderReplicationFactor() {
    // given
    final int partitionCount = 1;
    final int replicationFactor = 3;
    final String topicName = "topicName";
    final Topic topic = clusteringRule.createTopic(topicName, partitionCount, replicationFactor);

    final BrokerInfo leaderForSystemPartition =
        clusteringRule.getLeaderForPartition(Protocol.SYSTEM_PARTITION);
    final int partitionId = topic.getPartitions().get(0).getId();
    final BrokerInfo leaderForTopicPartition = clusteringRule.getLeaderForPartition(partitionId);
    final SocketAddress[] otherBrokers =
        clusteringRule.getOtherBrokers(leaderForTopicPartition.getAddress());

    // when
    // stop broker which is no leader to decrement member size correctly
    final SocketAddress brokerWhichIsNoLeader =
        Arrays.stream(otherBrokers)
            .filter(
                broker -> !broker.equals(SocketAddress.from(leaderForSystemPartition.getAddress())))
            .findAny()
            .get();
    clusteringRule.stopBroker(brokerWhichIsNoLeader);

    // then topology contains still topic
    clusteringRule.waitForTopology(
        (topology ->
            topology
                .stream()
                .flatMap(broker -> broker.getPartitions().stream())
                .anyMatch(partition -> partition.getTopicName().equalsIgnoreCase(topicName))));

    // but creating task on topic is no longer possible since replication factor is not reached ->
    // leader partition was removed
    final String jobType = "jobType";
    final Future<JobEvent> jobCreationFuture =
        client.topicClient(topicName).jobClient().newCreateCommand().jobType(jobType).send();

    doRepeatedly(() -> clientRule.getActorClock().addTime(Duration.ofSeconds(1)))
        .until((v) -> jobCreationFuture.isDone());
    assertThatThrownBy(() -> jobCreationFuture.get())
        .hasMessageContaining(
            "Request timed out (PT15S). Request was: [ topic = topicName, partition = 1, value type = JOB, command = CREATE ]")
        .hasCauseInstanceOf(ClientException.class);
  }

  @Test
  public void shouldCreateMultipleTopicsWithReplicationFactor() {
    // given
    final String[] topicNames = new String[] {"fooo", "bar"};
    final int partitions = 3;
    final int replicationFactor = clusteringRule.getBrokersInCluster().size();

    // when
    for (final String topicName : topicNames) {
      client
          .newCreateTopicCommand()
          .name(topicName)
          .partitions(partitions)
          .replicationFactor(replicationFactor)
          .send();
    }

    for (final String topicName : topicNames) {
      clusteringRule.waitForTopicPartitionReplicationFactor(
          topicName, partitions, replicationFactor);
    }

    // then
    final Topics topics = client.newTopicsRequest().send().join();
    final Function<String, Optional<Topic>> findTopic =
        (name) -> topics.getTopics().stream().filter(t -> name.equals(t.getName())).findFirst();

    for (final String topicName : topicNames) {
      assertThat(findTopic.apply(topicName))
          .hasValueSatisfying(t -> assertThat(t.getPartitions()).hasSize(partitions));
    }
  }
}
