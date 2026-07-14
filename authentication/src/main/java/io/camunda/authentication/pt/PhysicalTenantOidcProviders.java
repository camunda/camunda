/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static java.util.stream.Collectors.toMap;

import io.camunda.security.api.context.MembershipResolutionContextPropagator;
import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.core.authz.LazyTokenClaimsConverter;
import io.camunda.security.core.port.out.MembershipPort;
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
   * Flattens the named OIDC providers of every physical tenant into a map keyed by registration id.
   * The unnamed default slot ({@code authentication.oidc.*}) is not a named provider and is
   * therefore absent, so the root registration keeps the default converter.
   */
  public static Map<String, OidcConfiguration> providersByRegistrationId(
      final Environment environment) {
    final Map<String, OidcConfiguration> providersByRegistrationId = new LinkedHashMap<>();
    PhysicalTenantAuthConfigurations.forAllPhysicalTenants(environment).values().stream()
        .map(AuthenticationConfiguration::getProviders)
        .filter(providers -> providers != null && providers.getOidc() != null)
        .forEach(providers -> providersByRegistrationId.putAll(providers.getOidc()));
    return providersByRegistrationId;
  }

  /**
   * Builds a {@link LazyTokenClaimsConverter} per scoped registration from its configured username
   * and client-id claims.
   */
  public static Map<String, LazyTokenClaimsConverter> tokenClaimsConvertersByRegistrationId(
      final Environment environment,
      final MembershipPort membershipPort,
      final MembershipResolutionContextPropagator membershipResolutionContextPropagator) {
    return providersByRegistrationId(environment).entrySet().stream()
        .collect(
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
                }));
  }

  /**
   * Collects the configured identity claims (username and client-id) per scoped registration.
   * Spring can represent a standard claim such as {@code iss} as a URI, so the provider-aware
   * converter normalizes these claim values to strings before conversion.
   */
  public static Map<String, List<String>> identityClaimsByRegistrationId(
      final Environment environment) {
    return providersByRegistrationId(environment).entrySet().stream()
        .collect(
            toMap(
                Map.Entry::getKey,
                entry ->
                    Stream.of(
                            entry.getValue().getUsernameClaim(),
                            entry.getValue().getClientIdClaim())
                        .filter(Objects::nonNull)
                        .toList()));
  }
}
