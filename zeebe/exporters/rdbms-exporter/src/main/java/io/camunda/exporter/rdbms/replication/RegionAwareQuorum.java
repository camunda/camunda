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
 * Shared quorum math for {@link ReplicationSignalStrategy} implementations: replicas are grouped by
 * the region resolved from their {@code replicaLabel} via {@link ReplicaRegionResolver} (a label
 * matching no configured region counts toward no region), each declared region is ranked
 * independently - "sort best-replicas-first, take the region's own {@code minReplicas}, return the
 * worst of that slice" - and every region is mandatory: the overall result is the worst value
 * across all of them, so if any one region falls short of its own {@code minReplicas} the whole
 * position is unconfirmed.
 */
final class RegionAwareQuorum {

  private static final String CATCH_ALL_PATTERN = ".*";

  private static final Logger LOG = LoggerFactory.getLogger(RegionAwareQuorum.class);

  private RegionAwareQuorum() {}

  static boolean quorumMet(
      final List<? extends ReplicationStatus> statuses,
      final ReplicationConfiguration config,
      final ReplicaRegionResolver resolver,
      final Optional<String> currentPrimaryRegion) {
    return regionsBelowQuorum(statuses, config, resolver, currentPrimaryRegion).isEmpty();
  }

  /**
   * The names of mandatory regions currently short of their own {@code minReplicas} (counting the
   * primary's automatic credit where eligible). Used for diagnostic logging when the exporter
   * pauses, see {@link DefaultReplicationController}.
   */
  static List<String> regionsBelowQuorum(
      final List<? extends ReplicationStatus> statuses,
      final ReplicationConfiguration config,
      final ReplicaRegionResolver resolver,
      final Optional<String> currentPrimaryRegion) {
    final Map<String, Long> countsByRegion = countByRegion(statuses, resolver);
    final List<String> below = new ArrayList<>();
    for (final RegionConfiguration region : config.getRegions()) {
      long count = countsByRegion.getOrDefault(region.getName(), 0L);
      if (isPrimaryCreditedTo(region, currentPrimaryRegion)) {
        count++;
      }
      if (count < region.getMinReplicas()) {
        below.add(region.getName());
      }
    }
    return below;
  }

  /**
   * {@code higherIsBetter} orders replicas from most- to least-caught-up (e.g. {@code true} for an
   * LSN or an as-of timestamp, {@code false} for a lag in milliseconds). Returns {@link
   * OptionalLong#empty()} when at least one mandatory region falls short of its own {@code
   * minReplicas}.
   */
  static <T extends ReplicationStatus> OptionalLong evaluate(
      final List<T> statuses,
      final ReplicationConfiguration config,
      final ReplicaRegionResolver resolver,
      final Optional<String> currentPrimaryRegion,
      final ToLongFunction<? super T> valueExtractor,
      final boolean higherIsBetter) {
    final Map<String, List<Long>> valuesByRegion =
        groupValuesByRegion(statuses, resolver, valueExtractor);
    final long primaryCreditValue = higherIsBetter ? Long.MAX_VALUE : Long.MIN_VALUE;
    final List<Long> regionResults = new ArrayList<>();
    for (final RegionConfiguration region : config.getRegions()) {
      final List<Long> values =
          new ArrayList<>(valuesByRegion.getOrDefault(region.getName(), List.of()));
      if (isPrimaryCreditedTo(region, currentPrimaryRegion)) {
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

  private static boolean isPrimaryCreditedTo(
      final RegionConfiguration region, final Optional<String> currentPrimaryRegion) {
    return !CATCH_ALL_PATTERN.equals(region.getPattern())
        && currentPrimaryRegion.filter(region.getName()::equals).isPresent();
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

  private static <T extends ReplicationStatus> Map<String, List<Long>> groupValuesByRegion(
      final List<T> statuses,
      final ReplicaRegionResolver resolver,
      final ToLongFunction<? super T> valueExtractor) {
    final Map<String, List<Long>> byRegion = new LinkedHashMap<>();
    for (final T status : statuses) {
      resolveLogWarning(resolver, status.replicaLabel())
          .ifPresent(
              region ->
                  byRegion
                      .computeIfAbsent(region, key -> new ArrayList<>())
                      .add(valueExtractor.applyAsLong(status)));
    }
    return byRegion;
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
}
