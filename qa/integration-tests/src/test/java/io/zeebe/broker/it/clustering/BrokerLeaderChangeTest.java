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

import static io.zeebe.logstreams.log.LogStream.DEFAULT_PARTITION_ID;
import static io.zeebe.logstreams.log.LogStream.DEFAULT_TOPIC_NAME;
import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.zeebe.broker.Broker;
import io.zeebe.broker.it.ClientRule;
import io.zeebe.client.*;
import io.zeebe.client.clustering.RequestTopologyCmd;
import io.zeebe.client.clustering.impl.*;
import io.zeebe.client.event.TopicSubscription;
import io.zeebe.client.impl.Topic;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.client.task.TaskSubscription;
import io.zeebe.test.util.TestUtil;
import io.zeebe.transport.SocketAddress;
import org.junit.*;
import org.junit.rules.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Ignore("Unreliable cause of multiple problems: " +
    "https://github.com/camunda-tngp/zeebe/issues/292 " +
    "https://github.com/camunda-tngp/zeebe/issues/313 " +
    "https://github.com/camunda-tngp/zeebe/issues/314 " +
    "https://github.com/camunda-tngp/zeebe/issues/315")
public class BrokerLeaderChangeTest
{
    // TODO: remove logging after test becomes stable
    public static final Logger LOG = LoggerFactory.getLogger(BrokerLeaderChangeTest.class);

    public static final Topic DEFAULT_TOPIC = new Topic(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_ID);

    public static final String BROKER_1_TOML = "zeebe.cluster.1.cfg.toml";
    public static final SocketAddress BROKER_1_CLIENT_ADDRESS = new SocketAddress("localhost", 51015);
    public static final SocketAddress BROKER_1_RAFT_ADDRESS = new SocketAddress("localhost", 51017);

    public static final String BROKER_2_TOML = "zeebe.cluster.2.cfg.toml";
    public static final SocketAddress BROKER_2_CLIENT_ADDRESS = new SocketAddress("localhost", 41015);
    public static final SocketAddress BROKER_2_RAFT_ADDRESS = new SocketAddress("localhost", 41017);

    public static final String BROKER_3_TOML = "zeebe.cluster.3.cfg.toml";
    public static final SocketAddress BROKER_3_CLIENT_ADDRESS = new SocketAddress("localhost", 31015);
    public static final SocketAddress BROKER_3_RAFT_ADDRESS = new SocketAddress("localhost", 31017);

    public static final String TASK_TYPE = "testTask";

    @Rule
    public ClientRule clientRule = new ClientRule();

    protected final Map<SocketAddress, Broker> brokers = new HashMap<>();

    protected ZeebeClient client;
    protected TopicClient topicClient;
    protected TaskTopicClient taskClient;

    @Rule
    public Timeout testTimeout = Timeout.seconds(120);

