/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.topology.PersistedClusterTopology.Listener;
import io.camunda.zeebe.topology.state.ClusterChangePlan;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.state.PartitionState;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Initialized topology * */
public interface TopologyInitializer {

  /**
   * Initializes the cluster topology.
   *
   * @return a future when completed with true indicates that the cluster topology is initialized
   *     successfully. Otherwise, the topology is not initialized.
   */
  ActorFuture<Boolean> initialize();

  /**
   * Chain initializers in oder. If this.initialize did not successfully initialize, then the
   * topology is initialized using the provided initializer
   *
   * @param after the next initializer used to initialize topology if the current one did not
   *     succeed.
   * @return a chained TopologyInitializer
   */
  default TopologyInitializer orThen(final TopologyInitializer after) {
    final TopologyInitializer actual = this;
    return () -> {
      final ActorFuture<Boolean> chainedInitialize = new CompletableActorFuture<>();
      actual
          .initialize()
          .onComplete(
              (initialized, error) -> {
                if (error != null || !initialized) {
                  after.initialize().onComplete(chainedInitialize);
                } else {
                  chainedInitialize.complete(initialized);
                }
              });
      return chainedInitialize;
    };
  }

  /** Initialized topology from the locally persisted topology */
  class FileInitializer implements TopologyInitializer {

    private final PersistedClusterTopology persistedClusterTopology;

    public FileInitializer(final PersistedClusterTopology persistedClusterTopology) {
      this.persistedClusterTopology = persistedClusterTopology;
    }

    @Override
    public ActorFuture<Boolean> initialize() {
      try {
        persistedClusterTopology.tryInitialize();
        return CompletableActorFuture.completed(!persistedClusterTopology.isUninitialized());
      } catch (final Exception e) {
        return CompletableActorFuture.completedExceptionally(e);
      }
    }
  }

  /**
   * Initializes local topology from the topology received from other members via gossip.
   * Initialization completes successfully, when it receives a valid initialized topology from any
   * member. The future returned by initialize is never completed until a valid topology is
   * received.
   */
  class GossipInitializer implements TopologyInitializer, Listener {

    private static final Logger LOGGER = LoggerFactory.getLogger(GossipInitializer.class);
    private final PersistedClusterTopology persistedClusterTopology;
    private final Consumer<ClusterTopology> topologyGossiper;
    private final ActorFuture<Boolean> initialized;

    public GossipInitializer(
        final PersistedClusterTopology persistedClusterTopology,
        final Consumer<ClusterTopology> topologyGossiper) {
      this.persistedClusterTopology = persistedClusterTopology;
      this.topologyGossiper = topologyGossiper;
      initialized = new CompletableActorFuture<>();
    }

    @Override
    public ActorFuture<Boolean> initialize() {
      persistedClusterTopology.addUpdateListener(this);
      onTopologyUpdated(persistedClusterTopology.getTopology());
      if (persistedClusterTopology.isUninitialized()) {
        // When uninitialized, the member should gossip uninitialized topology so that the
        // coordinator is not waiting in SyncInitializer forever.
        topologyGossiper.accept(persistedClusterTopology.getTopology());
      }
      return initialized;
    }

    @Override
    public void onTopologyUpdated(final ClusterTopology clusterTopology) {
      if (!clusterTopology.isUninitialized()) {
        LOGGER.debug("Received cluster topology {} via gossip.", clusterTopology);
        initialized.complete(true);
        persistedClusterTopology.removeUpdateListener(this);
      }
    }
  }

  /**
   * Initializes topology by sending sync requests to other members. If any of them return a valid
   * topology, it will be initialized. If any of them returns an uninitialized topology, the future
   * returned by initialize completes as failed.
   */
  class SyncInitializer implements TopologyInitializer, Listener {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncInitializer.class);
    private static final Duration SYNC_QUERY_RETRY_DELAY = Duration.ofSeconds(5);
    private final PersistedClusterTopology persistedClusterTopology;
    private final ActorFuture<Boolean> initialized;
    private final List<MemberId> knownMembersToSync;
    private final ConcurrencyControl executor;
    private final Function<MemberId, ActorFuture<ClusterTopology>> syncRequester;

