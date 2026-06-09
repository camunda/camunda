/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.api.model.config.oidc.OidcProvidersConfiguration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.Environment;

/**
 * Derives a narrowed {@link AuthenticationConfiguration} for a specific physical tenant from the
 * root cluster configuration and the per-tenant overlay.
 *
 * <p>Merge rules (matching {@code PerTenantClientRegistrations} from the PoC branch):
 *
 * <ol>
 *   <li>The root configuration at {@code camunda.security.authentication.*} provides the base
 *       provider declarations.
 *   <li>The per-tenant overlay at {@code camunda.physical-tenants.<id>.security.authentication.*}
 *       overrides individual fields on a per-provider basis.
 *   <li>The {@code assigned} list at {@code
 *       camunda.physical-tenants.<id>.security.authentication.providers.assigned} selects which
 *       providers are active for this tenant. The special id {@code "oidc"} refers to the flat
 *       {@code authentication.oidc.*} slot; any other id refers to a named entry under {@code
 *       authentication.providers.oidc.<id>.*}.
 *   <li>For each assigned id, root fields are used as defaults; non-null/non-empty PT-side fields
 *       override them per field. The resulting merged {@link OidcConfiguration} is emitted into the
 *       returned {@link AuthenticationConfiguration}.
 *   <li>If a PT's {@code assigned} list references an id missing from both root and PT config, an
 *       {@link IllegalStateException} is thrown.
 * </ol>
 *
 * <p>The {@code method} field of the returned configuration is always taken from the root — method
 * selection is cluster-wide, not per-tenant.
 */
public final class PhysicalTenantAuthConfigurations {

  /** The registration id that refers to the flat {@code authentication.oidc.*} provider slot. */
  public static final String DEFAULT_PROVIDER_ID = "oidc";

  private static final String ROOT_PREFIX = "camunda.security";
  private static final String PT_PREFIX_TEMPLATE = "camunda.physical-tenants.%s.security";
  private static final String ASSIGNED_SUFFIX = ".authentication.providers.assigned";

  private PhysicalTenantAuthConfigurations() {}

  /**
   * Produces a narrowed {@link AuthenticationConfiguration} for the given physical tenant id. The
   * returned configuration contains only the providers the tenant has assigned, each merged with
   * the PT-side overlay. The {@code method} is inherited from the root.
   *
   * @param tenantId the physical tenant id (e.g. {@code "tenanta"})
   * @param environment the Spring {@link Environment} for binding root and overlay config
   * @return the narrowed {@link AuthenticationConfiguration} for this tenant
   * @throws IllegalStateException if the tenant's {@code assigned} list references a provider that
   *     is not declared in either root or PT configuration
   */
  public static AuthenticationConfiguration forPhysicalTenant(
      final String tenantId, final Environment environment) {
    final Binder binder = Binder.get(environment);
    final String ptPrefix = PT_PREFIX_TEMPLATE.formatted(tenantId);

    // Bind root authentication config
    final AuthenticationConfiguration rootAuth =
        bindOrDefault(binder, ROOT_PREFIX + ".authentication", AuthenticationConfiguration.class);

    // Bind PT authentication overlay
    final AuthenticationConfiguration ptAuth =
        bindOrDefault(binder, ptPrefix + ".authentication", AuthenticationConfiguration.class);

    // Bind the assigned list (separate from OidcProvidersConfiguration — not a CSL field)
    final List<String> assigned = bindAssigned(binder, ptPrefix);

    return buildConfig(tenantId, rootAuth, ptAuth, assigned);
  }

  private static AuthenticationConfiguration buildConfig(
      final String tenantId,
      final AuthenticationConfiguration rootAuth,
      final AuthenticationConfiguration ptAuth,
      final List<String> assigned) {
    if (assigned == null || assigned.isEmpty()) {
      throw new IllegalStateException(
          "Physical tenant '"
              + tenantId
              + "' has no providers.assigned configured under"
              + " camunda.physical-tenants."
              + tenantId
              + ".security.authentication.providers.assigned");
    }

    final Map<String, OidcConfiguration> rootNamed = namedProviders(rootAuth);
    final Map<String, OidcConfiguration> ptNamed = namedProviders(ptAuth);

    OidcConfiguration mergedDefaultSlot = null;
    final Map<String, OidcConfiguration> mergedNamed = new LinkedHashMap<>();

    for (final String id : assigned) {
      final OidcConfiguration rootProvider = lookupProvider(id, rootAuth.getOidc(), rootNamed);
      final OidcConfiguration ptProvider = lookupProvider(id, ptAuth.getOidc(), ptNamed);
      final OidcConfiguration merged = merge(rootProvider, ptProvider);
      if (merged == null) {
        if (DEFAULT_PROVIDER_ID.equals(id)) {
          throw new IllegalStateException(
              "Physical tenant '"
                  + tenantId
                  + "' assigns the default provider '"
                  + DEFAULT_PROVIDER_ID
                  + "' but authentication.oidc.* is not configured in"
                  + " root or PT overlay (no client-id or issuer-uri).");
        }
        throw new IllegalStateException(
            "Physical tenant '"
                + tenantId
                + "' assigns provider '"
                + id
                + "' but it is not declared in root authentication.providers.oidc."
                + id
                + ".* or PT overlay.");
      }
      if (DEFAULT_PROVIDER_ID.equals(id)) {
        mergedDefaultSlot = merged;
      } else {
        mergedNamed.put(id, merged);
      }
    }

    final AuthenticationConfiguration result = new AuthenticationConfiguration();
    // Inherit method from root — method is cluster-wide, not per-tenant.
    result.setMethod(
        rootAuth.getMethod() != null ? rootAuth.getMethod() : AuthenticationMethod.BASIC);
    // Only set the default oidc slot when "oidc" is in the assigned list and has a valid config.
    result.setOidc(mergedDefaultSlot);
    if (!mergedNamed.isEmpty()) {
      final OidcProvidersConfiguration providers = new OidcProvidersConfiguration();
      providers.setOidc(mergedNamed);
      result.setProviders(providers);
    } else {
      // No named providers assigned — clear the auto-created providers object so callers can
      // reliably check getProviders() == null to detect "no named providers configured".
      result.setProviders(null);
    }
    return result;
  }

