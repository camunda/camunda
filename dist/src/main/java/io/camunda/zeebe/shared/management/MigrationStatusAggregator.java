/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.cluster.MigrationConditionStatus;
import io.camunda.cluster.MigrationState;
import io.camunda.cluster.MigrationStatusProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects every registered {@link MigrationStatusProvider} and combines their per-physical-tenant
 * statuses into an {@link UpgradeReadinessResponse}.
 *
 * <p>Keeps the last confirmed {@code MIGRATED} status per (physical tenant, condition) pair so that
 * a single failed lookup — a provider throwing, or a distributed provider's fan-out timing out —
 * can never regress an already-confirmed pair back to {@code MIGRATION_IN_PROGRESS} or {@code
 * UNKNOWN}: the underlying fact (schema/state already migrated) does not un-migrate itself, only
 * our ability to observe it can fail.
 *
 * <p>Also remembers every physical tenant ID it has ever seen from any provider. If a provider
 * fails an entire poll (throws, rather than reporting per tenant), it contributes no fresh entries
 * at all — without this, an already-known tenant's condition for that provider would simply vanish
 * from the response instead of showing its last-known status, which would let the missing entry be
 * silently read as "not applicable" during {@code upgradeable} computation. Every known tenant is
 * therefore backfilled with every registered condition, using the cache (or {@code UNKNOWN} if
 * nothing was ever cached) for any pair a poll did not freshly report.
 *
 * <p>One instance is created per {@link UpgradeReadinessEndpoint} (a Spring singleton), so both the
 * cache and the known-tenant set persist across polls for the lifetime of the process.
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

  public UpgradeReadinessResponse aggregate() {
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

    final var upgradeable =
        !physicalTenants.isEmpty()
            && physicalTenants.values().stream()
                .allMatch(
                    conditions ->
                        !conditions.isEmpty()
                            && conditions.values().stream()
                                .allMatch(status -> status.state() == MigrationState.MIGRATED));
    return new UpgradeReadinessResponse(upgradeable, physicalTenants);
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
