/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/**
 * Builds a per-tenant {@link ClientRegistrationRepository} from a root {@link
 * SecurityConfiguration} (providing the canonical provider declarations under {@code
 * camunda.security.authentication.oidc.*} and {@code
 * camunda.security.authentication.providers.oidc.<id>.*}), a per-tenant overlay {@link
 * SecurityConfiguration} (whose provider entries override individual fields on the root provider
 * for that tenant only), and the tenant's {@code providers.assigned} list. Each entry in {@code
 * assigned} resolves to either:
 *
 * <ul>
 *   <li>The literal string {@value #DEFAULT_PROVIDER_REGISTRATION_ID} — the cluster-default {@code
 *       authentication.oidc.*} slot, or
 *   <li>Any other name — a named provider under {@code authentication.providers.oidc.<name>.*}.
 * </ul>
 *
 * <p>Resolution is root-first with PT-side field overrides: for each assigned id, the root config
 * is consulted; any non-null/non-empty fields on the PT-side overlay for the same id override the
 * root copy. The root config bean is never mutated — the merge produces a new {@link
 * OidcConfiguration} per build call.
 *
 * <p>Each resolved provider becomes one {@link ClientRegistration} discovered via {@link
 * ClientRegistrations#fromIssuerLocation(String)} with the redirect URI rewritten by {@link
 * PhysicalTenantRedirectUriRewriter} so the callback lands on the tenant's URL space.
 *
 * <p><b>About the separate {@code assigned} parameter:</b> the external {@code
 * OidcProvidersConfiguration} (from {@code camunda-security-library-api}) does not expose an {@code
 * assigned} field, and adding one upstream is out of scope for this PoC. So the builder accepts
 * {@code assigned} alongside the {@link SecurityConfiguration}s; the caller binds it separately (in
 * the PoC, from {@code camunda.physical-tenants.<id>.security.authentication.providers.assigned}).
 *
 * <p>The class name mirrors Spring Security's {@link ClientRegistrations} static utility: this is a
 * builder, not a stateful registry.
 *
 * <p>All built registrations have their redirect URI rewritten to the tenant's prefixed URL space
 * via {@link PhysicalTenantRedirectUriRewriter}; the unprefixed default access path is served by
 * CSL's standard chains, which use the standard {@link ClientRegistrationRepository} bean and do
 * not flow through this builder.
 */
@NullMarked
public final class PerTenantClientRegistrations {

  /** Registration id under which the default {@code authentication.oidc.*} provider lives. */
  public static final String DEFAULT_PROVIDER_REGISTRATION_ID = "oidc";

  /**
   * Key under which each registration's expected audiences are stored inside {@link
   * org.springframework.security.oauth2.client.registration.ClientRegistration.ProviderDetails#getConfigurationMetadata()}.
   * The value is a {@code List<String>}. Read by the audience-aware decoder factory and the
   * issuer/audience aware router so per-registration audience overrides flow correctly even when
   * multiple PTs register the same registration id with different audiences.
   */
  public static final String AUDIENCES_METADATA_KEY = "audiences";

  private PerTenantClientRegistrations() {
    // static-utility class — not instantiable
  }

  /**
   * Production entry point for prefixed-tenant chains — uses {@link
   * ClientRegistrations#fromIssuerLocation} for discovery and rewrites the redirect URI to the
   * tenant's URL space.
   */
  public static ClientRegistrationRepository buildFor(
      final String tenantId,
      final SecurityConfiguration rootSecurity,
      final SecurityConfiguration tenantSecurity,
      final List<String> assigned) {
    return buildFor(tenantId, rootSecurity, tenantSecurity, assigned, discoveringBuilder());
  }

