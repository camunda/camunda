/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

import io.camunda.security.api.model.config.AuthenticationConfiguration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.core.env.Environment;

/**
 * Whether each physical tenant manages its groups in Camunda, from the tenant's own authentication
 * configuration. A physical tenant can override its OIDC providers and groups claim, so the cluster
 * configuration alone gives every tenant the {@code default} tenant's answer.
 *
 * <p>The {@code default} tenant, and a tenant that is not configured, use the cluster
 * configuration, which is the {@code default} tenant's resolved configuration when physical tenants
 * are configured.
 */
public final class PhysicalTenantCamundaGroups {

  private final Map<String, Boolean> byExplicitTenant = new HashMap<>();

  public PhysicalTenantCamundaGroups(final Environment environment) {
    PhysicalTenantAuthConfigurations.forAllPhysicalTenants(environment)
        .forEach(
            (tenantId, config) -> {
              if (!DEFAULT_PHYSICAL_TENANT_ID.equals(tenantId)) {
                byExplicitTenant.put(tenantId, config.isCamundaGroupsEnabled());
              }
            });
  }

  /**
   * @param physicalTenantId the tenant of the request, or {@code null} for the cluster surface
   * @param clusterConfig the cluster authentication configuration, or {@code null} if there is none
   */
  public boolean isCamundaGroupsEnabled(
      final String physicalTenantId, final AuthenticationConfiguration clusterConfig) {
    final Boolean tenantValue =
        physicalTenantId != null ? byExplicitTenant.get(physicalTenantId) : null;
    if (tenantValue != null) {
      return tenantValue;
    }
    return clusterConfig != null && clusterConfig.isCamundaGroupsEnabled();
  }
}
