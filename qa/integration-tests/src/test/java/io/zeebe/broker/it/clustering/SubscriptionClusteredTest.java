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
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.subscription.RecordingEventHandler;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.impl.job.impl.CreateTaskCommandImpl;
import io.zeebe.client.impl.topic.Topic;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;
import io.zeebe.test.util.AutoCloseableRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class SubscriptionClusteredTest
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
                                            .mapToInt(p -> p.getId())
                                            .boxed()
                                            .toArray(Integer[]::new);

        createTaskOnPartition(topicName, partitionIds[0]);
        createTaskOnPartition(topicName, partitionIds[1]);
        createTaskOnPartition(topicName, partitionIds[2]);
        createTaskOnPartition(topicName, partitionIds[3]);
        createTaskOnPartition(topicName, partitionIds[4]);

        // and
        final RecordingEventHandler recordingEventHandler = new RecordingEventHandler();
        final List<Integer> receivedPartitionIds = new ArrayList<>();
        client.topics()
              .newSubscription(topicName)
              .handler(recordingEventHandler)
              .taskEventHandler(e ->
              {
                  if ("CREATE".equals(e.getState()))
                  {
                      receivedPartitionIds.add(e.getMetadata()
                                                .getPartitionId());
                  }
              })
              .startAtHeadOfTopic()
              .name("SubscriptionName")
              .open();

        // then
        waitUntil(() -> receivedPartitionIds.size() == PARTITION_COUNT);

        assertThat(receivedPartitionIds).containsExactlyInAnyOrder(partitionIds);
    }

    @Test
    public void shouldReceiveRaftEvent()
    {
        fail("assert that raft events are received and have a proper intent value");
    }

    protected void createTaskOnPartition(String topic, int partition)
    {
        final CreateTaskCommandImpl createTaskCommand = (CreateTaskCommandImpl) client.tasks().create(topic, "baz");
        createTaskCommand.getCommand().setPartitionId(partition);
        createTaskCommand.execute();
    }
}
