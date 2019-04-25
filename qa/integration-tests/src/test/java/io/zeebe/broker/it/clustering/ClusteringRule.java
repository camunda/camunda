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

import static io.zeebe.broker.Broker.LOG;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.ATOMIX_JOIN_SERVICE;
import static io.zeebe.broker.test.EmbeddedBrokerConfigurator.DEBUG_EXPORTER;
import static io.zeebe.broker.test.EmbeddedBrokerConfigurator.DISABLE_EMBEDDED_GATEWAY;
import static io.zeebe.broker.test.EmbeddedBrokerConfigurator.TEST_RECORDER;
import static io.zeebe.broker.test.EmbeddedBrokerConfigurator.setCluster;
import static io.zeebe.broker.test.EmbeddedBrokerConfigurator.setInitialContactPoints;
import static io.zeebe.broker.test.EmbeddedBrokerRule.assignSocketAddresses;
import static io.zeebe.protocol.Protocol.START_PARTITION_ID;
import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.test.util.TestUtil.waitUntil;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.utils.net.Address;
import io.zeebe.broker.Broker;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.BrokerInfo;
import io.zeebe.client.api.commands.PartitionInfo;
import io.zeebe.client.api.commands.Topology;
import io.zeebe.gateway.Gateway;
import io.zeebe.gateway.impl.broker.request.BrokerCreateWorkflowInstanceRequest;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.gateway.impl.configuration.ClusterCfg;
import io.zeebe.gateway.impl.configuration.GatewayCfg;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.impl.util.SocketUtil;
import io.zeebe.util.FileUtil;
import io.zeebe.util.sched.clock.ControlledActorClock;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

public class ClusteringRule extends ExternalResource {

  public static final int TOPOLOGY_RETRIES = 250;
  private static final AtomicLong CLUSTER_COUNT = new AtomicLong(0);
  private static final boolean ENABLE_DEBUG_EXPORTER = false;

  protected final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  protected final AutoCloseableRule closables = new AutoCloseableRule();

  // configuration
  private final int partitionCount;
  private final int replicationFactor;
  private final int clusterSize;
  private final Consumer<BrokerCfg> configurator;
  private final Map<Integer, Broker> brokers;
  private final Map<Integer, BrokerCfg> brokerCfgs;
  private final Map<Integer, File> brokerBases;
  private final List<Integer> partitionIds;
  private final String clusterName;
  private final ControlledActorClock controlledClock = new ControlledActorClock();
  // cluster
  private ZeebeClient client;
  private Gateway gateway;
  private AtomixCluster atomixCluster;

  public ClusteringRule() {
    this(3);
  }

  public ClusteringRule(int clusterSize) {
    this(clusterSize, clusterSize, clusterSize);
  }

  public ClusteringRule(
      final int partitionCount, final int replicationFactor, final int clusterSize) {
    this(partitionCount, replicationFactor, clusterSize, cfg -> {});
  }

  public ClusteringRule(
      final int partitionCount,
      final int replicationFactor,
      final int clusterSize,
      final Consumer<BrokerCfg> configurator) {
    this.partitionCount = partitionCount;
    this.replicationFactor = replicationFactor;
    this.clusterSize = clusterSize;
    this.configurator = configurator;

    brokers = new HashMap<>();
    brokerCfgs = new HashMap<>();
    brokerBases = new HashMap<>();
    this.partitionIds =
        IntStream.range(START_PARTITION_ID, START_PARTITION_ID + partitionCount)
            .boxed()
            .collect(Collectors.toList());

    clusterName = "zeebe-cluster-" + CLUSTER_COUNT.getAndIncrement();
  }

  public int getPartitionCount() {
    return partitionCount;
  }

  public int getReplicationFactor() {
    return replicationFactor;
  }

  public int getClusterSize() {
    return clusterSize;
  }

  @Override
  public Statement apply(final Statement base, final Description description) {
    Statement statement = recordingExporterTestWatcher.apply(base, description);
    statement = closables.apply(statement, description);
    return super.apply(statement, description);
  }

  @Override
  protected void before() throws IOException {
    // create brokers
    for (int nodeId = 0; nodeId < clusterSize; nodeId++) {
      getBroker(nodeId);
    }

    // create gateway
    gateway = createGateway();
    gateway.start();

    // create client
    client = createClient();

    try {
      waitUntilBrokersStarted();
      waitForPartitionReplicationFactor();
      LOG.info("Full replication factor");
      waitUntilBrokersInTopology();
      LOG.info("All brokers in topology {}", getTopologyFromClient());

    } catch (Error e) {
      // If the previous waits timeouts, the brokers are not closed automatically.
      closables.after();
      throw e;
    }
  }

  public Broker getBroker(final int nodeId) {
    return brokers.computeIfAbsent(nodeId, this::createBroker);
  }

