/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.cluster;

import org.jspecify.annotations.NullMarked;

/**
 * Reports the readiness of a physical tenant's secondary storage, consulted for request-time
 * rejection and node readiness.
 *
 * <p>Per ADR 001 D2 ({@code docs/adr/management/001-physical-tenant-health-status-topology.md}), a
 * physical tenant is <b>degraded</b> when its schema is not initialized or its secondary storage is
 * otherwise unusable. {@link #isReady(String)} returns {@code false} when the tenant is known to be
 * degraded.
 *
 * <p>Ready means the tenant's secondary-storage schema has been initialized and the storage is
 * ready for use. It does <b>not</b> probe live storage connectivity: a storage outage occurring
 * after successful initialization is not reflected by this signal.
 *
 * <p>This covers only the secondary-storage axis of physical tenant health. Partition/leader health
 * is a deliberately separate, topology-derived signal (per-PT topology, ADR 001 D1/D6; tracked by
 * https://github.com/camunda/camunda/issues/57026 and
 * https://github.com/camunda/camunda/issues/57027). Consumers that need the combined picture (e.g.
 * {@code /cluster/v2/status}) must query both signals independently.
 *
 * <p>Implementations currently derive readiness purely from schema-initialization state. Richer
 * "storage unusable" signals (e.g. a degraded connection to an already-initialized store) will be
 * folded in by the schema-init isolation work (https://github.com/camunda/camunda/issues/57025 for
 * Elasticsearch/OpenSearch, https://github.com/camunda/camunda/issues/54299 for RDBMS) without
 * requiring any change to the consumers of this interface.
 */
@NullMarked
public interface SecondaryStorageReadiness {

  /**
   * Reports every physical tenant's secondary storage as ready. Used when there is no degradation
   * signal to consult (e.g. {@code database.type=none}) and in tests.
   */
  SecondaryStorageReadiness ALWAYS_READY =
      new SecondaryStorageReadiness() {
        @Override
        public boolean isReady(final String physicalTenantId) {
          return true;
        }

        @Override
        public boolean anyReady() {
          return true;
        }
      };

  /**
   * @param physicalTenantId the physical tenant id to check
   * @return {@code true} if the physical tenant's secondary storage is ready (not degraded); {@code
   *     false} for a degraded physical tenant, or for an unknown physical tenant id (defense in
   *     depth — unknown tenant ids are already rejected upstream by the security chain)
   */
  boolean isReady(String physicalTenantId);

  /**
   * @return {@code true} if at least one known physical tenant's secondary storage is ready. Used
   *     to decide node readiness: the node stays ready as long as it can serve at least one
   *     physical tenant.
   */
  boolean anyReady();
}
