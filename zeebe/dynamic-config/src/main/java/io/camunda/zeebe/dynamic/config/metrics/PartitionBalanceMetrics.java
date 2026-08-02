/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.metrics;

import io.camunda.cluster.PartitionId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationUpdateNotifier.ClusterConfigurationUpdateListener;
import io.camunda.zeebe.dynamic.config.rebalance.PartitionLeaders;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Reports, for each partition, whether leadership is where the cluster configuration wants it.
 *
 * <p>This answers the question a rebalance exists to fix, so it deliberately does not depend on one
 * having run: it is published by every member from the moment it has a configuration to compare
 * against, whether or not the cluster has ever been rebalanced, and it keeps being published after
 * a rebalance has ended and after the coordinator has moved.
 *
 * <p>Each gauge reads its answer when the registry is scraped rather than being pushed a value.
 * Leadership moves on its own, by election as much as by transfer, so a pushed value would need
 * every source of a leadership change to remember to push - and would be wrong in between. The
 * comparison is two lookups and an equality, so doing it per scrape costs nothing worth saving.
 */
@NullMarked
public final class PartitionBalanceMetrics implements ClusterConfigurationUpdateListener {

  private final MeterRegistry registry;
  private final PartitionLeaders partitionLeaders;
  private final Map<PartitionId, Meter.Id> gauges = new HashMap<>();

  /** Read on every scrape, written whenever a new configuration arrives. */
  private volatile @Nullable ClusterConfiguration configuration;

  public PartitionBalanceMetrics(
      final MeterRegistry registry, final PartitionLeaders partitionLeaders) {
    this.registry = registry;
    this.partitionLeaders = partitionLeaders;
  }

  /**
   * Follows the configuration's partitions, so that a member reports on the partitions the cluster
   * has now rather than on every partition it has ever heard of.
   */
  @Override
  public synchronized void onClusterConfigurationUpdated(final ClusterConfiguration updated) {
    if (updated.isUninitialized()) {
      // Nothing to be balanced against until the configuration names the partitions and who may
      // lead them.
      return;
    }
    configuration = updated;
    // The legacy cluster configuration holds a single partition group, so every partition it names
    // belongs to the default physical tenant.
    final Set<PartitionId> current =
        updated
            .partitionIds()
            .mapToObj(
                partitionId ->
                    new PartitionId(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, partitionId))
            .collect(Collectors.toSet());

    final var gone = gauges.entrySet().iterator();
    while (gone.hasNext()) {
      final var gauge = gone.next();
      if (!current.contains(gauge.getKey())) {
        registry.remove(gauge.getValue());
        gone.remove();
      }
    }
    current.forEach(partition -> gauges.computeIfAbsent(partition, this::register));
  }

  private Meter.Id register(final PartitionId partition) {
    return Gauge.builder(
            PartitionBalanceMetricsDoc.PARTITION_BALANCED.getName(),
            () -> isBalanced(partition) ? 1 : 0)
        .description(PartitionBalanceMetricsDoc.PARTITION_BALANCED.getDescription())
        .tags(PartitionKeyNames.tags(partition))
        .register(registry)
        .getId();
  }

  /**
   * A partition with no leader, or one the configuration gives no eligible member, counts as
   * unbalanced: in both cases leadership is not where it is wanted, and reporting otherwise would
   * hide the very state an operator is watching for.
   */
  private boolean isBalanced(final PartitionId partition) {
    final var known = configuration;
    if (known == null) {
      return false;
    }
    final var desiredLeader = known.getPrimaryMemberForPartition(partition.number());
    final var currentLeader = partitionLeaders.currentLeader(partition.group(), partition.number());
    return desiredLeader.isPresent() && desiredLeader.equals(currentLeader);
  }
}