  private void waitUntilBrokersStarted() {
    // A hack to see if Atomix cluster has started
    brokers.forEach(
        (i, b) -> {
          b.getBrokerContext()
              .getServiceContainer()
              .createService(ServiceName.newServiceName("test", Void.class), () -> null)
              .dependency(ATOMIX_JOIN_SERVICE)
              .install()
              .join();
          b.getBrokerContext()
              .getServiceContainer()
              .removeService(ServiceName.newServiceName("test", Void.class));
        });
  }

  private Broker createBroker(int nodeId) {
    final File brokerBase = getBrokerBase(nodeId);
    final BrokerCfg brokerCfg = getBrokerCfg(nodeId);
    final Broker broker = new Broker(brokerCfg, brokerBase.getAbsolutePath(), controlledClock);
    closables.manage(broker);
    return broker;
  }

  private BrokerCfg getBrokerCfg(int nodeId) {
    return brokerCfgs.computeIfAbsent(nodeId, this::createBrokerCfg);
  }

  private BrokerCfg createBrokerCfg(int nodeId) {
    final BrokerCfg brokerCfg = new BrokerCfg();

    // build-in exporters
    if (ENABLE_DEBUG_EXPORTER) {
      DEBUG_EXPORTER.accept(brokerCfg);
    }
    TEST_RECORDER.accept(brokerCfg);

    // disable embedded gateway
    DISABLE_EMBEDDED_GATEWAY.accept(brokerCfg);

    // configure cluster
    setCluster(nodeId, partitionCount, replicationFactor, clusterSize, clusterName)
        .accept(brokerCfg);
    if (nodeId > 0) {
      // all nodes have to join the same broker
      // https://github.com/zeebe-io/zeebe/issues/2012

      setInitialContactPoints(getBrokerCfg(0).getNetwork().getAtomix().toSocketAddress().toString())
          .accept(brokerCfg);
    }

    // custom configurators
    configurator.accept(brokerCfg);

    // set random port numbers
    assignSocketAddresses(brokerCfg);

    return brokerCfg;
  }

  private File getBrokerBase(int nodeId) {
    return brokerBases.computeIfAbsent(nodeId, this::createBrokerBase);
  }

  private File createBrokerBase(int nodeId) {
    final File base = Files.newTemporaryFolder();
    closables.manage(() -> FileUtil.deleteFolder(base.getAbsolutePath()));
    return base;
  }

  private Gateway createGateway() {
    final String contactPoint =
        getBrokerCfg(0).getNetwork().getAtomix().toSocketAddress().toString();

    final GatewayCfg gatewayCfg = new GatewayCfg();
    gatewayCfg.getCluster().setContactPoint(contactPoint).setClusterName(clusterName);
    gatewayCfg.getNetwork().setPort(SocketUtil.getNextAddress().port());
    gatewayCfg.getCluster().setPort(SocketUtil.getNextAddress().port());
    gatewayCfg.init();

    final ClusterCfg clusterCfg = gatewayCfg.getCluster();

    // copied from StandaloneGateway
    atomixCluster =
        AtomixCluster.builder()
            .withMemberId(clusterCfg.getMemberId())
            .withAddress(Address.from(clusterCfg.getHost(), clusterCfg.getPort()))
            .withClusterId(clusterCfg.getClusterName())
            .withMembershipProvider(
                BootstrapDiscoveryProvider.builder()
                    .withNodes(Address.from(clusterCfg.getContactPoint()))
                    .build())
            .build();

    atomixCluster.start().join();

    final Gateway gateway = new Gateway(gatewayCfg, atomixCluster);
    closables.manage(gateway::stop);
    closables.manage(atomixCluster::stop);
    return gateway;
  }

  private ZeebeClient createClient() {
    final String contactPoint = gateway.getGatewayCfg().getNetwork().toSocketAddress().toString();
    final ZeebeClient client =
        ZeebeClient.newClientBuilder().brokerContactPoint(contactPoint).build();
    closables.manage(client);
    return client;
  }

  @Override
  protected void after() {
    brokerBases.clear();
    brokerCfgs.clear();
    brokers.clear();
  }

  private void waitUntilBrokersInTopology() {

    final Set<SocketAddress> addresses =
        brokers.values().stream()
            .map(Broker::getConfig)
            .map(b -> b.getNetwork().getClient().toSocketAddress())
            .collect(Collectors.toSet());

    waitForTopology(
        topology ->
            topology.stream()
                .map(b -> new SocketAddress(b.getHost(), b.getPort()))
                .collect(Collectors.toSet())
                .containsAll(addresses));
  }

