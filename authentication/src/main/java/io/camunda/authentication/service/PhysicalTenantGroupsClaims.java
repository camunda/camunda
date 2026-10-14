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
import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.core.oidc.OidcGroupsExtractor;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.spring.utils.PhysicalTenantContext;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.env.Environment;

/**
 * The OIDC groups settings of each physical tenant, for the membership services. A tenant's {@code
 * oidc.groups-claim} sets its groups mode. A token's groups are read from the {@code groups-claim}
 * of the provider that issued it, or from the tenant's {@code oidc.groups-claim} when that provider
 * sets none or the issuer is unknown.
 *
 * <p>The providers are keyed by issuer like {@code
 * PhysicalTenantHandlerFactory#claimConfigsByIssuer} (gRPC) and {@code
 * OidcOverrideBeansConfiguration#buildPhysicalTenantConverter} (REST principal claims), so the
 * three resolve a token with the same provider.
 */
final class PhysicalTenantGroupsClaims {

  private static final String ISSUER_CLAIM = "iss";

  private final Map<String, TenantGroupsClaims> byPhysicalTenant = new HashMap<>();

  /**
   * @param cslProperties the config of the cluster surface, which is the {@code default} tenant's
   *     resolved config when physical tenants are configured
   * @param environment the source of every other physical tenant's resolved config
   */
  PhysicalTenantGroupsClaims(
      final CamundaSecurityLibraryProperties cslProperties, final Environment environment) {
    PhysicalTenantAuthConfigurations.forAllPhysicalTenants(environment)
        .forEach(
            (tenantId, config) -> byPhysicalTenant.put(tenantId, TenantGroupsClaims.of(config)));
    byPhysicalTenant.put(
        DEFAULT_PHYSICAL_TENANT_ID, TenantGroupsClaims.of(cslProperties.getAuthentication()));
  }

  /**
   * The settings of the physical tenant of the current request. A request without a tenant, or with
   * a tenant that is not configured, uses the {@code default} tenant's settings, like the REST
   * bearer-token converter.
   */
  TenantGroupsClaims current() {
    final String physicalTenantId = PhysicalTenantContext.currentOrNull();
    final TenantGroupsClaims claims =
        physicalTenantId == null ? null : byPhysicalTenant.get(physicalTenantId);
    return claims != null ? claims : byPhysicalTenant.get(DEFAULT_PHYSICAL_TENANT_ID);
  }

  private static boolean hasText(final String value) {
    return value != null && !value.isBlank();
  }

  record TenantGroupsClaims(
      boolean groupsClaimConfigured,
      OidcGroupsExtractor tenantExtractor,
      Map<String, OidcGroupsExtractor> extractorsByIssuer) {

    private static TenantGroupsClaims of(final AuthenticationConfiguration config) {
      final OidcConfiguration defaultSlot = config.getOidc();
      final String tenantGroupsClaim = defaultSlot.getGroupsClaim();
      final Map<String, OidcGroupsExtractor> byIssuer = new LinkedHashMap<>();
      if (hasText(defaultSlot.getIssuerUri()) && hasText(defaultSlot.getClientId())) {
        byIssuer.put(defaultSlot.getIssuerUri(), new OidcGroupsExtractor(tenantGroupsClaim));
      }
      if (config.getProviders() != null && config.getProviders().getOidc() != null) {
        config
            .getProviders()
            .getOidc()
            .values()
            .forEach(
                provider -> {
                  if (hasText(provider.getIssuerUri())) {
                    byIssuer.putIfAbsent(
                        provider.getIssuerUri(),
                        new OidcGroupsExtractor(
                            provider.isGroupsClaimConfigured()
                                ? provider.getGroupsClaim()
                                : tenantGroupsClaim));
                  }
                });
      }
      return new TenantGroupsClaims(
          defaultSlot.isGroupsClaimConfigured(),
          new OidcGroupsExtractor(tenantGroupsClaim),
          Map.copyOf(byIssuer));
    }

    /**
     * Reads the groups with the extractor of the token's issuer. The ID token of a browser login
     * carries {@code iss} as a {@link java.net.URL}, so it is compared by its string form.
     */
    List<String> extract(final Map<String, Object> tokenClaims) {
      final String issuer = Objects.toString(tokenClaims.get(ISSUER_CLAIM), null);
      final OidcGroupsExtractor issuerExtractor =
          issuer == null ? null : extractorsByIssuer.get(issuer);
      return (issuerExtractor != null ? issuerExtractor : tenantExtractor).extract(tokenClaims);
    }
  }
}
