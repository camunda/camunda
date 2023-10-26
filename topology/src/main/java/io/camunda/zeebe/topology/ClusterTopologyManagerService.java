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
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.topology.TopologyInitializer.FileInitializer;
import io.camunda.zeebe.topology.TopologyInitializer.GossipInitializer;
import io.camunda.zeebe.topology.TopologyInitializer.StaticInitializer;
import io.camunda.zeebe.topology.TopologyInitializer.SyncInitializer;
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

public final class ClusterTopologyManagerService extends Actor implements TopologyUpdateNotifier {
  // Use a node 0 as always the coordinator. Later we can make it configurable or allow changing it
  // dynamically.
  private static final String COORDINATOR_ID = "0";
  private static final String TOPOLOGY_FILE_NAME = ".topology.meta";
  private final ClusterTopologyManagerImpl clusterTopologyManager;
  private final ClusterTopologyGossiper clusterTopologyGossiper;

  private final boolean isCoordinator;
  private final PersistedClusterTopology persistedClusterTopology;
  private final Path topologyFile;

  public ClusterTopologyManagerService(
      final Path dataRootDirectory,
      final ClusterCommunicationService communicationService,
      final ClusterMembershipService memberShipService,
      final ClusterTopologyGossiperConfig config) {
    try {
      FileUtil.ensureDirectoryExists(dataRootDirectory);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to create data directory", e);
    }

    final MemberId localMemberId = memberShipService.getLocalMember().id();
    topologyFile = dataRootDirectory.resolve(TOPOLOGY_FILE_NAME);
    persistedClusterTopology = new PersistedClusterTopology(topologyFile, new ProtoBufSerializer());
    clusterTopologyManager =
        new ClusterTopologyManagerImpl(this, localMemberId, persistedClusterTopology);
    clusterTopologyGossiper =
        new ClusterTopologyGossiper(
            this,
            communicationService,
            memberShipService,
            new ProtoBufSerializer(),
            config,
            clusterTopologyManager::onGossipReceived);
    clusterTopologyManager.setTopologyGossiper(clusterTopologyGossiper::updateClusterTopology);

    isCoordinator = localMemberId.id().equals(COORDINATOR_ID);
  }

  private TopologyInitializer getNonCoordinatorInitializer() {
    return new FileInitializer(topologyFile, new ProtoBufSerializer())
        .orThen(
            new GossipInitializer(
                clusterTopologyGossiper,
                persistedClusterTopology,
                clusterTopologyGossiper::updateClusterTopology,
                this));
  }

  private TopologyInitializer getCoordinatorInitializer(
      final StaticConfiguration staticConfiguration) {
    final var knownMembers =
        staticConfiguration.clusterMembers().stream()
            .filter(m -> !m.equals(staticConfiguration.localMemberId()))
            .toList();
    return new FileInitializer(topologyFile, new ProtoBufSerializer())
        .orThen(
            new SyncInitializer(
                clusterTopologyGossiper,
                knownMembers,
                this,
                clusterTopologyGossiper::queryClusterTopology))
        .orThen(new StaticInitializer(staticConfiguration));
  }

  /** Starts ClusterTopologyManager which initializes ClusterTopology */
  public ActorFuture<Void> start(
      final ActorSchedulingService actorSchedulingService,
      final StaticConfiguration staticConfiguration) {
    final var startFuture = new CompletableActorFuture<Void>();
    actorSchedulingService
        .submitActor(this)
        .onComplete(
            (ignore, error) -> {
              if (error == null) {
                actor.run(() -> startClusterTopologyServices(staticConfiguration, startFuture));
              } else {
                startFuture.completeExceptionally(error);
              }
            });

    return startFuture;
  }

  private void startClusterTopologyServices(
      final StaticConfiguration staticConfiguration,
      final CompletableActorFuture<Void> startFuture) {
    final TopologyInitializer topologyInitializer =
        isCoordinator
            ? getCoordinatorInitializer(staticConfiguration)
            : getNonCoordinatorInitializer();
    // Start gossiper first so that when ClusterTopologyManager initializes the topology, it can
    // immediately gossip it.
    clusterTopologyGossiper
        .start()
        .onComplete(
            (ignore, error) -> {
              if (error != null) {
                startFuture.completeExceptionally(error);
              } else {
                clusterTopologyManager.start(topologyInitializer).onComplete(startFuture);
              }
            });
  }

  public ActorFuture<ClusterTopology> getClusterTopology() {
    return clusterTopologyManager.getClusterTopology();
  }

  public Optional<TopologyChangeCoordinator> getTopologyChangeCoordinator() {
    // Only a coordinator can start topology change
    return isCoordinator
        // create new instance every time, as we do not expect to make topology changes very often.
        // So there is no need to keep an instance in the field unnecessarily.
        ? Optional.of(new TopologyChangeCoordinatorImpl(clusterTopologyManager, this))
        : Optional.empty();
  }

  @Override
  protected void onActorClosing() {
    clusterTopologyGossiper.closeAsync();
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
