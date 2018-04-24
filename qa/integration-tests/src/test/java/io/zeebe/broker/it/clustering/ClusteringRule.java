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

import io.zeebe.broker.Broker;
import io.zeebe.broker.it.ClientRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.clustering.impl.BrokerPartitionState;
import io.zeebe.client.clustering.impl.TopologyBroker;
import io.zeebe.client.clustering.impl.TopologyResponse;
import io.zeebe.client.event.Event;
import io.zeebe.client.impl.clustering.BrokerInfoImpl;
import io.zeebe.client.impl.clustering.PartitionInfoImpl;
import io.zeebe.client.impl.topic.Topic;
import io.zeebe.client.impl.topic.Topics;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.FileUtil;
import org.assertj.core.util.Files;
import org.junit.rules.ExternalResource;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static org.assertj.core.api.Assertions.assertThat;

public class ClusteringRule extends ExternalResource
{
    private static final int REPLICATION_RETRY_COUNT = 100;

    public static final int DEFAULT_REPLICATION_FACTOR = 1;
    public static final int SYSTEM_TOPIC_REPLICATION_FACTOR = 3;

    public static final String BROKER_1_TOML = "zeebe.cluster.1.cfg.toml";
    public static final SocketAddress BROKER_1_CLIENT_ADDRESS = new SocketAddress("0.0.0.0", 51015);

    public static final String BROKER_2_TOML = "zeebe.cluster.2.cfg.toml";
    public static final SocketAddress BROKER_2_CLIENT_ADDRESS = new SocketAddress("0.0.0.0", 41015);

    public static final String BROKER_3_TOML = "zeebe.cluster.3.cfg.toml";
    public static final SocketAddress BROKER_3_CLIENT_ADDRESS = new SocketAddress("0.0.0.0", 31015);

    public static final String BROKER_4_TOML = "zeebe.cluster.4.cfg.toml";
    public static final SocketAddress BROKER_4_CLIENT_ADDRESS = new SocketAddress("0.0.0.0", 21015);

    private SocketAddress[] brokerAddresses = new SocketAddress[]{BROKER_1_CLIENT_ADDRESS, BROKER_2_CLIENT_ADDRESS, BROKER_3_CLIENT_ADDRESS};
    private String[] brokerConfigs = new String[]{BROKER_1_TOML, BROKER_2_TOML, BROKER_3_TOML};
    private final int spreadCount = brokerAddresses.length * 6;

    // rules
    private final AutoCloseableRule autoCloseableRule;
    private final ClientRule clientRule;

    // internal
    private ZeebeClient zeebeClient;
    protected final Map<SocketAddress, Broker> brokers = new HashMap<>();
    protected final Map<SocketAddress, File> brokerBases = new HashMap<>();

    public ClusteringRule(AutoCloseableRule autoCloseableRule, ClientRule clientRule, SocketAddress[] brokerAddresses, String[] brokerConfigs)
    {
        this(autoCloseableRule, clientRule);
        this.brokerAddresses = brokerAddresses;
        this.brokerConfigs = brokerConfigs;
    }

    public ClusteringRule(AutoCloseableRule autoCloseableRule, ClientRule clientRule)
    {
        this.autoCloseableRule = autoCloseableRule;
        this.clientRule = clientRule;
    }

    @Override
    protected void before()
    {
        zeebeClient = clientRule.getClient();

        for (int i = 0; i < brokerConfigs.length; i++)
        {
            final File brokerBase = Files.newTemporaryFolder();
            final SocketAddress brokerAddress = brokerAddresses[i];
            brokerBases.put(brokerAddress, brokerBase);
            autoCloseableRule.manage(() -> FileUtil.deleteFolder(brokerBase.getAbsolutePath()));
            brokers.put(brokerAddress, startBroker(brokerAddress, brokerConfigs[i]));
        }

        waitForInternalSystemAndReplicationFactor();

        waitUntilBrokersInTopology(brokers.size());
    }

    private void waitUntilBrokersInTopology(int size)
    {
        waitForSpreading(() ->
        {
            doRepeatedly(this::requestBrokers)
                .until(topologyBrokers -> topologyBrokers.size() == size);
        });
    }

