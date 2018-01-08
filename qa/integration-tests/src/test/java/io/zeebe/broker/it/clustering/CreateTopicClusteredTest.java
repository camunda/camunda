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
import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.zeebe.broker.Broker;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.it.ClientRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.clustering.impl.TopicLeader;
import io.zeebe.client.event.Event;
import io.zeebe.client.event.TaskEvent;
import io.zeebe.client.topic.Topic;
import io.zeebe.client.topic.Topics;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.SocketAddress;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

public class CreateTopicClusteredTest
{
    private static final int PARTITION_COUNT = 5;

    private static final String BROKER1_CONFIG = "zeebe.cluster.1.cfg.toml";
    private static final String BROKER2_CONFIG = "zeebe.cluster.2.cfg.toml";
    private static final String BROKER3_CONFIG = "zeebe.cluster.3.cfg.toml";

    private static final SocketAddress BROKER1_ADDRESS = new SocketAddress("localhost", 51015);
    private static final SocketAddress BROKER2_ADDRESS = new SocketAddress("localhost", 41015);
    private static final SocketAddress BROKER3_ADDRESS = new SocketAddress("localhost", 31015);

    @Rule
    public AutoCloseableRule closeables = new AutoCloseableRule();

    @Rule
    public ClientRule clientRule = new ClientRule(false);

    @Rule
    public Timeout timeout = new Timeout(30, TimeUnit.SECONDS);

    private ZeebeClient client;
    private final Map<SocketAddress, Broker> brokers = new HashMap<>();

    @Before
    public void setUp()
    {
        client = clientRule.getClient();

        brokers.put(BROKER1_ADDRESS, startBroker(BROKER1_CONFIG));
        brokers.put(BROKER2_ADDRESS, startBroker(BROKER2_CONFIG));
        brokers.put(BROKER3_ADDRESS,  startBroker(BROKER3_CONFIG));

        doRepeatedly(() -> client.requestTopology().execute().getBrokers())
            .until(topologyBroker -> topologyBroker != null && topologyBroker.size() == 3);
    }

    @Test
    public void shouldCreateTopic()
    {
        // given

        // when
        final Event topicEvent = client.topics()
                                .create("foo", PARTITION_COUNT)
                                .execute();

        final List<SocketAddress> topicLeaders =
            doRepeatedly(() -> client.requestTopology()
                                     .execute()
                                     .getTopicLeaders())
                .until(leaders -> leaders != null && leaders.size() >= PARTITION_COUNT)
                .stream()
                .filter((topicLeader -> topicLeader.getTopicName().equals("foo")))
                .map((topicLeader -> topicLeader.getSocketAddress()))
                .collect(Collectors.toList());

        // then
        assertThat(topicEvent.getState()).isEqualTo("CREATED");
        assertThat(topicLeaders.size()).isEqualTo(PARTITION_COUNT);
        assertThat(topicLeaders).contains(brokers.keySet().toArray(new SocketAddress[3]));
    }

    @Test
    public void shouldRequestTopicsAfterTopicCreation()
    {
        // given
        client.topics()
              .create("foo", PARTITION_COUNT)
              .execute();

        doRepeatedly(() -> client.requestTopology()
                                 .execute()
                                 .getTopicLeaders()).until(leaders -> leaders != null && leaders.size() >= PARTITION_COUNT);
        // when
        final Topics topicsResponse = doRepeatedly(() -> client.topics()
                                                      .getTopics()
                                                      .execute()).until((topics -> topics.getTopics()
                                                                                         .size() == 1));
        final List<Topic> topics = topicsResponse.getTopics();

        // then
        assertThat(topics.size()).isEqualTo(1);
        assertThat(topics.get(0).getName()).isEqualTo("foo");
        final List<Integer> partitions = topics.get(0)
                                                 .getPartitions()
                                                 .stream()
                                                 .map((partition -> partition.getId()))
                                                 .collect(Collectors.toList());

        assertThat(partitions.size()).isEqualTo(5);
        assertThat(partitions).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
    }

    @Test
    public void shouldCreateTaskAfterTopicCreation()
    {
        // given

        // when
        client.topics().create("foo", PARTITION_COUNT).execute();
        doRepeatedly(() -> client.requestTopology()
                                 .execute()
                                 .getTopicLeaders()).until(leaders -> leaders != null && leaders.size() >= PARTITION_COUNT);

        // then
        final TaskEvent taskEvent = client.tasks().create("foo", "bar").execute();

        assertThat(taskEvent).isNotNull();
        assertThat(taskEvent.getState()).isEqualTo("CREATED");
    }

    @Test
    @Ignore("https://github.com/zeebe-io/zeebe/issues/617")
    public void shouldChooseNewLeaderForCreatedTopicAfterLeaderDies()
    {
        // given
        final int partitionsCount = 1;
        client.topics().create("foo", partitionsCount).execute();
        final TaskEvent taskEvent = client.tasks().create("foo", "bar").execute();
        final int partitionId = taskEvent.getMetadata().getPartitionId();

        final Optional<TopicLeader> topicLeader = doRepeatedly(() -> client.requestTopology()
                                                                         .execute()
                                                                         .getTopicLeaders()).until(leaders -> leaders != null && leaders.size() >= partitionsCount)
                                                                                            .stream()
                                                                                            .filter((leader) -> leader.getPartitionId() == partitionId)
                                                                                            .findAny();
        final SocketAddress currentLeaderAddress = topicLeader.get().getSocketAddress();
        final Broker currentLeader = brokers.get(currentLeaderAddress);

        final Set<SocketAddress> expectedFollowers = new HashSet<>(brokers.keySet());
        expectedFollowers.remove(currentLeaderAddress);

        // when
        Loggers.CLUSTERING_LOGGER.debug("Close leader {}", currentLeaderAddress);
        currentLeader.close();

        // then
        final Optional<TopicLeader> newLeader = doRepeatedly(() -> client.requestTopology()
                                                                         .execute()
                                                                         .getTopicLeaders()).until(leaders -> leaders != null && leaders.size() >= partitionsCount)
                                                                                            .stream()
                                                                                            .filter((leader) -> leader.getPartitionId() == partitionId)
                                                                                            .findAny();
        final SocketAddress newLeaderAddress = newLeader.get().getSocketAddress();
        assertThat(expectedFollowers).contains(newLeaderAddress);
    }

    protected Broker startBroker(String configFile)
    {
        final InputStream config = this.getClass().getClassLoader().getResourceAsStream(configFile);
        final Broker broker = new Broker(config);
        closeables.manage(broker);
        return broker;
    }
}
