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

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.Partition;
import io.zeebe.client.api.commands.Topic;
import io.zeebe.client.api.events.*;
import io.zeebe.client.impl.job.CreateJobCommandImpl;
import io.zeebe.test.util.AutoCloseableRule;
import java.util.ArrayList;
import java.util.List;
import org.junit.*;
import org.junit.rules.*;

public class SubscriptionClusteredTest {
  private static final int PARTITION_COUNT = 5;

  public AutoCloseableRule closeables = new AutoCloseableRule();
  public Timeout testTimeout = Timeout.seconds(30);
  public ClientRule clientRule = new ClientRule();
  public ClusteringRule clusteringRule = new ClusteringRule(closeables, clientRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(closeables).around(testTimeout).around(clientRule).around(clusteringRule);

  @Rule public ExpectedException thrown = ExpectedException.none();

  private ZeebeClient client;

  @Before
  public void startUp() {
    client = clientRule.getClient();
  }

  @Test
  public void shouldOpenSubscriptionGroupForDistributedTopic() {
    // given
    final String topicName = "pasta al forno";
    final Topic topic = clusteringRule.createTopic(topicName, PARTITION_COUNT);

    // when
    final Integer[] partitionIds =
        topic.getPartitions().stream().mapToInt(Partition::getId).boxed().toArray(Integer[]::new);

    createJobOnPartition(topicName, partitionIds[0]);
    createJobOnPartition(topicName, partitionIds[1]);
    createJobOnPartition(topicName, partitionIds[2]);
    createJobOnPartition(topicName, partitionIds[3]);
    createJobOnPartition(topicName, partitionIds[4]);

    // and
    final List<Integer> receivedPartitionIds = new ArrayList<>();
    client
        .topicClient(topicName)
        .newSubscription()
        .name("SubscriptionName")
        .jobEventHandler(
            e -> {
              if (e.getState() == JobState.CREATED) {
                receivedPartitionIds.add(e.getMetadata().getPartitionId());
              }
            })
        .startAtHeadOfTopic()
        .open();

    // then
    waitUntil(() -> receivedPartitionIds.size() == PARTITION_COUNT);

    assertThat(receivedPartitionIds).containsExactlyInAnyOrder(partitionIds);
  }

  @Test
  public void shouldReceiveRaftEvents() {
    // given
    final String topicName = "test";
    final int partitions = 1;
    final int replicationFactor = clusteringRule.getBrokersInCluster().size();

    clusteringRule.createTopic(topicName, partitions, replicationFactor);
    clusteringRule.restartBroker(clusteringRule.getFollowerOnly());

    // when
    final List<RaftEvent> raftEvents = new ArrayList<>();
    client
        .topicClient(topicName)
        .newSubscription()
        .name("test-subscription")
        .raftEventHandler(raftEvents::add)
        .startAtHeadOfTopic()
        .open();

    // then we should receive two raft add member events
    waitUntil(() -> raftEvents.size() == 4);

    assertThat(raftEvents).hasSize(4);
    assertThat(raftEvents)
        .extracting(RaftEvent::getState)
        .containsExactly(
            RaftState.MEMBER_ADDED,
            RaftState.MEMBER_ADDED,
            RaftState.MEMBER_REMOVED,
            RaftState.MEMBER_ADDED);
    assertThat(raftEvents.get(1).getMembers()).hasSize(clusteringRule.getBrokersInCluster().size());
  }

  @Test
  public void shouldReceiveRaftEventsFromSystemTopic() {
    // given
    clusteringRule.restartBroker(clusteringRule.getFollowerOnly());

    // when
    final List<RaftEvent> raftEvents = new ArrayList<>();
    client
        .newManagementSubscription()
        .name("test-subscription")
        .raftEventHandler(raftEvents::add)
        .startAtHeadOfTopic()
        .open();

    // then we should receive two raft add member events
    waitUntil(() -> raftEvents.size() == 4);

    assertThat(raftEvents).hasSize(4);
    assertThat(raftEvents)
        .extracting(RaftEvent::getState)
        .containsExactly(
            RaftState.MEMBER_ADDED,
            RaftState.MEMBER_ADDED,
            RaftState.MEMBER_REMOVED,
            RaftState.MEMBER_ADDED);
    assertThat(raftEvents.get(1).getMembers()).hasSize(clusteringRule.getBrokersInCluster().size());
  }

  protected void createJobOnPartition(String topic, int partition) {
    final CreateJobCommandImpl command =
        (CreateJobCommandImpl)
            client.topicClient(topic).jobClient().newCreateCommand().jobType("baz");

    command.getCommand().setPartitionId(partition);
    command.send().join();
  }
}
