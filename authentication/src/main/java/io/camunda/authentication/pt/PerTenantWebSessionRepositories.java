/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import io.camunda.authentication.session.WebSessionMapper;
import io.camunda.authentication.session.WebSessionRepository;
import io.camunda.search.clients.PersistentWebSessionClient;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

/**
 * Registry of per-tenant {@link WebSessionRepository} instances. Each tenant's repository is bound
 * to its own {@link PersistentWebSessionClient}; storage isolation is structural at the backend
 * layer — no shared backend, no key-prefixing decorator.
 *
 * <p>Created once at startup from the per-tenant {@code Map<String, PersistentWebSessionClient>}
 * produced by the {@code pt-security}-gated configuration in {@code
 * WebSessionRepositoryConfiguration}, and consumed by {@link PhysicalTenantSecurityConfiguration}
 * when assembling each tenant chain's {@code SessionRepositoryFilter}.
 */
@NullMarked
public final class PerTenantWebSessionRepositories {

  private final Map<String, WebSessionRepository> repositoriesByTenant;

  public PerTenantWebSessionRepositories(
      final Map<String, PersistentWebSessionClient> clientsByTenant,
      final WebSessionMapper mapper,
      final HttpServletRequest request) {
    final Map<String, WebSessionRepository> built = new HashMap<>();
    clientsByTenant.forEach(
        (tenantId, client) ->
            built.put(tenantId, new WebSessionRepository(client, mapper, request)));
    repositoriesByTenant = Map.copyOf(built);
  }

  public WebSessionRepository forTenant(final String tenantId) {
    final WebSessionRepository repo = repositoriesByTenant.get(tenantId);
    if (repo == null) {
      throw new IllegalArgumentException("No WebSessionRepository for tenant '" + tenantId + "'");
    }
    return repo;
  }
}
