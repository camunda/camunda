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
 * <p>Ready means the tenant's secondary-storage schema has been initialized and the storage is
 * ready for use. It does <b>not</b> probe live storage connectivity.
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
   *     false} for degraded or unknown tenants.
   */
  boolean isReady(String physicalTenantId);

  /**
   * @return {@code true} if at least one known physical tenant's secondary storage is ready. Used
   *     to decide node readiness: the node stays ready as long as it can serve at least one
   *     physical tenant.
   */
  boolean anyReady();
}
