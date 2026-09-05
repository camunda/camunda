/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiper;
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiperConfig;
import io.camunda.zeebe.dynamic.config.metrics.TopologyMetrics;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The GatewayClusterConfigurationService contains minimal functionality required for the Gateway.
 * The Gateway only listens to ClusterConfiguration changes. It cannot make changes to the
 * configuration. So the service does not run ClusterConfigurationManager, but only contains the
 * ClusterConfigurationGossiper.
 *
 * <p>This service tracks and gossips the multi-partition-group {@link CurrentClusterConfiguration}
 * instead of the legacy single-group {@code ClusterConfiguration}, mirroring what {@link
 * ClusterConfigurationManagerImpl} does for brokers. This matters beyond the gateway's own view:
 * before this, the gateway only ever gossiped the legacy field, so any real broker that fell back
 * to {@code CurrentClusterConfiguration#fromLegacy} for a message relayed by the gateway could
 * construct a lossy reconstruction of an in-progress plan that conflicted with its own,
 * natively-tracked one.
 */
public class GatewayClusterConfigurationService extends Actor
    implements ClusterConfigurationUpdateNotifier {
  private static final Logger LOG =
      LoggerFactory.getLogger(GatewayClusterConfigurationService.class);
  private final ClusterConfigurationGossiper clusterConfigurationGossiper;

  // Keep an in memory copy of the configuration. No need to persist it.
  private CurrentClusterConfiguration currentClusterConfiguration =
      CurrentClusterConfiguration.uninitialized();
  private final TopologyMetrics topologyMetrics;
  private final CompletableActorFuture<Void> startedFuture = new CompletableActorFuture<>();

  public GatewayClusterConfigurationService(
      final ClusterCommunicationService communicationService,
      final ClusterMembershipService memberShipService,
      final ClusterConfigurationGossiperConfig config,
      final MeterRegistry meterRegistry) {
    topologyMetrics = new TopologyMetrics(meterRegistry);
    clusterConfigurationGossiper =
        new ClusterConfigurationGossiper(
            this,
            communicationService,
            memberShipService,
            new ProtoBufSerializer(),
            config,
            ignored -> {},
            this::updateCurrentClusterTopology,
            topologyMetrics);
  }

  public ActorFuture<Void> start(final ActorScheduler scheduler) {
    scheduler.submitActor(this).onError(startedFuture::completeExceptionally);

    startedFuture.onSuccess(
        ignore -> {
          LOG.info("Cluster Configuration Manager started successfully");
        });

    startedFuture.onError(
        error -> LOG.error("Failed to start GatewayClusterConfigurationService", error));
    return startedFuture;
  }

  @Override
  protected void onActorStarting() {
    LOG.info("Starting Cluster Configuration Manager");
    clusterConfigurationGossiper.start().onComplete(startedFuture);
  }

  @Override
  protected void onActorClosing() {
    clusterConfigurationGossiper.close();
  }

  private void updateCurrentClusterTopology(
      final CurrentClusterConfiguration currentClusterConfiguration) {
    actor.run(
        () -> {
          if (currentClusterConfiguration == null
              || currentClusterConfiguration.isUninitialized()) {
            return;
          }

          try {
            final var mergedTopology =
                this.currentClusterConfiguration.merge(currentClusterConfiguration);
            if (mergedTopology.equals(this.currentClusterConfiguration)) {
              return;
            }
            LOG.debug(
                "Received new configuration {}. Updating local configuration to {}",
                currentClusterConfiguration,
                mergedTopology);
            this.currentClusterConfiguration = mergedTopology;
            clusterConfigurationGossiper.updateCurrentClusterConfiguration(
                this.currentClusterConfiguration);
          } catch (final Exception updateFailed) {
            LOG.warn(
                "Failed to process received configuration update {}",
                currentClusterConfiguration,
                updateFailed);
          }
        });
  }

  @Override
  public void addUpdateListener(final ClusterConfigurationUpdateListener listener) {
    clusterConfigurationGossiper.addUpdateListener(listener);
  }

  @Override
  public void removeUpdateListener(final ClusterConfigurationUpdateListener listener) {
    clusterConfigurationGossiper.removeUpdateListener(listener);
  }
}
