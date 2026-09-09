/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.cluster.migration.MigrationConditionStatus;
import io.camunda.cluster.migration.MigrationState;
import io.camunda.cluster.migration.MigrationStatusProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects every registered {@link MigrationStatusProvider} and combines their per-physical-tenant
 * statuses into one {@code Map<physicalTenantId, Map<conditionName, MigrationConditionStatus>>}.
 */
public class MigrationStatusAggregator {

  private static final Logger LOG = LoggerFactory.getLogger(MigrationStatusAggregator.class);

  private final List<MigrationStatusProvider> providers;
  private final Set<String> knownPhysicalTenantIds = ConcurrentHashMap.newKeySet();

  public MigrationStatusAggregator(final List<MigrationStatusProvider> providers) {
    this.providers = providers;
  }

  public Map<String, Map<String, MigrationConditionStatus>> aggregate() {
    final var conditionNames =
        providers.stream().map(MigrationStatusProvider::conditionName).toList();
    final var physicalTenants = new LinkedHashMap<String, Map<String, MigrationConditionStatus>>();

    // Poll every provider concurrently rather than one at a time, so a slow provider doesn't add
    // its own timeout on top of every other provider's. Explicitly named rather than the
    // executor-less overload: this class must stay constructible on broker-only nodes with no
    // ApiServicesExecutorProvider bean (the managed executor io.camunda.service normally requires),
    // so it names the common pool that overload would have used anyway.
    final var pendingStatusesByProvider =
        providers.stream()
            .map(
                provider ->
                    CompletableFuture.supplyAsync(
                        () -> safeGetMigrationStatus(provider), ForkJoinPool.commonPool()))
            .toList();
    CompletableFuture.allOf(pendingStatusesByProvider.toArray(CompletableFuture<?>[]::new)).join();

    for (int i = 0; i < providers.size(); i++) {
      final var conditionName = providers.get(i).conditionName();
      final var freshStatuses = pendingStatusesByProvider.get(i).join();
      knownPhysicalTenantIds.addAll(freshStatuses.keySet());
      freshStatuses.forEach(
          (physicalTenantId, status) ->
              physicalTenants
                  .computeIfAbsent(physicalTenantId, ignored -> new LinkedHashMap<>())
                  .put(conditionName, status));
    }

    backfillMissingPairs(physicalTenants, conditionNames);

    return physicalTenants;
  }

  private Map<String, MigrationConditionStatus> safeGetMigrationStatus(
      final MigrationStatusProvider provider) {
    try {
      return provider.getMigrationStatus();
    } catch (final Exception e) {
      LOG.warn(
          "Upgrade-readiness provider '{}' failed; no fresh status for this poll.",
          provider.conditionName(),
          e);
      return Map.of();
    }
  }

  /**
   * Fills in every (known physical tenant, registered condition) pair this poll did not freshly
   * report — whether because a whole provider call failed, or because that provider simply did not
   * report that tenant this cycle — with {@code UNKNOWN}.
   */
  private void backfillMissingPairs(
      final Map<String, Map<String, MigrationConditionStatus>> physicalTenants,
      final List<String> conditionNames) {
    for (final var physicalTenantId : knownPhysicalTenantIds) {
      final var conditions =
          physicalTenants.computeIfAbsent(physicalTenantId, ignored -> new LinkedHashMap<>());
      for (final var conditionName : conditionNames) {
        conditions.computeIfAbsent(
            conditionName,
            ignored ->
                new MigrationConditionStatus(
                    MigrationState.UNKNOWN, "no status reported for this poll"));
      }
    }
  }
}
