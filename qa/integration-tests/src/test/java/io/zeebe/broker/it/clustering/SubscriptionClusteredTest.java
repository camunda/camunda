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

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.subscription.RecordingEventHandler;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.Topic;
import io.zeebe.client.api.events.JobEvent.JobState;
import io.zeebe.client.impl.job.CreateJobCommandImpl;
import io.zeebe.test.util.AutoCloseableRule;
import org.junit.*;
import org.junit.rules.*;

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

        createJobOnPartition(topicName, partitionIds[0]);
        createJobOnPartition(topicName, partitionIds[1]);
        createJobOnPartition(topicName, partitionIds[2]);
        createJobOnPartition(topicName, partitionIds[3]);
        createJobOnPartition(topicName, partitionIds[4]);

        // and
        final RecordingEventHandler recordHandler = new RecordingEventHandler();
        final List<Integer> receivedPartitionIds = new ArrayList<>();
        client.topicClient(topicName)
              .subscriptionClient()
              .newTopicSubscription()
              .name("SubscriptionName")
              .recordHandler(recordHandler)
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

    protected void createJobOnPartition(String topic, int partition)
    {
        final CreateJobCommandImpl command = (CreateJobCommandImpl) client.topicClient(topic).jobClient()
            .newCreateCommand()
            .jobType("baz");

        command.getCommand().setPartitionId(partition);
        command.execute();
    }
}
