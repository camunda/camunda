/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.service.backup.HistoryBackupApi;
import io.camunda.service.backup.HistoryBackupRequests;
import io.camunda.service.backup.HistoryBackupSnapshot;
import io.camunda.service.backup.HistoryBackupState;
import io.camunda.service.backup.HistoryBackupStateCode;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * History (secondary-storage snapshot) backups across every physical tenant of the cluster, or
 * across the one a request narrows to (ADR 004, {@code
 * docs/adr/management/004-cluster-wide-history-backup-outcomes.md}).
 *
 * <p>Unlike {@link HistoryBackupServices} this takes no {@code CamundaAuthentication} and performs
 * no per-tenant authorization: per ADR 002 D4 the cluster-admin security chain is the only gate,
 * and taking no authentication at all makes that structurally true rather than a convention a later
 * edit can break.
 *
 * <p>Two rules shape every operation:
 *
 * <ul>
 *   <li><b>Absence is a successful observation.</b> A backup that exists on one physical tenant and
 *       not another is normal — both this API and the per-tenant one can produce it — so a tenant
 *       that was reached and holds nothing reports {@link TenantBackupStateCode#NOT_FOUND} rather
 *       than failing.
 *   <li><b>Anything else is all-or-nothing.</b> A tenant whose state cannot be observed fails the
 *       whole request; a caller works around a broken tenant by narrowing to the others.
 * </ul>
 *
 * <p>{@link HistoryBackupApi} blocks on secondary-storage round-trips, so the fan-out runs on the
 * shared API executor, one task per targeted tenant: the wall clock is the slowest tenant rather
 * than the sum, which matters most for a verbose cluster-wide listing.
 */
@NullMarked
public final class ClusterHistoryBackupServices {

  private final HistoryBackupApi api;
  private final List<String> physicalTenantIds;
  private final Executor executor;

  public ClusterHistoryBackupServices(
      final HistoryBackupApi api,
      final Collection<String> physicalTenantIds,
      final ApiServicesExecutorProvider executorProvider) {
    this.api = api;
    // Sorted so every response lists physical tenants in the same, assertable order.
    this.physicalTenantIds = physicalTenantIds.stream().sorted().toList();
    executor = executorProvider.getExecutor();
  }

  /**
   * Schedules a backup with the same id on every targeted physical tenant.
   *
   * <p>The id is checked on every targeted tenant before anything is scheduled: a cluster-wide
   * backup that only lands on some tenants is not what the caller asked for. The check cannot be
   * airtight — a per-tenant request can take the id in between — so a tenant rejecting the id
   * during the fan-out still fails the request, leaving the snapshots already scheduled behind.
   *
   * @param physicalTenantId the single tenant to target, or {@code null} for every tenant
   */
  public CompletableFuture<ClusterHistoryBackupTaken> takeBackup(
      final @Nullable String physicalTenantId, final @Nullable Long backupId) {
    return validated(
        () -> {
          final var targets = targets(physicalTenantId);
          final var id = HistoryBackupRequests.requireValidBackupId(backupId);
          return onEveryTenant(
                  targets,
                  tenant -> {
                    requireBackupIdFree(tenant, id);
                    return tenant;
                  })
              .thenCompose(ignored -> onEveryTenant(targets, tenant -> take(tenant, id)))
              .thenApply(taken -> new ClusterHistoryBackupTaken(id, taken));
        });
  }

  /**
   * Reports what every targeted physical tenant holds for the given backup id, including the ones
   * holding nothing. Fails with {@link Status#NOT_FOUND} only when no targeted tenant holds it.
   *
   * @param physicalTenantId the single tenant to target, or {@code null} for every tenant
   */
  public CompletableFuture<ClusterHistoryBackup> getBackup(
      final @Nullable String physicalTenantId, final long backupId) {
    return validated(
        () -> {
          final var targets = targets(physicalTenantId);
          final var id = HistoryBackupRequests.requireValidBackupId(backupId);
          return onEveryTenant(targets, tenant -> stateOf(tenant, id))
              .thenApply(
                  states -> {
                    if (states.stream()
                        .allMatch(state -> state.state() == TenantBackupStateCode.NOT_FOUND)) {
                      throw noTenantHolds(id, targets);
                    }
                    return new ClusterHistoryBackup(id, states);
                  });
        });
  }

  /**
   * Lists the backups of every targeted physical tenant, grouped by backup id, most recent id
   * first. A tenant that holds none of the matching ids simply contributes no entry.
   *
   * @param physicalTenantId the single tenant to target, or {@code null} for every tenant
   */
  public CompletableFuture<List<ClusterHistoryBackup>> listBackups(
      final @Nullable String physicalTenantId,
      final @Nullable String prefix,
      final boolean verbose) {
    return validated(
        () -> {
          final var targets = targets(physicalTenantId);
          final var queried = HistoryBackupRequests.requireValidPrefix(prefix);
          return onEveryTenant(
                  targets,
                  tenant ->
                      new PhysicalTenantBackups(tenant, api.getBackups(tenant, verbose, queried)))
              .thenApply(ClusterHistoryBackupServices::groupByBackupId);
        });
  }

  /**
   * Deletes the backup from every targeted physical tenant that holds it. A tenant that does not
   * hold it has already reached the requested end state, so it counts as deleted; only a request
   * where no targeted tenant held it at all fails with {@link Status#NOT_FOUND}.
   *
   * @param physicalTenantId the single tenant to target, or {@code null} for every tenant
   */
  public CompletableFuture<Void> deleteBackup(
      final @Nullable String physicalTenantId, final long backupId) {
    return validated(
        () -> {
          final var targets = targets(physicalTenantId);
          final var id = HistoryBackupRequests.requireValidBackupId(backupId);
          return onEveryTenant(targets, tenant -> delete(tenant, id))
              .thenApply(
                  deleted -> {
                    if (deleted.stream().noneMatch(Boolean::booleanValue)) {
                      throw noTenantHolds(id, targets);
                    }
                    return null;
                  });
        });
  }

  /**
   * Resolves the physical tenants a request targets, rejecting an id this cluster does not know
   * before any tenant is contacted — so an unknown id is never mistaken for a tenant that failed.
   */
  private List<String> targets(final @Nullable String physicalTenantId) {
    if (physicalTenantId == null) {
      return physicalTenantIds;
    }
    if (!physicalTenantIds.contains(physicalTenantId)) {
      throw new ServiceException(
          "Expected to target physical tenant '%s', but this cluster only has %s"
              .formatted(physicalTenantId, physicalTenantIds),
          Status.NOT_FOUND);
    }
    return List.of(physicalTenantId);
  }

  private void requireBackupIdFree(final String physicalTenantId, final long backupId) {
    final HistoryBackupState existing;
    try {
      existing = api.getBackupState(physicalTenantId, backupId);
    } catch (final ServiceException e) {
      if (e.getStatus() == Status.NOT_FOUND) {
        return;
      }
      throw e;
    }
    throw new ServiceException(
        "Expected physical tenant '%s' to hold no backup with id '%d', but it is already %s"
            .formatted(physicalTenantId, backupId, existing.state()),
        Status.ALREADY_EXISTS);
  }

  private PhysicalTenantBackupTaken take(final String physicalTenantId, final long backupId) {
    final var taken = api.takeBackup(physicalTenantId, backupId);
    return new PhysicalTenantBackupTaken(physicalTenantId, taken.scheduledSnapshots());
  }

  private PhysicalTenantBackupState stateOf(final String physicalTenantId, final long backupId) {
    try {
      return toTenantState(physicalTenantId, api.getBackupState(physicalTenantId, backupId));
    } catch (final ServiceException e) {
      if (e.getStatus() == Status.NOT_FOUND) {
        return new PhysicalTenantBackupState(
            physicalTenantId, TenantBackupStateCode.NOT_FOUND, null, List.of());
      }
      throw e;
    }
  }

  /**
   * @return {@code true} if the backup was deleted, {@code false} if the tenant did not hold it
   */
  private boolean delete(final String physicalTenantId, final long backupId) {
    try {
      api.deleteBackup(physicalTenantId, backupId);
      return true;
    } catch (final ServiceException e) {
      if (e.getStatus() == Status.NOT_FOUND) {
        return false;
      }
      throw e;
    }
  }

  /**
   * Runs one blocking port call per targeted physical tenant concurrently, and fails the whole
   * request if any of them failed — the all-or-nothing rule. Results keep the targets' order.
   */
  private <T> CompletableFuture<List<T>> onEveryTenant(
      final List<String> targets, final Function<String, T> perTenant) {
    final var outcomes =
        targets.stream()
            .map(
                tenant ->
                    CompletableFuture.supplyAsync(() -> perTenant.apply(tenant), executor)
                        .handle(
                            (value, error) ->
                                new PhysicalTenantFanOut.Outcome<>(tenant, value, error)))
            .toList();
    return CompletableFuture.allOf(outcomes.toArray(CompletableFuture[]::new))
        .thenApply(
            ignored ->
                PhysicalTenantFanOut.requireEveryTenant(
                    outcomes.stream().map(CompletableFuture::join).toList()));
  }

  private static List<ClusterHistoryBackup> groupByBackupId(
      final List<PhysicalTenantBackups> perTenant) {
    final Map<Long, List<PhysicalTenantBackupState>> byBackupId =
        new TreeMap<>(Comparator.reverseOrder());
    perTenant.forEach(
        tenant ->
            tenant
                .backups()
                .forEach(
                    backup ->
                        byBackupId
                            .computeIfAbsent(backup.backupId(), id -> new ArrayList<>())
                            .add(toTenantState(tenant.physicalTenantId(), backup))));
    return byBackupId.entrySet().stream()
        .map(entry -> new ClusterHistoryBackup(entry.getKey(), List.copyOf(entry.getValue())))
        .toList();
  }

  private static PhysicalTenantBackupState toTenantState(
      final String physicalTenantId, final HistoryBackupState state) {
    return new PhysicalTenantBackupState(
        physicalTenantId,
        toTenantStateCode(state.state()),
        state.failureReason(),
        state.snapshots());
  }

  private static TenantBackupStateCode toTenantStateCode(final HistoryBackupStateCode state) {
    return switch (state) {
      case IN_PROGRESS -> TenantBackupStateCode.IN_PROGRESS;
      case COMPLETED -> TenantBackupStateCode.COMPLETED;
      case FAILED -> TenantBackupStateCode.FAILED;
      case INCOMPLETE -> TenantBackupStateCode.INCOMPLETE;
      case INCOMPATIBLE -> TenantBackupStateCode.INCOMPATIBLE;
    };
  }

  private static ServiceException noTenantHolds(final long backupId, final List<String> targets) {
    return new ServiceException(
        "Expected to find a history backup with id '%d', but none of the physical tenants %s holds it"
            .formatted(backupId, targets),
        Status.NOT_FOUND);
  }

  /** Turns a rejection raised before the fan-out starts into a failed future. */
  private static <T> CompletableFuture<T> validated(final Supplier<CompletableFuture<T>> request) {
    try {
      return request.get();
    } catch (final ServiceException e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  private record PhysicalTenantBackups(String physicalTenantId, List<HistoryBackupState> backups) {}

  /** The snapshots scheduled for one backup id, per physical tenant. */
  public record ClusterHistoryBackupTaken(
      long backupId, List<PhysicalTenantBackupTaken> physicalTenants) {}

  public record PhysicalTenantBackupTaken(
      String physicalTenantId, List<String> scheduledSnapshots) {}

  /** What each physical tenant reports for one backup id. Carries no cluster-level aggregate. */
  public record ClusterHistoryBackup(
      long backupId, List<PhysicalTenantBackupState> physicalTenants) {}

  public record PhysicalTenantBackupState(
      String physicalTenantId,
      TenantBackupStateCode state,
      @Nullable String failureReason,
      List<HistoryBackupSnapshot> snapshots) {}

  /**
   * {@link HistoryBackupStateCode} plus {@code NOT_FOUND}, which only a fan-out can observe: the
   * tenant was reached and holds nothing. There is no code for a tenant that could not be reached —
   * such a tenant fails the whole request instead.
   */
  public enum TenantBackupStateCode {
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    INCOMPLETE,
    INCOMPATIBLE,
    NOT_FOUND
  }
}
