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
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public final class ClusterTopologyManagerService extends Actor {
  // Use a node 0 as always the coordinator. Later we can make it configurable or allow changing it
  // dynamically.
  private static final String COORDINATOR_ID = "0";
  private final ClusterTopologyManager clusterTopologyManager;
  private final ClusterTopologyGossiper clusterTopologyGossiper;

  private final boolean isCoordinator;
  private final PersistedClusterTopology persistedClusterTopology;
  private final Path topologyFile;

  public ClusterTopologyManagerService(
      final Path topologyFile,
      final ClusterCommunicationService communicationService,
      final ClusterMembershipService memberShipService,
      final ClusterTopologyGossiperConfig config) {
    persistedClusterTopology = new PersistedClusterTopology(topologyFile, new ProtoBufSerializer());
    this.topologyFile = topologyFile;
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
    return new FileInitializer(topologyFile, new ProtoBufSerializer())
        .orThen(
            new GossipInitializer(
                clusterTopologyGossiper,
                persistedClusterTopology,
                clusterTopologyGossiper::updateClusterTopology,
                this));
  }

  private TopologyInitializer getCoordinatorInitializer(
      final Supplier<Set<PartitionMetadata>> staticPartitionResolver,
      final List<MemberId> knownMembers) {
    return new FileInitializer(topologyFile, new ProtoBufSerializer())
        .orThen(
            new SyncInitializer(
                clusterTopologyGossiper,
                knownMembers,
                this,
                clusterTopologyGossiper::queryClusterTopology))
        .orThen(new StaticInitializer(staticPartitionResolver));
  }

  /**
   * NOTE: Do not integrate this into Broker startup steps until data layout and serialization
   * `ClusterTopology` is finalized. This is to prevent any backward incompatible changes to be
   * included in a released version.
   *
   * <p>Starts ClusterTopologyManager which initializes ClusterTopology
   */
  ActorFuture<Void> start(
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
}