    @Before
    public void setUp()
    {
        client = clientRule.getClient();
        topicClient = clientRule.topic();
        taskClient = clientRule.taskTopic();
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
    public void test() throws Exception
    {
        // start first broker
        startBroker(BROKER_1_CLIENT_ADDRESS, BROKER_1_TOML);

        final TopologyObserver topologyObserver = new TopologyObserver(client);
        topologyObserver.waitForBroker(BROKER_1_CLIENT_ADDRESS);

        final RaftMemberObserver raftMemberObserver = new RaftMemberObserver(topicClient);
        raftMemberObserver.waitForRaftMember(BROKER_1_RAFT_ADDRESS);

        // start second broker
        startBroker(BROKER_2_CLIENT_ADDRESS, BROKER_2_TOML);
        topologyObserver.waitForBroker(BROKER_2_CLIENT_ADDRESS);
        raftMemberObserver.waitForRaftMember(BROKER_2_RAFT_ADDRESS);

        // start third broker
        startBroker(BROKER_3_CLIENT_ADDRESS, BROKER_3_TOML);
        topologyObserver.waitForBroker(BROKER_3_CLIENT_ADDRESS);
        raftMemberObserver.waitForRaftMember(BROKER_3_RAFT_ADDRESS);

        // force topology manager refresh so that all brokers are known
        refreshTopologyNow();

        // wait for topic leader
        SocketAddress leader = topologyObserver.waitForLeader(DEFAULT_TOPIC, brokers.keySet());

        // create task on leader
        LOG.info("Creating task for type {}", TASK_TYPE);
        final long taskKey = taskClient
            .create()
            .taskType(TASK_TYPE)
            .execute();
        LOG.info("Task created with key {}", taskKey);

        // close topic subscription
        raftMemberObserver.close();

        // stop leader
        brokers.remove(leader).close();
        LOG.info("Leader {} is shutdown", leader);

        // wait for other broker become leader
        leader = topologyObserver.waitForLeader(DEFAULT_TOPIC, brokers.keySet());
        LOG.info("Leader changed to {}", leader);

        // complete task and wait for completed event
        final TaskCompleter taskCompleter = new TaskCompleter(taskClient, topicClient, taskKey);
        taskCompleter.waitForTaskCompletion();

        taskCompleter.close();
    }

    private void refreshTopologyNow() throws ExecutionException, InterruptedException
    {
        final ZeebeClientImpl client = (ZeebeClientImpl) this.client;
        final ClientTopologyManager topologyManager = client.getTopologyManager();
        topologyManager.refreshNow().get();
        LOG.info("Topology refreshed: {}", topologyManager.getTopology());
    }

    protected void startBroker(final SocketAddress socketAddress, final String configFilePath)
    {
        LOG.info("starting broker {} with config {}", socketAddress, configFilePath);
        final InputStream config = BrokerLeaderChangeTest.class.getClassLoader().getResourceAsStream(configFilePath);
        assertThat(config).isNotNull();

        brokers.put(socketAddress, new Broker(config));
    }

    static class RaftMemberObserver
    {

        private final ConcurrentHashMap.KeySetView<SocketAddress, Boolean> raftMembers;
        private final TopicSubscription subscription;

        RaftMemberObserver(final TopicClient client)
        {
            raftMembers = ConcurrentHashMap.newKeySet();
            subscription = doRepeatedly(() -> client.newSubscription()
                .name("raftObserver")
                .startAtHeadOfTopic()
                .forcedStart()
                .raftEventHandler((metadata, event) ->
                {
                    final List<SocketAddress> members = event.getMembers();
                    if (members != null)
                    {
                        raftMembers.retainAll(members);
                        raftMembers.addAll(members);
                    }
                })
                .open()
            )
                .until(Objects::nonNull, "Failed to open topic subscription for raft events");
        }

        boolean isRaftMember(final SocketAddress socketAddress)
        {
            return raftMembers.contains(socketAddress);
        }

        void waitForRaftMember(final SocketAddress socketAddress)
        {
            waitUntil(() -> isRaftMember(socketAddress), 100,
                "Failed to wait for %s become part of the raft group", socketAddress);
        }

        void close()
        {
            if (!subscription.isClosed())
            {
                subscription.close();
                LOG.info("Raft subscription closed");
            }
        }

    }

    static class TopologyObserver
    {

        private final RequestTopologyCmd requestTopologyCmd;

        TopologyObserver(final ZeebeClient client)
        {
            requestTopologyCmd = client.requestTopology();
        }

        void waitForBroker(final SocketAddress socketAddress)
        {
            updateTopology()
                .until(t -> t != null && t.getBrokers().contains(socketAddress),
                    "Failed to wait for %s be a known broker", socketAddress);

            LOG.info("Broker {} is known by the cluster", socketAddress);
        }

        SocketAddress waitForLeader(final Topic topic, final Set<SocketAddress> socketAddresses)
        {
            final TopologyResponse respose = updateTopology()
                .until(t -> t != null && socketAddresses.contains(getLeaderForTopic(t, topic)),
                    "Failed to wait for %s become leader of topic %s", socketAddresses, topic);

            final SocketAddress leader = getLeaderForTopic(respose, topic);

            LOG.info("Broker {} is leader for topic {}", leader, topic);
            return leader;
        }

        TestUtil.Invocation<TopologyResponse> updateTopology()
        {
            return doRepeatedly(requestTopologyCmd::execute);
        }

        static SocketAddress getLeaderForTopic(TopologyResponse resp, Topic topic)
        {
            for (TopicLeader leader : resp.getTopicLeaders())
            {
                if (topic.equals(leader.getTopic()))
                {
                    return leader.getSocketAddress();
                }
            }

            return null;
        }

    }

    static class TaskCompleter
    {

        private final AtomicBoolean isTaskCompleted = new AtomicBoolean(false);
        private final TaskSubscription taskSubscription;
        private final TopicSubscription topicSubscription;

        TaskCompleter(final TaskTopicClient taskClient, final TopicClient topicClient, final long taskKey)
        {
            LOG.info("Completing task wit key {}", taskKey);

            taskSubscription = doRepeatedly(() -> taskClient.newTaskSubscription()
                .taskType(TASK_TYPE)
                .lockOwner("taskCompleter")
                .lockTime(Duration.ofMinutes(1))
                .handler(task ->
                {
                    if (task.getKey() == taskKey)
                    {
                        task.complete();
                    }
                })
                .open()
            )
                .until(Objects::nonNull, "Failed to open task subscription for task completion");

            topicSubscription = doRepeatedly(() -> topicClient.newSubscription()
                 .startAtHeadOfTopic()
                 .forcedStart()
                 .name("taskObserver")
                 .taskEventHandler((metadata, event) ->
                 {
                     if (TASK_TYPE.equals(event.getType()) && "COMPLETED".equals(event.getEventType()))
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
            LOG.info("Task completed");
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

}
