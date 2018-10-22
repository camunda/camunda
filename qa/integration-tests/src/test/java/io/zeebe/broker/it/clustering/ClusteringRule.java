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

import static io.zeebe.broker.test.EmbeddedBrokerConfigurator.DEBUG_EXPORTER;
import static io.zeebe.broker.test.EmbeddedBrokerConfigurator.TEST_RECORDER;
import static io.zeebe.broker.test.EmbeddedBrokerConfigurator.setCluster;
import static io.zeebe.broker.test.EmbeddedBrokerConfigurator.setInitialContactPoints;
import static io.zeebe.broker.test.EmbeddedBrokerRule.assignSocketAddresses;
import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.test.util.TestUtil.waitUntil;

import io.zeebe.broker.Broker;
import io.zeebe.broker.it.util.TopologyClient;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.gateway.ZeebeClient;
import io.zeebe.gateway.api.commands.BrokerInfo;
import io.zeebe.gateway.api.commands.PartitionInfo;
import io.zeebe.gateway.impl.ZeebeClientImpl;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.FileUtil;
import io.zeebe.util.ZbLogger;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.assertj.core.util.Files;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;

public class ClusteringRule extends ExternalResource {

  private static final boolean ENABLE_DEBUG_EXPORTER = false;

  public static final Logger LOG = new ZbLogger(ClusteringRule.class);

  protected final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  // internal
  private ZeebeClient gatewayClient;
  private TopologyClient topologyClient;
  private io.zeebe.client.ZeebeClient grpcClient;

  private final List<AutoCloseable> closeables = new ArrayList<>();

  private final int partitionCount;
  private final int replicationFactor;
  private final int clusterSize;
  private final Consumer<BrokerCfg>[] configurators;

  private final File[] brokerBases;
  private final BrokerCfg[] brokerCfgs;
  private final Broker[] brokers;
  private final List<Integer> partitionIds;

  public ClusteringRule() {
    this(3, 3, 3);
  }

  @SafeVarargs
  public ClusteringRule(
      final int partitionCount,
      final int replicationFactor,
      final int clusterSize,
      final Consumer<BrokerCfg>... configurators) {
    this.partitionCount = partitionCount;
    this.replicationFactor = replicationFactor;
    this.clusterSize = clusterSize;
    this.configurators = configurators;

    brokerBases = new File[clusterSize];
    brokerCfgs = new BrokerCfg[clusterSize];
    brokers = new Broker[clusterSize];
    this.partitionIds = IntStream.range(0, partitionCount).boxed().collect(Collectors.toList());
  }

  @Override
  public Statement apply(final Statement base, final Description description) {
    final Statement statement = recordingExporterTestWatcher.apply(base, description);
    return super.apply(statement, description);
  }

  @Override
  protected void before() {
    RecordingExporter.reset();

    for (int i = 0; i < clusterSize; i++) {
      startBroker(i);
    }

    final BrokerCfg brokerCfg = brokerCfgs[0];
    gatewayClient =
        ZeebeClient.newClientBuilder()
            .brokerContactPoint(brokerCfg.getNetwork().getClient().toSocketAddress().toString())
            .build();

    closeables.add(gatewayClient);
    topologyClient = new TopologyClient(((ZeebeClientImpl) gatewayClient).getTransport());

    grpcClient =
        io.zeebe.client.ZeebeClient.newClientBuilder()
            .brokerContactPoint(brokerCfg.getNetwork().getGateway().toSocketAddress().toString())
            .build();
    closeables.add(grpcClient);

    waitForPartitionReplicationFactor();
    waitUntilBrokersInTopology(brokerCfgs);
  }

  @Override
  protected void after() {
    final int size = closeables.size();
    final RuntimeException failed = new RuntimeException("Failed to close everything after test");
    for (int i = size - 1; i >= 0; i--) {
      try {
        closeables.remove(i).close();
      } catch (final Exception e) {
        failed.addSuppressed(e);
        LOG.error("Failed to close something after the test, postponing and continuing", e);
      }
    }

    for (int i = 0; i < clusterSize; i++) {
      brokerBases[i] = null;
      brokerCfgs[i] = null;
      brokers[i] = null;
    }

    if (failed.getSuppressed().length > 0) {
      throw failed;
    }
  }

  private void waitUntilBrokersInTopology(final BrokerCfg... brokerCfgs) {
    final Set<SocketAddress> addresses =
        Arrays.stream(brokerCfgs)
            .map(b -> b.getNetwork().getClient().toSocketAddress())
            .collect(Collectors.toSet());

    waitForTopology(
        topology ->
            topology
                .stream()
                .map(b -> new SocketAddress(b.getHost(), b.getPort()))
                .collect(Collectors.toSet())
                .containsAll(addresses));
  }

