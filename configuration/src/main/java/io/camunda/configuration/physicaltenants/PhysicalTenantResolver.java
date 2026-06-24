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
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName.Form;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.core.env.Environment;

/**
 * Eagerly resolves a {@link Camunda} configuration per discovered physical tenant.
 *
 * <p>Each tenant {@code Camunda} is produced by:
 *
 * <ol>
 *   <li>creating a fresh {@link Camunda} instance,
 *   <li>binding {@code camunda.*} into it (so non-overridden values match the root resolution),
 *   <li>binding {@code camunda.physical-tenants.<tenantId>.*} into it (so tenant-specific keys
 *       override the seeded values).
 * </ol>
 *
 * <p>Legacy property fallback for non-overridden properties is preserved: it lives in the existing
 * {@code Camunda} getters via {@code UnifiedConfigurationHelper}, which reads from the global
 * environment at getter-call time. Because step 2 produces the same field state as the root binding
 * for any property the tenant did not override, those getters resolve legacy properties exactly as
 * they would on the root {@code Camunda}.
 *
 * <p>If no {@value
 * io.camunda.configuration.api.physicaltenants.PhysicalTenantIds#DEFAULT_PHYSICAL_TENANT_ID} tenant
 * is declared under {@code camunda.physical-tenants.*}, an entry under that key is synthesized from
 * the root configuration so that consumers can always address the root configuration as a tenant.
 * An explicit {@code default} declaration is honored as-is.
 */
public final class PhysicalTenantResolver implements PhysicalTenantIds {

  static final int MAX_TENANT_ID_LENGTH = 64;
  private static final String PHYSICAL_TENANTS_PREFIX = Camunda.PREFIX + ".physical-tenants";
  static final ConfigurationPropertyName PREFIX_NAME =
      ConfigurationPropertyName.of(PHYSICAL_TENANTS_PREFIX);
  // Mirrored in PhysicalTenantScopeProvider (authentication module) — see the note there for why
  // this is duplicated rather than shared. Keep the two in sync.
  private static final Pattern VALID_TENANT_ID = Pattern.compile("[a-z0-9]+");

  /**
   * Cross-tenant rules run at the end of {@link #of(Environment, Camunda)} so a multi-tenant
   * deployment that would write into a shared or incompatible secondary storage fails fast at boot.
   * There is intentionally no opt-out toggle — hard isolation between physical tenants is the
   * point.
   */
  private static final List<CrossTenantValidation> CROSS_TENANT_VALIDATIONS =
      List.of(
          new SecondaryStorageIsolationValidation(),
          new SecondaryStorageTypeHomogeneityValidation());

  private final Map<String, Camunda> resolved;

  private PhysicalTenantResolver(final Map<String, Camunda> resolved) {
    this.resolved = Collections.unmodifiableMap(resolved);
  }

  @Override
  public Set<String> known() {
    return resolved.keySet();
  }

  public static PhysicalTenantResolver of(final Environment environment, final Camunda camunda) {
    final Set<String> physicalTenantIds = discover(environment);
    PhysicalTenantOverridePolicyValidation.validate(environment);
    PhysicalTenantRequiredOverrideValidation.validate(environment);
    PhysicalTenantAssignedProvidersValidation.validate(environment);
    final Map<String, Camunda> resolvedPhysicalTenants = new LinkedHashMap<>();
    final Binder binder = Binder.get(environment);
    for (final String physicalTenantId : physicalTenantIds) {
      final Camunda physicalTenant = new Camunda();
      binder.bind(Camunda.PREFIX, Bindable.ofInstance(physicalTenant));
      binder.bind(
          PHYSICAL_TENANTS_PREFIX + "." + physicalTenantId, Bindable.ofInstance(physicalTenant));
      resolvedPhysicalTenants.put(physicalTenantId, physicalTenant);
    }
    if (!resolvedPhysicalTenants.containsKey(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)) {
      resolvedPhysicalTenants.put(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, camunda);
    }
    CROSS_TENANT_VALIDATIONS.forEach(validation -> validation.validate(resolvedPhysicalTenants));
    return new PhysicalTenantResolver(resolvedPhysicalTenants);
  }

  public Map<String, Camunda> getAll() {
    return resolved;
  }

  public <T> Map<String, T> mapValues(final Function<Camunda, T> mapper) {
    final Map<String, T> out = new LinkedHashMap<>();
    resolved.forEach(
        (physicalTenantId, camunda) -> out.put(physicalTenantId, mapper.apply(camunda)));
    return Collections.unmodifiableMap(out);
  }

  public Camunda forPhysicalTenant(final String physicalTenantId) {
    if (!resolved.containsKey(physicalTenantId)) {
      throw new IllegalArgumentException("Unknown physical tenant id '" + physicalTenantId + "'");
    }
    return resolved.get(physicalTenantId);
  }

  /**
   * Walks the {@link Environment} and returns the set of physical-tenant ids by collecting the
   * distinct id segments from keys matching {@code camunda.physical-tenants.<id>.*}. Pure key
   * inspection — no binding takes place.
   *
   * <p>Tenant ids must be lowercase alphanumeric ({@code [a-z0-9]+}) — no dashes. This keeps yaml
   * and environment-variable forms addressing the same tenant: Spring strips dashes when matching
   * env var names to property names, so {@code camunda.physical-tenants.tenant-a.*} (yaml) and
   * {@code CAMUNDA_PHYSICALTENANTS_TENANTA_*} (env) would resolve to two different ids ({@code
   * tenant-a} vs. {@code tenanta}). Forbidding dashes makes the two forms collapse into one id.
   */
  private static Set<String> discover(final Environment environment) {
    final Set<String> tenants = new LinkedHashSet<>();
    for (final ConfigurationPropertySource source : ConfigurationPropertySources.get(environment)) {
      if (source instanceof final IterableConfigurationPropertySource iter) {
        iter.stream()
            .filter(PREFIX_NAME::isAncestorOf)
            .forEach(
                name -> {
                  if (name.getNumberOfElements() > PREFIX_NAME.getNumberOfElements()) {
                    final String tenantId =
                        name.getElement(PREFIX_NAME.getNumberOfElements(), Form.UNIFORM);
                    if (tenantId != null && !tenantId.isEmpty()) {
                      tenants.add(tenantId);
                    }
                  }
                });
      }
    }
    tenants.forEach(PhysicalTenantResolver::validateTenantId);
    return tenants;
  }

  @VisibleForTesting
  static void validateTenantId(final String tenantId) {
    if (tenantId.length() > MAX_TENANT_ID_LENGTH) {
      throw new UnifiedConfigurationException(
          String.format(
              "Invalid physical tenant id '%s' under '%s.*'. "
                  + "Tenant ids must not exceed %d characters.",
              tenantId, PHYSICAL_TENANTS_PREFIX, MAX_TENANT_ID_LENGTH));
    }
    if (!VALID_TENANT_ID.matcher(tenantId).matches()) {
      throw new UnifiedConfigurationException(
          String.format(
              "Invalid physical tenant id '%s' under '%s.*'. "
                  + "Tenant ids must be lowercase alphanumeric (matching %s) — no dashes, so "
                  + "yaml and environment-variable forms resolve to the same tenant.",
              tenantId, PHYSICAL_TENANTS_PREFIX, VALID_TENANT_ID.pattern()));
    }
  }
}
