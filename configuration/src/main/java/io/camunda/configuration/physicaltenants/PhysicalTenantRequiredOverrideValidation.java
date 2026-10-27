/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.spring.utils.InvalidPhysicalTenantIdException;
import io.camunda.spring.utils.PhysicalTenantConfigUtil;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
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
 * is the tenant's effective {@code security.authorizations.enabled} (per-tenant override, else
 * root, else the default), which determines whether the tenant is exempt. A non-default tenant with
 * authorization enabled that declares no key at or under {@code security.initialization} fails
 * resolution.
 */
@NullMarked
final class PhysicalTenantRequiredOverrideValidation {

  /**
   * The per-tenant {@code initialization} block, expressed relative to {@code
   * camunda.physical-tenants.<id>.}. A tenant satisfies the requirement by declaring any key at or
   * under this name.
   */
  private static final ConfigurationPropertyName REQUIRED_INITIALIZATION =
      ConfigurationPropertyName.of("security.initialization");

  /**
   * Relative name of the authorization toggle the runtime enforces. The root and per-tenant keys
   * are both built from it, so the two cannot drift apart.
   */
  private static final String AUTHORIZATIONS_ENABLED = "security.authorizations.enabled";

  private PhysicalTenantRequiredOverrideValidation() {}

  static void validate(final Environment environment) {
    final Set<String> declaredTenants = new LinkedHashSet<>();
    final Set<String> tenantsWithInitialization = new LinkedHashSet<>();
    try {
      PhysicalTenantConfigUtil.forEachTenantProperty(
          environment,
          (tenantId, relative) -> {
            declaredTenants.add(tenantId);
            if (!relative.isEmpty() && declaresInitialization(relative)) {
              tenantsWithInitialization.add(tenantId);
            }
          });
    } catch (final InvalidPhysicalTenantIdException e) {
      throw new UnifiedConfigurationException(e);
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
              + "' tenant keeps the top-level 'camunda.security.initialization'). To exempt a "
              + "tenant instead, disable authorization for it with "
              + "'camunda.physical-tenants.<id>."
              + AUTHORIZATIONS_ENABLED
              + "'. Physical tenants missing a required initialization block: "
              + missing);
    }
  }

  private static boolean declaresInitialization(final ConfigurationPropertyName relative) {
    return REQUIRED_INITIALIZATION.equals(relative)
        || REQUIRED_INITIALIZATION.isAncestorOf(relative);
  }

  /**
   * Resolves the effective {@code authorizations.enabled} for a tenant: the per-tenant override if
   * declared, otherwise the root value, otherwise the default ({@code true}). This is the only
   * value read by this validation; the rest is pure key inspection.
   *
   * <p>Unbound and blank values resolve to {@code true} so that an unreadable toggle requires an
   * initialization block rather than silently exempting the tenant.
   */
  private static boolean authorizationEnabledFor(final Binder binder, final String tenantId) {
    final var perTenant =
        binder.bind(
            Camunda.PREFIX + ".physical-tenants." + tenantId + "." + AUTHORIZATIONS_ENABLED,
            Bindable.of(Boolean.class));
    if (perTenant.isBound()) {
      return perTenant.get();
    }
    return binder
        .bind(Camunda.PREFIX + "." + AUTHORIZATIONS_ENABLED, Bindable.of(Boolean.class))
        .orElse(true);
  }
}
