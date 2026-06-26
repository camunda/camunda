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
 * <p>Uses a snapshot-then-rebind strategy to avoid Spring's {@link Binder} replacing entire map
 * entries on partial field overrides. When a tenant overrides only some fields of a named OIDC
 * provider (e.g. {@code client-id}, {@code client-secret}, {@code audiences}), {@link Binder} would
 * replace the whole map entry with a fresh {@link OidcConfiguration} built only from the
 * PT-specific keys, silently losing root fields like {@code issuer-uri}, {@code username-claim},
 * and {@code redirect-uri}. This class avoids that by snapshotting the root POJOs first, running
 * the PT overlay, then re-binding the PT overlay keys onto the pristine root POJO for any shared
 * provider id so that Binder applies the PT delta on top of a fully-populated root object.
 */
@NullMarked
public final class PhysicalTenantOidcProviderConfigurations {

  private static final String ROOT_PREFIX = Camunda.PREFIX + ".security.authentication";
  private static final String PT_PREFIX_TEMPLATE =
      Camunda.PREFIX + ".physical-tenants.%s.security.authentication";

  private PhysicalTenantOidcProviderConfigurations() {}

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

    mergeSharedProviders(auth, rootOidc, binder, ptPrefix);

    return auth;
  }

  /**
   * Re-binds the PT overlay onto the pristine root POJOs for any provider id that existed in both
   * root and PT configs. Without this step a tenant that overrides only some fields of a shared
   * provider would lose all other root-level fields because Spring's {@link Binder} replaces the
   * entire map entry on the first matching key.
   */
  private static void mergeSharedProviders(
      final AuthenticationConfiguration auth,
      final Map<String, OidcConfiguration> rootOidc,
      final Binder binder,
      final String ptPrefix) {
    final Map<String, OidcConfiguration> resolvedOidc = auth.getProviders().getOidc();
    rootOidc.forEach(
        (providerId, rootProvider) -> {
          if (resolvedOidc.containsKey(providerId)) {
            // Apply the PT delta onto the pristine root POJO, then swap it back in for the
            // Binder-created entry that holds only the PT-specified fields. Both steps are
            // required: the bind layers the override, the put restores the merged result.
            binder.bind(
                ptPrefix + ".providers.oidc." + providerId, Bindable.ofInstance(rootProvider));
            resolvedOidc.put(providerId, rootProvider);
          }
        });
  }
}
