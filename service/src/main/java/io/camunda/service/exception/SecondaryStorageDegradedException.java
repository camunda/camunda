/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.exception;

/**
 * Thrown when secondary storage is configured but the request's physical tenant is currently
 * degraded (schema not initialized / storage unusable). HTTP 503 — contrast with {@link
 * SecondaryStorageUnavailableException}, which is HTTP 403 for secondary storage not being
 * configured at all.
 */
public final class SecondaryStorageDegradedException extends ServiceException {
  public static final String SECONDARY_STORAGE_DEGRADED_MESSAGE =
      "Physical tenant '%s' is degraded: its secondary storage is not ready. Requests are rejected until it recovers.";
  public static final String CLUSTER_SECONDARY_STORAGE_DEGRADED_MESSAGE =
      "No physical tenant of this cluster has ready secondary storage. Requests are rejected until at least one recovers.";

  private SecondaryStorageDegradedException(final String message) {
    super(message, Status.UNAVAILABLE);
  }

  /** The request's own physical tenant is degraded. */
  public static SecondaryStorageDegradedException forPhysicalTenant(final String physicalTenantId) {
    return new SecondaryStorageDegradedException(
        SECONDARY_STORAGE_DEGRADED_MESSAGE.formatted(physicalTenantId));
  }

  /**
   * Names no physical tenant, because a cluster-wide endpoint is not served on behalf of one: it is
   * rejected only when no tenant at all can be served.
   */
  public static SecondaryStorageDegradedException forCluster() {
    return new SecondaryStorageDegradedException(CLUSTER_SECONDARY_STORAGE_DEGRADED_MESSAGE);
  }
}
