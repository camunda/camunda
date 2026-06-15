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
import java.util.LinkedHashMap;
import java.util.Map;
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
 * <p>The merge is delegated to Spring's {@link Binder} rather than hand-written per field. The root
 * config is bound into a fresh instance, then the per-tenant overlay is bound <em>into the same
 * instance</em>. Because {@code Binder} only writes keys present in the overlay source, this yields
 * the desired semantics for free:
 *
 * <ul>
 *   <li>scalar fields the overlay sets override the root value; fields it omits keep the root
 *       value;
 *   <li>list fields ({@code audiences}, {@code scope}) the overlay sets <em>replace</em> the root
 *       list wholesale — a PT that declares its own audiences does not inherit the cluster's;
 *   <li>the single nested default slot ({@code authentication.oidc.*}) merges field-by-field.
 * </ul>
 *
 * <p>The one place {@code Binder} does not do the right thing on its own is the {@code providers}
 * <em>map</em>: binding the overlay key-merges the map (root-only and PT-only providers both
 * survive) but <em>replaces</em> the value object of any provider id present on both sides,
 * dropping the root fields the overlay did not restate. {@link #mergeSharedProviders} repairs that
 * by re-binding the overlay onto each pristine root provider object.
 *
 * <p>{@code method} is deliberately not taken from the overlay: it is cluster-wide and re-asserted
 * from the root after binding. Overriding it per tenant is rejected at startup by the configuration
 * layer (#54731).
 *
 * <p>No special handling is needed for an empty default slot (neither {@code client-id} nor {@code
 * issuer-uri}): the config getters never return {@code null} (the api setters coerce {@code null}
 * to an empty instance), and CSL's {@code flatten} ignores a slot or provider with no usable
 * config, so a content-less slot is simply not turned into a chain.
 */
public final class PhysicalTenantAuthConfigurations {

  private static final String ROOT_PREFIX = "camunda.security.authentication";
  private static final String PT_PREFIX_TEMPLATE =
      "camunda.physical-tenants.%s.security.authentication";

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

    // Bind the root config into a fresh, per-call instance (safe to mutate below).
    final AuthenticationConfiguration config = bindOrDefault(binder, ROOT_PREFIX);
    final AuthenticationMethod rootMethod = config.getMethod();
    // Snapshot the pristine root provider objects before the overlay bind can replace them.
    final Map<String, OidcConfiguration> rootProviders = snapshotProviders(config);

    // Bind the overlay into the SAME instance: scalars override, lists replace, the default slot
    // field-merges, and the providers map key-merges (but replaces shared values — repaired next).
    binder.bind(ptPrefix, Bindable.ofInstance(config));

    // method is cluster-wide, not per-tenant.
    config.setMethod(rootMethod != null ? rootMethod : AuthenticationMethod.BASIC);
    mergeSharedProviders(binder, ptPrefix, rootProviders, config);
    return config;
  }

  /**
   * Repairs provider ids present on both root and overlay. The overlay bind replaced each such
   * value with a fresh object carrying only the overlay's keys; here we re-bind the overlay onto
   * the <em>pristine</em> root object so the fields the overlay omitted survive (override +
   * list-replace + inherit, all by {@link Binder}). Root-only and PT-only providers need no repair.
   */
  private static void mergeSharedProviders(
      final Binder binder,
      final String ptPrefix,
      final Map<String, OidcConfiguration> rootProviders,
      final AuthenticationConfiguration config) {
    final Map<String, OidcConfiguration> merged = namedProviders(config);
    if (merged == null) {
      return;
    }
    rootProviders.forEach(
        (id, rootProvider) -> {
          if (merged.containsKey(id)) {
            binder.bind(ptPrefix + ".providers.oidc." + id, Bindable.ofInstance(rootProvider));
            merged.put(id, rootProvider);
          }
        });
  }

  private static Map<String, OidcConfiguration> snapshotProviders(
      final AuthenticationConfiguration config) {
    final Map<String, OidcConfiguration> named = namedProviders(config);
    return named == null ? Map.of() : new LinkedHashMap<>(named);
  }

  private static Map<String, OidcConfiguration> namedProviders(
      final AuthenticationConfiguration config) {
    final OidcProvidersConfiguration providers = config.getProviders();
    return providers == null ? null : providers.getOidc();
  }

  private static AuthenticationConfiguration bindOrDefault(
      final Binder binder, final String prefix) {
    final BindResult<AuthenticationConfiguration> result =
        binder.bind(prefix, Bindable.of(AuthenticationConfiguration.class));
    return result.orElseGet(AuthenticationConfiguration::new);
  }
}
