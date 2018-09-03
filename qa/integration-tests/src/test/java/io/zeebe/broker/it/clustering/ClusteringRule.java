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

import static io.zeebe.protocol.Protocol.DEFAULT_TOPIC;
import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.test.util.TestUtil.waitUntil;

import io.zeebe.broker.Broker;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.TopologyClient;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.TomlConfigurationReader;
import io.zeebe.broker.system.configuration.TopicCfg;
import io.zeebe.gateway.ZeebeClient;
import io.zeebe.gateway.api.commands.BrokerInfo;
import io.zeebe.gateway.api.commands.PartitionInfo;
import io.zeebe.gateway.api.commands.Topic;
import io.zeebe.gateway.api.commands.Topics;
import io.zeebe.gateway.impl.ZeebeClientImpl;
import io.zeebe.protocol.Protocol;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.FileUtil;
import io.zeebe.util.ZbLogger;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.assertj.core.util.Files;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;

public class ClusteringRule extends ExternalResource {

  public static final Logger LOG = new ZbLogger(ClusteringRule.class);

  public static final int DEFAULT_REPLICATION_FACTOR = 1;
  public static final int SYSTEM_TOPIC_REPLICATION_FACTOR = 3;

  public static final String BROKER_1_TOML = "zeebe.cluster.1.cfg.toml";
  public static final String BROKER_2_TOML = "zeebe.cluster.2.cfg.toml";
  public static final String BROKER_3_TOML = "zeebe.cluster.3.cfg.toml";

  // internal
  private ZeebeClient zeebeClient;
  private TopologyClient topologyClient;

  private List<AutoCloseable> closeables = new ArrayList<>();

  private final String[] brokerConfigFiles;
  private final BrokerCfg[] brokerCfgs;
  private final Broker[] brokers;
  private final File[] brokerBases;

  public ClusteringRule() {
    this(new String[] {BROKER_1_TOML, BROKER_2_TOML, BROKER_3_TOML});
  }

  public ClusteringRule(final String[] brokerConfigFiles) {
    this.brokerConfigFiles = brokerConfigFiles;
    this.brokerCfgs = new BrokerCfg[brokerConfigFiles.length];
    this.brokers = new Broker[brokerConfigFiles.length];
    this.brokerBases = new File[brokerConfigFiles.length];
  }

  @Override
  protected void before() {
    for (int i = 0; i < brokerConfigFiles.length; i++) {
      startBroker(i);
    }

    zeebeClient =
        ZeebeClient.newClientBuilder()
            .brokerContactPoint(brokerCfgs[0].getNetwork().getClient().toSocketAddress().toString())
            .build();

    topologyClient = new TopologyClient((ZeebeClientImpl) zeebeClient);

    waitForInternalSystemAndReplicationFactor();

    final Broker leaderBroker = brokers[0];
    final BrokerCfg brokerConfiguration = leaderBroker.getBrokerContext().getBrokerConfiguration();
    final TopicCfg defaultTopicCfg = brokerConfiguration.getTopics().get(0);
    final int partitions = defaultTopicCfg.getPartitions();
    final int replicationFactor = defaultTopicCfg.getReplicationFactor();

    waitForTopicPartitionReplicationFactor(DEFAULT_TOPIC, partitions, replicationFactor);

    waitUntilBrokersInTopology(brokers);
  }

  @Override
  protected void after() {
    final int size = closeables.size();
    final RuntimeException failed = new RuntimeException("Failed to close everything after test");
    for (int i = size - 1; i >= 0; i--) {
      try {
        closeables.remove(i).close();
      } catch (Exception e) {
        failed.addSuppressed(e);
        LOG.error("Failed to close something after the test, postponing and continuing", e);
      }
    }

    if (failed.getSuppressed().length > 0) {
      throw failed;
    }
  }

