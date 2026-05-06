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
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName.Form;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.core.env.Environment;

/**
 * Walks the {@link Environment} once and returns the set of physical-tenant ids by collecting the
 * distinct id segments from keys matching {@code camunda.physical-tenants.<id>.*}. Pure key
 * inspection — no binding takes place.
 *
 * <p>Tenant ids must be lowercase alphanumeric ({@code [a-z0-9]+}) — no dashes. This keeps yaml and
 * environment-variable forms addressing the same tenant: Spring strips dashes when matching env var
 * names to property names, so {@code camunda.physical-tenants.tenant-a.*} (yaml) and {@code
 * CAMUNDA_PHYSICALTENANTS_TENANTA_*} (env) would resolve to two different ids ({@code tenant-a} vs.
 * {@code tenanta}). Forbidding dashes makes the two forms collapse into one id.
 */
public class PhysicalTenantDiscovery {

  public static final String PHYSICAL_TENANTS_PREFIX = Camunda.PREFIX + ".physical-tenants";
  static final ConfigurationPropertyName PREFIX_NAME =
      ConfigurationPropertyName.of(PHYSICAL_TENANTS_PREFIX);
  private static final Pattern VALID_TENANT_ID = Pattern.compile("[a-z0-9]+");

  public Set<String> discover(final Environment environment) {
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
    tenants.forEach(PhysicalTenantDiscovery::validateTenantId);
    return tenants;
  }

  static void validateTenantId(final String tenantId) {
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
