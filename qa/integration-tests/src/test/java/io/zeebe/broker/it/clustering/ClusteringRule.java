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
import java.util.stream.Collectors;

import io.zeebe.broker.Broker;
import io.zeebe.broker.it.ClientRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.clustering.impl.BrokerPartitionState;
import io.zeebe.client.clustering.impl.TopologyBroker;
import io.zeebe.client.event.Event;
import io.zeebe.client.topic.Topic;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.SocketAddress;
import org.junit.rules.ExternalResource;

public class ClusteringRule extends ExternalResource
{
    public static final int DEFAULT_REPLICATION_FACTOR = 3;

    public static final String BROKER_1_TOML = "zeebe.cluster.1.cfg.toml";
    public static final SocketAddress BROKER_1_CLIENT_ADDRESS = new SocketAddress("localhost", 51015);

    public static final String BROKER_2_TOML = "zeebe.cluster.2.cfg.toml";
    public static final SocketAddress BROKER_2_CLIENT_ADDRESS = new SocketAddress("localhost", 41015);

    public static final String BROKER_3_TOML = "zeebe.cluster.3.cfg.toml";
    public static final SocketAddress BROKER_3_CLIENT_ADDRESS = new SocketAddress("localhost", 31015);

    private SocketAddress[] brokerAddresses = new SocketAddress[]{BROKER_1_CLIENT_ADDRESS, BROKER_2_CLIENT_ADDRESS, BROKER_3_CLIENT_ADDRESS};
    private String[] brokerConfigs = new String[]{BROKER_1_TOML, BROKER_2_TOML, BROKER_3_TOML};

    // rules
    private final AutoCloseableRule autoCloseableRule;
    private final ClientRule clientRule;

    // interal
    private int replicationFactor = DEFAULT_REPLICATION_FACTOR;
    private ZeebeClient zeebeClient;
    protected final Map<SocketAddress, Broker> brokers = new HashMap<>();

    public ClusteringRule(AutoCloseableRule autoCloseableRule, ClientRule clientRule)
    {
        this.autoCloseableRule = autoCloseableRule;
        this.clientRule = clientRule;
    }

    @Override
    protected void before()
    {
        zeebeClient = clientRule.getClient();

        brokers.put(BROKER_1_CLIENT_ADDRESS, startBroker(BROKER_1_TOML));
        brokers.put(BROKER_2_CLIENT_ADDRESS, startBroker(BROKER_2_TOML));
        brokers.put(BROKER_3_CLIENT_ADDRESS,  startBroker(BROKER_3_TOML));

        waitForTopicAndReplicationFactor("internal-system", 3);
    }

    private List<TopologyBroker> waitForTopicAndReplicationFactor(String topicName, int replicationFactor)
    {
        return doRepeatedly(() -> zeebeClient.requestTopology().execute().getBrokers())
            .until(topologyBrokers -> {
                int leadCount = 0;
                int followerCount = 0;

                if (topologyBrokers != null)
                {
                    for (TopologyBroker topologyBroker : topologyBrokers)
                    {
                        final List<BrokerPartitionState> partitions = topologyBroker.getPartitions();
                        for (BrokerPartitionState brokerPartitionState : partitions)
                        {
                            if (brokerPartitionState.getTopicName().equals(topicName))
                            {
                                if (brokerPartitionState.getState().equals("LEADER"))
                                {
                                    leadCount++;
                                }
                                else
                                {
                                    followerCount++;
                                }
                            }
                        }
                    }
                }
                return (leadCount + followerCount) >= replicationFactor;
            });
    }

    /**
     * Returns the current leader for the given partition.
     *
     * @param partition
     * @return
     */
    public TopologyBroker getLeaderForPartition(int partition)
    {
        final List<TopologyBroker> topologyBrokerList =
            doRepeatedly(() -> zeebeClient.requestTopology().execute().getBrokers())
                .until(topologyBrokers -> extractPartitionLeader(topologyBrokers, partition) != null);
        return extractPartitionLeader(topologyBrokerList, partition);
    }