  /**
   * Test seam: build registrations with a caller-supplied {@link ClientRegistrationBuilderFactory}
   * so unit tests can bypass live OIDC discovery.
   */
  static ClientRegistrationRepository buildFor(
      final String tenantId,
      final SecurityConfiguration rootSecurity,
      final SecurityConfiguration tenantSecurity,
      final List<String> assigned,
      final ClientRegistrationBuilderFactory builderFactory) {
    if (assigned == null || assigned.isEmpty()) {
      throw new IllegalStateException("Tenant '" + tenantId + "' has no providers.assigned");
    }
    final var rootAuth = rootSecurity.getAuthentication();
    final Map<String, OidcConfiguration> rootNamedProviders =
        rootAuth.getProviders() == null ? null : rootAuth.getProviders().getOidc();
    final var tenantAuth = tenantSecurity.getAuthentication();
    final Map<String, OidcConfiguration> tenantNamedProviders =
        tenantAuth.getProviders() == null ? null : tenantAuth.getProviders().getOidc();

    final List<ClientRegistration> registrations = new ArrayList<>();
    for (final String registrationId : assigned) {
      final OidcConfiguration rootProvider =
          lookupProvider(registrationId, rootAuth.getOidc(), rootNamedProviders);
      final OidcConfiguration tenantProvider =
          lookupProvider(registrationId, tenantAuth.getOidc(), tenantNamedProviders);
      final OidcConfiguration merged = merge(rootProvider, tenantProvider);
      if (merged == null) {
        if (DEFAULT_PROVIDER_REGISTRATION_ID.equals(registrationId)) {
          throw new IllegalStateException(
              "Tenant '"
                  + tenantId
                  + "' assigns the default provider 'oidc' but authentication.oidc.* is not"
                  + " configured in root or PT (no client-id or issuer-uri).");
        }
        throw new IllegalStateException(
            "Tenant '"
                + tenantId
                + "' assigns provider '"
                + registrationId
                + "' but it is missing from both root and PT authentication.providers.oidc.<name>.*"
                + " and the default authentication.oidc.* slot.");
      }
      registrations.add(buildRegistration(tenantId, registrationId, merged, builderFactory));
    }
    return new InMemoryClientRegistrationRepository(registrations);
  }

  /**
   * Returns the merged provider config for {@code registrationId} given the root and per-tenant
   * {@link SecurityConfiguration}s, or {@code null} when neither declares the provider. Same
   * resolution rule as {@link #buildFor}: root layer first, PT-side non-null/non-empty fields
   * override per-field. Callers that only need to inspect the resolved provider (issuer URI,
   * audiences) without building a {@link ClientRegistration} use this.
   */
  public static @Nullable OidcConfiguration resolveMergedProvider(
      final String registrationId,
      final SecurityConfiguration rootSecurity,
      final SecurityConfiguration tenantSecurity) {
    final var rootAuth = rootSecurity.getAuthentication();
    final Map<String, OidcConfiguration> rootNamedProviders =
        rootAuth.getProviders() == null ? null : rootAuth.getProviders().getOidc();
    final var tenantAuth = tenantSecurity.getAuthentication();
    final Map<String, OidcConfiguration> tenantNamedProviders =
        tenantAuth.getProviders() == null ? null : tenantAuth.getProviders().getOidc();
    final OidcConfiguration rootProvider =
        lookupProvider(registrationId, rootAuth.getOidc(), rootNamedProviders);
    final OidcConfiguration tenantProvider =
        lookupProvider(registrationId, tenantAuth.getOidc(), tenantNamedProviders);
    return merge(rootProvider, tenantProvider);
  }

  /**
   * Looks up a provider config for the given registration id from a single layer (either root or a
   * PT overlay). Returns the default slot for {@value #DEFAULT_PROVIDER_REGISTRATION_ID}, otherwise
   * the named-providers entry. Returns {@code null} when the layer doesn't declare anything for
   * that id, or when the default slot is unconfigured (no client-id AND no issuer-uri).
   */
  private static @Nullable OidcConfiguration lookupProvider(
      final String registrationId,
      final @Nullable OidcConfiguration defaultProvider,
      final @Nullable Map<String, OidcConfiguration> namedProviders) {
    if (DEFAULT_PROVIDER_REGISTRATION_ID.equals(registrationId)) {
      if (defaultProvider == null
          || (defaultProvider.getClientId() == null && defaultProvider.getIssuerUri() == null)) {
        return null;
      }
      return defaultProvider;
    }
    return namedProviders == null ? null : namedProviders.get(registrationId);
  }

