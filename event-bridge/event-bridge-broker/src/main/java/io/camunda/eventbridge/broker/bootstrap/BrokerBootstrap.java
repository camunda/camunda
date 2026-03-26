/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.bootstrap;

import io.atomix.cluster.AtomixCluster;
import io.camunda.eventbridge.broker.partitioning.PartitionDistributor;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.InstantSource;
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

  private final MessagingServiceSetup messagingServiceSetup;
  private final TopologySetup topologySetup;
  private final PartitionBootstrapper partitionBootstrapper;

  private volatile boolean started = false;

  public BrokerBootstrap(
      final AtomixCluster cluster,
      final ActorSchedulingService actorScheduler,
      final EventBridgeProperties properties,
      final PartitionDistributor distributor,
      final IdGenerator idGenerator,
      final MeterRegistry meterRegistry) {

    messagingServiceSetup = new MessagingServiceSetup(properties, meterRegistry);

    topologySetup = new TopologySetup(cluster.getMembershipService(), actorScheduler, properties);

    partitionBootstrapper =
        new PartitionBootstrapper(
            cluster, actorScheduler, properties, distributor, InstantSource.system(), idGenerator);
  }

  public void start() {
    if (started) {
      LOG.debug("BrokerBootstrap already started; ignoring");
      return;
    }
    started = true;

    LOG.info("Starting EventBridge broker");

    // 1. Start broker messaging service — handlers are registered here
    final var brokerMessagingService = messagingServiceSetup.start();

    // 2. Start topology — BrokerInfo + SWIM gossip
    final var topologyManager = topologySetup.start();

    // 3. Start partitions — raft + lifecycle actors (uses broker messaging service)
    partitionBootstrapper.start(topologyManager, brokerMessagingService);

    LOG.info("EventBridge broker started — waiting for raft elections");
  }

  public void stop() {
    if (!started) {
      return;
    }

    LOG.info("Stopping EventBridge broker");

    // Reverse order
    partitionBootstrapper.stop();
    topologySetup.stop();
    messagingServiceSetup.stop();

    started = false;
    LOG.info("EventBridge broker stopped");
  }
}
