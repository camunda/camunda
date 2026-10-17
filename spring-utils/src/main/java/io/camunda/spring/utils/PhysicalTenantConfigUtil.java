/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.utils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName.Form;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.core.env.Environment;

/**
 * Shared utilities over physical-tenant configuration declared under {@code
 * camunda.physical-tenants.<id>.*}: discovering the declared tenant ids, iterating the properties
 * each tenant declares, and validating tenant ids. Consolidates the environment walk previously
 * duplicated across {@code configuration} and {@code authentication} module call sites.
 *
 * <p>Tenant ids must be lowercase alphanumeric ({@code [a-z0-9]+}) — no dashes. This keeps yaml and
 * environment-variable forms addressing the same tenant: Spring strips dashes when matching env var
 * names to property names, so {@code camunda.physical-tenants.tenant-a.*} (yaml) and {@code
 * CAMUNDA_PHYSICALTENANTS_TENANTA_*} (env) would otherwise resolve to two different ids ({@code
 * tenant-a} vs. {@code tenanta}). Forbidding dashes makes the two forms collapse into one id.
 */
public final class PhysicalTenantConfigUtil {

  public static final int MAX_TENANT_ID_LENGTH = 64;

  private static final String PHYSICAL_TENANTS_PREFIX = "camunda.physical-tenants";
  private static final ConfigurationPropertyName PREFIX_NAME =
      ConfigurationPropertyName.of(PHYSICAL_TENANTS_PREFIX);
  private static final Pattern VALID_TENANT_ID = Pattern.compile("[a-z0-9]+");

  private PhysicalTenantConfigUtil() {}

  /**
   * Walks {@code environment} and returns the set of physical-tenant ids by collecting the distinct
   * id segments from keys matching {@code camunda.physical-tenants.<id>.*}. Pure key inspection —
   * no binding takes place. Every discovered id is validated via {@link #validateTenantId(String)}.
   *
   * @throws InvalidPhysicalTenantIdException if any discovered id fails format or length validation
   */
  public static Set<String> discover(final Environment environment) {
    final Set<String> tenants = new LinkedHashSet<>();
    forEachTenantProperty(environment, (tenantId, relative) -> tenants.add(tenantId));
    return tenants;
  }

  /**
   * Walks {@code environment} and invokes {@code consumer} once per property declared under {@code
   * camunda.physical-tenants.<id>.*}, passing the validated tenant id and the property name
   * relative to {@code camunda.physical-tenants.<id>} (empty when the key is only the id segment).
   * Pure key inspection — no binding. Blank id segments are skipped; every surfaced id is
   * validated.
   *
   * @throws InvalidPhysicalTenantIdException if any surfaced id fails format or length validation
   */
  public static void forEachTenantProperty(
      final Environment environment, final BiConsumer<String, ConfigurationPropertyName> consumer) {
    final int tenantIdIndex = PREFIX_NAME.getNumberOfElements();
    for (final ConfigurationPropertySource source : ConfigurationPropertySources.get(environment)) {
      if (source instanceof final IterableConfigurationPropertySource iter) {
        iter.stream()
            .filter(PREFIX_NAME::isAncestorOf)
            .forEach(
                name -> {
                  if (name.getNumberOfElements() <= tenantIdIndex) {
                    // only the physical-tenants prefix, no tenant id segment
                    return;
                  }
                  final String tenantId = name.getElement(tenantIdIndex, Form.UNIFORM);
                  if (tenantId == null || tenantId.isEmpty()) {
                    // never surface a blank tenant id
                    return;
                  }
                  validateTenantId(tenantId);
                  consumer.accept(tenantId, name.subName(tenantIdIndex + 1));
                });
      }
    }
  }

  /**
   * The names of entry {@code key} of the map at {@code mapName}, for binding that entry on its
   * own: one per form in which {@code key} is declared as an entry of {@code mapName} or of {@code
   * relatedMapNames}, either dotted ({@code map.azure_ad}) or in brackets ({@code map[AzureAd]}).
   *
   * <p>The form cannot be derived from the key, because Spring keys a bound map by the element as
   * written. It matters because Spring matches a bracketed element only exactly and a dotted one
   * leniently, so each form is looked up as declared. Pass the map that {@code mapName} overlays,
   * such as the cluster-level map for a tenant's, as a related map, so that the entry is found in
   * the form either side declares it in.
   */
  public static List<ConfigurationPropertyName> mapEntryNames(
      final Environment environment,
      final String mapName,
      final String key,
      final String... relatedMapNames) {
    final ConfigurationPropertyName map = ConfigurationPropertyName.of(mapName);
    final Set<ConfigurationPropertyName> elements = declaredEntryElements(environment, map, key);
    for (final String relatedMapName : relatedMapNames) {
      elements.addAll(
          declaredEntryElements(environment, ConfigurationPropertyName.of(relatedMapName), key));
    }
    return elements.stream().map(map::append).toList();
  }

  private static Set<ConfigurationPropertyName> declaredEntryElements(
      final Environment environment, final ConfigurationPropertyName map, final String key) {
    final int entryIndex = map.getNumberOfElements();
    final Set<ConfigurationPropertyName> elements = new LinkedHashSet<>();
    for (final ConfigurationPropertySource source : ConfigurationPropertySources.get(environment)) {
      if (source instanceof final IterableConfigurationPropertySource iter) {
        iter.stream()
            .filter(map::isAncestorOf)
            .filter(name -> key.equals(name.getElement(entryIndex, Form.ORIGINAL)))
            .forEach(name -> elements.add(name.subName(entryIndex).chop(1)));
      }
    }
    return elements;
  }

  /**
   * Validates a single tenant id against the format ({@code [a-z0-9]+}) and length ({@code <=}
   * {@value #MAX_TENANT_ID_LENGTH}) rules.
   *
   * @throws InvalidPhysicalTenantIdException if the id is invalid
   */
  public static void validateTenantId(final String tenantId) {
    if (tenantId == null) {
      throw new InvalidPhysicalTenantIdException(
          String.format(
              "Invalid physical tenant id under '%s.*'. Tenant ids must not be null.",
              PHYSICAL_TENANTS_PREFIX));
    }
    if (tenantId.length() > MAX_TENANT_ID_LENGTH) {
      throw new InvalidPhysicalTenantIdException(
          String.format(
              "Invalid physical tenant id under '%s.*'. "
                  + "Tenant ids must not exceed %d characters (was %d).",
              PHYSICAL_TENANTS_PREFIX, MAX_TENANT_ID_LENGTH, tenantId.length()));
    }
    if (!VALID_TENANT_ID.matcher(tenantId).matches()) {
      throw new InvalidPhysicalTenantIdException(
          String.format(
              "Invalid physical tenant id '%s' under '%s.*'. "
                  + "Tenant ids must be lowercase alphanumeric (matching %s) — no dashes, so "
                  + "yaml and environment-variable forms resolve to the same tenant.",
              tenantId, PHYSICAL_TENANTS_PREFIX, VALID_TENANT_ID.pattern()));
    }
  }
}
