/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker;

import io.atomix.cluster.AtomixCluster;
import io.camunda.eventbridge.broker.actor.CoordinatorActor;
import io.camunda.eventbridge.broker.actor.PollActor;
import io.camunda.eventbridge.broker.actor.PublishActor;
import io.camunda.eventbridge.broker.coordinator.ConsumerGroupRegistry;
import io.camunda.eventbridge.broker.offset.OffsetStore;
import io.camunda.eventbridge.broker.partition.PartitionBootstrap;
import io.camunda.eventbridge.broker.topology.TopologyService;
import io.camunda.eventbridge.broker.transport.BrokerRequestDispatcher;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring {@link Configuration} that wires up all Event Bridge broker beans.
 *
 * <p>Expects an {@link ActorScheduler} bean in the context (e.g. provided by {@code
 * ActorSchedulerConfiguration} from the dist module). All actors are submitted to the scheduler and
 * stopped via the {@code brokerLifecycle} {@link SmartLifecycle} bean.
 *
 * <p>An {@link AtomixCluster} bean is required for RAFT partition bootstrap; without it the {@link
 * PartitionBootstrap} bean is not created and publish requests will always fail with a "not-leader"
 * error. When absent (e.g. in unit tests that do not need RAFT) the topology service still
 * functions in local-only mode.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EventBridgeProperties.class)
