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
 * <p>Each per-tenant provider is backed by that tenant's own {@code AuthorizationReader}. When a
 * tenant has no dedicated provider -- it is absent from the map, or the physical tenant could not
 * be resolved off a request thread ({@code null}) -- {@code withPhysicalTenant} falls back to
 * {@code defaultProvider}, the root-scoped bean.
 */
public record PhysicalTenantResourceAccessProvider(
    ResourceAccessProvider defaultProvider,
    Map<String, ResourceAccessProvider> providersByPhysicalTenant)
    implements PhysicalTenantScoped<ResourceAccessProvider> {

  @Override
  public ResourceAccessProvider withPhysicalTenant(final String physicalTenantId) {
    if (physicalTenantId == null) {
      return defaultProvider;
    }
    return providersByPhysicalTenant.getOrDefault(physicalTenantId, defaultProvider);
  }
}
