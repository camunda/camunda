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
 * Reports the availability of physical tenants, consulted for request-time rejection and node
 * readiness.
 *
 * <p>Per ADR 001 D2 ({@code docs/adr/management/001-physical-tenant-health-status-topology.md}), a
 * physical tenant is <b>degraded</b> when its schema is not initialized or its secondary storage is
 * otherwise unusable. {@link #isServiceable(String)} is {@code true} exactly when the tenant is not
 * degraded.
 *
 * <p>Implementations currently derive serviceability purely from schema-initialization state.
 * Richer "storage unusable" signals (e.g. a degraded connection to an already-initialized store)
 * will be folded in by the schema-init isolation work
 * (https://github.com/camunda/camunda/issues/57025 for Elasticsearch/OpenSearch,
 * https://github.com/camunda/camunda/issues/54299 for RDBMS) without requiring any change to the
 * consumers of this interface.
 */
@NullMarked
public interface PhysicalTenantAvailability {

  /**
   * Reports every physical tenant as serviceable. Used when there is no degradation signal to
   * consult (e.g. {@code database.type=none}) and in tests.
   */
  PhysicalTenantAvailability ALWAYS_SERVICEABLE =
      new PhysicalTenantAvailability() {
        @Override
        public boolean isServiceable(final String physicalTenantId) {
          return true;
        }

        @Override
        public boolean anyServiceable() {
          return true;
        }
      };

  /**
   * @param physicalTenantId the physical tenant id to check
   * @return {@code true} if the physical tenant is serviceable (not degraded); {@code false} for a
   *     degraded physical tenant, or for an unknown physical tenant id (defense in depth — unknown
   *     tenant ids are already rejected upstream by the security chain)
   */
  boolean isServiceable(String physicalTenantId);

  /**
   * @return {@code true} if at least one known physical tenant is serviceable. Used to decide node
   *     readiness: the node stays ready as long as it can serve at least one physical tenant.
   */
  boolean anyServiceable();
}
