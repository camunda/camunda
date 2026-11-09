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
import io.camunda.spring.utils.PhysicalTenantAuthConfigurations;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Whether each physical tenant manages its groups in Camunda, from the tenant's own authentication
 * configuration. A physical tenant can override its OIDC providers and groups claim, so the cluster
 * configuration alone gives every tenant the {@code default} tenant's answer.
 *
 * <p>The web app configuration and the Groups API use this one source, so the UI offers group
 * management exactly where the API allows it. A tenant that is not configured uses the {@code
 * default} tenant's groups mode.
 *
 * <p>The {@code default} tenant comes from the cluster configuration bean in one overload and from
 * the environment in the other. Both hold the same configuration: with physical tenants, {@code
 * PhysicalTenantSecurityConfiguration#physicalTenantClusterAuthUnification} replaces the bean's
 * authentication with {@link PhysicalTenantAuthConfigurations#forPhysicalTenant} for {@code
 * default}, which is what the environment entry holds; without them, both bind {@code
 * camunda.security.authentication} from the environment.
 */
public final class PhysicalTenantCamundaGroups {

  // Every tenant, the default one included, resolved from the environment.
  private final Map<String, Boolean> byTenant = new HashMap<>();

  public PhysicalTenantCamundaGroups(final Environment environment) {
    PhysicalTenantAuthConfigurations.forAllPhysicalTenants(environment)
        .forEach((tenantId, config) -> byTenant.put(tenantId, config.isCamundaGroupsEnabled()));
  }

  /**
   * For a caller that has the cluster configuration bean: the {@code default} tenant, and a tenant
   * that is not configured, use {@code clusterConfig}, which is the {@code default} tenant's
   * resolved configuration when physical tenants are configured.
   *
   * @param physicalTenantId the tenant of the request, or {@code null} for the cluster surface
   * @param clusterConfig the cluster authentication configuration, or {@code null} if there is none
   */
  public boolean isCamundaGroupsEnabled(
      final String physicalTenantId, final AuthenticationConfiguration clusterConfig) {
    final Boolean tenantValue =
        physicalTenantId == null || DEFAULT_PHYSICAL_TENANT_ID.equals(physicalTenantId)
            ? null
            : byTenant.get(physicalTenantId);
    if (tenantValue != null) {
      return tenantValue;
    }
    return clusterConfig != null && clusterConfig.isCamundaGroupsEnabled();
  }

  /**
   * For a caller without the cluster configuration bean, such as a filter that runs before the
   * request's tenant is resolved: everything comes from the environment.
   *
   * @param physicalTenantId the tenant of the request
   */
  public boolean isCamundaGroupsEnabled(final String physicalTenantId) {
    final Boolean tenantValue = byTenant.get(physicalTenantId);
    return tenantValue != null ? tenantValue : byTenant.get(DEFAULT_PHYSICAL_TENANT_ID);
  }

  /** Whether some tenant manages its groups in Camunda, so the Groups API must exist. */
  public boolean isCamundaGroupsEnabledForAnyTenant() {
    return byTenant.containsValue(true);
  }

  /** Whether some tenant takes its groups from a groups claim, so the API must be blocked there. */
  public boolean isCamundaGroupsDisabledForAnyTenant() {
    return byTenant.containsValue(false);
  }

  /** Matches when some physical tenant manages its groups in Camunda. */
  public static final class EnabledForAnyTenant implements Condition {
    @Override
    public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      return new PhysicalTenantCamundaGroups(context.getEnvironment())
          .isCamundaGroupsEnabledForAnyTenant();
    }
  }

  /** Matches when some physical tenant takes its groups from a groups claim. */
  public static final class DisabledForAnyTenant implements Condition {
    @Override
    public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      return new PhysicalTenantCamundaGroups(context.getEnvironment())
          .isCamundaGroupsDisabledForAnyTenant();
    }
  }
}
