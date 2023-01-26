/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering;

import static io.camunda.zeebe.broker.Broker.LOG;
import static io.camunda.zeebe.broker.test.EmbeddedBrokerConfigurator.DEBUG_EXPORTER;
import static io.camunda.zeebe.broker.test.EmbeddedBrokerConfigurator.DISABLE_EMBEDDED_GATEWAY;
import static io.camunda.zeebe.broker.test.EmbeddedBrokerConfigurator.TEST_RECORDER;
import static io.camunda.zeebe.broker.test.EmbeddedBrokerConfigurator.setCluster;
import static io.camunda.zeebe.broker.test.EmbeddedBrokerConfigurator.setInitialContactPoints;
import static io.camunda.zeebe.broker.test.EmbeddedBrokerRule.assignSocketAddresses;
import static io.camunda.zeebe.protocol.Protocol.START_PARTITION_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.cluster.messaging.impl.NettyUnicastService;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.utils.Version;
import io.camunda.zeebe.broker.ActorSchedulerConfiguration;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.BrokerClusterConfiguration;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.bootstrap.BrokerContext;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirectorContext;
import io.camunda.zeebe.broker.partitioning.PartitionManagerImpl;
import io.camunda.zeebe.broker.system.SystemContext;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.NetworkCfg;
import io.camunda.zeebe.broker.system.configuration.SocketBindingCfg;
import io.camunda.zeebe.broker.system.management.BrokerAdminService;
import io.camunda.zeebe.broker.system.management.PartitionStatus;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import io.camunda.zeebe.client.api.response.BrokerInfo;
import io.camunda.zeebe.client.api.response.PartitionInfo;
import io.camunda.zeebe.client.api.response.Topology;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.gateway.ActorSchedulerComponent;
import io.camunda.zeebe.gateway.BrokerClientComponent;
import io.camunda.zeebe.gateway.Gateway;
import io.camunda.zeebe.gateway.GatewayClusterConfiguration;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.shared.ActorClockConfiguration;
import io.camunda.zeebe.shared.management.ActorClockService.MutableClock;
import io.camunda.zeebe.snapshots.SnapshotId;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotId;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.util.VersionUtil;
import io.camunda.zeebe.util.exception.UncheckedExecutionException;
import io.netty.util.NetUtil;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.agrona.LangUtil;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusteringRule extends ExternalResource {
  private static final Logger LOGGER = LoggerFactory.getLogger(ClusteringRule.class);
  private static final AtomicLong CLUSTER_COUNT = new AtomicLong(0);
  private static final boolean ENABLE_DEBUG_EXPORTER = false;
  private static final String RAFT_PARTITION_PATH =
      PartitionManagerImpl.GROUP_NAME + "/partitions/1";

  private final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();
  private final ArrayDeque<AutoCloseable> closeables = new ArrayDeque<>();
  private final TemporaryFolder temporaryFolder = new TemporaryFolder();

  // configuration
  private final int partitionCount;
  private final int replicationFactor;
  private final int clusterSize;
  private final Consumer<BrokerCfg> brokerConfigurator;
  private final Consumer<GatewayCfg> gatewayConfigurator;
  private final Consumer<ZeebeClientBuilder> clientConfigurator;
  private final Map<Integer, Broker> brokers;
  private final Map<Integer, BrokerCfg> brokerCfgs;
  private final List<Integer> partitionIds;
  private final String clusterName;
  private final Map<Integer, LogStream> logstreams;

  // cluster
  private ZeebeClient client;
  private Gateway gateway;
  private CountDownLatch partitionLatch;
  private final Map<Integer, Leader> partitionLeader;
  private final Map<Integer, SpringBrokerBridge> springBrokerBridge;
  private final Map<Integer, SystemContext> systemContexts;
  private final ActorClockConfiguration actorClockConfiguration;

  public ClusteringRule() {
    this(3);
  }

  public ClusteringRule(final int clusterSize) {
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
    this(
        partitionCount,
        replicationFactor,
        clusterSize,
        configurator,
        gatewayCfg -> {},
        ZeebeClientBuilder::usePlaintext);
  }

  public ClusteringRule(
      final int partitionCount,
      final int replicationFactor,
      final int clusterSize,
      final Consumer<BrokerCfg> brokerConfigurator,
      final Consumer<GatewayCfg> gatewayConfigurator) {
    this(
        partitionCount,
        replicationFactor,
        clusterSize,
        brokerConfigurator,
        gatewayConfigurator,
        ZeebeClientBuilder::usePlaintext);
  }

  public ClusteringRule(
      final int partitionCount,
      final int replicationFactor,
      final int clusterSize,
      final Consumer<BrokerCfg> brokerConfigurator,
      final Consumer<GatewayCfg> gatewayConfigurator,
      final Consumer<ZeebeClientBuilder> clientConfigurator) {
    this.partitionCount = partitionCount;
    this.replicationFactor = replicationFactor;
    this.clusterSize = clusterSize;
    this.brokerConfigurator = brokerConfigurator;
    this.gatewayConfigurator = gatewayConfigurator;
    this.clientConfigurator = clientConfigurator;

    actorClockConfiguration = new ActorClockConfiguration(true);
    brokers = new HashMap<>();
    brokerCfgs = new HashMap<>();
    systemContexts = new HashMap<>();
    partitionLeader = new ConcurrentHashMap<>();
    logstreams = new ConcurrentHashMap<>();
    springBrokerBridge = new HashMap<>();

    partitionIds =
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
    final var statement = recordingExporterTestWatcher.apply(base, description);
    return temporaryFolder.apply(super.apply(statement, description), description);
  }

  @Override
  protected void before() throws IOException {
    partitionLatch = new CountDownLatch(partitionCount);
    // create brokers
    for (int nodeId = 0; nodeId < clusterSize; nodeId++) {
      getBroker(nodeId);
    }

    final var contactPoints =
        brokerCfgs.values().stream()
            .map(BrokerCfg::getNetwork)
            .map(NetworkCfg::getInternalApi)
            .map(SocketBindingCfg::getAddress)
            .map(NetUtil::toSocketAddressString)
            .toArray(String[]::new);

    for (int nodeId = 0; nodeId < clusterSize; nodeId++) {
      final var brokerCfg = getBrokerCfg(nodeId);
      setInitialContactPoints(contactPoints).accept(brokerCfg);
    }

    // create gateway
    gateway = createGateway();
    gateway.start().join();

    // create client
    client = createClient();

    try {
      waitUntilBrokersStarted();
      waitForTopology(
          assertion -> assertion.isComplete(clusterSize, partitionCount, replicationFactor));
      LOG.info("All brokers in topology {}", getTopologyFromClient());

    } catch (final Exception e) {
      // If the previous waits timeouts, the brokers are not closed automatically.
      after();
      throw new UncheckedExecutionException("Cluster start failed", e);
    }
  }

  @Override
  protected void after() {
    LOG.debug("Closing ClusteringRule...");
    closeables
        .descendingIterator()
        .forEachRemaining(
            autoCloseable -> {
              try {
                autoCloseable.close();
              } catch (final Exception e) {
                LOG.error("Failed to close managed resource {}", autoCloseable, e);
              }
            });
    brokers.values().parallelStream()
        .forEach(
            b -> {
              try {
                b.close();
              } catch (final Exception e) {
                LOG.error("Failed to close broker: ", e);
              }
            });
    systemContexts.values().forEach(ctx -> ctx.getScheduler().stop());
    systemContexts.clear();
    brokers.clear();
    brokerCfgs.clear();
    logstreams.clear();
    partitionLeader.clear();
  }

  public Broker getBroker(final int nodeId) {
    return brokers.computeIfAbsent(nodeId, this::createBroker);
  }

  private void waitUntilBrokersStarted()
      throws InterruptedException, TimeoutException, ExecutionException {
    final var brokerStartFutures =
        brokers.values().parallelStream().map(Broker::start).toArray(CompletableFuture[]::new);
    CompletableFuture.allOf(brokerStartFutures).get(120, TimeUnit.SECONDS);

    assertThat(partitionLatch.await(60, TimeUnit.SECONDS)).isTrue();
  }

  private Broker createBroker(final int nodeId) {
    final var brokerBase = getBrokerBase(nodeId);
    final var brokerCfg = getBrokerCfg(nodeId);
    brokerCfg.init(brokerBase.getAbsolutePath());

    final var atomixCluster =
        new AtomixCluster(
            new BrokerClusterConfiguration().clusterConfig(brokerCfg),
            Version.from(VersionUtil.getVersion()));
    final var scheduler =
        new ActorSchedulerConfiguration(brokerCfg, actorClockConfiguration).scheduler();
    final var systemContext = new SystemContext(brokerCfg, scheduler, atomixCluster);
    systemContexts.put(nodeId, systemContext);

    systemContext.getScheduler().start();

    final Broker broker =
        new Broker(
            systemContext,
            getSpringBrokerBridge(nodeId),
            Collections.singletonList(new LeaderListener(partitionLatch, nodeId)));

    CompletableFuture.runAsync(broker::start);
    return broker;
  }

  private SpringBrokerBridge getSpringBrokerBridge(final int nodeId) {
    return springBrokerBridge.computeIfAbsent(nodeId, n -> new SpringBrokerBridge());
  }

  public boolean isBrokerHealthy(final int nodeId) {
    return getSpringBrokerBridge(nodeId)
        .getBrokerHealthCheckService()
        .orElseThrow()
        .isBrokerHealthy();
  }

  public BrokerCfg getBrokerCfg(final int nodeId) {
    return brokerCfgs.computeIfAbsent(nodeId, this::createBrokerCfg);
  }

  private BrokerCfg createBrokerCfg(final int nodeId) {
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

      setInitialContactPoints(
              NetUtil.toSocketAddressString(
                  getBrokerCfg(0).getNetwork().getInternalApi().getAddress()))
          .accept(brokerCfg);
    }

    // custom configurators
    brokerConfigurator.accept(brokerCfg);

    // set random port numbers
    assignSocketAddresses(brokerCfg);

    return brokerCfg;
  }

  protected File getBrokerBase(final int nodeId) {
    final var base = new File(temporaryFolder.getRoot(), String.valueOf(nodeId));
    if (!base.exists()) {
      base.mkdir();
    }

    return base;
  }

  private Gateway createGateway() {
    final List<String> initialContactPoints =
        brokerCfgs.values().stream()
            .map(
                brokerCfg ->
                    NetUtil.toSocketAddressString(
                        brokerCfg.getNetwork().getInternalApi().getAddress()))
            .collect(Collectors.toList());

    final GatewayCfg gatewayCfg = new GatewayCfg();
    gatewayCfg
        .getCluster()
        .setInitialContactPoints(initialContactPoints)
        .setClusterName(clusterName);
    gatewayCfg.getNetwork().setPort(SocketUtil.getNextAddress().getPort());
    gatewayCfg.getCluster().setPort(SocketUtil.getNextAddress().getPort());
    // temporarily increase request time out, but we should make this configurable per test
    gatewayCfg.getCluster().setRequestTimeout(Duration.ofSeconds(45));
    gatewayCfg.init();

    gatewayConfigurator.accept(gatewayCfg);

    final GatewayClusterConfiguration clusterFactory = new GatewayClusterConfiguration();
    final AtomixCluster atomixCluster = clusterFactory.atomixCluster(gatewayCfg);
    atomixCluster.start().join();

    final ActorScheduler actorScheduler =
        new ActorSchedulerComponent(gatewayCfg, actorClockConfiguration).actorScheduler();
    actorScheduler.start();

    final var brokerClient =
        new BrokerClientComponent(gatewayCfg, atomixCluster, actorScheduler).brokerClient();
    brokerClient.start();

    final Gateway gateway = new Gateway(gatewayCfg, brokerClient, actorScheduler);
    closeables.add(actorScheduler);
    closeables.add(gateway::stop);
    closeables.add(() -> atomixCluster.stop().join());
    closeables.add(brokerClient);
    return gateway;
  }

  private ZeebeClient createClient() {
    final String contactPoint =
        NetUtil.toSocketAddressString(gateway.getGatewayCfg().getNetwork().toSocketAddress());
    final ZeebeClientBuilder zeebeClientBuilder =
        ZeebeClient.newClientBuilder().gatewayAddress(contactPoint);

    clientConfigurator.accept(zeebeClientBuilder);

    final ZeebeClient client = zeebeClientBuilder.build();
    closeables.add(client);
    return client;
  }

  public Topology getTopologyFromClient() {
    return Awaitility.await()
        .pollInterval(Duration.ofMillis(100))
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .until(() -> client.newTopologyRequest().send().join(), Objects::nonNull);
  }

  /** Returns the current leader for the given partition. */
  public BrokerInfo getLeaderForPartition(final int partition) {
    return Awaitility.await()
        .pollInterval(Duration.ofMillis(100))
        .atMost(Duration.ofSeconds(10))
        .until(
            () -> {
              final List<BrokerInfo> brokers = getTopologyFromClient().getBrokers();
              return extractPartitionLeader(brokers, partition);
            },
            Optional::isPresent)
        .orElseThrow();
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

  /**
   * Restarts broker, if the broker is still running it will be closed before.
   *
   * <p>Returns to the user if the broker is back in the cluster.
   */
  public void restartBroker(final int nodeId) {
    stopBrokerAndAwaitNewLeader(nodeId);
    startBroker(nodeId);
  }

  public void startBroker(final int nodeId) {
    final var broker = Objects.requireNonNull(getBroker(nodeId), "must get existing broker");
    //noinspection resource
    broker.start().join();

    waitForTopology(
        assertion ->
            assertion
                .containsBroker(nodeId)
                .hasLeaderForEachPartition(partitionCount)
                .hasExpectedReplicasCount(partitionCount, replicationFactor));
  }

  public void restartCluster() {
    final var brokers =
        getBrokers().stream().map(b -> b.getConfig().getCluster().getNodeId()).toList();
    brokers.forEach(this::stopBroker);
    brokers.forEach(this::getBroker);
    try {
      waitUntilBrokersStarted();
      waitForTopology(
          assertion -> assertion.isComplete(clusterSize, partitionCount, replicationFactor));
    } catch (final Exception e) {
      LOG.error("Failed to restart cluster", e);
      Assert.fail("Failed to restart cluster");
    }
  }

  /** Returns the list of available brokers in a cluster. */
  public List<InetSocketAddress> getBrokersInCluster() {
    return client.newTopologyRequest().send().join().getBrokers().stream()
        .map(b -> new InetSocketAddress(b.getHost(), b.getPort()))
        .collect(Collectors.toList());
  }

  public Collection<Broker> getBrokers() {
    return brokers.values();
  }

  public InetSocketAddress[] getOtherBrokers(final InetSocketAddress address) {
    return getBrokers().stream()
        .map(b -> b.getConfig().getNetwork().getCommandApi().getAddress())
        .filter(a -> !address.equals(a))
        .toArray(InetSocketAddress[]::new);
  }

  public InetSocketAddress[] getOtherBrokers(final int nodeId) {
    final InetSocketAddress filter = getBrokerCfg(nodeId).getNetwork().getCommandApi().getAddress();
    return getOtherBrokers(filter);
  }

  /** Returns the count of partition leaders */
  public long getPartitionLeaderCount() {
    return client.newTopologyRequest().send().join().getBrokers().stream()
        .flatMap(broker -> broker.getPartitions().stream())
        .filter(PartitionInfo::isLeader)
        .count();
  }

  public BrokerInfo awaitOtherLeader(final int partitionId, final int previousLeader) {
    return Awaitility.await()
        .pollInterval(Duration.ofMillis(100))
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions()
        .until(
            () -> getLeaderForPartition(partitionId),
            (leader) -> leader.getNodeId() != previousLeader);
  }

  public void stepDown(final Broker broker, final int partitionId) {
    final var atomix = broker.getBrokerContext().getClusterServices();
    final MemberId nodeId = atomix.getMembershipService().getLocalMember().id();

    final var raftPartition =
        broker.getBrokerContext().getPartitionManager().getPartitionGroup().getPartitions().stream()
            .filter(partition -> partition.members().contains(nodeId))
            .filter(partition -> partition.id().id() == partitionId)
            .map(RaftPartition.class::cast)
            .findFirst()
            .orElseThrow();

    raftPartition.getServer().stepDown().join();
  }

  public void disconnect(final Broker broker) {
    final var cluster = broker.getSystemContext().getCluster();

    LOGGER.debug(
        "Disonnecting node {} to cluster",
        broker.getSystemContext().getBrokerConfiguration().getCluster().getNodeId());
    ((NettyUnicastService) cluster.getUnicastService()).stop().join();
    ((NettyMessagingService) cluster.getMessagingService()).stop().join();
  }

  public void connect(final Broker broker) {
    final var cluster = broker.getSystemContext().getCluster();

    LOGGER.debug(
        "Connecting node {} to cluster",
        broker.getSystemContext().getBrokerConfiguration().getCluster().getNodeId());
    ((NettyUnicastService) cluster.getUnicastService()).start().join();
    ((NettyMessagingService) cluster.getMessagingService()).start().join();
  }

  public void stopBrokerAndAwaitNewLeader(final int nodeId) {
    final Broker broker = brokers.get(nodeId);
    if (broker != null) {
      stopBroker(nodeId);
      waitForTopology(
          assertion ->
              assertion.doesNotContainBroker(nodeId).hasLeaderForEachPartition(partitionCount));
    }
  }

  public void stopBroker(final int nodeId) {
    final Broker broker = brokers.remove(nodeId);
    if (broker != null) {
      broker.close();
      waitForTopology(assertion -> assertion.doesNotContainBroker(nodeId));
      try {
        final var systemContext = systemContexts.remove(nodeId);
        if (systemContext != null) {
          systemContext.getScheduler().stop().get();
        }
      } catch (final InterruptedException | ExecutionException e) {
        LangUtil.rethrowUnchecked(e);
      }
    }
  }

  public void forceNewLeaderForPartition(final int expectedLeaderId, final int partitionId) {
    final var previousLeader = getCurrentLeaderForPartition(partitionId);
    final var expectedLeader = brokers.get(expectedLeaderId);

    if (previousLeader.getNodeId() == expectedLeaderId) {
      return;
    }

    final var serverOfExpectedLeader =
        ((RaftPartition)
                expectedLeader
                    .getBrokerContext()
                    .getPartitionManager()
                    .getPartitionGroup()
                    .getPartition(partitionId))
            .getServer();

    Awaitility.await("Promote request is successful")
        .pollInterval(Duration.ofMillis(500))
        .timeout(Duration.ofMinutes(1))
        .untilAsserted(
            () ->
                assertThat(serverOfExpectedLeader.promote())
                    .succeedsWithin(Duration.ofSeconds(15)));

    Awaitility.await("New leader of partition %s is %s".formatted(partitionId, expectedLeaderId))
        .pollInterval(Duration.ofMillis(500))
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions()
        .until(
            () -> getLeaderForPartition(partitionId),
            (leader) -> leader.getNodeId() == expectedLeaderId);
  }

  public void waitForTopology(final Consumer<TopologyAssert> assertions) {
    Awaitility.await("until topology satisfies a given condition")
        .pollInterval(Duration.ofMillis(100))
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var topology = getTopologyFromClient();
              assertions.accept(TopologyAssert.assertThat(topology));
            });
  }

  public long createProcessInstanceOnPartition(final int partitionId, final String bpmnProcessId) {
    final BrokerCreateProcessInstanceRequest request =
        new BrokerCreateProcessInstanceRequest().setBpmnProcessId(bpmnProcessId);

    request.setPartitionId(partitionId);

    final BrokerResponse<ProcessInstanceCreationRecord> response =
        gateway.getBrokerClient().sendRequestWithRetry(request).join();

    if (response.isResponse()) {
      return response.getResponse().getProcessInstanceKey();
    } else {
      throw new RuntimeException(
          "Failed to create process instance for bpmn process id "
              + bpmnProcessId
              + " on partition with id "
              + partitionId
              + ": "
              + response);
    }
  }

  public InetSocketAddress getGatewayAddress() {
    return gateway.getGatewayCfg().getNetwork().toSocketAddress();
  }

  public Gateway getGateway() {
    return gateway;
  }

  public ZeebeClient getClient() {
    return client;
  }

  public MutableClock getClock() {
    return actorClockConfiguration.getClockService().mutable().orElseThrow();
  }

  public List<Integer> getPartitionIds() {
    return partitionIds;
  }

  public List<Broker> getOtherBrokerObjects(final int leaderNodeId) {
    return brokers.keySet().stream()
        .filter(id -> id != leaderNodeId)
        .map(brokers::get)
        .collect(Collectors.toList());
  }

  public Path getSegmentsDirectory(final Broker broker) {
    final String dataDir = broker.getConfig().getData().getDirectory();
    return Paths.get(dataDir).resolve(RAFT_PARTITION_PATH);
  }

  /**
   * Writes entries until at least the given count of segments exist on all brokers. Automatically
   * accounts for offset log compaction to ensure that at least one segment will be deleted after
   * taking a snapshot.
   */
  public void fillSegments(final int minimumSegmentCount) {
    fillSegments(getBrokers(), minimumSegmentCount);
  }
  /**
   * Writes entries until at least the given count of segments exist on the given brokers.
   * Automatically accounts for offset log compaction to ensure that at least one segment will be
   * deleted after taking a snapshot.
   */
  public void fillSegments(final Collection<Broker> brokers, final int minimumSegmentCount) {
    final var logCompactionOffset =
        getBrokerCfg(0).getExperimental().getRaft().getPreferSnapshotReplicationThreshold();
    fillSegments(brokers, minimumSegmentCount, logCompactionOffset);
  }
  /**
   * Writes entries until at least the given count of segments exist on all brokers and at least the
   * given count of entries are written.
   */
  public void fillSegments(final int minimumSegmentCount, final int minimumWrittenEntries) {
    fillSegments(getBrokers(), minimumSegmentCount, minimumWrittenEntries);
  }
  /**
   * Writes entries until at least the given count of segments exist on the given brokers and at
   * least the given count of entries are written.
   */
  public void fillSegments(
      final Collection<Broker> brokers,
      final int minimumSegmentCount,
      final int minimumWrittenEntries) {
    var currentSegments = 0;
    var writtenEntries = 0;
    while (currentSegments < minimumSegmentCount || writtenEntries < minimumWrittenEntries) {
      client.newPublishMessageCommand().messageName("msg").correlationKey("key").send().join();
      currentSegments =
          brokers.stream().map(this::getSegmentsCount).min(Integer::compareTo).orElse(0);
      writtenEntries += 1;
    }
  }

  /**
   * Runs until a given number of segments are filled on all brokers. This is useful for publishing
   * messages until a segment is full, thus triggering log compaction.
   */
  public void runUntilSegmentsFilled(
      final Collection<Broker> brokers, final int segmentCount, final Runnable runnable) {
    while (brokers.stream().map(this::getSegmentsCount).allMatch(count -> count <= segmentCount)) {
      runnable.run();
    }
    runnable.run();
  }

  public int getSegmentsCount(final Broker broker) {
    return getSegments(broker).size();
  }

  Collection<Path> getSegments(final Broker broker) {
    try {
      return Files.list(getSegmentsDirectory(broker))
          .filter(path -> path.toString().endsWith(".log"))
          .collect(Collectors.toList());
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public SnapshotId waitForSnapshotAtBroker(final int nodeId) {
    return waitForNewSnapshotAtBroker(getBroker(nodeId), null);
  }

  public SnapshotId waitForSnapshotAtBroker(final Broker broker) {
    return waitForNewSnapshotAtBroker(broker, null);
  }

  public void takeSnapshot(final Broker broker) {
    broker.getBrokerContext().getBrokerAdminService().takeSnapshot();
  }

  public void triggerAndWaitForSnapshots() {
    // Ensure that the exporter positions are distributed to the followers
    getClock().addTime(ExporterDirectorContext.DEFAULT_DISTRIBUTION_INTERVAL);
    getBrokers().stream()
        .map(Broker::getBrokerContext)
        .map(BrokerContext::getBrokerAdminService)
        .forEach(BrokerAdminService::takeSnapshot);

    getBrokers()
        .forEach(
            broker ->
                Awaitility.await()
                    .pollInterval(2, TimeUnit.SECONDS)
                    .timeout(60, TimeUnit.SECONDS)
                    .until(
                        () -> {
                          // Trigger snapshot again in case snapshot is not already taken
                          broker.getBrokerContext().getBrokerAdminService().takeSnapshot();
                          return getSnapshot(broker);
                        },
                        Optional::isPresent));
  }

  /**
   * Waits until a newer snapshot than {@code previousSnapshot} has been committed on the given
   * {@code broker} and the previous ones have been removed. If {@code previousSnapshot} is null,
   * then this returns as soon as a new snapshot has been committed.
   *
   * @param broker the broker to check on
   * @param previousSnapshot the id of the previous expected snapshot
   * @return the id of the new snapshot
   */
  SnapshotId waitForNewSnapshotAtBroker(final Broker broker, final SnapshotId previousSnapshot) {
    return Awaitility.await(
            "Expected snapshot at partition one on broker: "
                + broker.getConfig().getCluster().getNodeId()
                + " with previous snapshot: "
                + previousSnapshot)
        .pollInterval(Duration.ofMillis(100))
        .atMost(Duration.ofMinutes(1))
        .until(
            () -> getSnapshot(broker, 1),
            latestSnapshot ->
                latestSnapshot.isPresent()
                    && (previousSnapshot == null
                        || latestSnapshot.get().compareTo(previousSnapshot) > 0))
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Snapshot expected, but reference to snapshot is corrupted"));
  }

  private Optional<SnapshotId> getSnapshot(final Broker broker, final int partitionId) {

    final var partitions = broker.getBrokerContext().getBrokerAdminService().getPartitionStatus();
    final var partitionStatus = partitions.get(partitionId);

    return Optional.ofNullable(partitionStatus)
        .map(PartitionStatus::getSnapshotId)
        .flatMap(FileBasedSnapshotId::ofFileName);
  }

  public Optional<SnapshotId> getSnapshot(final int brokerId) {
    final var broker = getBroker(brokerId);
    return getSnapshot(broker, 1);
  }

  public Optional<SnapshotId> getSnapshot(final Broker broker) {
    return getSnapshot(broker, 1);
  }

  LogStream getLogStream(final int partitionId) {
    return logstreams.get(partitionId);
  }

  public Leader getCurrentLeaderForPartition(final int partition) {
    return partitionLeader.get(partition);
  }

  public int stopAnyFollower() {
    final var leaderForPartition = getLeaderForPartition(START_PARTITION_ID);

    final var otherBrokerObjects = getOtherBrokerObjects(leaderForPartition.getNodeId());
    final var nodeId = otherBrokerObjects.get(0).getConfig().getCluster().getNodeId();
    stopBroker(nodeId);
    return nodeId;
  }

  private class LeaderListener implements PartitionListener {

    private final CountDownLatch latch;
    private final int nodeId;

    LeaderListener(final CountDownLatch latch, final int nodeId) {
      this.latch = latch;
      this.nodeId = nodeId;
    }

    @Override
    public ActorFuture<Void> onBecomingFollower(final int partitionId, final long term) {
      return CompletableActorFuture.completed(null);
    }

    @Override
    public ActorFuture<Void> onBecomingLeader(
        final int partitionId,
        final long term,
        final LogStream logStream,
        final QueryService queryService) {
      logstreams.put(partitionId, logStream);
      latch.countDown();
      partitionLeader.put(partitionId, new Leader(nodeId, term, logStream));
      return CompletableActorFuture.completed(null);
    }

    @Override
    public ActorFuture<Void> onBecomingInactive(final int partitionId, final long term) {
      return CompletableActorFuture.completed(null);
    }
  }
}
