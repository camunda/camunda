/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.configuration;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.dynamic.configuration.ClusterConfigurationInitializer.FileInitializer;
import io.camunda.zeebe.dynamic.configuration.ClusterConfigurationInitializer.GossipInitializer;
import io.camunda.zeebe.dynamic.configuration.ClusterConfigurationInitializer.InitializerError.PersistedTopologyIsBroken;
import io.camunda.zeebe.dynamic.configuration.ClusterConfigurationInitializer.RollingUpdateAwareInitializerV83ToV84;
import io.camunda.zeebe.dynamic.configuration.ClusterConfigurationInitializer.StaticInitializer;
import io.camunda.zeebe.dynamic.configuration.ClusterConfigurationInitializer.SyncInitializer;
import io.camunda.zeebe.dynamic.configuration.ClusterConfigurationManager.InconsistentConfigurationListener;
import io.camunda.zeebe.dynamic.configuration.api.ClusterConfigurationManagementRequestsHandler;
import io.camunda.zeebe.dynamic.configuration.api.ClusterConfigurationRequestServer;
import io.camunda.zeebe.dynamic.configuration.changes.ConfigurationChangeAppliersImpl;
import io.camunda.zeebe.dynamic.configuration.changes.ConfigurationChangeCoordinator;
import io.camunda.zeebe.dynamic.configuration.changes.ConfigurationChangeCoordinatorImpl;
import io.camunda.zeebe.dynamic.configuration.changes.NoopClusterMembershipChangeExecutor;
import io.camunda.zeebe.dynamic.configuration.changes.PartitionChangeExecutor;
import io.camunda.zeebe.dynamic.configuration.gossip.ClusterConfigurationGossiper;
import io.camunda.zeebe.dynamic.configuration.gossip.ClusterConfigurationGossiperConfig;
import io.camunda.zeebe.dynamic.configuration.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.configuration.state.ClusterConfiguration;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.AsyncClosable;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;

