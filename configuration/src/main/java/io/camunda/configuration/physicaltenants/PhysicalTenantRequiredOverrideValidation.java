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
import io.camunda.configuration.api.physicaltenants.PhysicalTenantIds;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName.Form;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.core.env.Environment;

/**
 * Required-override policy for physical-tenant configuration: every explicitly-configured physical
 * tenant must declare its own {@code initialization} block under {@code
 * camunda.physical-tenants.<id>.security.initialization.*}, unless authorization is disabled for
 * that tenant.
 *
 * <p>The {@code initialization} block seeds tenant-scoped identity (users, roles, authorizations,
 * tenants, …). Unlike the cluster-wide settings guarded by {@link
 * PhysicalTenantOverridePolicyValidation}, it must <em>not</em> be inherited from the root:
 * silently reusing the top-level seed across tenants would create the same admin user /
 * authorizations in every tenant, defeating tenant isolation. Each tenant therefore has to provide
 * its own.
 *
 * <p>The {@value PhysicalTenantIds#DEFAULT_PHYSICAL_TENANT_ID} tenant is exempt: it represents the
 * root configuration and keeps the top-level {@code camunda.security.initialization}, whether it is
 * synthesized from the root or declared explicitly.
 *
 * <p>Enforcement is <em>key inspection</em> over the declared {@code physical-tenants.<id>.*} keys
 * — the same walk {@link PhysicalTenantResolver#discover(Environment)} does. The one value it binds
 * is the tenant's effective {@code security.authorization.enabled} (per-tenant override, else root,
 * else the default), which determines whether the tenant is exempt. A tenant with authorization
 * enabled that declares no key at or under {@code security.initialization} fails resolution.
 */
@NullMarked
final class PhysicalTenantRequiredOverrideValidation {

  private static final ConfigurationPropertyName PHYSICAL_TENANTS_NAME =
      ConfigurationPropertyName.of(Camunda.PREFIX + ".physical-tenants");

  /**
   * The per-tenant {@code initialization} block, expressed relative to {@code
   * camunda.physical-tenants.<id>.}. A tenant satisfies the requirement by declaring any key at or
   * under this name.
   */
  private static final ConfigurationPropertyName REQUIRED_INITIALIZATION =
      ConfigurationPropertyName.of("security.initialization");

  private PhysicalTenantRequiredOverrideValidation() {}

  static void validate(final Environment environment) {
    final Set<String> declaredTenants = new LinkedHashSet<>();
    final Set<String> tenantsWithInitialization = new LinkedHashSet<>();
    final int tenantIdIndex = PHYSICAL_TENANTS_NAME.getNumberOfElements();
    for (final ConfigurationPropertySource source : ConfigurationPropertySources.get(environment)) {
      if (source instanceof final IterableConfigurationPropertySource iter) {
        iter.stream()
            .filter(PHYSICAL_TENANTS_NAME::isAncestorOf)
            .forEach(
                name -> {
                  if (name.getNumberOfElements() <= tenantIdIndex) {
                    // only the physical-tenants prefix, no tenant id segment
                    return;
                  }
                  final String tenantId = name.getElement(tenantIdIndex, Form.UNIFORM);
                  if (tenantId == null || tenantId.isEmpty()) {
                    // mirror PhysicalTenantResolver.discover — never report a blank tenant id
                    return;
                  }
                  declaredTenants.add(tenantId);
                  if (name.getNumberOfElements() > tenantIdIndex + 1
                      && declaresInitialization(name.subName(tenantIdIndex + 1))) {
                    tenantsWithInitialization.add(tenantId);
                  }
                });
      }
    }

    final Binder binder = Binder.get(environment);
    final List<String> missing =
        declaredTenants.stream()
            .filter(id -> !PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID.equals(id))
            .filter(id -> !tenantsWithInitialization.contains(id))
            // the initialization block only takes effect when authorization is enabled, so a
            // tenant running with authorization disabled is not required to declare one.
            .filter(id -> authorizationEnabledFor(binder, id))
            .toList();
    if (!missing.isEmpty()) {
      throw new UnifiedConfigurationException(
          "Each explicitly-configured physical tenant must declare its own initialization block under "
              + "'camunda.physical-tenants.<id>.security.initialization.*' when authorization is enabled "
              + "for that tenant; it may not be "
              + "inherited from the root (the '"
              + PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID
              + "' tenant keeps the top-level 'camunda.security.initialization'). Physical tenants "
              + "missing a required initialization block: "
              + missing);
    }
  }

  private static boolean declaresInitialization(final ConfigurationPropertyName relative) {
    return REQUIRED_INITIALIZATION.equals(relative)
        || REQUIRED_INITIALIZATION.isAncestorOf(relative);
  }

  /**
   * Resolves the effective {@code authorization.enabled} for a tenant: the per-tenant override if
   * declared, otherwise the root value, otherwise the default ({@code true}). This is the only
   * value read by this validation; the rest is pure key inspection.
   */
  private static boolean authorizationEnabledFor(final Binder binder, final String tenantId) {
    final var perTenant =
        binder.bind(
            Camunda.PREFIX + ".physical-tenants." + tenantId + ".security.authorization.enabled",
            Bindable.of(Boolean.class));
    if (perTenant.isBound()) {
      return perTenant.get();
    }
    return binder
        .bind(Camunda.PREFIX + ".security.authorization.enabled", Bindable.of(Boolean.class))
        .orElse(true);
  }
}
