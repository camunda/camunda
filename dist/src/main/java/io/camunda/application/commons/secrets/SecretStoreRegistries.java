/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import io.camunda.secretstore.SecretStoreRegistry;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

/**
 * The configured {@link SecretStoreRegistry} per physical tenant, keyed by physical tenant ID. A
 * first-class holder instead of a plain {@code Map} bean, so it can be injected by type.
 */
@NullMarked
public record SecretStoreRegistries(Map<String, SecretStoreRegistry> byPhysicalTenant) {

  private static final SecretStoreRegistry NO_STORES = new SecretStoreRegistry(Map.of());

  /**
   * Returns the registry of the given physical tenant, or an empty registry (no stores, no caches)
   * if the tenant has none.
   */
  public SecretStoreRegistry forPhysicalTenant(final String physicalTenantId) {
    return byPhysicalTenant.getOrDefault(physicalTenantId, NO_STORES);
  }
}
