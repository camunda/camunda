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
import java.util.LinkedHashSet;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * Derives a merged {@link AuthenticationConfiguration} for a specific physical tenant from the root
 * cluster configuration and the per-tenant overlay.
 *
 * <p>The returned configuration contains <em>all</em> cluster providers — the union of ROOT
 * providers and the PT's own OVERLAY providers — each root provider merged with the PT overlay.
 * Per-PT provider SELECTION ({@code assigned}) is intentionally deferred to issue #54730.
 *
 * <p>Merge rules:
 *
 * <ol>
 *   <li>The root configuration at {@code camunda.security.authentication.*} provides the base
 *       provider declarations.
 *   <li>The per-tenant overlay at {@code camunda.physical-tenants.<id>.security.authentication.*}
 *       overrides individual fields on a per-provider basis.
 *   <li>The union of root provider ids and PT overlay provider ids is used: every id from both
 *       sides is included. Root fields are used as defaults; non-null/non-empty PT-side fields
 *       override them per field.
 *   <li>The default slot ({@code authentication.oidc.*}) is included only when it carries a {@code
 *       client-id} or {@code issuer-uri} — a Spring-bound-but-empty slot is treated as absent.
 * </ol>
 *
 * <p>The {@code method} field of the returned configuration is always taken from the root — method
 * selection is cluster-wide, not per-tenant.
 */
public final class PhysicalTenantAuthConfigurations {

  private static final String ROOT_PREFIX = "camunda.security";
  private static final String PT_PREFIX_TEMPLATE = "camunda.physical-tenants.%s.security";

  private PhysicalTenantAuthConfigurations() {}

  /**
   * Produces a merged {@link AuthenticationConfiguration} for the given physical tenant id. The
   * returned configuration contains all cluster providers (root providers ∪ PT-overlay providers),
   * each root provider merged with the PT-side overlay. The {@code method} is inherited from the
   * root.
   *
   * @param tenantId the physical tenant id (e.g. {@code "tenanta"})
   * @param environment the Spring {@link Environment} for binding root and overlay config
   * @return the merged {@link AuthenticationConfiguration} for this tenant
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

    return buildConfig(rootAuth, ptAuth);
  }

  private static AuthenticationConfiguration buildConfig(
      final AuthenticationConfiguration rootAuth, final AuthenticationConfiguration ptAuth) {

    // Default slot: include only when at least one side carries a client-id or issuer-uri.
    final OidcConfiguration mergedDefault =
        merge(validDefaultSlot(rootAuth.getOidc()), validDefaultSlot(ptAuth.getOidc()));

    // Named providers: union of root and PT overlay ids.
    final Map<String, OidcConfiguration> rootNamed = namedProviders(rootAuth);
    final Map<String, OidcConfiguration> ptNamed = namedProviders(ptAuth);

    final LinkedHashSet<String> ids = new LinkedHashSet<>();
    if (rootNamed != null) {
      ids.addAll(rootNamed.keySet());
    }
    if (ptNamed != null) {
      ids.addAll(ptNamed.keySet());
    }

    final Map<String, OidcConfiguration> mergedNamed = new LinkedHashMap<>();
    for (final String id : ids) {
      final OidcConfiguration merged =
          merge(
              rootNamed == null ? null : rootNamed.get(id),
              ptNamed == null ? null : ptNamed.get(id));
      if (merged != null) {
        mergedNamed.put(id, merged);
      }
    }

    final AuthenticationConfiguration result = new AuthenticationConfiguration();
    // Inherit method from root — method is cluster-wide, not per-tenant.
    result.setMethod(
        rootAuth.getMethod() != null ? rootAuth.getMethod() : AuthenticationMethod.BASIC);
    result.setOidc(mergedDefault);
    if (!mergedNamed.isEmpty()) {
      final OidcProvidersConfiguration providers = new OidcProvidersConfiguration();
      providers.setOidc(mergedNamed);
      result.setProviders(providers);
    } else {
      // No named providers — clear the auto-created providers object so callers can
      // reliably check getProviders() == null to detect "no named providers configured".
      result.setProviders(null);
    }
    return result;
  }

  /**
   * Returns {@code slot} only if it carries a {@code client-id} or {@code issuer-uri}; otherwise
   * returns {@code null}. This guards against Spring-bound-but-empty default slots being treated as
   * a configured provider.
   */
  private static @Nullable OidcConfiguration validDefaultSlot(
      final @Nullable OidcConfiguration slot) {
    if (slot == null) {
      return null;
    }
    if (slot.getClientId() == null && slot.getIssuerUri() == null) {
      return null;
    }
    return slot;
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
}
