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
 * io.camunda.search.clients.reader.AuthorizationReader}. When there is no dedicated checker for a
 * tenant -- either because secondary storage is disabled (there is exactly one authorization source
 * cluster-wide) or because a tenant is absent from the map -- {@code withPhysicalTenant} falls back
 * to {@code defaultChecker}.
 */
public record AuthorizationCheckerProvider(
    AuthorizationChecker defaultChecker, Map<String, AuthorizationChecker> checkersByPhysicalTenant)
    implements PhysicalTenantScoped<AuthorizationChecker> {

  @Override
  public AuthorizationChecker withPhysicalTenant(final String physicalTenantId) {
    return checkersByPhysicalTenant.getOrDefault(physicalTenantId, defaultChecker);
  }
}
