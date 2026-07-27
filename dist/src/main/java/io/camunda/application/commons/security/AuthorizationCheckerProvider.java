/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import io.camunda.search.clients.tenant.PhysicalTenantScoped;
import io.camunda.security.core.authz.AuthorizationChecker;
import java.util.Map;

/**
 * Provides the {@link AuthorizationChecker} scoped to a given physical tenant, mirroring the way
 * {@link io.camunda.search.clients.SearchClientsProxy} is scoped via {@link PhysicalTenantScoped}.
 *
 * <p>Services that query {@link AuthorizationChecker} directly (bypassing the {@code
 * ResourceAccessController}/data-plane path), such as {@code DocumentServices} and {@code
 * SecretServices}, obtain their checker through {@link #withPhysicalTenant(String)} instead of
 * sharing the single default-tenant-pinned bean.
 *
 * <p>Each per-tenant checker is backed by that tenant's own {@link
 * io.camunda.search.clients.reader.AuthorizationReader}.
 *
 * <p>When secondary storage is disabled there are no per-tenant checkers at all ({@code
 * checkersByPhysicalTenant} is empty) and there is exactly one authorization source cluster-wide,
 * so {@code withPhysicalTenant} resolves every tenant to {@code defaultChecker}. Once per-tenant
 * checkers exist, however, an unknown physical tenant is <b>a configuration error rather than a
 * default</b>: {@code withPhysicalTenant} fails hard instead of silently resolving against another
 * tenant's authorization storage (which would break tenant isolation).
 */
public record AuthorizationCheckerProvider(
    AuthorizationChecker defaultChecker, Map<String, AuthorizationChecker> checkersByPhysicalTenant)
    implements PhysicalTenantScoped<AuthorizationChecker> {

  @Override
  public AuthorizationChecker withPhysicalTenant(final String physicalTenantId) {
    if (checkersByPhysicalTenant.isEmpty()) {
      // secondary storage disabled: one authorization source cluster-wide, no per-tenant checkers
      return defaultChecker;
    }
    final var checker =
        physicalTenantId == null ? null : checkersByPhysicalTenant.get(physicalTenantId);
    if (checker == null) {
      throw new IllegalStateException(
          "No AuthorizationChecker registered for physical tenant '"
              + physicalTenantId
              + "'; refusing to fall back to a shared default, as resolving authorizations against "
              + "another tenant's storage would break tenant isolation. This indicates a "
              + "configuration issue: the physical tenant is unknown or has no authorization source.");
    }
    return checker;
  }
}
