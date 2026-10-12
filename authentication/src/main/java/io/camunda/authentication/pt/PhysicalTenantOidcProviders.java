/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toMap;

import io.camunda.security.api.context.MembershipResolutionContextPropagator;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.core.authz.LazyTokenClaimsConverter;
import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.security.spring.oidc.ScopedClientRegistrationFactory;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.core.env.Environment;

/**
 * Derives per-registration OIDC claim wiring for the named providers of every physical tenant.
 *
 * <p>Interactive OIDC login selects claim mapping by {@code
 * OAuth2AuthenticationToken#getAuthorizedClientRegistrationId()}. A physical-tenant scoped provider
 * (e.g. an Auth0 registration) can configure different identity claims than the root provider, so
 * the host cannot reuse the single root {@link LazyTokenClaimsConverter}. This factory flattens the
 * named providers of all physical tenants (see {@link
 * PhysicalTenantAuthConfigurations#forAllPhysicalTenants(Environment)}) into maps keyed by
 * registration id that the provider-aware converter looks up at conversion time.
 */
public final class PhysicalTenantOidcProviders {

  private PhysicalTenantOidcProviders() {}

  /**
   * The named OIDC providers of the default tenant, keyed by registration id. They serve the
   * default tenant and requests outside any tenant; the unnamed default slot ({@code
   * authentication.oidc.*}) is not a named provider and is therefore absent, so the root
   * registration keeps the default converter.
   */
  public static Map<String, OidcConfiguration> defaultTenantNamedProviders(
      final Environment environment) {
    final var providers =
        PhysicalTenantAuthConfigurations.forPhysicalTenant(DEFAULT_PHYSICAL_TENANT_ID, environment)
            .getProviders();
    return providers == null || providers.getOidc() == null
        ? Map.of()
        : Collections.unmodifiableMap(new LinkedHashMap<>(providers.getOidc()));
  }

  /**
   * Resolves, per non-default physical tenant, every OIDC provider the tenant sees keyed by
   * registration id — the default slot under {@code oidc} plus each named provider, as CSL flattens
   * them — each carrying the tenant's own merged settings.
   *
   * <p>The default tenant is omitted: its merged configuration already is the cluster-wide one, so
   * the root provider map serves it. Nothing is merged across tenants, so two tenants (or a tenant
   * and the root) sharing a registration id keep distinct settings.
   */
  public static Map<String, Map<String, OidcConfiguration>> providersByPhysicalTenant(
      final Environment environment) {
    final var flattener = new ScopedClientRegistrationFactory();
    final Map<String, Map<String, OidcConfiguration>> providersByTenant = new LinkedHashMap<>();
    PhysicalTenantAuthConfigurations.forAllPhysicalTenants(environment)
        .forEach(
            (tenantId, authentication) -> {
              if (!DEFAULT_PHYSICAL_TENANT_ID.equals(tenantId)) {
                providersByTenant.put(
                    tenantId, Collections.unmodifiableMap(flattener.flatten(authentication)));
              }
            });
    return Collections.unmodifiableMap(providersByTenant);
  }

  /**
   * Builds a {@link LazyTokenClaimsConverter} per registration from its configured username and
   * client-id claims.
   */
  public static Map<String, LazyTokenClaimsConverter> tokenClaimsConverters(
      final Map<String, OidcConfiguration> providers,
      final MembershipPort membershipPort,
      final MembershipResolutionContextPropagator membershipResolutionContextPropagator) {
    return providers.entrySet().stream()
        .collect(
            collectingAndThen(
                toMap(
                    Map.Entry::getKey,
                    entry -> {
                      final var config = entry.getValue();
                      return new LazyTokenClaimsConverter(
                          config.getUsernameClaim(),
                          config.getClientIdClaim(),
                          config.isPreferUsernameClaim(),
                          membershipPort,
                          membershipResolutionContextPropagator);
                    }),
                Collections::unmodifiableMap));
  }

  /**
   * Collects the configured identity claims (username and client-id) per registration. Spring can
   * represent a standard claim such as {@code iss} as a URI, so the provider-aware converter
   * normalizes these claim values to strings before conversion.
   */
  public static Map<String, List<String>> identityClaimsByRegistrationId(
      final Map<String, OidcConfiguration> providers) {
    return providers.entrySet().stream()
        .collect(
            collectingAndThen(
                toMap(Map.Entry::getKey, entry -> identityClaims(entry.getValue())),
                Collections::unmodifiableMap));
  }

  /**
   * Returns the configured identity claim names (username and client-id, omitting the unset ones)
   * for a single provider. These are the claims the provider-aware converter normalizes to strings
   * before conversion.
   */
  public static List<String> identityClaims(final OidcConfiguration config) {
    return Stream.of(config.getUsernameClaim(), config.getClientIdClaim())
        .filter(Objects::nonNull)
        .toList();
  }
}