    private TopologyBroker extractPartitionLeader(List<TopologyBroker> topologyBrokers, int partition)
    {
        TopologyBroker leaderForPartition = null;

        for (TopologyBroker topologyBroker : topologyBrokers)
        {
            final List<BrokerPartitionState> partitions = topologyBroker.getPartitions();
            for (BrokerPartitionState brokerPartitionState : partitions)
            {
                final int currentPartitionId = brokerPartitionState.getPartitionId();

                if (currentPartitionId == partition &&
                    brokerPartitionState.getState().equals("LEADER"))
                {
                    leaderForPartition = topologyBroker;
                    break;
                }
            }
        }
        return leaderForPartition;
    }

    /**
     * Creates a topic with the given partition count in the cluster.
     *
     * This method returns to the user, if the topic and the partitions are created
     * and the replication factor was reached for each partition.
     * Besides that the topic request needs to be return the created topic.
     *
     * The replication factor is per default {@link #DEFAULT_REPLICATION_FACTOR}, but can be modified with
     * {@link #setReplicationFactor(int)}.
     *
     * @param topicName
     * @param partitionCount
     * @return
     */
    public Topic createTopic(String topicName, int partitionCount)
    {
        final Event topicEvent = zeebeClient.topics()
                                         .create(topicName, partitionCount)
                                         .execute();
        assertThat(topicEvent.getState()).isEqualTo("CREATED");

        waitForTopicPartitionReplicationFactor(topicName, partitionCount, replicationFactor);
        final Topic topic = waitForTopicAvailability(topicName);
        return topic;
    }

    private List<TopologyBroker> waitForTopicPartitionReplicationFactor(String topicName, int partitionCount, int replicationFactor)
    {
        return doRepeatedly(() -> zeebeClient.requestTopology().execute().getBrokers())
            .until(topologyBrokers -> {
                final HashMap<Integer, Integer> partitionReplicationCounts = new HashMap<>();

                for (TopologyBroker topologyBroker : topologyBrokers)
                {
                    final List<BrokerPartitionState> partitions = topologyBroker.getPartitions();
                    for (BrokerPartitionState brokerPartitionState : partitions)
                    {
                        final int currentPartitionId = brokerPartitionState.getPartitionId();
                        final String currentTopicName = brokerPartitionState.getTopicName();

                        if (currentTopicName.equals(topicName))
                        {
                            final Integer partitionReplicationFactor = partitionReplicationCounts.getOrDefault(currentPartitionId, 0);
                            partitionReplicationCounts.put(currentPartitionId, partitionReplicationFactor + 1);
                        }
                    }
                }

                boolean success = partitionReplicationCounts.size() == partitionCount;

                if (success)
                {
                    for (Integer partition : partitionReplicationCounts.keySet())
                    {
                        success = success && partitionReplicationCounts.get(partition) >= replicationFactor;
                    }
                }
                return success;
            });
    }

    private Topic waitForTopicAvailability(String topicName)
    {
        return doRepeatedly(() -> zeebeClient.topics().getTopics().execute())
            .until((topics -> topics.getTopics().stream().filter(topic -> topic.getName().equals(topicName)).count() == 1))
            .getTopics()
            .stream()
            .filter(topic -> topic.getName().equals(topicName))
            .findFirst()
            .get();
    }

    private Broker startBroker(String configFile)
    {
        final InputStream config = this.getClass().getClassLoader().getResourceAsStream(configFile);
        final Broker broker = new Broker(config);
        autoCloseableRule.manage(broker);
        return broker;
    }

    /**
     * Restarts broker, if the broker is still running it will be closed before.
     *
     * Returns to the user if the broker is back in the cluster.
     *
     * @param socketAddress
     * @return
     */
    public Broker restartBroker(SocketAddress socketAddress)
    {
        final Broker broker = brokers.get(socketAddress);
        if (broker != null)
        {
            stopBroker(socketAddress);
        }

        for (int i = 0; i < brokerAddresses.length; i++)
        {
            if (brokerAddresses[i].equals(socketAddress))
            {
                brokers.put(socketAddress, startBroker(brokerConfigs[i]));
                waitForTopicAndReplicationFactor("internal-system", replicationFactor);
                break;
            }
        }
        return brokers.get(socketAddress);
    }

