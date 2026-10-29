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

/**
 * The internal-config secret store per physical tenant: the store that configuration references
 * resolve from. Kept apart from {@link SecretStoreRegistries}, which the job path receives, so that
 * no job can reach it.
 */
public final class InternalConfigSecrets implements AutoCloseable {

  private final Map<String, SecretStoreRegistry> byPhysicalTenant;
  private final SecretStoreResources resources;

  InternalConfigSecrets(
      final Map<String, SecretStoreRegistry> byPhysicalTenant,
      final SecretStoreResources resources) {
    this.byPhysicalTenant = Map.copyOf(byPhysicalTenant);
    this.resources = resources;
  }

  /** A tenant without an internal-config store resolves literal values only. */
  public static InternalConfigSecrets none() {
    return new InternalConfigSecrets(Map.of(), new SecretStoreResources());
  }

  public InternalConfigSecretResolver forPhysicalTenant(final String physicalTenantId) {
    final var registry = byPhysicalTenant.get(physicalTenantId);
    return new InternalConfigSecretResolver(
        physicalTenantId,
        registry == null ? null : registry.getStores().get(SecretStoreRegistry.DEFAULT_STORE_ID));
  }

  @Override
  public void close() {
    resources.close();
  }
}
