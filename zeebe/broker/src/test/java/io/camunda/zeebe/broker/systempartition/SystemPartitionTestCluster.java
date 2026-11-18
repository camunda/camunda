/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.systempartition;

import static io.camunda.zeebe.broker.test.EmbeddedBrokerConfigurator.setCommandApiPort;
import static io.camunda.zeebe.broker.test.EmbeddedBrokerConfigurator.setGatewayApiPort;
import static io.camunda.zeebe.broker.test.EmbeddedBrokerConfigurator.setGatewayClusterPort;
import static io.camunda.zeebe.broker.test.EmbeddedBrokerConfigurator.setInternalApiPort;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.storage.system.Configuration;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.security.api.context.OidcClaimsProvider;
import io.camunda.security.configuration.EngineSecurityConfigurations;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.system.SystemContext;
import io.camunda.zeebe.broker.system.SystemContextTestFactory;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.test.TestActorSchedulerFactory;
import io.camunda.zeebe.broker.test.TestBrokerClientFactory;
import io.camunda.zeebe.broker.test.TestClusterFactory;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationCoordinatorSupplier;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.impl.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-process brokers with the system partition enabled, for the broker integration tests of the
 * system partition. Each broker has its own data directory, which survives a restart unless a test
 * deletes it, and its own ports, which stay the same over restarts.
 */