  public Topology getTopologyFromClient() {
    return doRepeatedly(
            () -> {
              try {
                return client.newTopologyRequest().send().join();
              } catch (Exception e) {
                return null;
              }
            })
        .until(Objects::nonNull);
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
                  client.newTopologyRequest().send().join().getBrokers();
              return extractPartitionLeader(brokers, partition);
            })
        .until(Optional::isPresent)
        .get();
  }

  private Optional<BrokerInfo> extractPartitionLeader(
      final List<BrokerInfo> brokers, final int partition) {
    return brokers.stream()
        .filter(
            b ->
                b.getPartitions().stream()
                    .anyMatch(p -> p.getPartitionId() == partition && p.isLeader()))
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

    brokers.stream()
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

  /**
   * Restarts broker, if the broker is still running it will be closed before.
   *
   * <p>Returns to the user if the broker is back in the cluster.
   */
  public void restartBroker(final int nodeId) {
    stopBroker(nodeId);
    final Broker broker = getBroker(nodeId);
    final SocketAddress clientApi = broker.getConfig().getNetwork().getClient().toSocketAddress();
    waitUntilBrokerIsAddedToTopology(clientApi);
    waitForPartitionReplicationFactor();
  }

  private void waitUntilBrokerIsAddedToTopology(final SocketAddress socketAddress) {
    waitForTopology(
        topology ->
            topology.stream()
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
    return client.newTopologyRequest().send().join().getBrokers().stream()
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
    return client.newTopologyRequest().send().join().getBrokers().stream()
        .map(b -> new SocketAddress(b.getHost(), b.getPort()))
        .collect(Collectors.toList());
  }

  public Collection<Broker> getBrokers() {
    return brokers.values();
  }

  public SocketAddress[] getOtherBrokers(final String address) {
    return getOtherBrokers(SocketAddress.from(address));
  }

  public SocketAddress[] getOtherBrokers(final SocketAddress address) {
    return getBrokers().stream()
        .map(b -> b.getConfig().getNetwork().getClient().toSocketAddress())
        .filter(a -> !address.equals(a))
        .toArray(SocketAddress[]::new);
  }

  public SocketAddress[] getOtherBrokers(final int nodeId) {
    final SocketAddress filter = getBrokerCfg(nodeId).getNetwork().getClient().toSocketAddress();
    return getOtherBrokers(filter);
  }

  /**
   * Returns the count of partition leaders
   *
   * @return
   */
  public long getPartitionLeaderCount() {
    return client.newTopologyRequest().send().join().getBrokers().stream()
        .flatMap(broker -> broker.getPartitions().stream())
        .filter(p -> p.isLeader())
        .count();
  }

  public void stopBroker(final int nodeId) {
    final Broker broker = brokers.remove(nodeId);
    if (broker != null) {
      final SocketAddress socketAddress =
          broker.getConfig().getNetwork().getClient().toSocketAddress();
      final List<Integer> brokersLeadingPartitions = getBrokersLeadingPartitions(socketAddress);
      broker.close();

      waitUntilBrokerIsRemovedFromTopology(socketAddress);
      waitForNewLeaderOfPartitions(brokersLeadingPartitions, socketAddress);
    }
  }

  private void waitUntilBrokerIsRemovedFromTopology(final SocketAddress socketAddress) {
    waitForTopology(
        topology ->
            topology.stream()
                .noneMatch(
                    b ->
                        b.getHost().equals(socketAddress.host())
                            && b.getPort() == socketAddress.port()));
  }

  private void waitForNewLeaderOfPartitions(
      final List<Integer> partitions, final SocketAddress oldLeader) {
    waitForTopology(
        topology ->
            topology.stream()
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
        () -> topologyPredicate.apply(getTopologyFromClient().getBrokers()),
        TOPOLOGY_RETRIES,
        "Failed to wait for topology %s",
        getTopologyFromClient());
  }

  public long createWorkflowInstanceOnPartition(int partitionId, String bpmnProcessId) {
    final BrokerCreateWorkflowInstanceRequest request =
        new BrokerCreateWorkflowInstanceRequest().setBpmnProcessId(bpmnProcessId);

    request.setPartitionId(partitionId);

    final BrokerResponse<WorkflowInstanceCreationRecord> response =
        gateway.getBrokerClient().sendRequest(request).join();

    if (response.isResponse()) {
      return response.getResponse().getWorkflowInstanceKey();
    } else {
      throw new RuntimeException(
          "Failed to create workflow instance for bpmn process id "
              + bpmnProcessId
              + " on partition with id "
              + partitionId
              + ": "
              + response);
    }
  }

  public AtomixCluster getAtomixCluster() {
    return atomixCluster;
  }

  public SocketAddress getGatewayAddress() {
    return gateway.getGatewayCfg().getNetwork().toSocketAddress();
  }

  public ZeebeClient getClient() {
    return client;
  }

  public ControlledActorClock getClock() {
    return controlledClock;
  }

  public List<Integer> getPartitionIds() {
    return partitionIds;
  }

  public List<Broker> getOtherBrokerObjects(int leaderNodeId) {
    return brokers.keySet().stream()
        .filter(id -> id != leaderNodeId)
        .map(brokers::get)
        .collect(Collectors.toList());
  }
}
