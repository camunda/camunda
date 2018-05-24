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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.zeebe.client.api.commands.Partition;
import io.zeebe.client.api.record.Record;
import io.zeebe.client.api.record.RecordMetadata;
import io.zeebe.client.api.record.RecordType;
import io.zeebe.client.api.record.ValueType;
import io.zeebe.protocol.intent.RaftIntent;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.subscription.RecordingEventHandler;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.Topic;
import io.zeebe.client.api.events.JobState;
import io.zeebe.client.impl.job.CreateJobCommandImpl;
import io.zeebe.test.util.AutoCloseableRule;

public class SubscriptionClusteredTest
{
    private static final int PARTITION_COUNT = 5;

    public AutoCloseableRule closeables = new AutoCloseableRule();
    public Timeout testTimeout = Timeout.seconds(30);
    public ClientRule clientRule = new ClientRule();
    public ClusteringRule clusteringRule = new ClusteringRule(closeables, clientRule);

    @Rule
    public RuleChain ruleChain =
        RuleChain.outerRule(closeables)
                 .around(testTimeout)
                 .around(clientRule)
                 .around(clusteringRule);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private ZeebeClient client;

    @Before
    public void startUp()
    {
        client = clientRule.getClient();
    }

    @Test
    public void shouldOpenSubscriptionGroupForDistributedTopic()
    {
        // given
        final String topicName = "pasta al forno";
        final Topic topic = clusteringRule.createTopic(topicName, PARTITION_COUNT);

        // when
        final Integer[] partitionIds = topic.getPartitions().stream()
                                            .mapToInt(Partition::getId)
                                            .boxed()
                                            .toArray(Integer[]::new);

        createJobOnPartition(topicName, partitionIds[0]);
        createJobOnPartition(topicName, partitionIds[1]);
        createJobOnPartition(topicName, partitionIds[2]);
        createJobOnPartition(topicName, partitionIds[3]);
        createJobOnPartition(topicName, partitionIds[4]);

        // and
        final List<Integer> receivedPartitionIds = new ArrayList<>();
        client.topicClient(topicName)
              .newSubscription()
              .name("SubscriptionName")
              .jobEventHandler(e ->
              {
                  if (e.getState() == JobState.CREATED)
                  {
                      receivedPartitionIds.add(e.getMetadata()
                                                .getPartitionId());
                  }
              })
              .startAtHeadOfTopic()
              .open();

        // then
        waitUntil(() -> receivedPartitionIds.size() == PARTITION_COUNT);

        assertThat(receivedPartitionIds).containsExactlyInAnyOrder(partitionIds);
    }

    @Test
    public void shouldReceiveRaftEvents()
    {
        // given
        final String topicName = "test";
        final int partitions = 1;
        final int replicationFactor = clusteringRule.getBrokersInCluster().size();

        clusteringRule.createTopic(topicName, partitions, replicationFactor);

        // when
        final RecordingEventHandler eventRecorder = new RecordingEventHandler();
        client.topicClient(topicName)
            .newSubscription()
            .name("test-subscription")
            .recordHandler(eventRecorder)
            .startAtHeadOfTopic()
            .open();

        // then we should receive two raft add member events
        waitUntil(() -> eventRecorder.numRaftRecords() == 2);

        final List<RecordMetadata> raftRecords = eventRecorder.getRecords()
            .stream()
            .map(Record::getMetadata)
            .filter(m -> m.getValueType() == ValueType.RAFT)
            .collect(Collectors.toList());

        assertThat(raftRecords.size()).isEqualTo(2);
        for (final RecordMetadata raftRecord : raftRecords)
        {
            assertThat(raftRecord)
                .hasFieldOrPropertyWithValue("valueType", ValueType.RAFT)
                .hasFieldOrPropertyWithValue("recordType", RecordType.EVENT)
                .hasFieldOrPropertyWithValue("intent", RaftIntent.ADD_MEMBER.name());
        }

        // TODO: extend test to contain remove member event, this is currently not possible
    }

    protected void createJobOnPartition(String topic, int partition)
    {
        final CreateJobCommandImpl command = (CreateJobCommandImpl) client.topicClient(topic).jobClient()
            .newCreateCommand()
            .jobType("baz");

        command.getCommand().setPartitionId(partition);
        command.send().join();
    }
}