  private void waitUntilBrokersInTopology(final Broker... brokers) {
    final Set<SocketAddress> addresses =
        Arrays.stream(brokers)
            .map(b -> b.getConfig().getNetwork().getClient().toSocketAddress())
            .collect(Collectors.toSet());

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
  public Broker getFollowerOnly() {
    for (final Broker broker : brokers) {
      if (getBrokersLeadingPartitions(broker.getConfig().getNetwork().getClient().toSocketAddress())
          .isEmpty()) {
        return broker;
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
   * Wait for a topic with the given partition count in the cluster.
   *
   * <p>This method returns to the user, if the topic and the partitions are created and the
   * replication factor was reached for each partition. Besides that the topic request needs to be
   * return the created topic.
   *
   * <p>The replication factor is per default the number of current brokers in the cluster, see
   * {@link #DEFAULT_REPLICATION_FACTOR}.
   *
   * @param partitionCount to number of partitions for the new topic
   * @return the created topic
   */
  public Topic waitForTopic(int partitionCount) {
    return waitForTopic(partitionCount, DEFAULT_REPLICATION_FACTOR);
  }

  public Topic waitForTopic(int partitionCount, int replicationFactor) {
    waitForTopicPartitionReplicationFactor(DEFAULT_TOPIC, partitionCount, replicationFactor);

    return waitForTopicAvailability(DEFAULT_TOPIC);
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

  private void startBroker(final int brokerId) {
    if (brokerCfgs[brokerId] == null) {
      brokerBases[brokerId] = Files.newTemporaryFolder();
      closeables.add(() -> FileUtil.deleteFolder(brokerBases[brokerId].getAbsolutePath()));

      final InputStream config =
          this.getClass().getClassLoader().getResourceAsStream(brokerConfigFiles[brokerId]);
      final BrokerCfg brokerCfg = TomlConfigurationReader.read(config);
      EmbeddedBrokerRule.assignSocketAddresses(brokerCfg);

      if (brokerId > 0) {
        brokerCfg
            .getCluster()
            .setInitialContactPoints(
                new String[] {
                  brokerCfgs[brokerId - 1].getNetwork().getManagement().toSocketAddress().toString()
                });
      }

      brokerCfgs[brokerId] = brokerCfg;
    }

    final Broker broker =
        new Broker(brokerCfgs[brokerId], brokerBases[brokerId].getAbsolutePath(), null);

    brokers[brokerId] = broker;
    closeables.add(broker);
  }

  /**
   * Restarts broker, if the broker is still running it will be closed before.
   *
   * <p>Returns to the user if the broker is back in the cluster.
   */
  public void restartBroker(final int brokerId) {
    final Broker broker = brokers[brokerId];
    if (broker != null) {
      stopBroker(broker);
    }

    startBroker(brokerId);

    waitUntilBrokerIsAddedToTopology(
        brokerCfgs[brokerId].getNetwork().getClient().toSocketAddress());
    waitForInternalSystemAndReplicationFactor();
  }

  public void restartBroker(Broker broker) {
    restartBroker(brokerId(broker));
  }

  private int brokerId(Broker broker) {
    for (int i = 0; i < brokers.length; i++) {
      if (broker.equals(brokers[i])) {
        return i;
      }
    }

    return -1;
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

  public List<Broker> getBrokers() {
    return Arrays.stream(brokers).filter(Objects::nonNull).collect(Collectors.toList());
  }

  public Broker getBroker(final SocketAddress address) {
    for (Broker broker : brokers) {
      if (address.equals(broker.getConfig().getNetwork().getClient().toSocketAddress())) {
        return broker;
      }
    }

    return null;
  }

  public SocketAddress[] getOtherBrokers(final String address) {
    return getOtherBrokers(SocketAddress.from(address));
  }

  public SocketAddress[] getOtherBrokers(final SocketAddress address) {
    return getBrokers()
        .stream()
        .map(b -> b.getConfig().getNetwork().getClient().toSocketAddress())
        .filter(a -> !address.equals(a))
        .toArray(SocketAddress[]::new);
  }

  public SocketAddress[] getOtherBrokers(final int brokerId) {
    return getOtherBrokers(brokerCfgs[brokerId].getNetwork().getClient().toSocketAddress());
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
   */
  public void stopBroker(final String address) {
    final SocketAddress socketAddress = SocketAddress.from(address);

    for (Broker broker : brokers) {
      if (broker.getConfig().getNetwork().getClient().toSocketAddress().equals(socketAddress)) {
        stopBroker(broker);
        break;
      }
    }
  }

  public void stopBroker(final Broker broker) {
    stopBroker(brokerId(broker));
  }

  public void stopBroker(final int brokerId) {
    if (brokerId >= 0) {
      final SocketAddress socketAddress =
          brokerCfgs[brokerId].getNetwork().getClient().toSocketAddress();
      final List<Integer> brokersLeadingPartitions = getBrokersLeadingPartitions(socketAddress);

      final Broker broker = brokers[brokerId];
      brokers[brokerId] = null;

      if (broker != null) {
        broker.close();

        waitForNewLeaderOfPartitions(brokersLeadingPartitions, socketAddress);
        waitUntilBrokerIsRemovedFromTopology(socketAddress);
      }
    }
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
            Arrays.stream(brokers)
                .filter(Objects::nonNull)
                .allMatch(b -> topologyPredicate.apply(requestTopology(b))),
        250);
  }

  public List<BrokerInfo> getTopologyFromBroker(final int nodeId) {
    return requestTopology(brokers[nodeId]);
  }

  private List<BrokerInfo> requestTopology(Broker broker) {
    final BrokerCfg config = broker.getConfig();
    return topologyClient.requestTopologyFromBroker(
        config.getNodeId(), config.getNetwork().getClient().toSocketAddress());
  }

  public SocketAddress getClientAddress() {
    return brokerCfgs[0].getNetwork().getClient().toSocketAddress();
  }
}
