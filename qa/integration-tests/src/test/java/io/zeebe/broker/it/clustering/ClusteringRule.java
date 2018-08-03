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

import io.zeebe.broker.Broker;
import io.zeebe.broker.client.ZeebeClient;
import io.zeebe.broker.client.api.commands.BrokerInfo;
import io.zeebe.broker.client.api.commands.PartitionInfo;
import io.zeebe.broker.client.api.commands.Topic;
import io.zeebe.broker.client.api.commands.Topics;
import io.zeebe.broker.client.api.events.TopicEvent;
import io.zeebe.broker.client.api.events.TopicState;
import io.zeebe.broker.client.impl.ZeebeClientImpl;
import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.util.TopologyClient;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.protocol.Protocol;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.FileUtil;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.assertj.core.util.Files;
import org.junit.rules.ExternalResource;

public class ClusteringRule extends ExternalResource {

  public static final int DEFAULT_REPLICATION_FACTOR = 1;
  public static final int SYSTEM_TOPIC_REPLICATION_FACTOR = 3;

  public static final String BROKER_1_TOML = "zeebe.cluster.1.cfg.toml";
  public static final SocketAddress BROKER_1_CLIENT_ADDRESS = new SocketAddress("0.0.0.0", 26501);

  public static final String BROKER_2_TOML = "zeebe.cluster.2.cfg.toml";
  public static final SocketAddress BROKER_2_CLIENT_ADDRESS = new SocketAddress("0.0.0.0", 26511);

  public static final String BROKER_3_TOML = "zeebe.cluster.3.cfg.toml";
  public static final SocketAddress BROKER_3_CLIENT_ADDRESS = new SocketAddress("0.0.0.0", 26521);

  public static final String BROKER_4_TOML = "zeebe.cluster.4.cfg.toml";
  public static final SocketAddress BROKER_4_CLIENT_ADDRESS = new SocketAddress("0.0.0.0", 26531);

  private SocketAddress[] brokerAddresses =
      new SocketAddress[] {
        BROKER_1_CLIENT_ADDRESS, BROKER_2_CLIENT_ADDRESS, BROKER_3_CLIENT_ADDRESS
      };

  private String[] brokerConfigs = new String[] {BROKER_1_TOML, BROKER_2_TOML, BROKER_3_TOML};

  // rules
  private final AutoCloseableRule autoCloseableRule;
  private final ClientRule clientRule;

  // internal
  private ZeebeClient zeebeClient;
  private TopologyClient topologyClient;
  protected final Map<SocketAddress, Broker> brokers = new HashMap<>();
  protected final Map<SocketAddress, File> brokerBases = new HashMap<>();

  public ClusteringRule(
      final AutoCloseableRule autoCloseableRule,
      final ClientRule clientRule,
      final SocketAddress[] brokerAddresses,
      final String[] brokerConfigs) {
    this(autoCloseableRule, clientRule);
    this.brokerAddresses = brokerAddresses;
    this.brokerConfigs = brokerConfigs;
  }

  public ClusteringRule(final AutoCloseableRule autoCloseableRule, final ClientRule clientRule) {
    this.autoCloseableRule = autoCloseableRule;
    this.clientRule = clientRule;
  }

  public BrokerCfg getBrokerConfig(final SocketAddress address) {
    return this.brokers.get(address).getConfig();
  }

  @Override
  protected void before() {
    zeebeClient = clientRule.getClient();
    topologyClient = new TopologyClient((ZeebeClientImpl) zeebeClient);

    for (int i = 0; i < brokerConfigs.length; i++) {
      final File brokerBase = Files.newTemporaryFolder();
      final SocketAddress brokerAddress = brokerAddresses[i];
      brokerBases.put(brokerAddress, brokerBase);
      autoCloseableRule.manage(() -> FileUtil.deleteFolder(brokerBase.getAbsolutePath()));
      brokers.put(brokerAddress, startBroker(brokerAddress, brokerConfigs[i]));
    }

    waitForInternalSystemAndReplicationFactor();

    waitUntilBrokersInTopology(brokers.keySet());
  }

  private void waitUntilBrokersInTopology(final Set<SocketAddress> addresses) {
    waitForTopology(
        topology ->
            topology
                .stream()
                .map(b -> new SocketAddress(b.getHost(), b.getPort()))
                .collect(Collectors.toSet())
                .containsAll(addresses));
  }

  private void waitForInternalSystemAndReplicationFactor() {
    waitForTopicPartitionReplicationFactor("internal-system", 1, SYSTEM_TOPIC_REPLICATION_FACTOR);
  }

  /**
   * Returns the current leader for the given partition.
   *
   * @param partition
   * @return
   */
  public BrokerInfo getLeaderForPartition(final int partition) {
    return doRepeatedly(
            () -> {
              final List<BrokerInfo> brokers =
                  zeebeClient.newTopologyRequest().send().join().getBrokers();
              return extractPartitionLeader(brokers, partition);
            })
        .until(Optional::isPresent)
        .get();
  }