  public List<Integer> getPartitionIds() {
    return partitionIds;
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
                  gatewayClient.newTopologyRequest().send().join().getBrokers();
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
                  gatewayClient.newTopologyRequest().send().join().getBrokers();
              return extractPartitionFollower(brokers, partitionId);
            })
        .until(Optional::isPresent)
        .orElse(null);
  }

  public SocketAddress getFollowerAddressForPartition(final int partition) {
    final BrokerInfo info = getFollowerForPartition(partition);
    return new SocketAddress(info.getHost(), info.getPort());
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

  /** Wait for a partition bootstrap in the cluster. */
  public void waitForPartitionReplicationFactor() {
    waitForTopology(
        topology ->
            hasPartitionsWithReplicationFactor(topology, partitionCount, replicationFactor));
  }

  private boolean hasPartitionsWithReplicationFactor(
      final List<BrokerInfo> brokers, final int partitionCount, final int replicationFactor) {
    final AtomicLong leaders = new AtomicLong();
    final AtomicLong followers = new AtomicLong();

    brokers
        .stream()
        .flatMap(b -> b.getPartitions().stream())
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

  private void startBroker(final int brokerId) {
    final File brokerBase;
    if (brokerBases[brokerId] == null) {
      brokerBase = Files.newTemporaryFolder();
      closeables.add(() -> FileUtil.deleteFolder(brokerBase.getAbsolutePath()));
      brokerBases[brokerId] = brokerBase;
    } else {
      brokerBase = brokerBases[brokerId];
    }

    BrokerCfg brokerCfg = brokerCfgs[brokerId];
    if (brokerCfg == null) {
      brokerCfg = new BrokerCfg();
      brokerCfgs[brokerId] = brokerCfg;

      configureBroker(brokerId, brokerCfg);
    }

    final Broker broker = new Broker(brokerCfg, brokerBase.getAbsolutePath(), null);

    brokers[brokerId] = broker;
    closeables.add(broker);
  }

  private void configureBroker(int brokerId, BrokerCfg brokerCfg) {
    // build-in exporters
    if (ENABLE_DEBUG_EXPORTER) {
      DEBUG_EXPORTER.accept(brokerCfg);
    }
    TEST_RECORDER.accept(brokerCfg);

    // configure cluster
    setCluster(brokerId, partitionCount, replicationFactor, clusterSize).accept(brokerCfg);
    if (brokerId > 0) {
      setInitialContactPoints(
              brokerCfgs[brokerId - 1].getNetwork().getManagement().toSocketAddress().toString())
          .accept(brokerCfg);
    }

    // custom configurators
    for (Consumer<BrokerCfg> configurator : configurators) {
      configurator.accept(brokerCfg);
    }

    // set random port numbers
    assignSocketAddresses(brokerCfg);
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

    final BrokerCfg brokerCfg = brokerCfgs[brokerId];
    waitUntilBrokerIsAddedToTopology(brokerCfg.getNetwork().getClient().toSocketAddress());
    waitForPartitionReplicationFactor();
  }

  private int brokerId(final Broker broker) {
    return broker.getConfig().getCluster().getNodeId();
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
    return gatewayClient
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
    return gatewayClient
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
    for (final Broker broker : brokers) {
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
   * Returns the count of partition leaders
   *
   * @return
   */
  public long getPartitionLeaderCount() {

    return gatewayClient
        .newTopologyRequest()
        .send()
        .join()
        .getBrokers()
        .stream()
        .flatMap(broker -> broker.getPartitions().stream())
        .filter(p -> p.isLeader())
        .count();
  }

  /**
   * Stops broker with the given socket address.
   *
   * <p>Returns to the user if the broker was stopped and new leader for the partitions are chosen.
   */
  public void stopBroker(final String address) {
    final SocketAddress socketAddress = SocketAddress.from(address);

    for (final Broker broker : brokers) {
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

  private List<BrokerInfo> requestTopology(final Broker broker) {
    final BrokerCfg config = broker.getConfig();
    return topologyClient.requestTopologyFromBroker(
        config.getCluster().getNodeId(), config.getNetwork().getClient().toSocketAddress());
  }

  public SocketAddress getClientAddress() {
    return brokerCfgs[0].getNetwork().getClient().toSocketAddress();
  }

  public SocketAddress getGatewayAddress() {
    return brokerCfgs[0].getNetwork().getGateway().toSocketAddress();
  }

  public ZeebeClient getGatewayClient() {
    return gatewayClient;
  }
}
