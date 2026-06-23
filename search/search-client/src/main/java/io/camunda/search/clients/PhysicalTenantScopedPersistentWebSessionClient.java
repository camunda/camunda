/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import io.camunda.search.clients.tenant.PhysicalTenantScoped;
import java.util.Map;

/**
 * Immutable {@link PhysicalTenantScoped} provider that resolves a {@link
 * PersistentWebSessionClient} by physical tenant id from a pre-built, per-tenant client map.
 *
 * @throws IllegalStateException if the requested physical tenant has no registered client
 */
public final class PhysicalTenantScopedPersistentWebSessionClient
    implements PhysicalTenantScoped<PersistentWebSessionClient> {

  private final Map<String, PersistentWebSessionClient> clientsByPhysicalTenant;

  public PhysicalTenantScopedPersistentWebSessionClient(
      final Map<String, PersistentWebSessionClient> clientsByPhysicalTenant) {
    this.clientsByPhysicalTenant = Map.copyOf(clientsByPhysicalTenant);
  }

  @Override
  public PersistentWebSessionClient withPhysicalTenant(final String physicalTenantId) {
    final PersistentWebSessionClient client = clientsByPhysicalTenant.get(physicalTenantId);
    if (client == null) {
      throw new IllegalStateException(
          "No persistent web session client registered for physical tenant '"
              + physicalTenantId
              + "'");
    }
    return client;
  }
}
