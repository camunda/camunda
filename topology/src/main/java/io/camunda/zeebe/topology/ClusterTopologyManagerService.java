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
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.topology.TopologyInitializer.FileInitializer;
import io.camunda.zeebe.topology.TopologyInitializer.GossipInitializer;
import io.camunda.zeebe.topology.TopologyInitializer.StaticInitializer;
import io.camunda.zeebe.topology.TopologyInitializer.SyncInitializer;
import io.camunda.zeebe.topology.gossip.ClusterTopologyGossiper;
import io.camunda.zeebe.topology.gossip.ClusterTopologyGossiperConfig;
import io.camunda.zeebe.topology.serializer.ProtoBufSerializer;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public final class ClusterTopologyManagerService extends Actor {
  // Use a node 0 as always the coordinator. Later we can make it configurable or allow changing it
  // dynamically.
  private static final String COORDINATOR_ID = "0";
  private static final String TOPOLOGY_FILE_NAME = ".topology.meta";
  private final ClusterTopologyManager clusterTopologyManager;
  private final ClusterTopologyGossiper clusterTopologyGossiper;

  private final boolean isCoordinator;
  private final PersistedClusterTopology persistedClusterTopology;

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

    final var topologyFile = dataRootDirectory.resolve(TOPOLOGY_FILE_NAME);
    persistedClusterTopology = new PersistedClusterTopology(topologyFile, new ProtoBufSerializer());
    clusterTopologyManager = new ClusterTopologyManager(this, persistedClusterTopology);
    clusterTopologyGossiper =
        new ClusterTopologyGossiper(
            this,
            communicationService,
            memberShipService,
            new ProtoBufSerializer(),
            config,
            clusterTopologyManager::onGossipReceived);
    clusterTopologyManager.setTopologyGossiper(clusterTopologyGossiper::updateClusterTopology);

    isCoordinator = memberShipService.getLocalMember().id().id().equals(COORDINATOR_ID);
  }

  private TopologyInitializer getNonCoordinatorInitializer() {
    return new FileInitializer(persistedClusterTopology)
        .orThen(
            new GossipInitializer(
                persistedClusterTopology, clusterTopologyGossiper::updateClusterTopology));
  }

  private TopologyInitializer getCoordinatorInitializer(
      final Supplier<Set<PartitionMetadata>> staticPartitionResolver,
      final List<MemberId> knownMembers) {
    return new FileInitializer(persistedClusterTopology)
        .orThen(
            new SyncInitializer(
                persistedClusterTopology,
                knownMembers,
                this,
                clusterTopologyGossiper::queryClusterTopology))
        .orThen(new StaticInitializer(staticPartitionResolver, persistedClusterTopology));
  }

  /** Starts ClusterTopologyManager which initializes ClusterTopology */
  public ActorFuture<Void> start(
      final ActorSchedulingService actorSchedulingService,
      final List<MemberId> clusterMembers,
      final Supplier<Set<PartitionMetadata>> partitionDistributionResolver) {
    final var startFuture = new CompletableActorFuture<Void>();
    actorSchedulingService
        .submitActor(this)
        .onComplete(
            (ignore, error) -> {
              if (error == null) {
                actor.run(
                    () ->
                        startClusterTopologyServices(
                            partitionDistributionResolver, clusterMembers, startFuture));
              } else {
                startFuture.completeExceptionally(error);
              }
            });

    return startFuture;
  }

  private void startClusterTopologyServices(
      final Supplier<Set<PartitionMetadata>> partitionDistributionResolver,
      final List<MemberId> knownMembers,
      final CompletableActorFuture<Void> startFuture) {
    final TopologyInitializer topologyInitializer =
        isCoordinator
            ? getCoordinatorInitializer(partitionDistributionResolver, knownMembers)
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
}