public final class ClusterConfigurationManagerService
    implements ClusterConfigurationUpdateNotifier, AsyncClosable {
  // Use a node 0 as always the coordinator. Later we can make it configurable or allow changing it
  // dynamically.
  private static final String COORDINATOR_ID = "0";
  private static final String TOPOLOGY_FILE_NAME = ".topology.meta";
  private final ClusterConfigurationManagerImpl clusterTopologyManager;
  private final ClusterConfigurationGossiper clusterConfigurationGossiper;
  private final ClusterMembershipService memberShipService;
  private final boolean isCoordinator;
  private final PersistedClusterConfiguration persistedClusterConfiguration;
  private final Path topologyFile;
  private final ConfigurationChangeCoordinator configurationChangeCoordinator;
  private final ClusterConfigurationRequestServer topologyRequestServer;
  private final Actor gossipActor;
  private final Actor managerActor;

  public ClusterConfigurationManagerService(
      final Path dataRootDirectory,
      final ClusterCommunicationService communicationService,
      final ClusterMembershipService memberShipService,
      final ClusterConfigurationGossiperConfig config) {
    this.memberShipService = memberShipService;
    try {
      FileUtil.ensureDirectoryExists(dataRootDirectory);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to create data directory", e);
    }

    final MemberId localMemberId = memberShipService.getLocalMember().id();
    topologyFile = dataRootDirectory.resolve(TOPOLOGY_FILE_NAME);
    persistedClusterConfiguration =
        PersistedClusterConfiguration.ofFile(topologyFile, new ProtoBufSerializer());
    gossipActor = new Actor() {};
    managerActor = new Actor() {};
    clusterTopologyManager =
        new ClusterConfigurationManagerImpl(
            managerActor, localMemberId, persistedClusterConfiguration);
    clusterConfigurationGossiper =
        new ClusterConfigurationGossiper(
            gossipActor,
            communicationService,
            memberShipService,
            new ProtoBufSerializer(),
            config,
            clusterTopologyManager::onGossipReceived);
    isCoordinator = localMemberId.id().equals(COORDINATOR_ID);
    configurationChangeCoordinator =
        new ConfigurationChangeCoordinatorImpl(clusterTopologyManager, localMemberId, managerActor);
    topologyRequestServer =
        new ClusterConfigurationRequestServer(
            communicationService,
            new ProtoBufSerializer(),
            new ClusterConfigurationManagementRequestsHandler(
                configurationChangeCoordinator, localMemberId, managerActor));

    clusterTopologyManager.setTopologyGossiper(clusterConfigurationGossiper::updateClusterTopology);
  }

  private ClusterConfigurationInitializer getNonCoordinatorInitializer(
      final ClusterMembershipService membershipService,
      final StaticConfiguration staticConfiguration) {
    final var otherKnownMembers =
        staticConfiguration.clusterMembers().stream()
            .filter(m -> !m.equals(staticConfiguration.localMemberId()))
            .toList();
    return new FileInitializer(topologyFile, new ProtoBufSerializer())
        // Recover via sync to ensure that we don't gossip an uninitialized configuration.
        // This is important so that we don't silently revert to uninitialized configuration when
        // multiple members have a broken configuration file at the same time, for example because
        // of a serialization bug.
        .recover(
            PersistedTopologyIsBroken.class,
            new SyncInitializer(
                clusterConfigurationGossiper,
                otherKnownMembers,
                managerActor,
                clusterConfigurationGossiper::queryClusterTopology))
        // Only to support rolling update from 8.3 to 8.4. Should be removed after 8.4 release
        .orThen(
            new RollingUpdateAwareInitializerV83ToV84(
                membershipService, staticConfiguration, managerActor))
        .orThen(
            new GossipInitializer(
                clusterConfigurationGossiper,
                persistedClusterConfiguration,
                clusterConfigurationGossiper::updateClusterTopology,
                managerActor));
  }

  private ClusterConfigurationInitializer getCoordinatorInitializer(
      final StaticConfiguration staticConfiguration) {
    final var otherKnownMembers =
        staticConfiguration.clusterMembers().stream()
            .filter(m -> !m.equals(staticConfiguration.localMemberId()))
            .toList();
    return new FileInitializer(topologyFile, new ProtoBufSerializer())
        // Only to support rolling update from 8.3 to 8.4. Should be removed after 8.4 release
        .orThen(
            new RollingUpdateAwareInitializerV83ToV84(
                memberShipService, staticConfiguration, managerActor))
        .orThen(
            new SyncInitializer(
                clusterConfigurationGossiper,
                otherKnownMembers,
                managerActor,
                clusterConfigurationGossiper::queryClusterTopology))
        .orThen(new StaticInitializer(staticConfiguration));
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
            : getNonCoordinatorInitializer(memberShipService, staticConfiguration);

    topologyRequestServer.start();

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
                clusterTopologyManager.start(clusterConfigurationInitializer).onComplete(result);
              }
            });
    return result;
  }

  public ActorFuture<ClusterConfiguration> getClusterTopology() {
    return clusterTopologyManager.getClusterConfiguration();
  }

  public Optional<ConfigurationChangeCoordinator> getTopologyChangeCoordinator() {
    return Optional.ofNullable(configurationChangeCoordinator);
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    if (topologyRequestServer != null) {
      topologyRequestServer.close();
    }
    clusterConfigurationGossiper.close();
    return managerActor.closeAsync().andThen(gossipActor::closeAsync, Runnable::run);
  }

  public void registerPartitionChangeExecutor(
      final PartitionChangeExecutor partitionChangeExecutor) {
    // TODO: pass concrete TopologyMembershipChangeExecutor
    clusterTopologyManager.registerTopologyChangeAppliers(
        new ConfigurationChangeAppliersImpl(
            partitionChangeExecutor, new NoopClusterMembershipChangeExecutor()));
  }

  public void removePartitionChangeExecutor() {
    clusterTopologyManager.removeTopologyChangeAppliers();
  }

  public void registerTopologyChangedListener(final InconsistentConfigurationListener listener) {
    clusterTopologyManager.registerTopologyChangedListener(listener);
  }

  public void removeTopologyChangedListener() {
    clusterTopologyManager.removeTopologyChangedListener();
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
