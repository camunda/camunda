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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.zeebe.broker.Broker;
import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.subscription.RecordingEventHandler;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.task.impl.CreateTaskCommandImpl;
import io.zeebe.client.topic.Topic;
import io.zeebe.client.topic.Topics;
import io.zeebe.test.util.AutoCloseableRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;

/**
 *
 */
public class SubscriptionClusteredTest
{
    private static final int PARTITION_COUNT = 5;

    @Rule
    public AutoCloseableRule closeables = new AutoCloseableRule();

    @Rule
    public ClientRule clientRule = new ClientRule(false);

    @Rule
    public Timeout timeout = new Timeout(15, TimeUnit.SECONDS);

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
        startBroker("zeebe.cluster.1.cfg.toml");
        startBroker("zeebe.cluster.2.cfg.toml");
        startBroker("zeebe.cluster.3.cfg.toml");

        doRepeatedly(() -> client.requestTopology().execute().getBrokers())
            .until(topologyBroker -> topologyBroker != null && topologyBroker.size() == 3);

        client.topics().create(topicName, PARTITION_COUNT).execute();

        doRepeatedly(() -> client.requestTopology()
                                 .execute()
                                 .getTopicLeaders())
            .until(leaders -> leaders != null && leaders.size() >= PARTITION_COUNT);

        final Topics topicsResponse =
            doRepeatedly(() -> client.topics()
                                     .getTopics()
                                     .execute())
                .until((topics -> topics.getTopics().size() == 1));

        // when
        final Topic topic = topicsResponse.getTopics().stream()
                                  .filter(t -> t.getName().equals(topicName))
                                  .findFirst()
                                  .get();

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


    protected void createTaskOnPartition(String topic, int partition)
    {
        final CreateTaskCommandImpl createTaskCommand = (CreateTaskCommandImpl) client.tasks().create(topic, "baz");
        createTaskCommand.getEvent().setPartitionId(partition);
        createTaskCommand.execute();
    }

    private Broker startBroker(String configFile)
    {
        final InputStream config = this.getClass().getClassLoader().getResourceAsStream(configFile);
        final Broker broker = new Broker(config);
        closeables.manage(broker);

        return broker;
    }
}
