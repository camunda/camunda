/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.clustering.topology;

import io.camunda.zeebe.broker.partitioning.topology.PartitionDistribution;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Responsible for initializing ClusterTopology and managing ClusterTopology changes. */
final class ClusterTopologyManager {
  private static final Logger LOG = LoggerFactory.getLogger(ClusterTopologyManager.class);

  private final ConcurrencyControl executor;
  private final PersistedClusterTopology persistedClusterTopology;

  ClusterTopologyManager(
      final ConcurrencyControl executor, final PersistedClusterTopology persistedClusterTopology) {
    this.executor = executor;
    this.persistedClusterTopology = persistedClusterTopology;
  }

  ActorFuture<ClusterTopology> getClusterTopology() {
    return executor.call(persistedClusterTopology::getTopology);
  }

  ActorFuture<Void> start(final Supplier<PartitionDistribution> staticParitionResolver) {
    final ActorFuture<Void> startFuture = executor.createFuture();

    executor.run(
        () -> {
          try {
            initialize(staticParitionResolver);
            startFuture.complete(null);
          } catch (final Exception e) {
            LOG.error("Failed to initialize ClusterTopology", e);
            startFuture.completeExceptionally(e);
          }
        });

    return startFuture;
  }

  private void initialize(final Supplier<PartitionDistribution> staticPartitionResolver)
      throws IOException {
    persistedClusterTopology.initialize();
    if (persistedClusterTopology.getTopology() == null) {
      final var topology = initializeFromConfig(staticPartitionResolver);
      persistedClusterTopology.update(topology);
      LOG.info(
          "Initialized ClusterTopology from BrokerCfg {}", persistedClusterTopology.getTopology());
    }
  }

  private ClusterTopology initializeFromConfig(
      final Supplier<PartitionDistribution> staticPartitionResolver) throws IOException {
    final var partitionDistribution = staticPartitionResolver.get();

    final var partitionsOwnedByMembers =
        partitionDistribution.partitions().stream()
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
                Collectors.toMap(Entry::getKey, e -> MemberState.initializeAsActive(e.getValue())));

    final var topology = new ClusterTopology(0, memberStates, ClusterChangePlan.empty());

    updateLocalTopology(topology);
    return topology;
  }

  private void updateLocalTopology(final ClusterTopology topology) throws IOException {
    persistedClusterTopology.update(topology);
  }
}
