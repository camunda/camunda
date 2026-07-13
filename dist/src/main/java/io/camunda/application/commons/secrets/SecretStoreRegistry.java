/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import io.camunda.secretstore.SecretStore;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/** Provides the configured {@link SecretStore}s per physical tenant, keyed by store ID. */
@NullMarked
public final class SecretStoreRegistry {

  private final Map<String, Map<String, SecretStore<?>>> storesByTenant;

  public SecretStoreRegistry(final Map<String, Map<String, SecretStore<?>>> storesByTenant) {
    this.storesByTenant = storesByTenant;
  }

  /**
   * Returns all secret stores configured for the given physical tenant, keyed by store ID.
   *
   * @throws IllegalArgumentException if the tenant ID is not known
   */
  public Map<String, SecretStore<?>> getStores(final String physicalTenantId) {
    final Map<String, SecretStore<?>> stores = storesByTenant.get(physicalTenantId);
    if (stores == null) {
      throw new IllegalArgumentException(
          "No secret store registry entry for unknown physical tenant '" + physicalTenantId + "'");
    }
    return stores;
  }

  /** Returns the set of all known physical tenant IDs. */
  public Set<String> tenants() {
    return storesByTenant.keySet();
  }
}
