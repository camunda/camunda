/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.exception.ServiceException;
import io.camunda.zeebe.gateway.admin.ExportingRequestBroadcaster;
import io.camunda.zeebe.gateway.admin.ExportingStatus;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;

/**
 * Pauses, resumes, and reports exporting status across every physical tenant of the cluster in one
 * call (ADR 003 D2), served by the cluster-admin security chain.
 *
 * <p>Unlike {@link ExportingServices}, this class does not extend {@link
 * PhysicalTenantScopedApiServices} and performs no {@code CamundaAuthentication}-based permission
 * check: the cluster-admin chain authenticates against an isolated credential set that produces no
 * {@code CamundaAuthentication}, so there is nothing to check {@code EXPORTER:PAUSE} against. The
 * chain itself is the gate.
 *
 * <p>All-or-error, no rollback: if any physical tenant cannot be reached, the whole request fails
 * and no already-succeeded tenant is reverted. Pause and resume are idempotent, so retrying the
 * same call is the correct remedy.
 */
@NullMarked
public final class ClusterExportingServices {

  private final ExportingRequestBroadcaster broadcaster;
  private final Set<String> physicalTenantIds;

  public ClusterExportingServices(
      final ExportingRequestBroadcaster broadcaster, final Set<String> physicalTenantIds) {
    this.broadcaster = broadcaster;
    this.physicalTenantIds = Set.copyOf(physicalTenantIds);
  }

  public CompletableFuture<Void> pauseExporting(final boolean soft) {
    return mapped(
        () -> fanOut(soft ? broadcaster::softPauseExporting : broadcaster::pauseExporting));
  }

  public CompletableFuture<Void> resumeExporting() {
    return mapped(() -> fanOut(broadcaster::resumeExporting));
  }

  public CompletableFuture<ExportingStatus> getExportingStatus() {
    return mapped(
        () -> {
          if (physicalTenantIds.isEmpty()) {
            // No configured tenant can process work. Not reachable in a running cluster (there
            // is always at least the default tenant), but folding an empty set to EXPORTING
            // would be actively wrong.
            return CompletableFuture.completedFuture(ExportingStatus.MIXED);
          }

          final var futures =
              physicalTenantIds.stream().map(broadcaster::getExportingStatus).toList();
          return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
              .thenApply(ignored -> fold(futures.stream().map(CompletableFuture::join).toList()));
        });
  }

  private CompletableFuture<Void> fanOut(final Function<String, CompletableFuture<Void>> action) {
    final var futures =
        physicalTenantIds.stream()
            .map(tenantId -> fanOutOne(tenantId, action))
            .toArray(CompletableFuture[]::new);
    return CompletableFuture.allOf(futures);
  }

  /**
   * Attaches the physical tenant id to a failure, both for a future that completes exceptionally
   * and for a synchronous throw ({@code validateTopology()} rejects before returning a future).
   * Without this, a failure on a 10-tenant cluster tells the operator only "one tenant failed", not
   * which one — the exact toil this endpoint exists to remove.
   *
   * <p>Maps through {@link ErrorMapper} first, rather than matching on the raw throwable, because
   * {@link CompletableFuture#allOf} wraps every dependency's failure in a {@link
   * java.util.concurrent.CompletionException} — so a bare {@code instanceof} check against, say,
   * {@code IncompleteTopologyException} never matches here, and only {@code ErrorMapper}'s
   * recursive unwrap reaches the real cause and its mapped status regardless of exception type.
   */
  private static CompletableFuture<Void> fanOutOne(
      final String physicalTenantId, final Function<String, CompletableFuture<Void>> action) {
    try {
      return action
          .apply(physicalTenantId)
          .exceptionallyCompose(
              e -> CompletableFuture.failedFuture(withTenantId(physicalTenantId, e)));
    } catch (final RuntimeException e) {
      return CompletableFuture.failedFuture(withTenantId(physicalTenantId, e));
    }
  }

  private static ServiceException withTenantId(
      final String physicalTenantId, final Throwable error) {
    final var mapped = ErrorMapper.mapError(error);
    return new ServiceException(
        "Physical tenant '%s': %s".formatted(physicalTenantId, mapped.getMessage()),
        mapped.getStatus());
  }

  private static ExportingStatus fold(final List<ExportingStatus> perTenantStatuses) {
    final var distinct = new HashSet<>(perTenantStatuses);
    if (distinct.size() == 1) {
      return distinct.iterator().next();
    }
    return ExportingStatus.MIXED;
  }

  /**
   * Maps both the synchronous validation failure ({@code IncompleteTopologyException} thrown by
   * {@link ExportingRequestBroadcaster} before it returns a future) and the asynchronous failure
   * (an exceptionally-completed future) to a {@link io.camunda.service.exception.ServiceException},
   * mirroring {@code ExportingServices}'s wrapper minus the permission check. Without this, both
   * failure paths reach {@code GatewayErrorMapper} as an unrecognized exception and become HTTP 500
   * instead of the per-PT endpoint's 503.
   */
  private <T> CompletableFuture<T> mapped(final Supplier<CompletableFuture<T>> action) {
    try {
      return action
          .get()
          .exceptionallyCompose(e -> CompletableFuture.failedFuture(ErrorMapper.mapError(e)));
    } catch (final RuntimeException e) {
      return CompletableFuture.failedFuture(ErrorMapper.mapError(e));
    }
  }
}