  /**
   * Looks up the provider config for {@code id} from a single layer. Returns the default slot for
   * {@value #DEFAULT_PROVIDER_ID}, otherwise the named-providers map entry. Returns {@code null}
   * when the layer has nothing for that id, or when the default slot has neither client-id nor
   * issuer-uri (i.e. is effectively unconfigured).
   */
  private static @Nullable OidcConfiguration lookupProvider(
      final String id,
      final @Nullable OidcConfiguration defaultSlot,
      final @Nullable Map<String, OidcConfiguration> named) {
    if (DEFAULT_PROVIDER_ID.equals(id)) {
      if (defaultSlot == null
          || (defaultSlot.getClientId() == null && defaultSlot.getIssuerUri() == null)) {
        return null;
      }
      return defaultSlot;
    }
    return named == null ? null : named.get(id);
  }

  /**
   * Merges the PT-side overlay onto a clone of the root config. PT-side non-null/non-empty fields
   * win; absent overrides inherit from root. Returns {@code null} when neither side declares the
   * provider.
   */
  private static @Nullable OidcConfiguration merge(
      final @Nullable OidcConfiguration root, final @Nullable OidcConfiguration overlay) {
    if (root == null && overlay == null) {
      return null;
    }
    final OidcConfiguration source = root != null ? root : overlay;
    // Both null is already guarded above; this non-null assertion narrows for the compiler.
    assert source != null;
    final OidcConfiguration base = cloneConfig(source);
    if (overlay != null && root != null) {
      applyOverlay(base, overlay);
    }
    return base;
  }

  /** Applies non-null/non-empty fields from {@code overlay} onto {@code base}, in-place. */
  private static void applyOverlay(final OidcConfiguration base, final OidcConfiguration overlay) {
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
    if (overlay.getRedirectUri() != null) {
      base.setRedirectUri(overlay.getRedirectUri());
    }
    if (overlay.getJwkSetUri() != null) {
      base.setJwkSetUri(overlay.getJwkSetUri());
    }
    if (overlay.getAdditionalJwkSetUris() != null && !overlay.getAdditionalJwkSetUris().isEmpty()) {
      base.setAdditionalJwkSetUris(new ArrayList<>(overlay.getAdditionalJwkSetUris()));
    }
    if (overlay.getClientAuthenticationMethod() != null
        && !overlay
            .getClientAuthenticationMethod()
            .equals(OidcConfiguration.CLIENT_AUTHENTICATION_METHOD_CLIENT_SECRET_BASIC)) {
      // Skip when overlay carries the default value — Spring Boot binds the default even when
      // the property is absent, so treating the default as "no override" avoids stomping root.
      base.setClientAuthenticationMethod(overlay.getClientAuthenticationMethod());
    }
    if (overlay.getScope() != null && !overlay.getScope().equals(OidcConfiguration.DEFAULT_SCOPE)) {
      base.setScope(new ArrayList<>(overlay.getScope()));
    }
    if (overlay.getClientName() != null) {
      base.setClientName(overlay.getClientName());
    }
    if (overlay.getUsernameClaim() != null) {
      base.setUsernameClaim(overlay.getUsernameClaim());
    }
    if (overlay.getOrganizationId() != null) {
      base.setOrganizationId(overlay.getOrganizationId());
    }
  }

  /** Shallow-clones the fields relevant to chain construction so the root bean is never mutated. */
  private static OidcConfiguration cloneConfig(final OidcConfiguration source) {
    final OidcConfiguration copy = new OidcConfiguration();
    copy.setClientId(source.getClientId());
    copy.setClientSecret(source.getClientSecret());
    copy.setIssuerUri(source.getIssuerUri());
    copy.setAudiences(source.getAudiences());
    copy.setRedirectUri(source.getRedirectUri());
    copy.setJwkSetUri(source.getJwkSetUri());
    copy.setAdditionalJwkSetUris(source.getAdditionalJwkSetUris());
    copy.setClientAuthenticationMethod(source.getClientAuthenticationMethod());
    copy.setScope(source.getScope());
    copy.setClientName(source.getClientName());
    copy.setUsernameClaim(source.getUsernameClaim());
    copy.setOrganizationId(source.getOrganizationId());
    return copy;
  }

  private static @Nullable Map<String, OidcConfiguration> namedProviders(
      final AuthenticationConfiguration auth) {
    return auth.getProviders() == null ? null : auth.getProviders().getOidc();
  }

  private static AuthenticationConfiguration bindOrDefault(
      final Binder binder, final String prefix, final Class<AuthenticationConfiguration> type) {
    final BindResult<AuthenticationConfiguration> result = binder.bind(prefix, Bindable.of(type));
    return result.orElseGet(AuthenticationConfiguration::new);
  }

  private static @Nullable List<String> bindAssigned(final Binder binder, final String ptPrefix) {
    final Bindable<List<String>> bindable =
        Bindable.of(ResolvableType.forClassWithGenerics(List.class, String.class));
    final BindResult<List<String>> result = binder.bind(ptPrefix + ASSIGNED_SUFFIX, bindable);
    return result.orElse(null);
  }
}
