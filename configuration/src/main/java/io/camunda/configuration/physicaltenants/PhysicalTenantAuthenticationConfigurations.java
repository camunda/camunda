/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Camunda;
import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * Resolves a per-tenant {@link AuthenticationConfiguration} by overlaying {@code
 * camunda.physical-tenants.<id>.security.authentication.*} on top of the root {@code
 * camunda.security.authentication.*} config.
 *
 * <p>The root config is bound first, its providers are snapshotted, then the PT overlay is bound on
 * top and the {@code providers} map is reconciled by {@link #mergeRootProviders}: every root
 * provider is kept with the overlay's fields for that id applied on top, alongside any provider
 * defined only in the overlay. This is needed because binding the overlay onto the root instance
 * would otherwise replace a shared provider's value object with one holding only the PT-specific
 * keys (losing root fields like {@code issuer-uri}), and an empty overlay {@code providers} map
 * would reset the whole map (dropping the inherited providers).
 */
@NullMarked
public final class PhysicalTenantAuthenticationConfigurations {

  private static final String ROOT_PREFIX = Camunda.PREFIX + ".security.authentication";
  private static final String PT_PREFIX_TEMPLATE =
      Camunda.PREFIX + ".physical-tenants.%s.security.authentication";

  private PhysicalTenantAuthenticationConfigurations() {}

  public static AuthenticationConfiguration forPhysicalTenant(
      final String tenantId, final Environment environment) {
    final Binder binder = Binder.get(environment);

    final AuthenticationConfiguration auth =
        binder
            .bind(ROOT_PREFIX, Bindable.of(AuthenticationConfiguration.class))
            .orElseGet(AuthenticationConfiguration::new);

    // snapshot pristine root provider POJOs before the PT overlay mutates them
    final Map<String, OidcConfiguration> rootOidc =
        new LinkedHashMap<>(auth.getProviders().getOidc());

    final String ptPrefix = PT_PREFIX_TEMPLATE.formatted(tenantId);
    binder.bind(ptPrefix, Bindable.ofInstance(auth));

    mergeRootProviders(auth, rootOidc, binder, ptPrefix);

    return auth;
  }

  /**
   * Sets the tenant's OIDC providers to the union of the root providers and the PT overlay: every
   * root provider is present with the overlay's fields for that id applied on top, and providers
   * defined only in the overlay are kept. {@code rootOidc} is the snapshot of the root providers
   * taken before the overlay was bound onto {@code auth}.
   */
  private static void mergeRootProviders(
      final AuthenticationConfiguration auth,
      final Map<String, OidcConfiguration> rootOidc,
      final Binder binder,
      final String ptPrefix) {
    final Map<String, OidcConfiguration> postOverlayProviders = auth.getProviders().getOidc();
    final Map<String, OidcConfiguration> union = new LinkedHashMap<>();
    // Seed with the map as the overlay bind left it (PT-only providers, plus any partially-bound
    // shared ids), then overwrite every root id with its pristine root object + overlay delta.
    if (postOverlayProviders != null) {
      union.putAll(postOverlayProviders);
    }
    rootOidc.forEach(
        (providerId, rootProvider) -> {
          binder.bind(
              ptPrefix + ".providers.oidc." + providerId, Bindable.ofInstance(rootProvider));
          union.put(providerId, rootProvider);
        });
    auth.getProviders().setOidc(union);
  }
}