final class SystemPartitionTestCluster implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(SystemPartitionTestCluster.class);

  private final Path root;
  private final Consumer<BrokerCfg> clusterConfiguration;
  private final Map<MemberId, Node> nodes = new LinkedHashMap<>();
  private final Map<MemberId, Broker> running = new ConcurrentHashMap<>();

  /**
   * @param root the directory of the data directories
   * @param members the brokers that the tests may start, with their zone (or null) and node id
   * @param clusterConfiguration the configuration that all brokers share: size, replication factor,
   *     zones
   */
  SystemPartitionTestCluster(
      final Path root, final List<Node> members, final Consumer<BrokerCfg> clusterConfiguration) {
    this.root = root;
    this.clusterConfiguration = clusterConfiguration;
    members.forEach(node -> nodes.put(node.id(), node));
  }

  static Node node(final int nodeId) {
    return new Node(null, nodeId, ports());
  }

  static Node node(final String zone, final int nodeId) {
    return new Node(zone, nodeId, ports());
  }

  private static Ports ports() {
    return new Ports(
        SocketUtil.getNextAddress().getPort(),
        SocketUtil.getNextAddress().getPort(),
        SocketUtil.getNextAddress().getPort(),
        SocketUtil.getNextAddress().getPort());
  }

  /** Starts the given brokers at the same time, as their data partitions need each other. */
  void start(final Collection<MemberId> members) {
    start(members, cfg -> {});
  }

  void start(final Collection<MemberId> members, final Consumer<BrokerCfg> override) {
    final var futures = new ArrayList<CompletableFuture<Broker>>();
    for (final var member : members) {
      final var broker = build(nodes.get(member), override);
      running.put(member, broker);
      // Broker#start blocks until the broker started, which needs the other brokers
      futures.add(CompletableFuture.supplyAsync(() -> broker.start().join()));
    }
    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
        .orTimeout(3, TimeUnit.MINUTES)
        .join();
  }

  void stop(final MemberId member) {
    final var broker = running.remove(member);
    if (broker != null) {
      broker.close();
    }
  }

  /** The broker loses its data directory while it is down. */
  void deleteData(final MemberId member) {
    try {
      FileUtil.deleteFolderIfExists(dataDirectory(member));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  Path dataDirectory(final MemberId member) {
    return root.resolve(member.id());
  }

  SystemPartitionService service(final MemberId member) {
    return running.get(member).getBrokerContext().getSystemPartitionService();
  }

  boolean isRunning(final MemberId member) {
    return running.containsKey(member);
  }

  /** The configuration of the system partition replica of the broker, if it has one. */
  Optional<Configuration> configuration(final MemberId member) {
    if (!isRunning(member)) {
      return Optional.empty();
    }
    return service(member)
        .partition()
        .filter(partition -> partition.getServer() != null)
        .flatMap(
            partition -> {
              try {
                return Optional.ofNullable(
                    partition.getServer().getConfiguration().get(5, TimeUnit.SECONDS));
              } catch (final Exception e) {
                return Optional.empty();
              }
            });
  }

  Optional<MemberId> leader() {
    return running.keySet().stream()
        .filter(
            member ->
                service(member)
                    .partition()
                    .map(p -> p.getRole() == io.atomix.raft.RaftServer.Role.LEADER)
                    .orElse(false))
        .findFirst();
  }

  /**
   * Waits until the data partition has a leader, so that a forced reconfiguration of it has a term.
   */
  void awaitDataPartitionLeader() {
    Awaitility.await("the data partition has a leader")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions()
        .until(
            () ->
                running.values().stream()
                    .anyMatch(
                        broker ->
                            broker
                                .getBrokerContext()
                                .getPartitionManager()
                                .getRaftPartitions()
                                .stream()
                                .anyMatch(
                                    partition ->
                                        partition.getRole()
                                            == io.atomix.raft.RaftServer.Role.LEADER)));
  }

  MemberId awaitLeader() {
    return Awaitility.await("the system partition has a leader")
        .atMost(Duration.ofMinutes(1))
        .until(this::leader, Optional::isPresent)
        .orElseThrow();
  }

  /** The configuration of the leader, once it has the given types and is not joint. */
  Configuration awaitLeaderConfiguration(final Map<MemberId, Type> types) {
    return awaitLeaderConfiguration(types, configuration -> true);
  }

  /** Like {@link #awaitLeaderConfiguration(Map)}, once the configuration also matches. */
  Configuration awaitLeaderConfiguration(
      final Map<MemberId, Type> types, final java.util.function.Predicate<Configuration> matches) {
    return Awaitility.await("the system partition has the members " + types)
        .atMost(Duration.ofMinutes(2))
        .pollInterval(Duration.ofMillis(500))
        .until(
            () ->
                leader()
                    .flatMap(this::configuration)
                    .filter(configuration -> !configuration.requiresJointConsensus())
                    .filter(configuration -> typesOf(configuration).equals(types))
                    .filter(matches),
            Optional::isPresent)
        .orElseThrow();
  }

  static Map<MemberId, Type> typesOf(final Configuration configuration) {
    return configuration.newMembers().stream()
        .collect(Collectors.toMap(RaftMember::memberId, RaftMember::getType));
  }

  /** Sends management requests through the given broker, as the gateway would. */
  ClusterConfigurationManagementRequestSender requestSender(final MemberId via) {
    final var broker = running.get(via);
    return new ClusterConfigurationManagementRequestSender(
        broker.getBrokerContext().getClusterServices().getCommunicationService(),
        ClusterConfigurationCoordinatorSupplier.ofMembers(running.keySet()),
        new ProtoBufSerializer(),
        via);
  }

  /** Waits until the dynamic configuration has no pending change any more. */
  CurrentClusterConfiguration awaitNoPendingChange(final MemberId via) {
    return Awaitility.await("the change is applied")
        .atMost(Duration.ofMinutes(3))
        .pollInterval(Duration.ofSeconds(1))
        .ignoreExceptions()
        .until(
            () -> requestSender(via).getTopology().get(10, TimeUnit.SECONDS).get(),
            configuration ->
                configuration.phasedChangeState().pending().isEmpty()
                    && configuration.globalConfiguration().pendingChanges().isEmpty());
  }

  private Broker build(final Node node, final Consumer<BrokerCfg> override) {
    final var brokerCfg = new BrokerCfg();
    setGatewayApiPort(node.ports().gatewayApi()).accept(brokerCfg);
    setGatewayClusterPort(node.ports().gatewayCluster()).accept(brokerCfg);
    setCommandApiPort(node.ports().commandApi()).accept(brokerCfg);
    setInternalApiPort(node.ports().internalApi()).accept(brokerCfg);
    brokerCfg.getGateway().setEnable(false);
    final var cluster = brokerCfg.getCluster();
    cluster.setNodeId(node.nodeId());
    cluster.setZone(node.zone());
    cluster.setPartitionsCount(1);
    cluster.setElectionTimeout(Duration.ofMillis(1500));
    cluster.setHeartbeatInterval(Duration.ofMillis(250));
    cluster.getMembership().setProbeInterval(Duration.ofMillis(200));
    cluster.getMembership().setFailureTimeout(Duration.ofMillis(2000));
    cluster.getMembership().setSyncInterval(Duration.ofMillis(500));
    cluster.setInitialContactPoints(
        nodes.values().stream()
            .filter(other -> !other.id().equals(node.id()))
            .map(other -> "localhost:" + other.ports().internalApi())
            .toList());
    final var systemPartition = brokerCfg.getExperimental().getSystemPartition();
    systemPartition.setEnabled(true);
    systemPartition.setMembershipCheckInterval(Duration.ofMillis(300));
    systemPartition.setLeaveTimeout(Duration.ofSeconds(15));
    clusterConfiguration.accept(brokerCfg);
    override.accept(brokerCfg);
    brokerCfg.init(dataDirectory(node.id()).toAbsolutePath().toString());

    final var meterRegistry = new SimpleMeterRegistry();
    final var atomixCluster = TestClusterFactory.createAtomixCluster(brokerCfg, meterRegistry);
    final var actorScheduler = TestActorSchedulerFactory.ofBrokerConfig(brokerCfg);
    final var brokerClient =
        TestBrokerClientFactory.createBrokerClient(atomixCluster, actorScheduler);
    final var systemContext =
        SystemContextTestFactory.singleTenant(
            SystemContext.DEFAULT_SHUTDOWN_TIMEOUT,
            brokerCfg,
            null,
            actorScheduler,
            atomixCluster,
            brokerClient,
            meterRegistry,
            EngineSecurityConfigurations.defaultConfig(),
            null,
            null,
            null,
            (OidcClaimsProvider) (jwtClaims, tokenValue) -> jwtClaims,
            null,
            null,
            NodeIdProvider.staticProvider(node.nodeId()),
            PhysicalTenantIds.DEFAULT);
    return new Broker(systemContext, new SpringBrokerBridge(), List.of());
  }

  @Override
  public void close() {
    for (final var member : List.copyOf(running.keySet())) {
      try {
        stop(member);
      } catch (final Exception e) {
        LOG.warn("Failed to stop {}", member, e);
      }
    }
  }

  record Ports(int gatewayApi, int gatewayCluster, int commandApi, int internalApi) {}

  record Node(String zone, int nodeId, Ports ports) {
    MemberId id() {
      return MemberId.from(zone, nodeId);
    }
  }
}
