/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.service.TopologyServices.ClusterStatus;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import org.jspecify.annotations.NullMarked;

/**
 * Reports one aggregated status for the whole cluster, folded over all physical tenants.
 *
 * <p>{@link AggregatedStatus#HEALTHY} when every physical tenant is healthy, {@link
 * AggregatedStatus#DOWN} when no physical tenant can process work, {@link
 * AggregatedStatus#DEGRADED} in every other case.
 *
 * <p>Two independent per-tenant signals are combined: whether the tenant's partition group has a
 * healthy leader and whether the tenant's secondary storage is ready. Only the first decides
 * whether a tenant can process work; a tenant with a healthy leader but degraded storage is
 * degraded, not down.
 *
 * <p>This service is cluster-wide, so it deliberately <b>exposes no physical tenant ids</b>: it
 * backs the unauthenticated {@code GET /cluster/v2/status}, where a tenant id or even a tenant
 * count would allow unauthenticated tenant enumeration. Per-tenant detail belongs to the
 * cluster-admin authenticated {@code GET /cluster/v2/topology} or per-tenant authenticated {@code
 * GET /physical-tenants/{physicalTenantId}/v2/topology}.
 */
@NullMarked
public final class ClusterStatusServices {

  private final Map<String, TopologyServices> topologyServicesByPhysicalTenant;
  private final Predicate<String> secondaryStorageReady;

  public ClusterStatusServices(
      final Map<String, TopologyServices> topologyServicesByPhysicalTenant,
      final Predicate<String> secondaryStorageReady) {
    this.topologyServicesByPhysicalTenant = Map.copyOf(topologyServicesByPhysicalTenant);
    this.secondaryStorageReady = secondaryStorageReady;
  }

  /**
   * @return the aggregated status over all known physical tenants. Counts a tenant whose status
   *     cannot be determined as {@link ClusterStatus#UNHEALTHY}.
   */
  public CompletableFuture<AggregatedStatus> getStatus() {
    if (topologyServicesByPhysicalTenant.isEmpty()) {
      // No configured tenant can process work. Not reachable in a running cluster (there is always
      // at least the default tenant), but folding an empty set to HEALTHY would be actively wrong.
      return CompletableFuture.completedFuture(AggregatedStatus.DOWN);
    }

    final var perTenant =
        topologyServicesByPhysicalTenant.entrySet().stream()
            .map(
                entry ->
                    entry
                        .getValue()
                        .getStatus()
                        .exceptionally(error -> ClusterStatus.UNHEALTHY)
                        .thenApply(status -> statusOf(entry.getKey(), status)))
            .toList();

    return CompletableFuture.allOf(perTenant.toArray(CompletableFuture[]::new))
        .thenApply(ignored -> aggregate(perTenant.stream().map(CompletableFuture::join).toList()));
  }

  private AggregatedStatus statusOf(final String physicalTenantId, final ClusterStatus topology) {
    if (topology != ClusterStatus.HEALTHY) {
      return AggregatedStatus.DOWN;
    }
    return secondaryStorageReady.test(physicalTenantId)
        ? AggregatedStatus.HEALTHY
        : AggregatedStatus.DEGRADED;
  }

  /** Folds the non-empty per-tenant statuses into the cluster-wide one. */
  private static AggregatedStatus aggregate(final List<AggregatedStatus> perTenant) {
    if (perTenant.stream().allMatch(AggregatedStatus.HEALTHY::equals)) {
      return AggregatedStatus.HEALTHY;
    }
    if (perTenant.stream().allMatch(AggregatedStatus.DOWN::equals)) {
      return AggregatedStatus.DOWN;
    }
    return AggregatedStatus.DEGRADED;
  }

  /**
   * The cluster-wide status. Distinct from {@link ClusterStatus}, which is the per-physical-tenant
   * signal this is folded from.
   */
  public enum AggregatedStatus {
    HEALTHY,
    DEGRADED,
    DOWN
  }
}
