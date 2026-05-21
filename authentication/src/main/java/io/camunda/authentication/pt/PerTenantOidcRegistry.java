/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/**
 * Builds a per-tenant {@link ClientRegistrationRepository} from a per-tenant {@link
 * SecurityConfiguration} and the tenant's {@code providers.assigned} list. Each entry in {@code
 * assigned} resolves to either:
 *
 * <ul>
 *   <li>The literal string {@value #DEFAULT_PROVIDER_REGISTRATION_ID} — the cluster-default {@code
 *       authentication.oidc.*} slot, or
 *   <li>Any other name — a named provider under {@code authentication.providers.oidc.<name>.*}.
 * </ul>
 *
 * <p>Each resolved provider becomes one {@link ClientRegistration} discovered via {@link
 * ClientRegistrations#fromIssuerLocation(String)} with the redirect URI rewritten by {@link
 * PhysicalTenantRedirectUriRewriter} so the callback lands on the tenant's URL space.
 *
 * <p><b>About the separate {@code assigned} parameter:</b> the external {@code
 * OidcProvidersConfiguration} (from {@code camunda-security-library-api}) does not expose an {@code
 * assigned} field, and adding one upstream is out of scope for this PoC. So the registry accepts
 * {@code assigned} alongside the {@link SecurityConfiguration}; the caller binds it separately (in
 * the PoC, from {@code camunda.physical-tenants.<id>.security.authentication.providers.assigned}).
 */
@NullMarked
public final class PerTenantOidcRegistry {

  /** Registration id under which the default {@code authentication.oidc.*} provider lives. */
  public static final String DEFAULT_PROVIDER_REGISTRATION_ID = "oidc";

  private PerTenantOidcRegistry() {
    // static-utility class — not instantiable
  }

  /**
   * Production entry point for prefixed-tenant chains — uses {@link
   * ClientRegistrations#fromIssuerLocation} for discovery and rewrites the redirect URI to the
   * tenant's URL space.
   */
  public static ClientRegistrationRepository buildFor(
      final String tenantId,
      final SecurityConfiguration tenantSecurity,
      final List<String> assigned) {
    return buildFor(
        tenantId,
        tenantSecurity,
        assigned,
        TenantSecuritySlice.AccessPath.PREFIXED,
        discoveringBuilder());
  }

  /**
   * Production entry point that accepts an explicit {@link TenantSecuritySlice.AccessPath}. The
   * access path controls whether the redirect URI is rewritten to the tenant prefix. For {@link
   * TenantSecuritySlice.AccessPath#UNPREFIXED_DEFAULT} the rewrite is skipped so the IdP redirects
   * back to {@code /login/oauth2/code/{registrationId}} on the root, which the unprefixed default
   * webapp chain handles.
   */
  public static ClientRegistrationRepository buildFor(
      final String tenantId,
      final SecurityConfiguration tenantSecurity,
      final List<String> assigned,
      final TenantSecuritySlice.AccessPath accessPath) {
    return buildFor(tenantId, tenantSecurity, assigned, accessPath, discoveringBuilder());
  }

  /**
   * Test seam: build registrations with a caller-supplied {@link ClientRegistrationBuilderFactory}
   * so unit tests can bypass live OIDC discovery. Defaults to {@link
   * TenantSecuritySlice.AccessPath#PREFIXED}.
   */
  static ClientRegistrationRepository buildFor(
      final String tenantId,
      final SecurityConfiguration tenantSecurity,
      final List<String> assigned,
      final ClientRegistrationBuilderFactory builderFactory) {
    return buildFor(
        tenantId,
        tenantSecurity,
        assigned,
        TenantSecuritySlice.AccessPath.PREFIXED,
        builderFactory);
  }

