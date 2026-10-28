/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * Derives the environment a runtime change of the physical tenant set resolves against, so that
 * {@link PhysicalTenantResolver#of} runs the same discovery, binding and validation as at boot.
 *
 * <p>The derived environment holds the property sources of the base environment, minus every
 * property a changed tenant declares, plus the new subtree of each changed tenant. The base
 * environment is not modified, so a change that fails resolution leaves nothing behind.
 */
@NullMarked
public final class PhysicalTenantEnvironments {

  static final String OVERLAY_SOURCE_NAME = "physicalTenantRuntimeOverlay";

  private PhysicalTenantEnvironments() {}

  /**
   * @param base the environment the current tenant set was resolved from
   * @param tenantSubtrees per changed tenant, its whole subtree with names relative to {@code
   *     camunda.physical-tenants.<id>}; replaces every property the tenant declares in {@code base}
   * @param removedTenants tenants whose properties are dropped
   */
  public static ConfigurableEnvironment withTenants(
      final ConfigurableEnvironment base,
      final Map<String, Map<String, Object>> tenantSubtrees,
      final Set<String> removedTenants) {
    final var hidden =
        Stream.concat(tenantSubtrees.keySet().stream(), removedTenants.stream())
            .distinct()
            .map(TenantKeys::new)
            .toList();

    final var derived = new StandardEnvironment();
    final var sources = derived.getPropertySources();
    sources.stream().map(PropertySource::getName).toList().forEach(sources::remove);

    final Map<String, Object> overlay = new LinkedHashMap<>();
    if (base.getPropertySources().get(OVERLAY_SOURCE_NAME) instanceof final MapPropertySource old) {
      old.getSource()
          .forEach(
              (key, value) -> {
                if (hidden.stream().noneMatch(keys -> keys.matches(key))) {
                  overlay.put(key, value);
                }
              });
    }
    tenantSubtrees.forEach(
        (tenantId, subtree) ->
            subtree.forEach(
                (relativeName, value) ->
                    overlay.put(
                        PhysicalTenantResolver.PHYSICAL_TENANTS_PREFIX
                            + "."
                            + tenantId
                            + "."
                            + relativeName,
                        value)));
    sources.addLast(new MapPropertySource(OVERLAY_SOURCE_NAME, overlay));

    for (final PropertySource<?> source : base.getPropertySources()) {
      if (ConfigurationPropertySources.isAttachedConfigurationPropertySource(source)
          || OVERLAY_SOURCE_NAME.equals(source.getName())) {
        continue;
      }
      sources.addLast(withoutTenantKeys(source, hidden));
    }
    derived.setActiveProfiles(base.getActiveProfiles());
    ConfigurationPropertySources.attach(derived);
    return derived;
  }

  /**
   * The subtree a tenant declares in the environment, with names relative to {@code
   * camunda.physical-tenants.<id>} in their canonical form and values as text. Where several
   * property sources declare a name, the one that takes precedence wins.
   */
  public static Map<String, String> subtreeOf(
      final Environment environment, final String tenantId) {
    final var prefix =
        ConfigurationPropertyName.of(
            PhysicalTenantResolver.PHYSICAL_TENANTS_PREFIX + "." + tenantId);
    final Map<String, String> subtree = new TreeMap<>();
    for (final ConfigurationPropertySource source : ConfigurationPropertySources.get(environment)) {
      if (!(source instanceof final IterableConfigurationPropertySource iterable)) {
        continue;
      }
      iterable.stream()
          .filter(prefix::isAncestorOf)
          .forEach(
              name -> {
                final var relative = name.subName(prefix.getNumberOfElements()).toString();
                final var property = source.getConfigurationProperty(name);
                if (property != null && !subtree.containsKey(relative)) {
                  subtree.put(relative, String.valueOf(property.getValue()));
                }
              });
    }
    return subtree;
  }

  private static PropertySource<?> withoutTenantKeys(
      final PropertySource<?> source, final List<TenantKeys> hidden) {
    if (!(source instanceof final EnumerablePropertySource<?> enumerable)) {
      return source;
    }
    final var names = enumerable.getPropertyNames();
    if (Arrays.stream(names).noneMatch(name -> isHidden(name, hidden))) {
      return source;
    }
    final Map<String, Object> visible = new LinkedHashMap<>();
    for (final String name : names) {
      if (!isHidden(name, hidden)) {
        final var value = enumerable.getProperty(name);
        if (value != null) {
          visible.put(name, value);
        }
      }
    }
    return source instanceof SystemEnvironmentPropertySource
        ? new SystemEnvironmentPropertySource(source.getName(), visible)
        : new MapPropertySource(source.getName(), visible);
  }

  private static boolean isHidden(final String name, final List<TenantKeys> hidden) {
    return hidden.stream().anyMatch(keys -> keys.matches(name));
  }

  private record TenantKeys(ConfigurationPropertyName prefix, String environmentPrefix) {

    private TenantKeys(final String tenantId) {
      this(
          ConfigurationPropertyName.of(
              PhysicalTenantResolver.PHYSICAL_TENANTS_PREFIX + "." + tenantId),
          "CAMUNDA_PHYSICALTENANTS_" + tenantId.toUpperCase(Locale.ROOT) + "_");
    }

    private boolean matches(final String key) {
      if (key.toUpperCase(Locale.ROOT).startsWith(environmentPrefix)) {
        return true;
      }
      final @Nullable ConfigurationPropertyName name = adapt(key);
      return name != null && prefix.isAncestorOf(name);
    }

    private static @Nullable ConfigurationPropertyName adapt(final String key) {
      try {
        return ConfigurationPropertyName.adapt(key, '.');
      } catch (final RuntimeException e) {
        return null;
      }
    }
  }
}