public class BrokerModuleConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(BrokerModuleConfiguration.class);

  @Bean
  public ConsumerGroupRegistry consumerGroupRegistry() {
    return new ConsumerGroupRegistry();
  }

  @Bean
  public OffsetStore offsetStore() {
    return new OffsetStore();
  }

  @Bean
  public CoordinatorActor coordinatorActor(
      final ConsumerGroupRegistry registry,
      final OffsetStore offsetStore,
      final EventBridgeProperties properties,
      final Map<Integer, PublishActor> publishActors) {
    return new CoordinatorActor(
        registry, offsetStore, properties, properties.broker().partitionCount(), publishActors);
  }

  /**
   * Creates one {@link PublishActor} per configured partition. Actors start in a disconnected state
   * (no log writer/reader). Once the {@link PartitionBootstrap} starts RAFT and the local node
   * becomes leader for a partition, {@link PublishActor#connect} is called automatically to wire up
   * the live {@link LogStream}.
   */
  @Bean
  public Map<Integer, PublishActor> publishActors(final EventBridgeProperties properties) {
    final int partitionCount = properties.broker().partitionCount();
    final Map<Integer, PublishActor> actors = new HashMap<>(partitionCount);
    for (int partitionId = 0; partitionId < partitionCount; partitionId++) {
      // Writer and reader start as null. PublishActor.connect() will be called by
      // EventBridgePartition.onNewRole(LEADER) once RAFT elects this node as leader.
      final PublishActor actor =
          new PublishActor(partitionId, (LogStreamWriter) null, (LogStreamReader) null, properties);
      actors.put(partitionId, actor);
    }
    return actors;
  }

  /**
   * Creates one {@link PollActor} per configured partition. Actors start disconnected; once the
   * local node becomes RAFT leader for a partition, {@link PollActor#connect(LogStream)} is called
   * by {@link io.camunda.eventbridge.broker.partition.EventBridgePartition} to open a dedicated
   * reader and register for log-record notifications.
   */
  @Bean
  public Map<Integer, PollActor> pollActors(final EventBridgeProperties properties) {
    final int partitionCount = properties.broker().partitionCount();
    final Map<Integer, PollActor> actors = new HashMap<>(partitionCount);
    for (int partitionId = 0; partitionId < partitionCount; partitionId++) {
      actors.put(partitionId, new PollActor(partitionId, properties));
    }
    return actors;
  }

  /**
   * Creates the {@link TopologyService} that maintains the in-memory partition-leader routing
   * table. The local member ID is taken from {@code event-bridge.coordinator.broker-id}, which in
   * standalone (single-JVM) mode equals the only broker's SWIM member ID.
   */
  @Bean
  public TopologyService topologyService(final EventBridgeProperties properties) {
    return new TopologyService(properties.coordinator().brokerId());
  }

  /**
   * Creates the {@link BrokerRequestDispatcher} that registers Netty message handlers when an
   * {@link AtomixCluster} bean is present. Returns {@code null} (no bean registered) when no
   * cluster is available, e.g. in unit-test contexts.
   *
   * <p>The dispatcher must be started <em>before</em> the HTTP server accepts requests so that the
   * broker is ready to serve gateway-routed requests as soon as clients can reach the gateway.
   */
  @Bean
  public BrokerRequestDispatcher brokerRequestDispatcher(
      @Autowired(required = false) final AtomixCluster cluster,
      final Map<Integer, PublishActor> publishActors,
      final Map<Integer, PollActor> pollActors,
      final CoordinatorActor coordinatorActor) {
    if (cluster == null) {
      LOG.info(
          "No AtomixCluster bean present; skipping BrokerRequestDispatcher registration "
              + "(Netty handlers will not be registered)");
      return null;
    }
    return new BrokerRequestDispatcher(
        cluster.getMessagingService(), publishActors, pollActors, coordinatorActor);
  }

  /**
   * Creates the {@link PartitionBootstrap} that starts RAFT partitions when an {@link
   * AtomixCluster} bean is present. Returns {@code null} (no bean registered) when no cluster is
   * available, e.g. in unit-test contexts.
   */
  @Bean
  public PartitionBootstrap partitionBootstrap(
      @Autowired(required = false) final AtomixCluster cluster,
      final ActorScheduler actorScheduler,
      final Map<Integer, PublishActor> publishActors,
      final Map<Integer, PollActor> pollActors,
      final EventBridgeProperties properties,
      final MeterRegistry meterRegistry) {
    if (cluster == null) {
      LOG.info("No AtomixCluster bean present; skipping RAFT partition bootstrap");
      return null;
    }
    return new PartitionBootstrap(
        cluster, actorScheduler, properties, publishActors, pollActors, meterRegistry);
  }

  /**
   * Lifecycle bean that starts all broker actors once the {@link ActorScheduler} is ready and stops
   * them on context close. Phase 100 ensures we start after the scheduler (phase 0) but before
   * Spring's HTTP server (phase {@link Integer#MAX_VALUE}).
   *
   * <p>If an {@link AtomixCluster} is present in the context, the {@link TopologyService} is
   * registered as a SWIM listener, the broker publishes its partition leadership into member
   * properties, and all RAFT partitions are bootstrapped via {@link PartitionBootstrap}.
   */
  @Bean
  public SmartLifecycle brokerLifecycle(
      final ActorScheduler actorScheduler,
      final Map<Integer, PublishActor> publishActors,
      final Map<Integer, PollActor> pollActors,
      final CoordinatorActor coordinatorActor,
      final TopologyService topologyService,
      final EventBridgeProperties properties,
      @Autowired(required = false) final AtomixCluster cluster,
      @Autowired(required = false) final PartitionBootstrap partitionBootstrap,
      @Autowired(required = false) final BrokerRequestDispatcher brokerRequestDispatcher) {
    return new SmartLifecycle() {
      private volatile boolean running = false;

      @Override
      public void start() {
        LOG.info("Starting Event Bridge broker actors");
        actorScheduler.submitActor(coordinatorActor);
        publishActors.values().forEach(actorScheduler::submitActor);
        pollActors.values().forEach(actorScheduler::submitActor);

        if (cluster != null) {
          registerTopologyService(cluster, topologyService);
        } else {
          LOG.info(
              "No AtomixCluster bean present; TopologyService running in local-only mode "
                  + "(RAFT partition bootstrap and leader routing via SWIM gossip disabled)");
        }

        if (brokerRequestDispatcher != null) {
          brokerRequestDispatcher.start();
        }

        if (partitionBootstrap != null) {
          partitionBootstrap.start();
        }

        running = true;
      }

      @Override
      public void stop() {
        LOG.info("Stopping Event Bridge broker actors");

        if (partitionBootstrap != null) {
          partitionBootstrap.stop();
        }

        if (brokerRequestDispatcher != null) {
          brokerRequestDispatcher.stop();
        }

        coordinatorActor.closeAsync();
        publishActors.values().forEach(PublishActor::closeAsync);
        pollActors.values().forEach(PollActor::closeAsync);

        if (cluster != null) {
          cluster.getMembershipService().removeListener(topologyService);
        }

        running = false;
      }

      @Override
      public boolean isRunning() {
        return running;
      }

      @Override
      public int getPhase() {
        return 100;
      }
    };
  }

  // -------------------------------------------------------------------------
  // Helpers

  private static void registerTopologyService(
      final AtomixCluster cluster, final TopologyService topologyService) {
    final var membershipService = cluster.getMembershipService();

    // Seed routing table from the current membership snapshot before registering for live events.
    topologyService.initialize(membershipService);

    // Subscribe to future membership changes.
    membershipService.addListener(topologyService);

    // Note: partition leadership properties ("eb.partition.N.leader") and the coordinator property
    // ("eb.coordinator") are written into SWIM member properties by TopologyBroadcaster inside
    // PartitionBootstrap.start(), which runs after this method. Writing them here eagerly would be
    // incorrect for multi-node clusters because we do not yet know which partitions this node will
    // lead. In single-node mode the effect is the same since all partitions elect the local node.

    LOG.info(
        "TopologyService registered as SWIM listener; "
            + "partition leadership will be advertised reactively as RAFT elects leaders");
  }
}
