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
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import io.zeebe.broker.Broker;
import io.zeebe.broker.it.ClientRule;
import io.zeebe.client.TasksClient;
import io.zeebe.client.TopicsClient;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.clustering.impl.TopicLeader;
import io.zeebe.client.event.TaskEvent;
import io.zeebe.client.event.TopicSubscription;
import io.zeebe.client.task.TaskSubscription;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.SocketAddress;
import org.junit.*;
import org.junit.rules.Timeout;

public class BrokerLeaderChangeTest
{
    public static final String BROKER_1_TOML = "zeebe.cluster.1.cfg.toml";
    public static final SocketAddress BROKER_1_CLIENT_ADDRESS = new SocketAddress("localhost", 51015);

    public static final String BROKER_2_TOML = "zeebe.cluster.2.cfg.toml";
    public static final SocketAddress BROKER_2_CLIENT_ADDRESS = new SocketAddress("localhost", 41015);

    public static final String BROKER_3_TOML = "zeebe.cluster.3.cfg.toml";
    public static final SocketAddress BROKER_3_CLIENT_ADDRESS = new SocketAddress("localhost", 31015);

    public static final String TASK_TYPE = "testTask";

    @Rule
    public AutoCloseableRule closeables = new AutoCloseableRule();

    @Rule
    public ClientRule clientRule = new ClientRule(false);

    protected final Map<SocketAddress, Broker> brokers = new HashMap<>();

    protected ZeebeClient client;
    protected TopicsClient topicClient;
    protected TasksClient taskClient;
    protected int partition;

    @Rule
    public Timeout testTimeout = Timeout.seconds(120);

    @Before
    public void setUp()
    {
        client = clientRule.getClient();
        topicClient = clientRule.topics();
        taskClient = clientRule.tasks();
        partition = clientRule.getDefaultPartition();
    }

    @After
    public void tearDown()
    {
        for (final Broker broker : brokers.values())
        {
            broker.close();
        }
    }

    @Test
    @Ignore("https://github.com/zeebe-io/zeebe/issues/617")
    public void shouldChangeLeaderAfterLeaderDies()
    {
        // given
        brokers.put(BROKER_1_CLIENT_ADDRESS, startBroker(BROKER_1_TOML));
        brokers.put(BROKER_2_CLIENT_ADDRESS, startBroker(BROKER_2_TOML));
        brokers.put(BROKER_3_CLIENT_ADDRESS,  startBroker(BROKER_3_TOML));

        doRepeatedly(() -> client.requestTopology().execute().getBrokers())
            .until(topologyBroker -> topologyBroker != null && topologyBroker.size() == 3);

        client.topics().create(clientRule.getDefaultTopic(), 2).execute();
        final Optional<TopicLeader> topicLeader = doRepeatedly(() -> client.requestTopology()
                                                                     .execute()
                                                                     .getTopicLeaders()).until(leaders -> leaders != null && leaders.size() >= 2)
                                                                                        .stream()
                                                                                        .filter((leader) -> leader.getPartitionId() == partition)
                                                                                        .findAny();

        final SocketAddress leaderAddress = topicLeader.get().getSocketAddress();
        final TaskEvent taskEvent = taskClient.create(clientRule.getDefaultTopic(), TASK_TYPE)
                                              .execute();

        // when
        brokers.remove(leaderAddress).close();
        doRepeatedly(() -> client.requestTopology().execute().getBrokers())
            .until(topologyBroker -> topologyBroker != null && topologyBroker.size() == 2);

        // then
        final Optional<TopicLeader> newLeader = doRepeatedly(() -> client.requestTopology()
                                                                         .execute()
                                                                         .getTopicLeaders()).until(leaders -> leaders != null && leaders.size() >= 2)
                                                                                            .stream()
                                                                                            .filter((leader) -> leader.getPartitionId() == partition)
                                                                                            .findAny();

        assertThat(newLeader.get().getSocketAddress()).isNotEqualTo(leaderAddress);

        // when
        final TaskCompleter taskCompleter = new TaskCompleter(taskEvent);

        // then
        taskCompleter.waitForTaskCompletion();

        taskCompleter.close();
    }

    class TaskCompleter
    {

        private final AtomicBoolean isTaskCompleted = new AtomicBoolean(false);
        private final TaskSubscription taskSubscription;
        private final TopicSubscription topicSubscription;

        TaskCompleter(TaskEvent task)
        {
            final long eventKey = task.getMetadata().getKey();

            taskSubscription = doRepeatedly(() -> taskClient.newTaskSubscription(clientRule.getDefaultTopic())
                .taskType(TASK_TYPE)
                .lockOwner("taskCompleter")
                .lockTime(Duration.ofMinutes(1))
                .handler((c, t) ->
                {
                    if (t.getMetadata().getKey() == eventKey)
                    {
                        c.complete(t).withoutPayload().execute();
                    }
                })
                .open()
            )
                .until(Objects::nonNull, "Failed to open task subscription for task completion");

            topicSubscription = doRepeatedly(() -> topicClient.newSubscription(clientRule.getDefaultTopic())
                 .startAtHeadOfTopic()
                 .forcedStart()
                 .name("taskObserver")
                 .taskEventHandler(e ->
                 {
                     if (TASK_TYPE.equals(e.getType()) && "COMPLETED".equals(e.getState()))
                     {
                         isTaskCompleted.set(true);
                     }
                 })
                 .open()
            )
                .until(Objects::nonNull, "Failed to open topic subscription for task completion");
        }

        void waitForTaskCompletion()
        {
            waitUntil(isTaskCompleted::get, 100, "Failed to wait for task completion");
        }

        void close()
        {
            if (!taskSubscription.isClosed())
            {
                taskSubscription.close();
            }

            if (!topicSubscription.isClosed())
            {
                topicSubscription.close();
            }
        }

    }

    protected Broker startBroker(String configFile)
    {
        final InputStream config = this.getClass().getClassLoader().getResourceAsStream(configFile);
        final Broker broker = new Broker(config);
        closeables.manage(broker);
        return broker;
    }

}
