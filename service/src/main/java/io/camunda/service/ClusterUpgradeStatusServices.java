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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;

/**
 * Reports one overall {@link MigrationState} for the whole cluster, folded over every physical
 * tenant and condition tracked by the shared {@link MigrationStatusAggregator} — the same instance
 * backing the {@code GET /actuator/upgradeReadiness} endpoint, so both can never disagree due to
 * independent caching.
 */
@NullMarked
public final class ClusterUpgradeStatusServices {

  private final MigrationStatusAggregator aggregator;

  public ClusterUpgradeStatusServices(final MigrationStatusAggregator aggregator) {
    this.aggregator = aggregator;
  }

  public CompletableFuture<MigrationState> getStatus() {
    return CompletableFuture.completedFuture(fold(aggregator.aggregate()));
  }

  private static MigrationState fold(
      final Map<String, Map<String, MigrationConditionStatus>> physicalTenants) {
    if (physicalTenants.isEmpty()) {
      return MigrationState.UNKNOWN;
    }

    var sawUnknown = false;
    for (final var conditions : physicalTenants.values()) {
      if (conditions.isEmpty()) {
        sawUnknown = true;
        continue;
      }
      for (final var status : conditions.values()) {
        switch (status.state()) {
          case MIGRATION_IN_PROGRESS -> {
            return MigrationState.MIGRATION_IN_PROGRESS;
          }
          case UNKNOWN -> sawUnknown = true;
          case MIGRATED -> {
            // keep scanning; MIGRATED only wins if nothing worse is found
          }
          default ->
              throw new IllegalStateException("Unexpected migration state: " + status.state());
        }
      }
    }
    return sawUnknown ? MigrationState.UNKNOWN : MigrationState.MIGRATED;
  }
}
