/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import io.camunda.security.api.context.CamundaSecurityScopeProvider;
import io.camunda.security.api.model.config.ScopedSecurityDescriptor;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.Environment;

/**
 * {@link CamundaSecurityScopeProvider} that emits one {@link ScopedSecurityDescriptor} per
 * explicitly configured physical tenant.
 *
 * <p>Each descriptor carries:
 *
 * <ul>
 *   <li>A base path of {@code /physical-tenants/<id>}, matched by CSL against {@code basePath +
 *       apiPaths} to build a per-tenant API {@code SecurityFilterChain}.
 *   <li>A narrowed {@link io.camunda.security.api.model.config.AuthenticationConfiguration}
 *       containing only the providers assigned to that tenant, each merged with PT-side overrides.
 * </ul>
 *
 * <p><b>Empty-list behaviour:</b> if no {@code camunda.physical-tenants.*} entries are present in
 * the {@link Environment} (i.e. only the implicit {@code default} tenant exists), this provider
 * returns an empty list. The cluster then behaves identically to a non-PT deployment — CSL's
 * standard chains serve all traffic.
 */
public final class PhysicalTenantScopeProvider implements CamundaSecurityScopeProvider {

  private static final Logger LOG = LoggerFactory.getLogger(PhysicalTenantScopeProvider.class);
  private static final String PHYSICAL_TENANTS_PREFIX = "camunda.physical-tenants";
  private static final ConfigurationPropertyName PREFIX_NAME =
      ConfigurationPropertyName.of(PHYSICAL_TENANTS_PREFIX);

  /** Pattern matching valid tenant ids (lowercase alphanumeric only, no dashes). */
  private static final Pattern VALID_TENANT_ID = Pattern.compile("[a-z0-9]+");

  private final Environment environment;
  private final List<ScopedSecurityDescriptor> descriptors;

  public PhysicalTenantScopeProvider(final Environment environment) {
    this.environment = environment;
    this.descriptors = buildDescriptors();
  }

  @Override
  public List<ScopedSecurityDescriptor> get() {
    return descriptors;
  }

  private List<ScopedSecurityDescriptor> buildDescriptors() {
    final Set<String> tenantIds = discoverExplicitTenantIds();
    if (tenantIds.isEmpty()) {
      LOG.debug("No camunda.physical-tenants.* entries found; PT-scoped security chains disabled.");
      return List.of();
    }

    final List<ScopedSecurityDescriptor> result = new ArrayList<>();
    for (final String tenantId : tenantIds) {
      final List<String> assigned = readAssigned(tenantId);
      if (assigned == null || assigned.isEmpty()) {
        LOG.debug(
            "Physical tenant '{}' has no providers.assigned; skipping scoped chain.", tenantId);
        continue;
      }
      try {
        final var authConfig =
            PhysicalTenantAuthConfigurations.forPhysicalTenant(tenantId, environment);
        result.add(new ScopedSecurityDescriptor("/physical-tenants/" + tenantId, authConfig));
        LOG.debug(
            "Registered scoped security descriptor for physical tenant '{}' at /physical-tenants/{}",
            tenantId,
            tenantId);
      } catch (final IllegalStateException e) {
        LOG.warn(
            "Skipping scoped security chain for physical tenant '{}': {}",
            tenantId,
            e.getMessage());
      }
    }
    return List.copyOf(result);
  }

  /**
   * Walks the {@link Environment} and returns the set of explicitly configured physical-tenant ids
   * (those with at least one key under {@code camunda.physical-tenants.<id>.*}).
   *
   * <p>Tenant ids must be lowercase alphanumeric ({@code [a-z0-9]+}) — no dashes. This matches the
   * constraint enforced by {@code PhysicalTenantResolver} to keep yaml and env-var forms addressing
   * the same tenant.
   */
  private Set<String> discoverExplicitTenantIds() {
    final Set<String> tenants = new LinkedHashSet<>();
    for (final ConfigurationPropertySource source : ConfigurationPropertySources.get(environment)) {
      if (source instanceof final IterableConfigurationPropertySource iter) {
        iter.stream()
            .filter(PREFIX_NAME::isAncestorOf)
            .forEach(
                name -> {
                  if (name.getNumberOfElements() > PREFIX_NAME.getNumberOfElements()) {
                    final String tenantId =
                        name.getElement(
                            PREFIX_NAME.getNumberOfElements(),
                            ConfigurationPropertyName.Form.UNIFORM);
                    if (tenantId != null
                        && !tenantId.isEmpty()
                        && VALID_TENANT_ID.matcher(tenantId).matches()) {
                      tenants.add(tenantId);
                    }
                  }
                });
      }
    }
    return tenants;
  }

  private List<String> readAssigned(final String tenantId) {
    final Binder binder = Binder.get(environment);
    final String prefix =
        "camunda.physical-tenants." + tenantId + ".security.authentication.providers.assigned";
    final Bindable<List<String>> bindable =
        Bindable.of(ResolvableType.forClassWithGenerics(List.class, String.class));
    final BindResult<List<String>> result = binder.bind(prefix, bindable);
    return result.orElse(null);
  }
}
