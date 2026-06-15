/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfigurationException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName.Form;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.core.env.Environment;

/**
 * Fail-fast startup validation for the per-physical-tenant OIDC provider selection {@code
 * providers.assigned} (issue #54730, see ADR-0004). This is the configuration-layer counterpart of
 * the auth-merge narrowing in {@code PhysicalTenantAuthConfigurations.narrowToAssigned} (the {@code
 * authentication} module): validation rejects an invalid selection here so the merge only ever
 * <em>applies</em> an already-valid one — a non-default tenant can therefore never silently end up
 * with no providers and hence no usable API chain.
 *
 * <p>Enforcement is pure <em>key inspection</em> over the {@code physical-tenants.<id>.*} and
 * {@code camunda.security.*} keys (mirroring {@link PhysicalTenantOverridePolicyValidation}), plus
 * a {@link Binder} read of each {@code assigned} list. The rules:
 *
 * <ul>
 *   <li>The implicit {@code default} tenant carries the full provider set; declaring {@code
 *       providers.assigned} under it is rejected as a misconfiguration.
 *   <li>{@code assigned} applies only to the OIDC authentication method. If the cluster method is
 *       not {@code oidc}, a tenant that declares {@code assigned} is rejected (per-PT basic-auth is
 *       a separate concern — see #51949 — not a provider-selection one).
 *   <li>Under OIDC, every non-default tenant <em>must</em> declare a non-empty {@code assigned}.
 *   <li>Every id in {@code assigned} must be a known provider id: the reserved id {@value
 *       #DEFAULT_SLOT_ASSIGNED_ID} (only when the merged default slot {@code authentication.oidc.*}
 *       carries content), a cluster-level named provider {@code
 *       security.authentication.providers.oidc.<name>}, or one the tenant declares in its own
 *       overlay.
 *   <li>A named provider literally called {@value #DEFAULT_SLOT_ASSIGNED_ID} collides with the
 *       reserved default-slot id and is rejected as unsupported.
 * </ul>
 */
@NullMarked
final class PhysicalTenantAssignedProvidersValidation {

  /**
   * Reserved {@code assigned} id for the unnamed default slot ({@code
   * camunda.security.authentication.oidc.*}). Mirrored from {@code
   * PhysicalTenantAuthConfigurations.DEFAULT_SLOT_ASSIGNED_ID} in the {@code authentication} module
   * (the two modules do not share a type for it); keep the two in sync.
   */
  private static final String DEFAULT_SLOT_ASSIGNED_ID = "oidc";

  private static final String PHYSICAL_TENANTS_PREFIX = Camunda.PREFIX + ".physical-tenants";
  private static final ConfigurationPropertyName PHYSICAL_TENANTS_NAME =
      ConfigurationPropertyName.of(PHYSICAL_TENANTS_PREFIX);

  private static final String ROOT_AUTH = Camunda.PREFIX + ".security.authentication";
  private static final ConfigurationPropertyName ROOT_DEFAULT_SLOT =
      ConfigurationPropertyName.of(ROOT_AUTH + ".oidc");
  private static final ConfigurationPropertyName ROOT_NAMED_PROVIDERS =
      ConfigurationPropertyName.of(ROOT_AUTH + ".providers.oidc");
  private static final String METHOD_PROPERTY = ROOT_AUTH + ".method";

  private PhysicalTenantAssignedProvidersValidation() {}

  static void validate(final Environment environment) {
    final Binder binder = Binder.get(environment);
    final boolean oidcMethod = isOidcMethod(binder);

    for (final String tenantId : discoverTenantIds(environment)) {
      final List<String> assigned = bindAssigned(binder, tenantId);

      if (PhysicalTenantResolver.DEFAULT_PHYSICAL_TENANT_ID.equals(tenantId)) {
        if (assigned != null) {
          throw fail(
              "the implicit '%s' physical tenant always carries the full provider set; remove '%s'"
                  .formatted(tenantId, assignedPath(tenantId)));
        }
        continue;
      }

      if (!oidcMethod) {
        if (assigned != null) {
          throw fail(
              ("'%s' applies only to the OIDC authentication method, but '%s' is not 'oidc'; "
                      + "remove the selection or set the method to 'oidc'")
                  .formatted(assignedPath(tenantId), METHOD_PROPERTY));
        }
        continue;
      }

      if (assigned == null || assigned.isEmpty()) {
        throw fail(
            ("non-default physical tenant '%s' must declare a non-empty '%s' selecting which "
                    + "cluster OIDC providers apply to it")
                .formatted(tenantId, assignedPath(tenantId)));
      }

      final Set<String> namedIds = namedProviderIds(environment, tenantId);
      if (namedIds.contains(DEFAULT_SLOT_ASSIGNED_ID)) {
        throw fail(
            ("a named OIDC provider may not be called '%s' for physical tenant '%s' — that id is "
                    + "reserved for the unnamed default slot in '%s'")
                .formatted(DEFAULT_SLOT_ASSIGNED_ID, tenantId, assignedPath(tenantId)));
      }

      final Set<String> known = new LinkedHashSet<>(namedIds);
      if (hasDefaultSlotContent(environment, tenantId)) {
        known.add(DEFAULT_SLOT_ASSIGNED_ID);
      }

      final List<String> unknown =
          assigned.stream().filter(id -> !known.contains(id)).distinct().toList();
      if (!unknown.isEmpty()) {
        throw fail(
            ("physical tenant '%s' assigns unknown OIDC provider id(s) %s in '%s'; known ids are %s "
                    + "('%s' refers to the default slot '%s.oidc.*')")
                .formatted(
                    tenantId,
                    unknown,
                    assignedPath(tenantId),
                    known,
                    DEFAULT_SLOT_ASSIGNED_ID,
                    ROOT_AUTH));
      }
    }
  }

