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
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects every registered {@link MigrationStatusProvider} and combines their per-physical-tenant
 * statuses into one {@code Map<physicalTenantId, Map<conditionName, MigrationConditionStatus>>}.
 * Different consumers derive their own reduction over this shared, cache-smoothed map — the
 * actuator's upgrade-readiness endpoint computes a boolean, the public {@code GET
 * /cluster/v2/status/upgrade} endpoint ({@link ClusterUpgradeStatusServices}) computes one overall
 * {@link MigrationState} — so both read from the same instance and can never disagree.
 *
 * <p>Keeps the last confirmed {@code MIGRATED} status per (physical tenant, condition) pair.
 */
public class MigrationStatusAggregator {

  private static final Logger LOG = LoggerFactory.getLogger(MigrationStatusAggregator.class);

  private final List<MigrationStatusProvider> providers;
  private final Map<TenantCondition, MigrationConditionStatus> lastConfirmedMigrated =
      new ConcurrentHashMap<>();
  private final Set<String> knownPhysicalTenantIds = ConcurrentHashMap.newKeySet();

  public MigrationStatusAggregator(final List<MigrationStatusProvider> providers) {
    this.providers = providers;
  }

  public Map<String, Map<String, MigrationConditionStatus>> aggregate() {
    final var conditionNames =
        providers.stream().map(MigrationStatusProvider::conditionName).toList();
    final var physicalTenants = new LinkedHashMap<String, Map<String, MigrationConditionStatus>>();

    for (final var provider : providers) {
      final var conditionName = provider.conditionName();
      final var freshStatuses = safeGetMigrationStatus(provider);
      knownPhysicalTenantIds.addAll(freshStatuses.keySet());
      freshStatuses.forEach(
          (physicalTenantId, status) -> {
            final var resolved = resolveWithCache(physicalTenantId, conditionName, status);
            physicalTenants
                .computeIfAbsent(physicalTenantId, ignored -> new LinkedHashMap<>())
                .put(conditionName, resolved);
          });
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

  private MigrationConditionStatus resolveWithCache(
      final String physicalTenantId,
      final String conditionName,
      final MigrationConditionStatus fresh) {
    final var key = new TenantCondition(physicalTenantId, conditionName);
    if (fresh.state() == MigrationState.MIGRATED) {
      lastConfirmedMigrated.put(key, fresh);
      return fresh;
    }
    final var cached = lastConfirmedMigrated.get(key);
    return cached != null ? cached : fresh;
  }

  /**
   * Fills in every (known physical tenant, registered condition) pair this poll did not freshly
   * report — whether because a whole provider call failed, or because that provider simply did not
   * report that tenant this cycle. A pair once confirmed {@code MIGRATED} is restored from the
   * cache rather than defaulting to {@code UNKNOWN}, preserving the same monotonicity guarantee as
   * a fresh, successful lookup would.
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
            ignored -> {
              final var cached =
                  lastConfirmedMigrated.get(new TenantCondition(physicalTenantId, conditionName));
              return cached != null
                  ? cached
                  : new MigrationConditionStatus(
                      MigrationState.UNKNOWN, "no status reported for this poll");
            });
      }
    }
  }

  private record TenantCondition(String physicalTenantId, String conditionName) {}
}
