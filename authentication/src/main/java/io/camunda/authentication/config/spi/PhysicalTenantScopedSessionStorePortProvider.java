/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.spi;

import io.camunda.search.clients.PersistentWebSessionClient;
import io.camunda.search.clients.tenant.PhysicalTenantScoped;
import io.camunda.security.core.port.out.ScopedSessionStorePortProvider;
import io.camunda.security.core.port.out.SessionStorePort;
import io.camunda.spring.utils.PhysicalTenantContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Host implementation of CSL's {@link ScopedSessionStorePortProvider}: maps a scope's {@code
 * basePath} ({@code /physical-tenants/<id>}) to a {@link SessionStorePort} bound to that physical
 * tenant's store, resolved through the existing {@link PhysicalTenantScoped} provider.
 *
 * <p>This lets CSL give each scoped {@code SessionRepositoryFilter} its own single-tenant store, so
 * a scope's persistent session reads/writes route to the correct storage <em>structurally</em> —
 * even during Spring Session's commit phase, when the request scope is gone. See CSL ADR-0029.
 *
 * <p>Adapters are built once per tenant and cached: CSL's expiry sweep deduplicates repositories by
 * backing {@link SessionStorePort} <em>instance</em>, so a caller that needs the default tenant's
 * store outside a basePath (the global session filter's default surface) should call {@link
 * #forPhysicalTenant} on this same shared instance rather than build its own adapter — that is what
 * makes the sweep's dedup of the default store actually collapse (CSL ADR-0029 §4).
 */
public final class PhysicalTenantScopedSessionStorePortProvider
    implements ScopedSessionStorePortProvider {

  private final PhysicalTenantScoped<PersistentWebSessionClient> sessionClients;
  private final Map<String, SessionStorePort> adaptersByPhysicalTenant = new ConcurrentHashMap<>();

  public PhysicalTenantScopedSessionStorePortProvider(
      final PhysicalTenantScoped<PersistentWebSessionClient> sessionClients) {
    this.sessionClients = sessionClients;
  }

  @Override
  public SessionStorePort forBasePath(final String basePath) {
    return forPhysicalTenant(physicalTenantIdFrom(basePath));
  }

  /**
   * Returns the single-store adapter for {@code physicalTenantId}, building and caching it on first
   * use so repeated lookups for the same tenant share one instance.
   */
  public SessionStorePort forPhysicalTenant(final String physicalTenantId) {
    return adaptersByPhysicalTenant.computeIfAbsent(
        physicalTenantId, id -> new SessionStoreAdapter(sessionClients.withPhysicalTenant(id)));
  }

  private static String physicalTenantIdFrom(final String basePath) {
    final String prefix = PhysicalTenantContext.PHYSICAL_TENANTS_PATH_SEGMENT;
    if (basePath != null && basePath.startsWith(prefix)) {
      final String id = basePath.substring(prefix.length());
      if (!id.isEmpty()) {
        return id;
      }
    }
    throw new IllegalArgumentException(
        "Cannot derive a physical tenant id from scope basePath '"
            + basePath
            + "'; expected the form '"
            + prefix
            + "<id>'");
  }
}