    /**
     * Returns for a given broker the leading partition id's.
     *
     * @param socketAddress
     * @return
     */
    public List<Integer> getBrokersLeadingPartitions(SocketAddress socketAddress)
    {
        final List<TopologyBroker> topologyBrokers = zeebeClient.requestTopology()
                                                                .execute()
                                                                .getBrokers();

        final List<Integer> leadingPartitions = new ArrayList<>();

        for (TopologyBroker topologyBroker : topologyBrokers)
        {
            if (topologyBroker.getSocketAddress().equals(socketAddress))
            {
                final List<BrokerPartitionState> partitions = topologyBroker.getPartitions();
                for (BrokerPartitionState brokerPartitionState : partitions)
                {
                    final int currentPartitionId = brokerPartitionState.getPartitionId();
                    if (brokerPartitionState.getState().equals("LEADER"))
                    {
                        leadingPartitions.add(currentPartitionId);
                    }
                }
                break;
            }
        }

        return leadingPartitions;
    }

    /**
     * Returns the list of available brokers in a cluster.
     * @return
     */
    public List<SocketAddress> getBrokersInCluster()
    {
        return zeebeClient.requestTopology()
                          .execute()
                          .getBrokers()
                          .stream()
                          .map(topologyBroker -> topologyBroker.getSocketAddress())
                          .collect(Collectors.toList());

    }

    /**
     * Returns the count of partition leaders for a given topic.
     *
     * @param topic
     * @return
     */
    public int getPartitionLeaderCountForTopic(String topic)
    {
        int leaderCount = 0;

        final List<TopologyBroker> topologyBrokers = zeebeClient.requestTopology()
                                                                .execute()
                                                                .getBrokers();

        for (TopologyBroker topologyBroker : topologyBrokers)
        {
            final List<BrokerPartitionState> partitions = topologyBroker.getPartitions();
            for (BrokerPartitionState brokerPartitionState : partitions)
            {
                final String topicName = brokerPartitionState.getTopicName();
                if (topic.equals(topicName) && brokerPartitionState.getState().equals("LEADER"))
                {
                    leaderCount++;
                }
            }
        }
        return leaderCount;
    }

    /**
     * Stops broker with the given socket address.
     *
     * Returns to the user if the broker was stopped and new leader for the partitions
     * are chosen.
     *
     * @param socketAddress
     */
    public void stopBroker(SocketAddress socketAddress)
    {
        final List<Integer> brokersLeadingPartitions = getBrokersLeadingPartitions(socketAddress);

        brokers.remove(socketAddress).close();

        waitForNewLeaderOfPartitions(brokersLeadingPartitions, socketAddress);
    }

    private void waitForNewLeaderOfPartitions(List<Integer> partitions, SocketAddress oldLeader)
    {
        doRepeatedly(() -> zeebeClient.requestTopology().execute().getBrokers())
            .until(topologyBrokers -> {

                final HashSet<Integer> toSearchPartitions = new HashSet<>(partitions);

                if (topologyBrokers != null)
                {
                    for (TopologyBroker topologyBroker : topologyBrokers)
                    {
                        if (!topologyBroker.getSocketAddress().equals(oldLeader))
                        {
                            final List<BrokerPartitionState> partitionStates = topologyBroker.getPartitions();
                            for (BrokerPartitionState brokerPartitionState : partitionStates)
                            {
                                if (brokerPartitionState.getState().equals("LEADER"))
                                {
                                    toSearchPartitions.remove(brokerPartitionState.getPartitionId());
                                }
                            }
                        }
                    }
                }
                return toSearchPartitions.isEmpty();
            });
    }

    public void setReplicationFactor(int replicationFactor)
    {
        this.replicationFactor = replicationFactor;
    }
}
