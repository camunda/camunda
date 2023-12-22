/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.AsyncClosable;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.topology.TopologyInitializer.FileInitializer;
import io.camunda.zeebe.topology.TopologyInitializer.GossipInitializer;
import io.camunda.zeebe.topology.TopologyInitializer.InitializerError.PersistedTopologyIsBroken;
import io.camunda.zeebe.topology.TopologyInitializer.RollingUpdateAwareInitializerV83ToV84;
import io.camunda.zeebe.topology.TopologyInitializer.StaticInitializer;
import io.camunda.zeebe.topology.TopologyInitializer.SyncInitializer;
import io.camunda.zeebe.topology.api.TopologyManagementRequestsHandler;
import io.camunda.zeebe.topology.api.TopologyRequestServer;
import io.camunda.zeebe.topology.changes.NoopTopologyMembershipChangeExecutor;
import io.camunda.zeebe.topology.changes.PartitionChangeExecutor;
import io.camunda.zeebe.topology.changes.TopologyChangeAppliersImpl;
import io.camunda.zeebe.topology.changes.TopologyChangeCoordinator;
import io.camunda.zeebe.topology.changes.TopologyChangeCoordinatorImpl;
import io.camunda.zeebe.topology.gossip.ClusterTopologyGossiper;
import io.camunda.zeebe.topology.gossip.ClusterTopologyGossiperConfig;
import io.camunda.zeebe.topology.serializer.ProtoBufSerializer;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;

public final class ClusterTopologyManagerService implements TopologyUpdateNotifier, AsyncClosable {
  // Use a node 0 as always the coordinator. Later we can make it configurable or allow changing it
  // dynamically.
  private static final String COORDINATOR_ID = "0";
  private static final String TOPOLOGY_FILE_NAME = ".topology.meta";
  private final ClusterTopologyManagerImpl clusterTopologyManager;
  private final ClusterTopologyGossiper clusterTopologyGossiper;
  private final ClusterMembershipService memberShipService;
  private final boolean isCoordinator;
  private final PersistedClusterTopology persistedClusterTopology;
  private final Path topologyFile;
  private TopologyChangeCoordinator topologyChangeCoordinator;
  private TopologyRequestServer topologyRequestServer;
  private final Actor gossipActor;
  private final Actor managerActor;

  public ClusterTopologyManagerService(
      final Path dataRootDirectory,
      final ClusterCommunicationService communicationService,
      final ClusterMembershipService memberShipService,
      final ClusterTopologyGossiperConfig config) {
    this.memberShipService = memberShipService;
    try {
      FileUtil.ensureDirectoryExists(dataRootDirectory);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to create data directory", e);
    }

    final MemberId localMemberId = memberShipService.getLocalMember().id();
    topologyFile = dataRootDirectory.resolve(TOPOLOGY_FILE_NAME);
    persistedClusterTopology =
        PersistedClusterTopology.ofFile(topologyFile, new ProtoBufSerializer());
    gossipActor = new Actor() {};
    managerActor = new Actor() {};
    clusterTopologyManager =
        new ClusterTopologyManagerImpl(managerActor, localMemberId, persistedClusterTopology);
    clusterTopologyGossiper =
        new ClusterTopologyGossiper(
            gossipActor,
            communicationService,
            memberShipService,
            new ProtoBufSerializer(),
            config,
            clusterTopologyManager::onGossipReceived);
    isCoordinator = localMemberId.id().equals(COORDINATOR_ID);
    if (isCoordinator) {
      // Only a coordinator can start topology change
      topologyChangeCoordinator =
          new TopologyChangeCoordinatorImpl(clusterTopologyManager, managerActor);
      topologyRequestServer =
          new TopologyRequestServer(
              communicationService,
              new ProtoBufSerializer(),
              new TopologyManagementRequestsHandler(topologyChangeCoordinator, managerActor),
              managerActor);
    }
    clusterTopologyManager.setTopologyGossiper(clusterTopologyGossiper::updateClusterTopology);
  }