    private void waitForInternalSystemAndReplicationFactor()
    {
        waitForTopicPartitionReplicationFactor("internal-system", 1, SYSTEM_TOPIC_REPLICATION_FACTOR);
    }

    /**
     * Returns the current leader for the given partition.
     *
     * @param partition
     * @return
     */
    public BrokerInfoImpl getLeaderForPartition(int partition)
    {
        return
            doRepeatedly(() -> {
                final List<BrokerInfoImpl> brokers = zeebeClient.requestTopology().execute().getBrokers();
                return extractPartitionLeader(brokers, partition);
            })
                .until(Optional::isPresent)
                .get();
    }

    private Optional<BrokerInfoImpl> extractPartitionLeader(List<BrokerInfoImpl> topologyBrokers, int partition)
    {
        return topologyBrokers.stream()
            .filter(b -> b.getPartitions()
                    .stream()
                    .anyMatch(p -> p.getPartitionId() == partition && p.isLeader())
            )
            .findFirst();
    }

    /**
     * Creates a topic with the given partition count in the cluster.
     *
     * This method returns to the user, if the topic and the partitions are created
     * and the replication factor was reached for each partition.
     * Besides that the topic request needs to be return the created topic.
     *
     * The replication factor is per default the number of current brokers in the cluster, see {@link #getReplicationFactor()}.
     *
     * @param topicName the name of the topic to create
     * @param partitionCount to number of partitions for the new topic
     * @return the created topic
     */
    public Topic createTopic(String topicName, int partitionCount)
    {
        return createTopic(topicName, partitionCount, DEFAULT_REPLICATION_FACTOR);
    }

    public Topic createTopic(String topicName, int partitionCount, int replicationFactor)
    {
        final Event topicEvent = zeebeClient.topics()
                                         .create(topicName, partitionCount, replicationFactor)
                                         .execute();
        assertThat(topicEvent.getState()).isEqualTo("CREATING");

        waitForTopicPartitionReplicationFactor(topicName, partitionCount, replicationFactor);

        return waitForSpreading(() -> waitForTopicAvailability(topicName));
    }

    private boolean hasPartitionsWithReplicationFactor(List<BrokerInfoImpl> brokers, String topicName, int partitionCount, int replicationFactor)
    {
        final AtomicLong leaders = new AtomicLong();
        final AtomicLong followers = new AtomicLong();

        brokers.stream()
               .flatMap(b -> b.getPartitions().stream())
               .filter(p -> p.getTopicName().equals(topicName))
               .forEach(p ->
               {
                   if (p.isLeader())
                   {
                       leaders.getAndIncrement();
                   }
                   else
                   {
                       followers.getAndIncrement();
                   }
               });

        return leaders.get() >= partitionCount && followers.get() >= partitionCount * (replicationFactor - 1);
    }

    public void waitForTopicPartitionReplicationFactor(String topicName, int partitionCount, int replicationFactor)
    {
        waitForSpreading(() ->
        {
            doRepeatedly(this::requestBrokers)
                .until(topologyBrokers -> hasPartitionsWithReplicationFactor(topologyBrokers, topicName, partitionCount, replicationFactor), REPLICATION_RETRY_COUNT);
        });
    }

    private Topic waitForTopicAvailability(String topicName)
    {
        return doRepeatedly(() ->
        {
            final Topics topics = zeebeClient.topics().getTopics().execute();
            return topics.getTopics().stream().filter(topic -> topicName.equals(topic.getName())).findAny();
        })
            .until(Optional::isPresent)
            .get();
    }

