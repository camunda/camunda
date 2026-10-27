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
import io.camunda.security.api.model.config.oidc.OidcPrincipalClaims;
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
 * Resolves the OIDC providers each physical tenant sees, and derives per-registration claim wiring
 * from them.
 *
 * <p>Interactive OIDC login selects settings by the physical tenant of the request and the {@code
 * OAuth2AuthenticationToken#getAuthorizedClientRegistrationId()}. A registration id can be shared
 * by the root and several tenants (a shared cluster provider is merged into every tenant) with
 * different issuer, algorithm, claim or JWKS settings, so nothing is merged across tenants: each
 * non-default tenant gets its own map (see {@link #providersByPhysicalTenant}), and the default
 * tenant, which serves the root, is read separately (see {@link #defaultTenantNamedProviders}).
 * Tenants come from {@link PhysicalTenantAuthConfigurations#forAllPhysicalTenants(Environment)}.
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
    final Map<String, Map<String, OidcConfiguration>> providersByTenant = new LinkedHashMap<>();
    byPhysicalTenant(environment)
        .forEach((tenantId, tenant) -> providersByTenant.put(tenantId, tenant.providers()));
    return Collections.unmodifiableMap(providersByTenant);
  }

  /**
   * Like {@link #providersByPhysicalTenant}, and also the tenant's default slot ({@code
   * authentication.oidc.*}), whose claims apply to each provider that does not set its own. Reads
   * each tenant's configuration once.
   */
  public static Map<String, TenantOidc> byPhysicalTenant(final Environment environment) {
    final var flattener = new ScopedClientRegistrationFactory();
    final Map<String, TenantOidc> byTenant = new LinkedHashMap<>();
    PhysicalTenantAuthConfigurations.forAllPhysicalTenants(environment)
        .forEach(
            (tenantId, authentication) -> {
              if (!DEFAULT_PHYSICAL_TENANT_ID.equals(tenantId)) {
                byTenant.put(
                    tenantId,
                    new TenantOidc(
                        Collections.unmodifiableMap(flattener.flatten(authentication)),
                        authentication.getOidc()));
              }
            });
    return Collections.unmodifiableMap(byTenant);
  }

  /**
   * Builds a {@link LazyTokenClaimsConverter} per registration from its username, client-id and
   * prefer-username claims. A provider uses the claims of {@code defaultSlot} for each claim that
   * it does not set.
   */
  public static Map<String, LazyTokenClaimsConverter> tokenClaimsConverters(
      final Map<String, OidcConfiguration> providers,
      final OidcConfiguration defaultSlot,
      final MembershipPort membershipPort,
      final MembershipResolutionContextPropagator membershipResolutionContextPropagator) {
    return providers.entrySet().stream()
        .collect(
            collectingAndThen(
                toMap(
                    Map.Entry::getKey,
                    entry -> {
                      final var claims = OidcPrincipalClaims.resolve(entry.getValue(), defaultSlot);
                      return new LazyTokenClaimsConverter(
                          claims.usernameClaim(),
                          claims.clientIdClaim(),
                          claims.preferUsernameClaim(),
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
      final Map<String, OidcConfiguration> providers, final OidcConfiguration defaultSlot) {
    return providers.entrySet().stream()
        .collect(
            collectingAndThen(
                toMap(
                    Map.Entry::getKey,
                    entry ->
                        identityClaims(OidcPrincipalClaims.resolve(entry.getValue(), defaultSlot))),
                Collections::unmodifiableMap));
  }

  /**
   * Returns the configured identity claim names (username and client-id, omitting the unset ones)
   * for a single provider. These are the claims the provider-aware converter normalizes to strings
   * before conversion.
   */
  public static List<String> identityClaims(final OidcConfiguration config) {
    return identityClaims(config.getUsernameClaim(), config.getClientIdClaim());
  }

  private static List<String> identityClaims(final OidcPrincipalClaims claims) {
    return identityClaims(claims.usernameClaim(), claims.clientIdClaim());
  }

  private static List<String> identityClaims(
      final String usernameClaim, final String clientIdClaim) {
    return Stream.of(usernameClaim, clientIdClaim).filter(Objects::nonNull).toList();
  }

  /** The OIDC providers of one physical tenant, keyed by registration id, and its default slot. */
  public record TenantOidc(
      Map<String, OidcConfiguration> providers, OidcConfiguration defaultSlot) {}
}
