/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.replication;

import io.camunda.db.rdbms.read.replication.ReplicationStatus;
import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration;
import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration.RegionAwarenessConfiguration;
import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration.RegionConfiguration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.ToLongFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared quorum math for {@link ReplicationSignalStrategy} implementations: the "sort
 * best-replicas-first, take the configured quorum size, return the worst of that slice" pattern
 * both {@link LsnReplicationSignalStrategy} and {@link TimeMonitoringReplicationSignalStrategy}
 * already apply, generalized to optionally partition replicas into operator-declared regions - see
 * {@code docs/adr/0001-region-aware-replication-quorum.md}.
 *
 * <p>When {@link RegionAwarenessConfiguration#isEnabled()} is {@code false} (the default), both
 * methods reduce to the original flat behavior: rank all replicas together and require {@link
 * ReplicationConfiguration#getMinSyncReplicas()} of them.
 *
 * <p>When enabled, replicas are grouped by the region resolved from their {@code replicaLabel} via
 * {@link ReplicaRegionResolver}; a label matching no configured region counts toward no region.
 * Every declared region is mandatory and independently ranked using its own {@link
 * RegionConfiguration#getMinReplicas()}; the region hosting the primary (see {@link
 * RegionAwarenessConfiguration#getPrimaryRegion()}) gets one synthetic, always-best entry credited
 * to it, so operators size {@code minReplicas} as the desired total healthy node count for that
 * region. The overall result is the worst value among all mandatory regions - if any region falls
 * short of its own {@code minReplicas}, the whole position is unconfirmed.
 */
final class RegionAwareQuorum {

  private static final Logger LOG = LoggerFactory.getLogger(RegionAwareQuorum.class);

  private RegionAwareQuorum() {}

  /**
   * The count-only quorum check, used where no per-replica value can be ranked (e.g. LSN mode's
   * pause lag).
   */
  static boolean quorumMet(
      final List<? extends ReplicationStatus> statuses,
      final ReplicationConfiguration config,
      final ReplicaRegionResolver resolver) {
    final RegionAwarenessConfiguration regionAwareness = config.getRegionAwareness();
    if (!regionAwareness.isEnabled()) {
      return statuses.size() >= config.getMinSyncReplicas();
    }
    return regionsBelowQuorum(statuses, config, resolver).isEmpty();
  }

  /**
   * The names of mandatory regions currently short of their own {@code minReplicas} (counting the
   * primary's automatic credit where applicable). Empty when region awareness is disabled or every
   * declared region meets its own quorum - used for diagnostic logging when the exporter pauses,
   * see {@link DefaultReplicationController}.
   */
  static List<String> regionsBelowQuorum(
      final List<? extends ReplicationStatus> statuses,
      final ReplicationConfiguration config,
      final ReplicaRegionResolver resolver) {
    final RegionAwarenessConfiguration regionAwareness = config.getRegionAwareness();
    if (!regionAwareness.isEnabled()) {
      return List.of();
    }

    final Map<String, Long> countsByRegion = countByRegion(statuses, resolver);
    final List<String> below = new ArrayList<>();
    for (final RegionConfiguration region : regionAwareness.getRegions()) {
      long count = countsByRegion.getOrDefault(region.getName(), 0L);
      if (region.getName().equals(regionAwareness.getPrimaryRegion())) {
        count++;
      }
      if (count < region.getMinReplicas()) {
        below.add(region.getName());
      }
    }
    return below;
  }

  private static Map<String, Long> countByRegion(
      final List<? extends ReplicationStatus> statuses, final ReplicaRegionResolver resolver) {
    final Map<String, Long> counts = new LinkedHashMap<>();
    for (final ReplicationStatus status : statuses) {
      resolveLogWarning(resolver, status.replicaLabel())
          .ifPresent(region -> counts.merge(region, 1L, Long::sum));
    }
    return counts;
  }

  /**
   * The ranked quorum value: {@code higherIsBetter} orders replicas from most- to least-caught-up
   * (e.g. {@code true} for an LSN or an as-of timestamp, {@code false} for a lag in milliseconds).
   * Returns {@link OptionalLong#empty()} when quorum isn't met (flat: not enough replicas overall;
   * region-aware: at least one mandatory region falls short of its own {@code minReplicas}).
   */
  static <T extends ReplicationStatus> OptionalLong evaluate(
      final List<T> statuses,
      final ReplicationConfiguration config,
      final ReplicaRegionResolver resolver,
      final ToLongFunction<? super T> valueExtractor,
      final boolean higherIsBetter) {
    final RegionAwarenessConfiguration regionAwareness = config.getRegionAwareness();
    if (!regionAwareness.isEnabled()) {
      final List<Long> values = statuses.stream().map(valueExtractor::applyAsLong).toList();
      return worstOfTopN(values, config.getMinSyncReplicas(), higherIsBetter);
    }

    final Map<String, List<Long>> valuesByRegion = new LinkedHashMap<>();
    for (final T status : statuses) {
      resolveLogWarning(resolver, status.replicaLabel())
          .ifPresent(
              region ->
                  valuesByRegion
                      .computeIfAbsent(region, key -> new ArrayList<>())
                      .add(valueExtractor.applyAsLong(status)));
    }

    final long primaryCreditValue = higherIsBetter ? Long.MAX_VALUE : Long.MIN_VALUE;
    final List<Long> regionResults = new ArrayList<>();
    for (final RegionConfiguration region : regionAwareness.getRegions()) {
      final List<Long> values =
          new ArrayList<>(valuesByRegion.getOrDefault(region.getName(), List.of()));
      if (region.getName().equals(regionAwareness.getPrimaryRegion())) {
        values.add(primaryCreditValue);
      }
      final OptionalLong regionResult =
          worstOfTopN(values, region.getMinReplicas(), higherIsBetter);
      if (regionResult.isEmpty()) {
        return OptionalLong.empty();
      }
      regionResults.add(regionResult.getAsLong());
    }
    // every region is mandatory, so the overall result is simply the worst across all of them -
    // reuse the same reduction with n == regionResults.size() (no top-N truncation needed).
    return worstOfTopN(regionResults, regionResults.size(), higherIsBetter);
  }

  private static Optional<String> resolveLogWarning(
      final ReplicaRegionResolver resolver, final String replicaLabel) {
    final Optional<String> region = resolver.resolve(replicaLabel);
    if (region.isEmpty()) {
      LOG.warn(
          "Replica label '{}' did not match any configured region pattern; it will not count"
              + " toward any region's replication quorum.",
          replicaLabel);
    }
    return region;
  }

  private static OptionalLong worstOfTopN(
      final List<Long> values, final int n, final boolean higherIsBetter) {
    if (values.size() < n) {
      return OptionalLong.empty();
    }
    final Comparator<Long> bestFirst =
        higherIsBetter ? Comparator.<Long>reverseOrder() : Comparator.naturalOrder();
    final var topN = values.stream().sorted(bestFirst).limit(n).mapToLong(Long::longValue);
    return higherIsBetter ? topN.min() : topN.max();
  }
}