    private Broker startBroker(SocketAddress socketAddress, String configFile)
    {
        final File base = brokerBases.get(socketAddress);

        final InputStream config = this.getClass().getClassLoader().getResourceAsStream(configFile);
        final Broker broker = new Broker(config, base.getAbsolutePath(), null);
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
    public void restartBroker(SocketAddress socketAddress)
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
                brokers.put(socketAddress, startBroker(socketAddress, brokerConfigs[i]));
                break;
            }
        }

        waitUntilBrokerIsAddedToTopology(socketAddress);
        waitForInternalSystemAndReplicationFactor();
    }

    private void waitUntilBrokerIsAddedToTopology(SocketAddress socketAddress)
    {
        waitForSpreading(() ->
        {
            doRepeatedly(this::requestBrokers)
                .until(topologyBrokers -> topologyBrokers.stream().anyMatch(topologyBroker -> topologyBroker.getSocketAddress().equals(socketAddress)));
        });
    }

    private List<TopologyBroker> requestBrokers()
    {
        return zeebeClient.requestTopology().execute().getBrokers();
    }

    /**
     * Returns for a given broker the leading partition id's.
     *
     * @param socketAddress
     * @return
     */
    public List<Integer> getBrokersLeadingPartitions(SocketAddress socketAddress)
    {
        return zeebeClient.requestTopology()
                          .execute()
                          .getBrokers()
                          .stream()
                          .filter(broker -> broker.getSocketAddress().equals(socketAddress))
                          .flatMap(broker -> broker.getPartitions().stream())
                          .filter(PartitionInfoImpl::isLeader)
                          .map(PartitionInfoImpl::getPartitionId)
                          .collect(Collectors.toList());
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
                          .map(BrokerInfoImpl::getSocketAddress)
                          .collect(Collectors.toList());

    }

    public SocketAddress[] getOtherBrokers(SocketAddress address)
    {
        final List<SocketAddress> collect = brokers.keySet()
            .stream()
            .filter((s) -> !s.equals(address))
            .collect(Collectors.toList());
        return collect.toArray(new SocketAddress[collect.size()]);
    }

    /**
     * Returns the count of partition leaders for a given topic.
     *
     * @param topic
     * @return
     */
    public long getPartitionLeaderCountForTopic(String topic)
    {

        return zeebeClient.requestTopology()
                          .execute()
                          .getBrokers()
                          .stream()
                          .flatMap(broker -> broker.getPartitions().stream())
                          .filter(p -> p.getTopicName().equals(topic) && p.isLeader())
                          .count();
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

        final Broker removedBroker = brokers.remove(socketAddress);
        assertThat(removedBroker).withFailMessage("Unable to find broker to remove %s", socketAddress).isNotNull();
        removedBroker.close();

        waitForNewLeaderOfPartitions(brokersLeadingPartitions, socketAddress);
        waitUntilBrokerIsRemovedFromTopology(socketAddress);
    }

    private void waitUntilBrokerIsRemovedFromTopology(SocketAddress socketAddress)
    {
        waitForSpreading(() ->
        {
            doRepeatedly(this::requestBrokers)
                .until(topologyBrokers -> topologyBrokers.stream().noneMatch(topologyBroker -> topologyBroker.getSocketAddress().equals(socketAddress)));
        });
    }

    private void waitForNewLeaderOfPartitions(List<Integer> partitions, SocketAddress oldLeader)
    {
        waitForSpreading(() ->
        {
            doRepeatedly(this::requestBrokers)
                .until(topologyBrokers ->
                    topologyBrokers != null && topologyBrokers.stream()
                        .filter(broker -> !broker.getSocketAddress().equals(oldLeader))
                        .flatMap(broker -> broker.getPartitions().stream())
                        .filter(PartitionInfoImpl::isLeader)
                        .map(PartitionInfoImpl::getPartitionId)
                        .collect(Collectors.toSet())
                        .containsAll(partitions));
        });
    }

    public void checkTopology(Predicate<TopologyResponse> topologyPredicate)
    {
        final AtomicBoolean predicate = new AtomicBoolean();
        waitForSpreading(() ->
        {
            final TopologyResponse topologyResponse = zeebeClient.requestTopology().execute();
            predicate.compareAndSet(false, topologyPredicate.test(topologyResponse));
        });
        assertThat(predicate).isTrue();
    }

    private void waitForSpreading(Runnable r)
    {
        for (int i = 0; i < spreadCount; i++)
        {
            r.run();

            // retry to make sure
        }
    }

    private <V> V waitForSpreading(Callable<V> callable)
    {
        V value = null;
        for (int i = 0; i < spreadCount; i++)
        {
            try
            {
                value = callable.call();
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }

            // retry to make sure
        }
        return value;
    }

}