  /** Full-form test seam taking both the {@link TenantSecuritySlice.AccessPath} and a builder. */
  static ClientRegistrationRepository buildFor(
      final String tenantId,
      final SecurityConfiguration tenantSecurity,
      final List<String> assigned,
      final TenantSecuritySlice.AccessPath accessPath,
      final ClientRegistrationBuilderFactory builderFactory) {
    if (assigned == null || assigned.isEmpty()) {
      throw new IllegalStateException("Tenant '" + tenantId + "' has no providers.assigned");
    }
    final var auth = tenantSecurity.getAuthentication();
    final Map<String, OidcConfiguration> namedProviders =
        auth.getProviders() == null ? null : auth.getProviders().getOidc();

    final List<ClientRegistration> registrations = new ArrayList<>();
    for (final String registrationId : assigned) {
      final OidcConfiguration provider = resolveProvider(registrationId, auth, namedProviders);
      if (provider == null) {
        if (DEFAULT_PROVIDER_REGISTRATION_ID.equals(registrationId)) {
          throw new IllegalStateException(
              "Tenant '"
                  + tenantId
                  + "' assigns the default provider 'oidc' but authentication.oidc.* is not"
                  + " configured (no client-id or issuer-uri).");
        }
        throw new IllegalStateException(
            "Tenant '"
                + tenantId
                + "' assigns provider '"
                + registrationId
                + "' but it is missing from both authentication.oidc.* and"
                + " authentication.providers.oidc.<name>.*.");
      }
      registrations.add(
          buildRegistration(tenantId, registrationId, provider, accessPath, builderFactory));
    }
    return new InMemoryClientRegistrationRepository(registrations);
  }

  private static @Nullable OidcConfiguration resolveProvider(
      final String registrationId,
      final AuthenticationConfiguration auth,
      final @Nullable Map<String, OidcConfiguration> namedProviders) {
    if (DEFAULT_PROVIDER_REGISTRATION_ID.equals(registrationId)) {
      final var defaultProvider = auth.getOidc();
      // Treat unconfigured default (no client-id AND no issuer-uri) as absent so the caller
      // raises the precise error message.
      if (defaultProvider == null
          || (defaultProvider.getClientId() == null && defaultProvider.getIssuerUri() == null)) {
        return null;
      }
      return defaultProvider;
    }
    return namedProviders == null ? null : namedProviders.get(registrationId);
  }

  private static ClientRegistration buildRegistration(
      final String tenantId,
      final String registrationId,
      final OidcConfiguration provider,
      final TenantSecuritySlice.AccessPath accessPath,
      final ClientRegistrationBuilderFactory builderFactory) {
    final String redirectUriTemplate =
        provider.getRedirectUri() != null
            ? provider.getRedirectUri()
            : "{baseUrl}/login/oauth2/code/{registrationId}";
    // For UNPREFIXED_DEFAULT the IdP must redirect back to /login/oauth2/code/{registrationId}
    // on the root — that's the callback path the unprefixed default webapp chain serves. Skipping
    // the rewrite means the redirect URI resolves to the un-rewritten template.
    final String redirectUri =
        accessPath == TenantSecuritySlice.AccessPath.UNPREFIXED_DEFAULT
            ? redirectUriTemplate
            : PhysicalTenantRedirectUriRewriter.rewrite(redirectUriTemplate, tenantId);
    final var builder =
        builderFactory
            .builderFor(registrationId, provider)
            .clientId(provider.getClientId())
            .clientSecret(provider.getClientSecret())
            // Keycloak's userinfo endpoint requires "openid". fromIssuerLocation does NOT seed
            // default scopes, so we set them explicitly here.
            .scope("openid", "profile", "email")
            .redirectUri(redirectUri);
    if (provider.getClientName() != null) {
      builder.clientName(provider.getClientName());
    }
    return builder.build();
  }

  private static ClientRegistrationBuilderFactory discoveringBuilder() {
    return (registrationId, provider) ->
        ClientRegistrations.fromIssuerLocation(provider.getIssuerUri())
            .registrationId(registrationId);
  }

  /**
   * Factory hook that produces a {@link ClientRegistration.Builder} prepopulated with the parts
   * that require IdP discovery (auth/token/jwks endpoints). Production uses {@link
   * ClientRegistrations#fromIssuerLocation}; tests inject a stub that skips the network call.
   */
  @FunctionalInterface
  interface ClientRegistrationBuilderFactory {
    ClientRegistration.Builder builderFor(String registrationId, OidcConfiguration provider);
  }
}
