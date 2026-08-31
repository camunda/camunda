/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationUpdateNotifier.ClusterConfigurationUpdateListener;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Reports, for each partition of every physical tenant's partition group, whether leadership is
 * where the cluster configuration wants it.
 */
@NullMarked
public final class PartitionBalanceMetrics implements ClusterConfigurationUpdateListener {

  private final MeterRegistry registry;
  private final PartitionLeaders partitionLeaders;
  private final Map<PartitionId, Gauge> gauges = new HashMap<>();

  private volatile @Nullable CurrentClusterConfiguration configuration;

  public PartitionBalanceMetrics(
      final MeterRegistry registry, final PartitionLeaders partitionLeaders) {
    this.registry = registry;
    this.partitionLeaders = partitionLeaders;
  }

  @Override
  public void onClusterConfigurationUpdated(final ClusterConfiguration clusterConfiguration) {
    onClusterConfigurationUpdated(CurrentClusterConfiguration.fromLegacy(clusterConfiguration));
  }

  @Override
  public synchronized void onClusterConfigurationUpdated(
      final CurrentClusterConfiguration updated) {
    if (updated.isUninitialized()) {
      return;
    }
    configuration = updated;
    final Set<PartitionId> current =
        updated.activePartitionGroups().entrySet().stream()
            .flatMap(
                entry ->
                    entry
                        .getValue()
                        .partitionIds()
                        .mapToObj(partitionId -> new PartitionId(entry.getKey(), partitionId)))
            .collect(java.util.stream.Collectors.toSet());

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

  private Gauge register(final PartitionId partition) {
    return Gauge.builder(
            PartitionBalanceMetricsDoc.PARTITION_BALANCED.getName(),
            () -> isBalanced(partition) ? 1 : 0)
        .description(PartitionBalanceMetricsDoc.PARTITION_BALANCED.getDescription())
        .tags(PartitionKeyNames.tags(partition))
        .register(registry);
  }

  private boolean isBalanced(final PartitionId partition) {
    final var known = configuration;
    if (known == null) {
      return false;
    }
    final var group = known.partitionGroups().get(partition.group());
    if (group == null || group.isDisabled()) {
      return false;
    }
    final var desiredLeader = group.getDesiredLeader(partition.number());
    final var currentLeader =
        partitionLeaders.forGroup(partition.group()).currentLeader(partition.number());
    return desiredLeader.isPresent() && desiredLeader.equals(currentLeader);
  }
}
