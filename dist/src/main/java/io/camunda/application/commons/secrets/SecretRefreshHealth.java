/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

/**
 * Where a refresher reports whether a consumer of a physical tenant runs with its current secrets.
 * A failed refresh leaves the consumer on its previous client, so the tenant is degraded rather
 * than down.
 */
public interface SecretRefreshHealth {

  /** Reports nowhere, for refreshers that no tenant health depends on. */
  SecretRefreshHealth IGNORED =
      new SecretRefreshHealth() {
        @Override
        public void refreshed(final String physicalTenantId, final String consumer) {}

        @Override
        public void failed(
            final String physicalTenantId, final String consumer, final String reason) {}
      };

  void refreshed(String physicalTenantId, String consumer);

  /**
   * @param reason why the refresh failed; it must not contain a secret value
   */
  void failed(String physicalTenantId, String consumer, String reason);
}