  private static boolean isOidcMethod(final Binder binder) {
    return binder
        .bind(METHOD_PROPERTY, Bindable.of(String.class))
        .map(method -> method.equalsIgnoreCase("oidc"))
        .orElse(false);
  }

  private static @Nullable List<String> bindAssigned(final Binder binder, final String tenantId) {
    return binder.bind(assignedPath(tenantId), Bindable.listOf(String.class)).orElse(null);
  }

  private static String assignedPath(final String tenantId) {
    return "%s.%s.security.authentication.providers.assigned"
        .formatted(PHYSICAL_TENANTS_PREFIX, tenantId);
  }

  /** Cluster-level (root) ∪ tenant-overlay named provider ids visible to the given tenant. */
  private static Set<String> namedProviderIds(
      final Environment environment, final String tenantId) {
    final Set<String> ids = new LinkedHashSet<>();
    ids.addAll(childSegments(environment, ROOT_NAMED_PROVIDERS));
    ids.addAll(
        childSegments(
            environment,
            ConfigurationPropertyName.of(
                "%s.%s.security.authentication.providers.oidc"
                    .formatted(PHYSICAL_TENANTS_PREFIX, tenantId))));
    return ids;
  }

  /** Whether the merged default slot (root ∪ tenant overlay) carries any content. */
  private static boolean hasDefaultSlotContent(
      final Environment environment, final String tenantId) {
    return hasDescendant(environment, ROOT_DEFAULT_SLOT)
        || hasDescendant(
            environment,
            ConfigurationPropertyName.of(
                "%s.%s.security.authentication.oidc".formatted(PHYSICAL_TENANTS_PREFIX, tenantId)));
  }

  /** The distinct id segments declared immediately under {@code prefix.<id>.*}. */
  private static Set<String> childSegments(
      final Environment environment, final ConfigurationPropertyName prefix) {
    final Set<String> segments = new LinkedHashSet<>();
    for (final ConfigurationPropertySource source : ConfigurationPropertySources.get(environment)) {
      if (source instanceof final IterableConfigurationPropertySource iter) {
        iter.stream()
            .filter(prefix::isAncestorOf)
            .filter(name -> name.getNumberOfElements() > prefix.getNumberOfElements())
            .forEach(
                name -> segments.add(name.getElement(prefix.getNumberOfElements(), Form.UNIFORM)));
      }
    }
    return segments;
  }

  /** Whether any key exists at or under {@code prefix}. */
  private static boolean hasDescendant(
      final Environment environment, final ConfigurationPropertyName prefix) {
    for (final ConfigurationPropertySource source : ConfigurationPropertySources.get(environment)) {
      if (source instanceof final IterableConfigurationPropertySource iter) {
        if (iter.stream().anyMatch(name -> prefix.equals(name) || prefix.isAncestorOf(name))) {
          return true;
        }
      }
    }
    return false;
  }

  private static Set<String> discoverTenantIds(final Environment environment) {
    final Set<String> tenants = new LinkedHashSet<>();
    for (final ConfigurationPropertySource source : ConfigurationPropertySources.get(environment)) {
      if (source instanceof final IterableConfigurationPropertySource iter) {
        iter.stream()
            .filter(PHYSICAL_TENANTS_NAME::isAncestorOf)
            .filter(
                name -> name.getNumberOfElements() > PHYSICAL_TENANTS_NAME.getNumberOfElements())
            .forEach(
                name ->
                    tenants.add(
                        name.getElement(
                            PHYSICAL_TENANTS_NAME.getNumberOfElements(), Form.UNIFORM)));
      }
    }
    return tenants;
  }

  private static UnifiedConfigurationException fail(final String detail) {
    return new UnifiedConfigurationException(
        "Invalid physical-tenant provider selection: " + detail);
  }
}
