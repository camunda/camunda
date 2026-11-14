/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

import io.camunda.gateway.protocol.model.CamundaProblemDetail;
import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.spring.utils.PhysicalTenantAuthConfigurations;
import io.camunda.zeebe.util.Either;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.util.StringUtils;

/**
 * Whether mapping rules can be assigned to and unassigned from roles and groups. On SaaS, mapping
 * rules can only be managed once an additional IdP is configured; without one, an administrator
 * could otherwise remove the default mapping rules, such as the one that gives users the admin
 * role. The Admin UI applies the same condition to its mapping rules screens.
 *
 * <p>A physical tenant can override its OIDC providers, so the additional IdP is resolved for each
 * tenant. SaaS comes from the cluster configuration, because SaaS runs one organization per cluster
 * (#68208).
 */
public final class MappingRuleMembershipPolicy {

  static final String DETAIL =
      "Mapping rules can't be assigned to or unassigned from roles and groups on a SaaS cluster"
          + " without an additional identity provider.";

  private static final Logger LOG = LoggerFactory.getLogger(MappingRuleMembershipPolicy.class);

  private final boolean saas;
  // The default tenant's answer, also used for a tenant that is not configured.
  private final boolean clusterAdditionalIdpConfigured;
  private final Map<String, Boolean> additionalIdpConfiguredByTenant;

  /** A policy that gives every physical tenant the same answer. */
  public MappingRuleMembershipPolicy(final boolean manageable) {
    this(!manageable, false, Map.of());
  }

  private MappingRuleMembershipPolicy(
      final boolean saas,
      final boolean clusterAdditionalIdpConfigured,
      final Map<String, Boolean> additionalIdpConfiguredByTenant) {
    this.saas = saas;
    this.clusterAdditionalIdpConfigured = clusterAdditionalIdpConfigured;
    this.additionalIdpConfiguredByTenant = additionalIdpConfiguredByTenant;
  }

  /**
   * Manageable outside SaaS, and on SaaS when a named {@code providers.oidc.*} entry of the tenant,
   * the additional IdP, sets a client ID.
   *
   * @param properties the cluster configuration, which is the {@code default} tenant's resolved
   *     configuration when physical tenants are configured
   * @param environment the source of the other physical tenants' configuration
   */
  public static MappingRuleMembershipPolicy of(
      final CamundaSecurityLibraryProperties properties, final Environment environment) {
    final var saas = StringUtils.hasText(properties.getSaas().getOrganizationId());
    final Map<String, Boolean> byTenant = new HashMap<>();
    PhysicalTenantAuthConfigurations.forAllPhysicalTenants(environment)
        .forEach((tenantId, config) -> byTenant.put(tenantId, isAdditionalIdpConfigured(config)));
    final var clusterValue = isAdditionalIdpConfigured(properties.getAuthentication());
    LOG.info(
        "Mapping rule membership resolved: SaaS={}, additional IdP for the cluster={}, by physical"
            + " tenant={}",
        saas,
        clusterValue,
        byTenant);
    return new MappingRuleMembershipPolicy(saas, clusterValue, Map.copyOf(byTenant));
  }

  /**
   * Whether the physical tenant has an additional IdP. The {@code default} tenant, a tenant that is
   * not configured, and {@code null} use the cluster configuration.
   */
  public boolean isAdditionalIdpConfigured(final String physicalTenantId) {
    final Boolean tenantValue =
        physicalTenantId == null || DEFAULT_PHYSICAL_TENANT_ID.equals(physicalTenantId)
            ? null
            : additionalIdpConfiguredByTenant.get(physicalTenantId);
    return tenantValue != null ? tenantValue : clusterAdditionalIdpConfigured;
  }

  /** A {@code 403} problem when the tenant's mapping rules can't be managed, else nothing. */
  Either<ProblemDetail, Void> requireManageable(final String physicalTenantId) {
    if (!saas || isAdditionalIdpConfigured(physicalTenantId)) {
      return Either.right(null);
    }
    final var problemDetail = CamundaProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, DETAIL);
    problemDetail.setTitle("FORBIDDEN");
    return Either.left(problemDetail);
  }

  // Only the named providers.oidc.* slot counts (the BYOIDP additional-IdP surface); the flat
  // authentication.oidc block is the primary IdP and is intentionally excluded.
  private static boolean isAdditionalIdpConfigured(final AuthenticationConfiguration config) {
    final var providers = config != null ? config.getProviders() : null;
    final Map<String, OidcConfiguration> named = providers != null ? providers.getOidc() : null;
    return named != null
        && named.values().stream()
            .filter(Objects::nonNull)
            .anyMatch(oidc -> StringUtils.hasText(oidc.getClientId()));
  }
}
