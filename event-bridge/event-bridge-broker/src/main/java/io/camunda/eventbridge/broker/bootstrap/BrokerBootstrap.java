/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.bootstrap;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.MemberId;
import io.camunda.eventbridge.broker.partitioning.PartitionDistributor;
import io.camunda.eventbridge.broker.partitioning.PartitionFactory;
import io.camunda.eventbridge.coordinator.stream.TopicAssignmentGossip;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.eventbridge.messaging.threading.ExecutorServiceFactory;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.InstantSource;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.agrona.concurrent.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates broker startup and shutdown.
 *
 * <p>Startup order:
 *
 * <ol>
 *   <li>{@link MessagingServiceSetup} — broker command API (port 26501)
 *   <li>{@link TopologySetup} — BrokerInfo + SWIM gossip
 *   <li>{@link PartitionBootstrapper} — raft partitions + lifecycle actors
 * </ol>
 *
 * <p>Shutdown is reverse order.
 */
public final class BrokerBootstrap {

  private static final Logger LOG = LoggerFactory.getLogger(BrokerBootstrap.class);

  private final AtomixCluster cluster;
  private final ActorSchedulingService actorScheduler;
  private final EventBridgeProperties properties;
  private final PartitionDistributor distributor;
  private final ExecutorServiceFactory executorServiceFactory;
  private final IdGenerator idGenerator;
  private final MeterRegistry meterRegistry;
  private final BrokerTopologyManager gatewayTopologyManager;

  private MessagingServiceSetup messagingServiceSetup;
  private TopologySetup topologySetup;
  private PartitionBootstrapper partitionBootstrapper;
  private ExecutorServiceSetup executorServiceSetup;

  private volatile boolean started = false;

  public BrokerBootstrap(
      final AtomixCluster cluster,
      final ActorSchedulingService actorScheduler,
      final EventBridgeProperties properties,
      final PartitionDistributor distributor,
      final ExecutorServiceFactory executorServiceFactory,
      final IdGenerator idGenerator,
      final MeterRegistry meterRegistry,
      final BrokerTopologyManager gatewayTopologyManager) {
    this.cluster = cluster;
    this.actorScheduler = actorScheduler;
    this.properties = properties;
    this.distributor = distributor;
    this.executorServiceFactory = executorServiceFactory;
    this.idGenerator = idGenerator;
    this.meterRegistry = meterRegistry;
    this.gatewayTopologyManager = gatewayTopologyManager;
  }

  public void start() {
    if (started) {
      LOG.debug("BrokerBootstrap already started; ignoring");
      return;
    }
    started = true;

    LOG.info("Starting EventBridge broker");

    // 1. Start broker messaging service — handlers are registered here
    messagingServiceSetup = new MessagingServiceSetup(properties, meterRegistry);
    final var brokerMessagingService = messagingServiceSetup.start();

    // 2. Start topology — BrokerInfo + SWIM gossip
    topologySetup =
        new TopologySetup(
            cluster.getMembershipService(), actorScheduler, properties, gatewayTopologyManager);
    final var topologyManager = topologySetup.start();

    // 2b. Build the cluster configuration once — the single source of truth (as in Zeebe) for both
    // raft partition placement (which partitions this broker starts) and gateway routing (which
    // partitions exist, via BrokerClusterState.getPartitions()).
    final var configuration = buildClusterConfiguration();

    // Feed it to the gateway topology so getPartitions() is populated; combined with gossiped
    // leadership this lets the BrokerClient route partition-addressed requests natively.
    gatewayTopologyManager.onClusterConfigurationUpdated(configuration);
    LOG.info(
        "Seeded gateway cluster configuration with partitions {}",
        configuration.partitionIds().boxed().toList());

    // Derive the raft partition distribution from the same configuration (mirrors Zeebe's
    // PartitionManagerImpl, which starts the partitions whose members include the local node).
    final var distribution =
        ConfigurationUtil.getPartitionDistributionFrom(configuration, PartitionFactory.GROUP_NAME);

    // 3. Start Fetch Stream Executor Service
    executorServiceSetup = new ExecutorServiceSetup(executorServiceFactory);
    final var executorService = executorServiceSetup.start();

    // 3b. Wire the topic-registry propagation channel (Option 1: the registry-shard coordinator
    // leader broadcasts the registry; every broker reconciles from it). The local node also needs
    // the registry, but cluster broadcast excludes the sender, so the publisher additionally
    // delivers to the local sink. The subscriber receives broadcasts from remote registry leaders.
    final var comm = cluster.getCommunicationService();
    final Consumer<byte[]> registrySink =
        payload ->
            LOG.info(
                "Received topic registry broadcast: {}",
                TopicAssignmentGossip.decode(payload).keySet());
    comm.consume(TopicAssignmentGossip.SUBJECT, Function.identity(), registrySink, executorService);
    final TopicAssignmentGossip.Publisher topicAssignmentPublisher =
        payload -> {
          comm.broadcast(TopicAssignmentGossip.SUBJECT, payload, Function.identity(), true);
          registrySink.accept(payload);
        };

    // 4. Start partitions — raft + lifecycle actors (uses broker messaging service)
    partitionBootstrapper =
        new PartitionBootstrapper(
            cluster,
            actorScheduler,
            properties,
            InstantSource.system(),
            idGenerator,
            executorService);
    partitionBootstrapper.start(
        distribution,
        topologyManager,
        topologySetup.getCoordinatorTopologyManager(),
        brokerMessagingService,
        topicAssignmentPublisher);

    LOG.info("EventBridge broker started — waiting for raft elections");
  }

  /**
   * Builds the cluster configuration from the (deterministic) partition distribution over the
   * configured members. Every node computes the same configuration, so raft placement and gateway
   * routing agree across the cluster.
   */
  private ClusterConfiguration buildClusterConfiguration() {
    final var clusterSize = properties.cluster().clusterSize();
    final var members =
        IntStream.range(0, clusterSize)
            .mapToObj(i -> MemberId.from("broker-" + i))
            .sorted()
            .toList();
    final var distribution =
        distributor.distributePartitions(
            members, properties.broker().partitionCount(), properties.raft().replicationFactor());

    return ConfigurationUtil.getClusterConfigFrom(
        distribution, DynamicPartitionConfig.init(), properties.cluster().name());
  }

  public void stop() {
    if (!started) {
      return;
    }

    LOG.info("Stopping EventBridge broker");

    // Reverse order
    partitionBootstrapper.stop();
    executorServiceSetup.stop();
    topologySetup.stop();
    messagingServiceSetup.stop();

    started = false;
    LOG.info("EventBridge broker stopped");
  }
}