  /**
   * Merges the PT-side overlay onto a clone of the root config. PT-side non-null / non-empty fields
   * win; absent overrides inherit from root. Mutates only the freshly-cloned copy so the root bean
   * stays untouched.
   *
   * <p>Returns {@code null} when neither side declares the provider.
   */
  private static @Nullable OidcConfiguration merge(
      final @Nullable OidcConfiguration root, final @Nullable OidcConfiguration overlay) {
    if (root == null && overlay == null) {
      return null;
    }
    final OidcConfiguration source = root != null ? root : overlay;
    if (source == null) {
      // Unreachable: the guard above ensures at least one of root/overlay is non-null. NullAway
      // can't see the relationship through the && check, so this assertion narrows the type.
      return null;
    }
    final OidcConfiguration base = clone(source);
    if (overlay != null && root != null) {
      // Per-field override: PT-side non-null/non-empty beats root.
      if (overlay.getClientId() != null) {
        base.setClientId(overlay.getClientId());
      }
      if (overlay.getClientSecret() != null) {
        base.setClientSecret(overlay.getClientSecret());
      }
      if (overlay.getIssuerUri() != null) {
        base.setIssuerUri(overlay.getIssuerUri());
      }
      if (overlay.getAudiences() != null && !overlay.getAudiences().isEmpty()) {
        base.setAudiences(overlay.getAudiences());
      }
      if (overlay.getClientAuthenticationMethod() != null
          && !overlay
              .getClientAuthenticationMethod()
              .equals(OidcConfiguration.CLIENT_AUTHENTICATION_METHOD_CLIENT_SECRET_BASIC)) {
        // Skip when overlay carries the default-default value, which Spring Boot binds even for
        // a "no override" overlay (the default-default is assigned during construction, before
        // binding).
        base.setClientAuthenticationMethod(overlay.getClientAuthenticationMethod());
      }
      if (overlay.getScope() != null
          && !overlay.getScope().equals(OidcConfiguration.DEFAULT_SCOPE)) {
        base.setScope(overlay.getScope());
      }
      if (overlay.getRedirectUri() != null) {
        base.setRedirectUri(overlay.getRedirectUri());
      }
      if (overlay.getAdditionalJwkSetUris() != null
          && !overlay.getAdditionalJwkSetUris().isEmpty()) {
        base.setAdditionalJwkSetUris(overlay.getAdditionalJwkSetUris());
      }
    }
    return base;
  }

  /**
   * Clones the fields used by {@link #buildRegistration} so we never mutate the root bean.
   * Restricted to the fields the registration builder actually reads (clientId, clientSecret,
   * issuerUri, audiences, scope, redirectUri, additionalJwkSetUris, clientAuthenticationMethod,
   * clientName) — anything else inherits via reference and is fine because the merge never touches
   * it.
   */
  private static OidcConfiguration clone(final OidcConfiguration source) {
    final var copy = new OidcConfiguration();
    copy.setClientId(source.getClientId());
    copy.setClientSecret(source.getClientSecret());
    copy.setIssuerUri(source.getIssuerUri());
    copy.setAudiences(source.getAudiences());
    copy.setScope(source.getScope());
    copy.setRedirectUri(source.getRedirectUri());
    copy.setAdditionalJwkSetUris(source.getAdditionalJwkSetUris());
    copy.setClientAuthenticationMethod(source.getClientAuthenticationMethod());
    copy.setClientName(source.getClientName());
    return copy;
  }

  private static ClientRegistration buildRegistration(
      final String tenantId,
      final String registrationId,
      final OidcConfiguration provider,
      final ClientRegistrationBuilderFactory builderFactory) {
    final String redirectUriTemplate =
        provider.getRedirectUri() != null
            ? provider.getRedirectUri()
            : "{baseUrl}/login/oauth2/code/{registrationId}";
    final String redirectUri =
        PhysicalTenantRedirectUriRewriter.rewrite(redirectUriTemplate, tenantId);
    // Stash audiences on the registration via providerConfigurationMetadata so the audience-
    // aware decoder + validator factory can route + validate by audience without consulting any
    // static providers map keyed by registration id (which would always reflect ROOT audiences
    // and silently discard PT-side overrides for shared registration ids — see
    // MetadataAwareTokenValidatorFactory).
    final Map<String, Object> metadata = new HashMap<>();
    if (provider.getAudiences() != null && !provider.getAudiences().isEmpty()) {
      metadata.put(AUDIENCES_METADATA_KEY, List.copyOf(provider.getAudiences()));
    }
    final var builder =
        builderFactory
            .builderFor(registrationId, provider)
            .clientId(provider.getClientId())
            .clientSecret(provider.getClientSecret())
            // Keycloak's userinfo endpoint requires "openid". fromIssuerLocation does NOT seed
            // default scopes, so we set them explicitly here.
            .scope("openid", "profile", "email")
            .redirectUri(redirectUri)
            .providerConfigurationMetadata(metadata);
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
