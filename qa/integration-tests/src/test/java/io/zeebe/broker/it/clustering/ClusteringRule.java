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
import static io.zeebe.broker.test.EmbeddedBrokerConfigurator.DISABLE_EMBEDDED_GATEWAY;
import static io.zeebe.broker.test.EmbeddedBrokerConfigurator.TEST_RECORDER;
import static io.zeebe.broker.test.EmbeddedBrokerConfigurator.setCluster;
import static io.zeebe.broker.test.EmbeddedBrokerConfigurator.setInitialContactPoints;
import static io.zeebe.broker.test.EmbeddedBrokerRule.assignSocketAddresses;
import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.test.util.TestUtil.waitUntil;

import io.zeebe.broker.Broker;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.BrokerInfo;
import io.zeebe.client.api.commands.PartitionInfo;
import io.zeebe.client.impl.TopologyImpl;
import io.zeebe.gateway.Gateway;
import io.zeebe.gateway.ResponseMapper;
import io.zeebe.gateway.impl.broker.BrokerClientImpl;
import io.zeebe.gateway.impl.broker.request.BrokerCreateWorkflowInstanceRequest;
import io.zeebe.gateway.impl.broker.request.BrokerRequest;
import io.zeebe.gateway.impl.broker.request.BrokerTopologyRequest;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.gateway.impl.configuration.GatewayCfg;
import io.zeebe.protocol.impl.data.cluster.TopologyResponseDto;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.zeebe.transport.ClientResponse;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.Transports;
import io.zeebe.transport.impl.memory.UnboundedMemoryPool;
import io.zeebe.transport.impl.util.SocketUtil;
import io.zeebe.util.FileUtil;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  private static final boolean ENABLE_DEBUG_EXPORTER = false;

  protected final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  protected final AutoCloseableRule closables = new AutoCloseableRule();

  // configuration
  private final int partitionCount;
  private final int replicationFactor;
  private final int clusterSize;
  private final Consumer<BrokerCfg> configurator;

  // cluster
  private ZeebeClient client;
  private Gateway gateway;
  private final Map<Integer, Broker> brokers;
  private final Map<Integer, BrokerCfg> brokerCfgs;
  private final Map<Integer, File> brokerBases;
  private final List<Integer> partitionIds;

  // internal
  private ClientTransport internalTransport;

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
    this.partitionIds = IntStream.range(0, partitionCount).boxed().collect(Collectors.toList());
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

    // internal transport for requests to specific broker
    internalTransport =
        Transports.newClientTransport("cluster-test-client")
            .messageMaxLength(1024 * 1024)
            .messageMemoryPool(new UnboundedMemoryPool())
            .requestMemoryPool(new UnboundedMemoryPool())
            .scheduler(((BrokerClientImpl) gateway.getBrokerClient()).getScheduler())
            .build();

    waitForPartitionReplicationFactor();
    waitUntilBrokersInTopology();
  }

  private Broker getBroker(final int nodeId) {
    return brokers.computeIfAbsent(nodeId, this::createBroker);
  }

  private Broker createBroker(int nodeId) {
    final File brokerBase = getBrokerBase(nodeId);
    final BrokerCfg brokerCfg = getBrokerCfg(nodeId);
    final Broker broker = new Broker(brokerCfg, brokerBase.getAbsolutePath(), null);
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
    setCluster(nodeId, partitionCount, replicationFactor, clusterSize).accept(brokerCfg);
    if (nodeId > 0) {
      setInitialContactPoints(
              getBrokerCfg(nodeId - 1).getNetwork().getManagement().toSocketAddress().toString())
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
        getBrokerCfg(0).getNetwork().getClient().toSocketAddress().toString();

    final GatewayCfg gatewayCfg = new GatewayCfg();
    gatewayCfg.getCluster().setContactPoint(contactPoint);
    gatewayCfg.getNetwork().setPort(SocketUtil.getNextAddress().port());
    gatewayCfg.init();

    final Gateway gateway = new Gateway(gatewayCfg);
    closables.manage(gateway::stop);
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
        brokers
            .values()
            .stream()
            .map(Broker::getConfig)
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
                  client.newTopologyRequest().send().join().getBrokers();
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
                  client.newTopologyRequest().send().join().getBrokers();
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

  private int nodeId(final Broker broker) {
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
    return client
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
    return client
        .newTopologyRequest()
        .send()
        .join()
        .getBrokers()
        .stream()
        .map(b -> new SocketAddress(b.getHost(), b.getPort()))
        .collect(Collectors.toList());
  }

  public Collection<Broker> getBrokers() {
    return brokers.values();
  }

  public Broker getBroker(final SocketAddress address) {
    for (final Broker broker : brokers.values()) {
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

    return client
        .newTopologyRequest()
        .send()
        .join()
        .getBrokers()
        .stream()
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

      waitForNewLeaderOfPartitions(brokersLeadingPartitions, socketAddress);
      waitUntilBrokerIsRemovedFromTopology(socketAddress);
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
            brokers
                .values()
                .stream()
                .allMatch(
                    b ->
                        topologyPredicate.apply(
                            getTopologyFromBroker(b.getConfig().getCluster().getNodeId()))),
        250);
  }

  public long createWorkflowInstanceOnPartition(int partitionId, String bpmnProcessId) {
    final BrokerCreateWorkflowInstanceRequest request =
        new BrokerCreateWorkflowInstanceRequest().setBpmnProcessId(bpmnProcessId);

    request.setPartitionId(partitionId);

    final BrokerResponse<WorkflowInstanceRecord> response =
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

  public List<BrokerInfo> getTopologyFromBroker(final int nodeId) {
    final BrokerTopologyRequest request = new BrokerTopologyRequest();
    final BrokerResponse<TopologyResponseDto> response = sendRequestToNode(nodeId, request);

    if (response.isResponse()) {
      final TopologyImpl topology =
          new TopologyImpl(ResponseMapper.toTopologyResponse(0, response.getResponse()));
      return topology.getBrokers();
    } else {
      return Collections.emptyList();
    }
  }

  private <T> BrokerResponse<T> sendRequestToNode(int nodeId, BrokerRequest<T> request) {
    final BrokerCfg brokerCfg = getBrokerCfg(nodeId);
    internalTransport.registerEndpointAndAwaitChannel(
        nodeId, brokerCfg.getNetwork().getClient().toSocketAddress());

    request.serializeValue();

    final ClientResponse response =
        internalTransport
            .getOutput()
            .sendRequestWithRetry(() -> nodeId, b -> false, request, Duration.ofSeconds(5))
            .join();

    return request.getResponse(response);
  }

  public SocketAddress getGatewayAddress() {
    return gateway.getGatewayCfg().getNetwork().toSocketAddress();
  }

  public ZeebeClient getClient() {
    return client;
  }
}
