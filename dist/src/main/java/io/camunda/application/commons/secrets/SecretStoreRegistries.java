/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import io.camunda.configuration.Secrets;
import io.camunda.secretstore.SecretStoreRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import org.agrona.CloseHelper;

/**
 * The configured {@link SecretStoreRegistry} per physical tenant, keyed by physical tenant ID. A
 * first-class holder instead of a plain {@code Map} bean, so it can be injected by type.
 *
 * <p>Each tenant's stores are built, replaced and closed on their own, so a change to one tenant
 * leaves the stores of the others untouched.
 */
public final class SecretStoreRegistries implements AutoCloseable {

  // holds no stores, so no cache is ever built and camunda.secrets.cache.* does not reach it
  private static final SecretStoreRegistry NO_STORES = new SecretStoreRegistry(Map.of());

  private final Map<String, TenantSecretStores> byPhysicalTenant = new ConcurrentHashMap<>();
  private final BiFunction<String, Secrets, TenantSecretStores> factory;

  public SecretStoreRegistries(final Map<String, SecretStoreRegistry> byPhysicalTenant) {
    this(
        (tenantId, secrets) -> {
          throw new UnsupportedOperationException("These registries can't build secret stores");
        });
    byPhysicalTenant.forEach(
        (tenantId, registry) ->
            this.byPhysicalTenant.put(
                tenantId, new TenantSecretStores(registry, new SecretStoreResources())));
  }

  SecretStoreRegistries(final BiFunction<String, Secrets, TenantSecretStores> factory) {
    this.factory = factory;
  }

  public Map<String, SecretStoreRegistry> byPhysicalTenant() {
    final Map<String, SecretStoreRegistry> registries = new HashMap<>();
    byPhysicalTenant.forEach((tenantId, stores) -> registries.put(tenantId, stores.registry()));
    return Map.copyOf(registries);
  }

  /**
   * Builds the stores of the given physical tenant and puts them in place of its current stores,
   * which are then closed. If the new stores fail to build, the current ones stay in place.
   */
  public void put(final String physicalTenantId, final Secrets secrets) {
    final var replaced =
        byPhysicalTenant.put(physicalTenantId, factory.apply(physicalTenantId, secrets));
    if (replaced != null) {
      replaced.close();
    }
  }

  /** Removes the stores of the given physical tenant and closes them. */
  public void remove(final String physicalTenantId) {
    final var removed = byPhysicalTenant.remove(physicalTenantId);
    if (removed != null) {
      removed.close();
    }
  }

  /** Releases the stores, pools and meter registries behind the registries; called on shutdown. */
  @Override
  public void close() {
    final List<TenantSecretStores> stores = List.copyOf(byPhysicalTenant.values());
    byPhysicalTenant.clear();
    CloseHelper.closeAll(stores);
  }

  /**
   * Returns the registry of the given physical tenant, or an empty registry (no stores, no caches)
   * if the tenant has none.
   */
  public SecretStoreRegistry forPhysicalTenant(final String physicalTenantId) {
    final var stores = byPhysicalTenant.get(physicalTenantId);
    return stores == null ? NO_STORES : stores.registry();
  }
}
