/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.api.model.config.oidc.OidcProvidersConfiguration;
import io.camunda.spring.utils.PhysicalTenantIdDiscovery;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * Derives a merged {@link AuthenticationConfiguration} for a specific physical tenant from the root
 * cluster configuration and the per-tenant overlay.
 *
 * <p>The merge first builds the union of <em>all</em> cluster providers — ROOT providers ∪ the PT's
 * own OVERLAY providers, each root provider merged with the PT overlay — and then, for any tenant
 * that declares one (the {@code default} tenant included), <em>narrows</em> that union to its
 * {@code providers.assigned} selection (issue #54730, see {@link #narrowToAssigned}). A tenant that
 * declares no {@code assigned} keeps the full union.
 *
 * <p>The {@code default} tenant is treated uniformly here: its resolved configuration drives both
 * the {@code /physical-tenants/default} alias and — via {@code PhysicalTenantSecurityConfiguration}
 * — the unprefixed {@code /v2} cluster chain, so the two surfaces stay identical.
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
 * <p>The {@code providers} <em>map</em> is reconciled explicitly by {@link #mergeRootProviders}
 * rather than left to the overlay bind: every cluster (root) provider is kept, with the overlay's
 * fields for that id applied on top, alongside any provider defined only in the overlay.
 *
 * <p>{@code method} is deliberately not taken from the overlay: it is cluster-wide and re-asserted
 * from the root after binding. Overriding it per tenant is rejected at startup by the configuration
 * layer (#54731).
 *
 * <p>No special handling is needed for an empty default slot (neither {@code client-id} nor {@code
 * issuer-uri}): the config getters never return {@code null} (the api setters coerce {@code null}
 * to an empty instance), and CSL's {@code flatten} ignores a slot or provider with no usable
 * config, so a content-less slot is simply not turned into a chain.
 *
 * <p>Note: {@code io.camunda.configuration.physicaltenants
 * .PhysicalTenantAuthenticationProviderConfigurations} in the configuration module solves the same
 * provider merge for the unified-configuration tree via the generic spec-driven overlay engine
 * (rooted at {@code .providers}, empty-map case additionally covered by an empty-tolerant bind
 * handler). The two must stay behavior-equivalent; behavioral fixes here likely apply there too.
 * Harmonizing them into one mechanism is tracked in <a
 * href="https://github.com/camunda/camunda/issues/56964">#56964</a>.
 */
public final class PhysicalTenantAuthConfigurations {

  private static final String ROOT_PREFIX = "camunda.security.authentication";
  private static final String PT_PREFIX_TEMPLATE =
      "camunda.physical-tenants.%s.security.authentication";

  /**
   * Reserved {@code providers.assigned} id for the unnamed default slot — CSL's {@link
   * OidcConfiguration#DEFAULT_REGISTRATION_ID} ({@code "oidc"}), the registration id of the default
   * slot ({@code camunda.security.authentication.oidc.*}). Sourcing it from the CSL constant keeps
   * it in lockstep with the configuration-layer validation, which references the same constant — so
   * the two layers cannot drift. A named provider literally called this id collides and is rejected
   * at startup by validation.
   */
  private static final String DEFAULT_SLOT_ASSIGNED_ID = OidcConfiguration.DEFAULT_REGISTRATION_ID;

  private PhysicalTenantAuthConfigurations() {}

  /**
   * Returns a map from physical-tenant id to its resolved {@link AuthenticationConfiguration},
   * always including the {@code default} tenant even when no PTs are explicitly configured.
   *
   * <p>The map contains one entry per explicitly configured physical tenant (discovered via {@link
   * #discoverExplicitTenantIds}) plus a {@code default} entry. Each value is computed via {@link
   * #forPhysicalTenant(String, Environment)}. The returned map is unmodifiable and
   * insertion-ordered with {@code default} always present; explicit tenant order is discovery
   * order.
   *
   * @param environment Spring {@link Environment} used for both discovery and config binding
   * @return unmodifiable, insertion-ordered map of tenant id → merged auth config
   */
  public static Map<String, AuthenticationConfiguration> forAllPhysicalTenants(
      final Environment environment) {
    final Set<String> tenantIds = discoverExplicitTenantIds(environment);
    final Map<String, AuthenticationConfiguration> result = new LinkedHashMap<>();
    result.put(
        DEFAULT_PHYSICAL_TENANT_ID, forPhysicalTenant(DEFAULT_PHYSICAL_TENANT_ID, environment));
    for (final String tenantId : tenantIds) {
      if (!tenantId.equals(DEFAULT_PHYSICAL_TENANT_ID)) {
        result.put(tenantId, forPhysicalTenant(tenantId, environment));
      }
    }
    return Collections.unmodifiableMap(result);
  }

  /**
   * Walks the {@link Environment} and returns the set of explicitly configured physical-tenant ids
   * (those with at least one key under {@code camunda.physical-tenants.<id>.*}).
   *
   * <p>Tenant ids must be lowercase alphanumeric ({@code [a-z0-9]+}) — no dashes. This matches the
   * constraint enforced by {@code PhysicalTenantResolver} to keep yaml and env-var forms addressing
   * the same tenant.
   *
   * <p>Package-private so {@code PhysicalTenantScopeProvider} can delegate to it; must not be
   * tightened to {@code private}.
   */
  static Set<String> discoverExplicitTenantIds(final Environment environment) {
    return PhysicalTenantIdDiscovery.discover(environment);
  }

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

    // Bind the overlay into the SAME instance: scalars override, lists replace, and the default
    // slot field-merges. The providers map is reconciled separately by mergeRootProviders.
    binder.bind(ptPrefix, Bindable.ofInstance(config));

    // method is cluster-wide, not per-tenant.
    config.setMethod(rootMethod != null ? rootMethod : AuthenticationMethod.BASIC);
    mergeRootProviders(binder, ptPrefix, rootProviders, config);
    narrowToAssigned(binder, ptPrefix, config);
    return config;
  }

  /**
   * Narrows the merged union to the providers a physical tenant has explicitly SELECTED via {@code
   * ...providers.assigned} (issue #54730) — applied uniformly to every tenant, the {@code default}
   * tenant included. Each list entry is a provider id drawn from the union {@code {oidc} ∪
   * providers.oidc.<name>}:
   *
   * <ul>
   *   <li>the reserved id {@value #DEFAULT_SLOT_ASSIGNED_ID} keeps the unnamed default slot ({@code
   *       authentication.oidc.*}); when it is absent from {@code assigned} the slot is reset to a
   *       content-less instance, which CSL's {@code flatten} ignores (so the inherited cluster
   *       provider is not turned into a chain for this tenant);
   *   <li>every other entry keeps the like-named {@code providers.oidc.<name>}; named providers not
   *       listed are removed.
   * </ul>
   *
   * <p>A tenant with no {@code assigned} list bound keeps the full union — selection is optional
   * <em>here</em>. Enforcing that a non-default tenant MUST declare a valid {@code assigned} list
   * (non-empty, every id known), that the {@code default} tenant <em>may</em> declare one, and that
   * a declared list is valid, is a fail-fast <em>configuration-layer</em> concern (#54730), kept
   * out of this merge so the merge only ever <em>applies</em> an already-valid selection.
   */
  private static void narrowToAssigned(
      final Binder binder, final String ptPrefix, final AuthenticationConfiguration config) {
    final List<String> assigned =
        binder.bind(ptPrefix + ".providers.assigned", Bindable.listOf(String.class)).orElse(null);
    if (assigned == null || assigned.isEmpty()) {
      // No selection — or an empty list — keeps the full union here. The configuration layer
      // rejects
      // an empty `assigned` at startup with a clear message; returning early avoids stripping every
      // provider first (this can run before that validation, via the cluster-unification BPP).
      return;
    }
    // Defensive: tolerate null/blank list elements (some yaml list shapes bind them) so this never
    // throws an opaque NPE. Blank ids match no provider, so dropping them is safe; the
    // configuration
    // layer still rejects them at startup with a clear message.
    final Set<String> assignedIds = new LinkedHashSet<>();
    for (final String id : assigned) {
      if (id != null && !id.isBlank()) {
        assignedIds.add(id);
      }
    }

    // Default slot: drop unless explicitly assigned by its reserved id. setOidc never stores null
    // (it coerces to an empty instance), so a fresh OidcConfiguration is the canonical "no slot".
    if (!assignedIds.contains(DEFAULT_SLOT_ASSIGNED_ID)) {
      config.setOidc(new OidcConfiguration());
    }

    // Named providers: keep only the assigned ids.
    final Map<String, OidcConfiguration> named = namedProviders(config);
    if (named != null) {
      named.keySet().removeIf(id -> !assignedIds.contains(id));
    }
  }

  /**
   * Sets the tenant's OIDC providers to the union of the cluster (root) providers and the
   * per-tenant overlay: every root provider is present with the overlay's fields for that id
   * applied on top, and providers defined only in the overlay are kept. {@code rootProviders} is
   * the snapshot of the root providers taken before the overlay was bound onto {@code config}.
   */
  private static void mergeRootProviders(
      final Binder binder,
      final String ptPrefix,
      final Map<String, OidcConfiguration> rootProviders,
      final AuthenticationConfiguration config) {
    final Map<String, OidcConfiguration> postOverlayProviders = namedProviders(config);
    final Map<String, OidcConfiguration> union = new LinkedHashMap<>();
    // Seed with the map as the overlay bind left it (PT-only providers, plus any partially-bound
    // shared ids), then overwrite every root id with its pristine root object + overlay delta.
    if (postOverlayProviders != null) {
      union.putAll(postOverlayProviders);
    }
    rootProviders.forEach(
        (id, rootProvider) -> {
          binder.bind(ptPrefix + ".providers.oidc." + id, Bindable.ofInstance(rootProvider));
          union.put(id, rootProvider);
        });
    // getProviders() is null only when neither root nor overlay declared any providers, in which
    // case the union is empty and there is nothing to write.
    if (config.getProviders() != null) {
      config.getProviders().setOidc(union);
    }
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
