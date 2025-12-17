/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationInitializer.FileInitializer;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationInitializer.GossipInitializer;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationInitializer.InitializerError.PersistedConfigurationIsBroken;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationInitializer.StaticInitializer;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationInitializer.SyncInitializer;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationManager.InconsistentConfigurationListener;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestsHandler;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestServer;
import io.camunda.zeebe.dynamic.config.changes.ClusterChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeAppliersImpl;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinatorImpl;
import io.camunda.zeebe.dynamic.config.changes.NoopClusterMembershipChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiper;
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiperConfig;
import io.camunda.zeebe.dynamic.config.metrics.TopologyManagerMetrics;
import io.camunda.zeebe.dynamic.config.metrics.TopologyMetrics;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.AsyncClosable;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;

public final class ClusterConfigurationManagerService
    implements ClusterConfigurationUpdateNotifier, AsyncClosable {
  public static final String TOPOLOGY_FILE_NAME = ".topology.meta";
  // Use a node 0 as always the coordinator. Later we can make it configurable or allow changing it
  // dynamically.
  private static final String COORDINATOR_ID = "0";
  private final ClusterConfigurationManagerImpl clusterConfigurationManager;
  private final ClusterConfigurationGossiper clusterConfigurationGossiper;
  private final boolean isCoordinator;
  private final PersistedClusterConfiguration persistedClusterConfiguration;
  private final Path configurationFile;
  private final ConfigurationChangeCoordinator configurationChangeCoordinator;
  private final ClusterConfigurationRequestServer configurationRequestServer;
  private final Actor gossipActor;
  private final Actor managerActor;
  private final ClusterConfigurationGossiperConfig gossiperConfig;
  private final ClusterChangeExecutor clusterChangeExecutor;
  private final TopologyMetrics topologyMetrics;
  private final TopologyManagerMetrics topologyManagerMetrics;

  public ClusterConfigurationManagerService(
      final Path dataRootDirectory,
      final ClusterCommunicationService communicationService,
      final ClusterMembershipService memberShipService,
      final ClusterConfigurationGossiperConfig config,
      final ClusterChangeExecutor clusterChangeExecutor,
      final MeterRegistry meterRegistry) {
    gossiperConfig = config;
    this.clusterChangeExecutor = clusterChangeExecutor;
    topologyMetrics = new TopologyMetrics(meterRegistry);
    topologyManagerMetrics = new TopologyManagerMetrics(meterRegistry);
    try {
      FileUtil.ensureDirectoryExists(dataRootDirectory);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to create data directory", e);
    }

    final MemberId localMemberId = memberShipService.getLocalMember().id();
    configurationFile = dataRootDirectory.resolve(TOPOLOGY_FILE_NAME);
    persistedClusterConfiguration =
        PersistedClusterConfiguration.ofFile(configurationFile, new ProtoBufSerializer());
    gossipActor = Actor.newActor().name("ClusterConfigGossip").build();
    managerActor = Actor.newActor().name("ClusterConfigManager").build();
    clusterConfigurationManager =
        new ClusterConfigurationManagerImpl(
            managerActor, localMemberId, persistedClusterConfiguration, topologyManagerMetrics);
    clusterConfigurationGossiper =
        new ClusterConfigurationGossiper(
            gossipActor,
            communicationService,
            memberShipService,
            new ProtoBufSerializer(),
            config,
            clusterConfigurationManager::onGossipReceived,
            topologyMetrics);
    isCoordinator = localMemberId.id().equals(COORDINATOR_ID);
    configurationChangeCoordinator =
        new ConfigurationChangeCoordinatorImpl(
            clusterConfigurationManager, localMemberId, managerActor);
    configurationRequestServer =
        new ClusterConfigurationRequestServer(
            communicationService,
            new ProtoBufSerializer(),
            new ClusterConfigurationManagementRequestsHandler(
                configurationChangeCoordinator, localMemberId, managerActor));

    clusterConfigurationManager.setConfigurationGossiper(
        clusterConfigurationGossiper::updateClusterConfiguration);
  }

  private ClusterConfigurationInitializer getNonCoordinatorInitializer(
      final StaticConfiguration staticConfiguration) {
    final var otherKnownMembers =
        staticConfiguration.clusterMembers().stream()
            .filter(m -> !m.equals(staticConfiguration.localMemberId()))
            .toList();
    return new FileInitializer(configurationFile, new ProtoBufSerializer())
        // Recover via sync to ensure that we don't gossip an uninitialized configuration.
        // This is important so that we don't silently revert to uninitialized configuration when
        // multiple members have a broken configuration file at the same time, for example because
        // of a serialization bug.
        .recover(
            PersistedConfigurationIsBroken.class,
            new SyncInitializer(
                gossiperConfig.syncInitializerDelay(),
                clusterConfigurationGossiper,
                otherKnownMembers,
                managerActor,
                clusterConfigurationGossiper::queryClusterConfiguration))
        .orThen(
            new GossipInitializer(
                clusterConfigurationGossiper,
                persistedClusterConfiguration,
                clusterConfigurationGossiper::updateClusterConfiguration,
                managerActor))
        .andThen(
            new ExporterStateInitializer(
                staticConfiguration.partitionConfig().exporting().exporters().keySet(),
                staticConfiguration.localMemberId(),
                managerActor))
        .andThen(new RoutingStateInitializer(staticConfiguration.partitionCount()));
    // This initializer does not set the cluster ID, as it is not required for non-coordinators.
    // Non-coordinators will receive the cluster ID from the coordinator via gossip.
  }

  private ClusterConfigurationInitializer getCoordinatorInitializer(
      final StaticConfiguration staticConfiguration) {
    final var otherKnownMembers =
        staticConfiguration.clusterMembers().stream()
            .filter(m -> !m.equals(staticConfiguration.localMemberId()))
            .toList();
    return new FileInitializer(configurationFile, new ProtoBufSerializer())
        .orThen(
            new SyncInitializer(
                gossiperConfig.syncInitializerDelay(),
                clusterConfigurationGossiper,
                otherKnownMembers,
                managerActor,
                clusterConfigurationGossiper::queryClusterConfiguration))
        .orThen(new StaticInitializer(staticConfiguration))
        .andThen(
            new ExporterStateInitializer(
                staticConfiguration.partitionConfig().exporting().exporters().keySet(),
                staticConfiguration.localMemberId(),
                managerActor))
        .andThen(new RoutingStateInitializer(staticConfiguration.partitionCount()))
        .andThen(new ClusterIdInitializer(staticConfiguration.clusterId()));
  }

  /** Starts ClusterConfigurationManager which initializes ClusterConfiguration */
  public ActorFuture<Void> start(
      final ActorSchedulingService actorSchedulingService,
      final StaticConfiguration staticConfiguration) {
    return startGossiper(actorSchedulingService)
        .andThen(
            () -> startClusterTopologyServices(actorSchedulingService, staticConfiguration),
            Runnable::run);
  }

  private ActorFuture<Void> startGossiper(final ActorSchedulingService actorSchedulingService) {
    return actorSchedulingService
        .submitActor(gossipActor)
        .andThen(clusterConfigurationGossiper::start, Runnable::run);
  }

  private CompletableActorFuture<Void> startClusterTopologyServices(
      final ActorSchedulingService actorSchedulingService,
      final StaticConfiguration staticConfiguration) {
    final var result = new CompletableActorFuture<Void>();
    final ClusterConfigurationInitializer clusterConfigurationInitializer =
        isCoordinator
            ? getCoordinatorInitializer(staticConfiguration)
            : getNonCoordinatorInitializer(staticConfiguration);

    configurationRequestServer.start();

    // Start gossiper first so that when ClusterConfigurationManager initializes the configuration,
    // it
    // can
    // immediately gossip it.
    actorSchedulingService
        .submitActor(managerActor)
        .onComplete(
            (ok, error) -> {
              if (error != null) {
                result.completeExceptionally(error);
              } else {
                clusterConfigurationManager
                    .start(clusterConfigurationInitializer)
                    .onComplete(result);
              }
            });
    return result;
  }

  public ActorFuture<ClusterConfiguration> getClusterTopology() {
    return clusterConfigurationManager.getClusterConfiguration();
  }

  public Optional<ConfigurationChangeCoordinator> getTopologyChangeCoordinator() {
    return Optional.ofNullable(configurationChangeCoordinator);
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    if (configurationRequestServer != null) {
      configurationRequestServer.close();
    }
    clusterConfigurationGossiper.close();
    return managerActor.closeAsync().andThen(gossipActor::closeAsync, Runnable::run);
  }

  public void registerPartitionChangeExecutors(
      final PartitionChangeExecutor partitionChangeExecutor,
      final PartitionScalingChangeExecutor partitionScalingChangeExecutor) {
    clusterConfigurationManager.registerTopologyChangeAppliers(
        new ConfigurationChangeAppliersImpl(
            partitionChangeExecutor,
            new NoopClusterMembershipChangeExecutor(),
            partitionScalingChangeExecutor,
            clusterChangeExecutor));
  }

  public void removePartitionChangeExecutor() {
    clusterConfigurationManager.removeTopologyChangeAppliers();
  }

  public void registerTopologyChangedListener(final InconsistentConfigurationListener listener) {
    clusterConfigurationManager.registerTopologyChangedListener(listener);
  }

  public void removeTopologyChangedListener() {
    clusterConfigurationManager.removeTopologyChangedListener();
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