  private TopologyInitializer getNonCoordinatorInitializer(
      final ClusterMembershipService membershipService,
      final StaticConfiguration staticConfiguration) {
    final var otherKnownMembers =
        staticConfiguration.clusterMembers().stream()
            .filter(m -> !m.equals(staticConfiguration.localMemberId()))
            .toList();
    return new FileInitializer(topologyFile, new ProtoBufSerializer())
        // Recover via sync to ensure that we don't gossip an uninitialized topology.
        // This is important so that we don't silently revert to uninitialized topology when
        // multiple members have a broken topology file at the same time, for example because of
        // a serialization bug.
        .recover(
            PersistedTopologyIsBroken.class,
            new SyncInitializer(
                clusterTopologyGossiper,
                otherKnownMembers,
                managerActor,
                clusterTopologyGossiper::queryClusterTopology))
        // Only to support rolling update from 8.3 to 8.4. Should be removed after 8.4 release
        .orThen(
            new RollingUpdateAwareInitializerV83ToV84(
                membershipService, staticConfiguration, managerActor))
        .orThen(
            new GossipInitializer(
                clusterTopologyGossiper,
                persistedClusterTopology,
                clusterTopologyGossiper::updateClusterTopology,
                managerActor));
  }

  private TopologyInitializer getCoordinatorInitializer(
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
                clusterTopologyGossiper,
                otherKnownMembers,
                managerActor,
                clusterTopologyGossiper::queryClusterTopology))
        .orThen(new StaticInitializer(staticConfiguration));
  }

  /** Starts ClusterTopologyManager which initializes ClusterTopology */
  public ActorFuture<Void> start(
      final ActorSchedulingService actorSchedulingService,
      final StaticConfiguration staticConfiguration) {
    final var startFuture = new CompletableActorFuture<Void>();

    startGossiper(actorSchedulingService)
        .onComplete(
            (ok, error) -> {
              if (error != null) {
                startFuture.completeExceptionally(error);
              } else {
                startClusterTopologyServices(actorSchedulingService, staticConfiguration)
                    .onComplete(startFuture);
              }
            });

    return startFuture;
  }

  private ActorFuture<Void> startGossiper(final ActorSchedulingService actorSchedulingService) {
    final var result = new CompletableActorFuture<Void>();
    actorSchedulingService
        .submitActor(gossipActor)
        .onComplete(
            (ok, error) -> {
              if (error != null) {
                result.completeExceptionally(error);
              } else {
                clusterTopologyGossiper.start().onComplete(result);
              }
            });
    return result;
  }

  private ActorFuture<Void> startClusterTopologyServices(
      final ActorSchedulingService actorSchedulingService,
      final StaticConfiguration staticConfiguration) {
    final var result = new CompletableActorFuture<Void>();
    final TopologyInitializer topologyInitializer =
        isCoordinator
            ? getCoordinatorInitializer(staticConfiguration)
            : getNonCoordinatorInitializer(memberShipService, staticConfiguration);

    if (topologyRequestServer != null) {
      topologyRequestServer.start();
    }

    // Start gossiper first so that when ClusterTopologyManager initializes the topology, it can
    // immediately gossip it.
    actorSchedulingService
        .submitActor(managerActor)
        .onComplete(
            (ok, error) -> {
              if (error != null) {
                result.completeExceptionally(error);
              } else {
                clusterTopologyManager.start(topologyInitializer).onComplete(result);
              }
            });
    return result;
  }

  public ActorFuture<ClusterTopology> getClusterTopology() {
    return clusterTopologyManager.getClusterTopology();
  }

  public Optional<TopologyChangeCoordinator> getTopologyChangeCoordinator() {
    return Optional.ofNullable(topologyChangeCoordinator);
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    if (topologyRequestServer != null) {
      topologyRequestServer.close();
    }

    final var result = new CompletableActorFuture<Void>();
    clusterTopologyGossiper.close();
    managerActor
        .closeAsync()
        .onComplete(
            (ok, error) -> {
              if (error != null) {
                result.completeExceptionally(error);
              } else {
                gossipActor.closeAsync().onComplete(result);
              }
            });
    return result;
  }

  public void registerPartitionChangeExecutor(
      final PartitionChangeExecutor partitionChangeExecutor) {
    // TODO: pass concrete TopologyMembershipChangeExecutor
    clusterTopologyManager.registerTopologyChangeAppliers(
        new TopologyChangeAppliersImpl(
            partitionChangeExecutor, new NoopTopologyMembershipChangeExecutor()));
  }

  public void removePartitionChangeExecutor() {
    clusterTopologyManager.removeTopologyChangeAppliers();
  }

  @Override
  public void addUpdateListener(final TopologyUpdateListener listener) {
    clusterTopologyGossiper.addUpdateListener(listener);
  }

  @Override
  public void removeUpdateListener(final TopologyUpdateListener listener) {
    clusterTopologyGossiper.removeUpdateListener(listener);
  }
}
