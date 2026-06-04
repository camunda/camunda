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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName.Form;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.core.env.Environment;

/**
 * Override policy for physical-tenant configuration: a small <em>deny-list</em> of cluster-wide
 * properties that may not be overridden under {@code camunda.physical-tenants.<id>.*}. Every other
 * property is freely overridable (the physical-tenant model is "override anything except cluster
 * identity", so an allow-list would be enormous and perpetually out of date).
 *
 * <p>Enforcement is pure <em>key inspection</em> over the declared {@code physical-tenants.<id>.*}
 * keys — the same walk {@link PhysicalTenantResolver#discover(Environment)} does — with no value
 * comparison and no binding. A tenant that declares any key under a non-overridable subtree fails
 * resolution.
 *
 * <p>Non-overridable subtrees:
 *
 * <ul>
 *   <li>{@code cluster.*} — broker topology (nodeId, size, contact points). Shared by the whole
 *       physical cluster, <em>except</em> {@code cluster.partition-count} and {@code
 *       cluster.replication-factor}, which stay overridable per tenant.
 *   <li>{@code system.*} — system/process-wide settings, <em>except</em> {@code
 *       system.clock-controlled}, which stays overridable so a tenant can run a controlled clock.
 *   <li>{@code license.*} — one license per installation.
 * </ul>
 */
@NullMarked
final class PhysicalTenantOverridePolicyValidation {

  private static final ConfigurationPropertyName PHYSICAL_TENANTS_NAME =
      ConfigurationPropertyName.of(Camunda.PREFIX + ".physical-tenants");

  /** Cluster-wide subtrees that may not be overridden per physical tenant. */
  private static final List<ConfigurationPropertyName> NON_OVERRIDABLE =
      Stream.of("cluster", "system", "license").map(ConfigurationPropertyName::of).toList();

  /** Carve-outs that remain overridable even though their parent subtree is non-overridable. */
  private static final List<ConfigurationPropertyName> OVERRIDABLE_EXCEPTIONS =
      Stream.of("cluster.partition-count", "cluster.replication-factor", "system.clock-controlled")
          .map(ConfigurationPropertyName::of)
          .toList();

  private PhysicalTenantOverridePolicyValidation() {}

  static void validate(final Environment environment) {
    // tenant id -> the forbidden relative property names it declares
    final Map<String, List<String>> violationsByTenant = new LinkedHashMap<>();
    for (final ConfigurationPropertySource source : ConfigurationPropertySources.get(environment)) {
      if (source instanceof final IterableConfigurationPropertySource iter) {
        iter.stream()
            .filter(PHYSICAL_TENANTS_NAME::isAncestorOf)
            .forEach(name -> collectViolation(name, violationsByTenant));
      }
    }
    if (!violationsByTenant.isEmpty()) {
      final String detail =
          violationsByTenant.entrySet().stream()
              .map(entry -> entry.getKey() + "=" + entry.getValue())
              .collect(Collectors.joining(", "));
      throw new UnifiedConfigurationException(
          "Cluster-wide properties may not be overridden per physical tenant; configure them once "
              + "under the root 'camunda.*'. Forbidden tenant-level overrides: "
              + detail);
    }
  }

  private static void collectViolation(
      final ConfigurationPropertyName name, final Map<String, List<String>> violationsByTenant) {
    final int tenantIdIndex = PHYSICAL_TENANTS_NAME.getNumberOfElements();
    if (name.getNumberOfElements() <= tenantIdIndex + 1) {
      // only the tenant id segment, no relative property to classify
      return;
    }
    final String tenantId = name.getElement(tenantIdIndex, Form.UNIFORM);
    final ConfigurationPropertyName relative = name.subName(tenantIdIndex + 1);
    if (isNonOverridable(relative)) {
      violationsByTenant.computeIfAbsent(tenantId, k -> new ArrayList<>()).add(relative.toString());
    }
  }

  private static boolean isNonOverridable(final ConfigurationPropertyName relative) {
    final boolean clusterWide =
        NON_OVERRIDABLE.stream().anyMatch(prefix -> isUnder(prefix, relative));
    if (!clusterWide) {
      return false;
    }
    return OVERRIDABLE_EXCEPTIONS.stream().noneMatch(exception -> isUnder(exception, relative));
  }

  private static boolean isUnder(
      final ConfigurationPropertyName prefix, final ConfigurationPropertyName name) {
    return prefix.equals(name) || prefix.isAncestorOf(name);
  }
}