  public SocketAddress getLeaderAddressForPartition(final int partition) {
    final BrokerInfo info = getLeaderForPartition(partition);
    return new SocketAddress(info.getHost(), info.getPort());
  }

  public BrokerInfo getFollowerForPartition(final int partitionId) {
    return doRepeatedly(
            () -> {
              final List<BrokerInfo> brokers =
                  zeebeClient.newTopologyRequest().send().join().getBrokers();
              return extractPartitionFollower(brokers, partitionId);
            })
        .until(Optional::isPresent)
        .orElse(null);
  }

  public SocketAddress getFollowerAddressForPartition(final int partition) {
    final BrokerInfo info = getFollowerForPartition(partition);
    return new SocketAddress(info.getHost(), info.getPort());
  }

  /** @return a node which is not leader of any partition, or null if none exist */
  public SocketAddress getFollowerOnly() {
    for (final SocketAddress brokerAddress : brokerAddresses) {
      if (getBrokersLeadingPartitions(brokerAddress).isEmpty()) {
        return brokerAddress;
      }
    }

    return null;
  }

  private Optional<BrokerInfo> extractPartitionLeader(
      final List<BrokerInfo> brokers, final int partition) {
    return brokers
        .stream()
        .filter(
            b ->
                b.getPartitions()
                    .stream()
                    .anyMatch(p -> p.getPartitionId() == partition && p.isLeader()))
        .findFirst();
  }

  private Optional<BrokerInfo> extractPartitionFollower(
      final List<BrokerInfo> brokers, final int partition) {
    return brokers
        .stream()
        .filter(
            b ->
                b.getPartitions()
                    .stream()
                    .anyMatch(p -> p.getPartitionId() == partition && !p.isLeader()))
        .findFirst();
  }

  /**
   * Creates a topic with the given partition count in the cluster.
   *
   * <p>This method returns to the user, if the topic and the partitions are created and the
   * replication factor was reached for each partition. Besides that the topic request needs to be
   * return the created topic.
   *
   * <p>The replication factor is per default the number of current brokers in the cluster, see
   * {@link #DEFAULT_REPLICATION_FACTOR}.
   *
   * @param topicName the name of the topic to create
   * @param partitionCount to number of partitions for the new topic
   * @return the created topic
   */
  public Topic createTopic(final String topicName, final int partitionCount) {
    return createTopic(topicName, partitionCount, DEFAULT_REPLICATION_FACTOR);
  }

  public Topic createTopic(
      final String topicName, final int partitionCount, final int replicationFactor) {
    final TopicEvent topicEvent =
        zeebeClient
            .newCreateTopicCommand()
            .name(topicName)
            .partitions(partitionCount)
            .replicationFactor(replicationFactor)
            .send()
            .join();

    assertThat(topicEvent.getState()).isEqualTo(TopicState.CREATING);

    waitForTopicPartitionReplicationFactor(topicName, partitionCount, replicationFactor);

    return waitForTopicAvailability(topicName);
  }

  private boolean hasPartitionsWithReplicationFactor(
      final List<BrokerInfo> brokers,
      final String topicName,
      final int partitionCount,
      final int replicationFactor) {
    final AtomicLong leaders = new AtomicLong();
    final AtomicLong followers = new AtomicLong();

    brokers
        .stream()
        .flatMap(b -> b.getPartitions().stream())
        .filter(p -> p.getTopicName().equals(topicName))
        .forEach(
            p -> {
              if (p.isLeader()) {
                leaders.getAndIncrement();
              } else {
                followers.getAndIncrement();
              }
            });

    return leaders.get() >= partitionCount
        && followers.get() >= partitionCount * (replicationFactor - 1);
  }

  public void waitForTopicPartitionReplicationFactor(
      final String topicName, final int partitionCount, final int replicationFactor) {
    waitForTopology(
        topology ->
            hasPartitionsWithReplicationFactor(
                topology, topicName, partitionCount, replicationFactor));
  }

  public Topic getInternalSystemTopic() {
    return waitForTopicAvailability(Protocol.SYSTEM_TOPIC);
  }

  private Topic waitForTopicAvailability(final String topicName) {
    return doRepeatedly(
            () -> {
              final Topics topics = zeebeClient.newTopicsRequest().send().join();
              return topics
                  .getTopics()
                  .stream()
                  .filter(topic -> topicName.equals(topic.getName()))
                  .findAny();
            })
        .until(Optional::isPresent)
        .get();
  }

  private Broker startBroker(final SocketAddress socketAddress, final String configFile) {
    final File base = brokerBases.get(socketAddress);

    final InputStream config = this.getClass().getClassLoader().getResourceAsStream(configFile);
    final Broker broker = new Broker(config, base.getAbsolutePath(), null);
    autoCloseableRule.manage(broker);

    return broker;
  }

