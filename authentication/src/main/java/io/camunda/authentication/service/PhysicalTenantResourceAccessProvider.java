/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import io.camunda.search.clients.tenant.PhysicalTenantScoped;
import io.camunda.security.core.authz.ResourceAccessProvider;
import java.util.Map;

/**
 * Provides the {@link ResourceAccessProvider} scoped to a given physical tenant, mirroring {@code
 * AuthorizationCheckerProvider} and the way {@code SearchClientsProxy} is scoped via {@link
 * PhysicalTenantScoped}.
 *
 * <p>The consolidated-webapp {@code /v2/authentication/me} handlers ({@code
 * BasicCamundaUserService} and {@code AuthorizedComponentsAdapter}) resolve a user's {@code
 * COMPONENT ACCESS} authorizations by querying a {@link ResourceAccessProvider} directly, bypassing
 * the request-path {@code ResourceAccessController}. They obtain their provider through {@link
 * #withPhysicalTenant(String)} so the lookup reads that tenant's own authorization storage,
 * consistent with how the same authorizations are enforced on API requests.
 *
 * <p>Every configured physical tenant (including the default one) has its own provider, backed by
 * that tenant's own {@code AuthorizationReader}. {@link #withPhysicalTenant(String)} <b>fails hard
 * when no provider is registered for the requested tenant</b> rather than falling back to a shared
 * default: silently resolving against another tenant's authorization storage would break tenant
 * isolation, so an unknown (or unresolved) physical tenant is treated as a configuration error.
 */
public record PhysicalTenantResourceAccessProvider(
    Map<String, ResourceAccessProvider> providersByPhysicalTenant)
    implements PhysicalTenantScoped<ResourceAccessProvider> {

  @Override
  public ResourceAccessProvider withPhysicalTenant(final String physicalTenantId) {
    final var provider =
        physicalTenantId == null ? null : providersByPhysicalTenant.get(physicalTenantId);
    if (provider == null) {
      throw new IllegalStateException(
          "No ResourceAccessProvider registered for physical tenant '"
              + physicalTenantId
              + "'; refusing to fall back to a shared default, as resolving authorizations against "
              + "another tenant's storage would break tenant isolation. This indicates a "
              + "configuration issue: the physical tenant is unknown or has no authorization source.");
    }
    return provider;
  }
}