    public SyncInitializer(
        final PersistedClusterTopology persistedClusterTopology,
        final List<MemberId> knownMembersToSync,
        final ConcurrencyControl executor,
        final Function<MemberId, ActorFuture<ClusterTopology>> syncRequester) {
      this.persistedClusterTopology = persistedClusterTopology;
      this.knownMembersToSync = knownMembersToSync;
      this.executor = executor;
      this.syncRequester = syncRequester;
      initialized = new CompletableActorFuture<>();
    }

    @Override
    public ActorFuture<Boolean> initialize() {
      if (knownMembersToSync.isEmpty()) {
        initialized.complete(false);
      } else {
        LOGGER.debug("Querying members {} before initializing ClusterTopology", knownMembersToSync);
        persistedClusterTopology.addUpdateListener(this);
        onTopologyUpdated(persistedClusterTopology.getTopology());
        knownMembersToSync.forEach(this::tryInitializeFrom);
      }
      return initialized;
    }

    private void tryInitializeFrom(final MemberId memberId) {
      requestSync(memberId)
          .onComplete(
              (topology, error) -> {
                if (initialized.isDone()) {
                  return;
                }
                if (error == null && topology != null) {
                  if (topology.isUninitialized()) {
                    LOGGER.trace("Cluster topology is uninitialized in {}", memberId);
                    initialized.complete(false);
                    return;
                  }
                  try {
                    LOGGER.debug(
                        "Received cluster topology {} from {}", knownMembersToSync, memberId);
                    persistedClusterTopology.update(topology);
                    onTopologyUpdated(topology);
                    return;
                  } catch (final IOException e) {
                    LOGGER.warn("Failed to persist received topology");
                  }
                }
                // retry
                if (!initialized.isDone()) {
                  LOGGER.trace(
                      "Failed to get a response for cluster topology sync query to {}. Will retry.",
                      memberId);

                  executor.schedule(SYNC_QUERY_RETRY_DELAY, () -> tryInitializeFrom(memberId));
                }
              });
    }

    private ActorFuture<ClusterTopology> requestSync(final MemberId memberId) {
      return syncRequester.apply(memberId);
    }

    @Override
    public void onTopologyUpdated(final ClusterTopology clusterTopology) {
      if (initialized.isDone()) {
        return;
      }
      if (!clusterTopology.isUninitialized()) {
        initialized.complete(true);
        persistedClusterTopology.removeUpdateListener(this);
      }
    }
  }

  /** Initialized topology from the given static partition distribution */
  class StaticInitializer implements TopologyInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(StaticInitializer.class);
    private final Supplier<Set<PartitionMetadata>> staticPartitionResolver;
    private final PersistedClusterTopology persistedClusterTopology;

    public StaticInitializer(
        final Supplier<Set<PartitionMetadata>> staticPartitionResolver,
        final PersistedClusterTopology persistedClusterTopology) {
      this.staticPartitionResolver = staticPartitionResolver;
      this.persistedClusterTopology = persistedClusterTopology;
    }

    @Override
    public ActorFuture<Boolean> initialize() {
      final var partitionDistribution = staticPartitionResolver.get();

      final var partitionsOwnedByMembers =
          partitionDistribution.stream()
              .flatMap(
                  p ->
                      p.members().stream()
                          .map(m -> Map.entry(m, Map.entry(p.id().id(), p.getPriority(m)))))
              .collect(
                  Collectors.groupingBy(
                      Entry::getKey,
                      Collectors.toMap(
                          e -> e.getValue().getKey(),
                          e -> PartitionState.active(e.getValue().getValue()))));

      final var memberStates =
          partitionsOwnedByMembers.entrySet().stream()
              .collect(
                  Collectors.toMap(
                      Entry::getKey, e -> MemberState.initializeAsActive(e.getValue())));

      final var topology = new ClusterTopology(0, memberStates, ClusterChangePlan.empty());
      LOGGER.debug("Generated cluster topology from provided configuration. {}", topology);
      try {
        persistedClusterTopology.update(topology);
      } catch (final IOException e) {
        return CompletableActorFuture.completedExceptionally(e);
      }

      return CompletableActorFuture.completed(true);
    }
  }
}
