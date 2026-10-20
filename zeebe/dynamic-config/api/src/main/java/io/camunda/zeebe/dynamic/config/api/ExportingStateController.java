/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves either the {@link ByTenant} controller for a single physical tenant, or the {@link
 * ClusterWide} controller for every physical tenant of the cluster in one atomic change, so callers
 * pick their scope once and then issue operations without repeating it.
 *
 * <p>This is the single abstraction for changing exporting state: the {@code /actuator/exporting}
 * and {@code /v2/exporting} endpoints (per-tenant) and the {@code /cluster/v2/exporting} endpoint
 * (cluster-wide) all go through it, so they cannot drift on how a change is submitted or awaited.
 */
@NullMarked
public interface ExportingStateController {

  ByTenant getByTenant(String physicalTenantId);

  /**
   * Resolves the controller that applies exporting operations to every physical tenant of the
   * cluster at once, as a single atomic change plan, rather than one request per tenant.
   */
  ClusterWide clusterWide();

  /**
   * Controls exporting for the partitions of the single physical tenant this instance was resolved
   * for, mirroring the exporting operations of {@code BrokerAdminService}. The returned futures
   * complete once the requested state has actually been applied, so callers can expose a
   * synchronous request/response contract. The returned futures are guaranteed to terminate within
   * a timeout.
   */
  interface ByTenant {

    CompletableFuture<Void> pauseExporting();

    CompletableFuture<Void> softPauseExporting();

    CompletableFuture<Void> resumeExporting();

    /**
     * Returns the exporting status aggregated over every partition replica, so callers can confirm
     * a pause took effect instead of relying on their own bookkeeping.
     */
    CompletableFuture<ExportingStatus> getExportingStatus();
  }

  /**
   * Mirrors {@link ByTenant}, but every operation applies to every physical tenant of the cluster
   * at once, submitted as a single change plan rather than one request per tenant. A tenant that is
   * disabled, or added after the change was submitted, is unaffected by it — the same scope the
   * coordinator already applies to any unscoped request.
   */
  interface ClusterWide {

    CompletableFuture<Void> pauseExporting();

    CompletableFuture<Void> softPauseExporting();

    CompletableFuture<Void> resumeExporting();

    /**
     * Returns the exporting status aggregated over every partition replica of every physical
     * tenant, so callers can confirm a cluster-wide pause took effect instead of relying on their
     * own bookkeeping.
     */
    CompletableFuture<ExportingStatus> getExportingStatus();
  }
}
