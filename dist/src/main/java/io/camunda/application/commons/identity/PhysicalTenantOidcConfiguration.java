/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import io.camunda.authentication.pt.PerTenantOidcRegistry;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * Per-tenant counterpart to OIDC client-registration and issuer wiring (gated on the {@code
 * pt-security} profile).
 *
 * <p>Iterates {@link PhysicalTenantResolver#getAll()} and produces two map beans keyed by tenant
 * id:
 *
 * <ul>
 *   <li>{@code Map<String, ClientRegistrationRepository> ptClientRegistrationRepositories} — one
 *       {@link ClientRegistrationRepository} per tenant, assembled from the tenant's {@code
 *       camunda.physical-tenants.<id>.security.*} overlay and {@code providers.assigned} list via
 *       {@link PerTenantOidcRegistry#buildFor}.
 *   <li>{@code Map<String, Set<String>> ptAllowedIssuersPerTenant} — the set of OIDC issuer URIs
 *       each tenant has assigned. Used both for the per-tenant API chain allowlist and for the
 *       cluster-shared issuer-aware {@code JwtDecoder} (which unions all sets).
 * </ul>
 *
 * <p>This module hosts the configuration because {@link PhysicalTenantResolver} lives in the {@code
 * configuration} module; binding directly here avoids a heavier dependency change in the
 * authentication module.
 */
@Configuration(proxyBeanMethods = false)
@Profile("pt-security")
@NullMarked
public class PhysicalTenantOidcConfiguration {

  private static final String PHYSICAL_TENANTS_PREFIX = "camunda.physical-tenants";

  @Bean
  public Map<String, ClientRegistrationRepository> ptClientRegistrationRepositories(
      final PhysicalTenantResolver physicalTenantResolver, final Environment environment) {
    final Map<String, ClientRegistrationRepository> repositories = new LinkedHashMap<>();
    for (final String tenantId : physicalTenantResolver.getAll().keySet()) {
      final SecurityConfiguration tenantSecurity = bindTenantSecurity(tenantId, environment);
      final List<String> assigned = bindAssigned(tenantId, environment);
      repositories.put(
          tenantId, PerTenantOidcRegistry.buildFor(tenantId, tenantSecurity, assigned));
    }
    return Map.copyOf(repositories);
  }

  @Bean
  public Map<String, Set<String>> ptAllowedIssuersPerTenant(
      final PhysicalTenantResolver physicalTenantResolver, final Environment environment) {
    final Map<String, Set<String>> perTenant = new LinkedHashMap<>();
    for (final String tenantId : physicalTenantResolver.getAll().keySet()) {
      final SecurityConfiguration tenantSecurity = bindTenantSecurity(tenantId, environment);
      final List<String> assigned = bindAssigned(tenantId, environment);
      if (assigned.isEmpty()) {
        continue;
      }
      final var auth = tenantSecurity.getAuthentication();
      final Map<String, OidcConfiguration> namedProviders =
          auth.getProviders() == null ? null : auth.getProviders().getOidc();
      final Set<String> issuers = new LinkedHashSet<>();
      for (final String id : assigned) {
        final OidcConfiguration provider = resolveProvider(id, auth.getOidc(), namedProviders);
        if (provider != null && provider.getIssuerUri() != null) {
          issuers.add(provider.getIssuerUri());
        }
      }
      perTenant.put(tenantId, Set.copyOf(issuers));
    }
    return Map.copyOf(perTenant);
  }

  private static @Nullable OidcConfiguration resolveProvider(
      final String registrationId,
      final @Nullable OidcConfiguration defaultProvider,
      final @Nullable Map<String, OidcConfiguration> namedProviders) {
    if (PerTenantOidcRegistry.DEFAULT_PROVIDER_REGISTRATION_ID.equals(registrationId)) {
      return defaultProvider;
    }
    return namedProviders == null ? null : namedProviders.get(registrationId);
  }

  private static SecurityConfiguration bindTenantSecurity(
      final String tenantId, final Environment environment) {
    final var tenantSecurity = new SecurityConfiguration();
    Binder.get(environment)
        .bind(
            PHYSICAL_TENANTS_PREFIX + "." + tenantId + ".security",
            Bindable.ofInstance(tenantSecurity));
    return tenantSecurity;
  }

  @SuppressWarnings("unchecked")
  private static List<String> bindAssigned(final String tenantId, final Environment environment) {
    final var bound =
        Binder.get(environment)
            .bind(
                PHYSICAL_TENANTS_PREFIX
                    + "."
                    + tenantId
                    + ".security.authentication.providers.assigned",
                Bindable.listOf(String.class));
    return bound.isBound() ? (List<String>) bound.get() : List.of();
  }
}
