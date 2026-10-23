/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

import io.camunda.authentication.pt.PhysicalTenantAuthConfigurations;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.spring.utils.PhysicalTenantContext;
import java.util.HashMap;
import java.util.Map;
import org.springframework.core.env.Environment;

/**
 * The OIDC groups settings of each physical tenant, for the membership services. Each tenant reads
 * a token's groups from the groups claim of the provider that issued it, as its own config
 * declares; a provider without one leaves the user's groups to Camunda (see {@link
 * ProviderGroupsClaims}).
 */
final class PhysicalTenantGroupsClaims {

  private final Map<String, ProviderGroupsClaims> byPhysicalTenant = new HashMap<>();

  /**
   * @param cslProperties the config of the cluster surface, which is the {@code default} tenant's
   *     resolved config when physical tenants are configured
   * @param environment the source of every other physical tenant's resolved config
   */
  PhysicalTenantGroupsClaims(
      final CamundaSecurityLibraryProperties cslProperties, final Environment environment) {
    PhysicalTenantAuthConfigurations.forAllPhysicalTenants(environment)
        .forEach(
            (tenantId, config) -> byPhysicalTenant.put(tenantId, new ProviderGroupsClaims(config)));
    byPhysicalTenant.put(
        DEFAULT_PHYSICAL_TENANT_ID, new ProviderGroupsClaims(cslProperties.getAuthentication()));
  }

  /**
   * The settings of the physical tenant of the current request. A tenant that is not configured
   * uses the {@code default} tenant's settings, like the REST bearer-token converter.
   *
   * @throws IllegalStateException when no physical tenant is bound, so that a lookup that lost its
   *     tenant fails instead of reading groups with another tenant's settings
   */
  ProviderGroupsClaims current() {
    final ProviderGroupsClaims claims = byPhysicalTenant.get(PhysicalTenantContext.current());
    return claims != null ? claims : byPhysicalTenant.get(DEFAULT_PHYSICAL_TENANT_ID);
  }
}
