/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.admin;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.cluster.migration.MigrationConditionStatus;
import io.camunda.cluster.migration.MigrationState;
import io.camunda.zeebe.protocol.impl.encoding.MigrationStatusCode;
import io.camunda.zeebe.protocol.impl.encoding.PartitionMigrationStatus;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

@NullMarked
final class ClusterMigrationStatusReader {

  private ClusterMigrationStatusReader() {}

  static Map<String, MigrationConditionStatus> resolveTenants(
      final PhysicalTenantIds physicalTenantIds,
      final Duration fetchTimeout,
      final Function<String, CompletableFuture<PartitionMigrationStatus>> fetchTenantStatus,
      final Logger log,
      final String conditionDescription) {
    final var futuresByPhysicalTenant =
        new LinkedHashMap<String, CompletableFuture<PartitionMigrationStatus>>();
    for (final var physicalTenantId : physicalTenantIds.known()) {
      futuresByPhysicalTenant.put(physicalTenantId, fetchTenantStatus.apply(physicalTenantId));
    }

    // One shared timeout budget for every tenant's fan-out, run concurrently -- not one timeout
    // per tenant, which would let a large multi-tenant cluster's total latency grow with the
    // tenant count.
    try {
      CompletableFuture.allOf(futuresByPhysicalTenant.values().toArray(CompletableFuture<?>[]::new))
          .get(fetchTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (final InterruptedException e) {
      // Restore the interrupt so callers on this thread (e.g. during a graceful shutdown) still
      // observe it -- swallowing it here would hide the signal from whatever cancellation logic
      // is waiting on it, even though the resolution below still proceeds on a best-effort basis.
      Thread.currentThread().interrupt();
      log.warn(
          "Interrupted while waiting for every physical tenant's {} to be determined.",
          conditionDescription,
          e);
    } catch (final Exception e) {
      log.warn(
          "Not every physical tenant's {} could be determined within {}.",
          conditionDescription,
          fetchTimeout,
          e);
    }

    final var statuses = new LinkedHashMap<String, MigrationConditionStatus>();
    futuresByPhysicalTenant.forEach(
        (physicalTenantId, future) ->
            statuses.put(physicalTenantId, resolve(physicalTenantId, future)));
    return statuses;
  }

  /**
   * Reports each tenant's best available answer independently: a tenant whose fan-out finished
   * (successfully or not) reports its actual result; a tenant still pending when the shared budget
   * ran out reports {@code UNKNOWN} on its own, without holding back tenants that did finish.
   */
  private static MigrationConditionStatus resolve(
      final String physicalTenantId, final CompletableFuture<PartitionMigrationStatus> future) {
    if (future.isDone() && !future.isCompletedExceptionally()) {
      return toConditionStatus(future.join());
    }
    if (future.isCompletedExceptionally()) {
      return new MigrationConditionStatus(
          MigrationState.UNKNOWN,
          "physical tenant '" + physicalTenantId + "': failed to determine migration status");
    }
    return new MigrationConditionStatus(
        MigrationState.UNKNOWN,
        "physical tenant '"
            + physicalTenantId
            + "': timed out waiting for every partition replica to respond");
  }

  /**
   * Combines every queried replica/partition's status with {@code UNKNOWN > MIGRATION_IN_PROGRESS >
   * MIGRATED} precedence: any one we can't confidently assess makes the whole tenant's condition
   * {@code UNKNOWN} rather than silently reporting a partial answer as though it were complete.
   */
  static PartitionMigrationStatus aggregate(final List<PartitionMigrationStatus> statuses) {
    if (statuses.isEmpty()) {
      return new PartitionMigrationStatus(
          MigrationStatusCode.UNKNOWN, "no partitions found in the topology");
    }

    final var notMigrated =
        statuses.stream().filter(status -> status.code() != MigrationStatusCode.MIGRATED).toList();
    if (notMigrated.isEmpty()) {
      return new PartitionMigrationStatus(MigrationStatusCode.MIGRATED, "All partitions migrated");
    }

    final var overallCode =
        notMigrated.stream().anyMatch(status -> status.code() == MigrationStatusCode.UNKNOWN)
            ? MigrationStatusCode.UNKNOWN
            : MigrationStatusCode.MIGRATION_IN_PROGRESS;
    final var detail =
        notMigrated.stream()
            .map(PartitionMigrationStatus::detail)
            .collect(Collectors.joining("; "));
    return new PartitionMigrationStatus(overallCode, detail);
  }

  /** Maps the wire-level protocol type to the upgrade-readiness API/SPI type. */
  static MigrationConditionStatus toConditionStatus(final PartitionMigrationStatus status) {
    final var state =
        switch (status.code()) {
          case MIGRATED -> MigrationState.MIGRATED;
          case MIGRATION_IN_PROGRESS -> MigrationState.MIGRATION_IN_PROGRESS;
          case UNKNOWN -> MigrationState.UNKNOWN;
        };
    return new MigrationConditionStatus(state, status.detail());
  }
}