  /**
   * Restarts broker, if the broker is still running it will be closed before.
   *
   * <p>Returns to the user if the broker is back in the cluster.
   *
   * @param socketAddress
   * @return
   */
  public void restartBroker(final SocketAddress socketAddress) {
    final Broker broker = brokers.get(socketAddress);
    if (broker != null) {
      stopBroker(socketAddress);
    }

    for (int i = 0; i < brokerAddresses.length; i++) {
      if (brokerAddresses[i].equals(socketAddress)) {
        brokers.put(socketAddress, startBroker(socketAddress, brokerConfigs[i]));
        break;
      }
    }

    waitUntilBrokerIsAddedToTopology(socketAddress);
    waitForInternalSystemAndReplicationFactor();
  }

  private void waitUntilBrokerIsAddedToTopology(final SocketAddress socketAddress) {
    waitForTopology(
        topology ->
            topology
                .stream()
                .anyMatch(
                    b ->
                        b.getHost().equals(socketAddress.host())
                            && b.getPort() == socketAddress.port()));
  }

  /**
   * Returns for a given broker the leading partition id's.
   *
   * @param socketAddress
   * @return
   */
  public List<Integer> getBrokersLeadingPartitions(final SocketAddress socketAddress) {
    return zeebeClient
        .newTopologyRequest()
        .send()
        .join()
        .getBrokers()
        .stream()
        .filter(
            b -> b.getHost().equals(socketAddress.host()) && b.getPort() == socketAddress.port())
        .flatMap(broker -> broker.getPartitions().stream())
        .filter(PartitionInfo::isLeader)
        .map(PartitionInfo::getPartitionId)
        .collect(Collectors.toList());
  }

  /**
   * Returns the list of available brokers in a cluster.
   *
   * @return
   */
  public List<SocketAddress> getBrokersInCluster() {
    return zeebeClient
        .newTopologyRequest()
        .send()
        .join()
        .getBrokers()
        .stream()
        .map(b -> new SocketAddress(b.getHost(), b.getPort()))
        .collect(Collectors.toList());
  }

  public SocketAddress[] getOtherBrokers(final String address) {
    return getOtherBrokers(SocketAddress.from(address));
  }

  public SocketAddress[] getOtherBrokers(final SocketAddress address) {
    final List<SocketAddress> collect =
        brokers.keySet().stream().filter((s) -> !s.equals(address)).collect(Collectors.toList());
    return collect.toArray(new SocketAddress[collect.size()]);
  }

  /**
   * Returns the count of partition leaders for a given topic.
   *
   * @param topic
   * @return
   */
  public long getPartitionLeaderCountForTopic(final String topic) {

    return zeebeClient
        .newTopologyRequest()
        .send()
        .join()
        .getBrokers()
        .stream()
        .flatMap(broker -> broker.getPartitions().stream())
        .filter(p -> p.getTopicName().equals(topic) && p.isLeader())
        .count();
  }

  /**
   * Stops broker with the given socket address.
   *
   * <p>Returns to the user if the broker was stopped and new leader for the partitions are chosen.
   *
   * @param socketAddress
   */
  public void stopBroker(final String address) {
    stopBroker(SocketAddress.from(address));
  }

  public void stopBroker(final SocketAddress socketAddress) {
    final List<Integer> brokersLeadingPartitions = getBrokersLeadingPartitions(socketAddress);

    final Broker removedBroker = brokers.remove(socketAddress);
    assertThat(removedBroker)
        .withFailMessage("Unable to find broker to remove %s", socketAddress)
        .isNotNull();
    removedBroker.close();

    waitForNewLeaderOfPartitions(brokersLeadingPartitions, socketAddress);
    waitUntilBrokerIsRemovedFromTopology(socketAddress);
  }

  private void waitUntilBrokerIsRemovedFromTopology(final SocketAddress socketAddress) {
    waitForTopology(
        topology ->
            topology
                .stream()
                .noneMatch(
                    b ->
                        b.getHost().equals(socketAddress.host())
                            && b.getPort() == socketAddress.port()));
  }

  private void waitForNewLeaderOfPartitions(
      final List<Integer> partitions, final SocketAddress oldLeader) {
    waitForTopology(
        topology ->
            topology
                .stream()
                .filter(
                    b -> !(b.getHost().equals(oldLeader.host()) && b.getPort() == oldLeader.port()))
                .flatMap(broker -> broker.getPartitions().stream())
                .filter(PartitionInfo::isLeader)
                .map(PartitionInfo::getPartitionId)
                .collect(Collectors.toSet())
                .containsAll(partitions));
  }

  public void waitForTopology(final Function<List<BrokerInfo>, Boolean> topologyPredicate) {
    waitUntil(
        () ->
            brokers
                .keySet()
                .stream()
                .allMatch(
                    socketAddress -> {
                      final List<BrokerInfo> topology =
                          topologyClient.requestTopologyFromBroker(socketAddress);
                      // printTopology(topology);
                      return topologyPredicate.apply(topology);
                    }),
        250);
  }
}
