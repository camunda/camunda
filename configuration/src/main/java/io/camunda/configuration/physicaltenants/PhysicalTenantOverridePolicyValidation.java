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
 * Override policy for physical-tenant configuration: an explicit <em>deny-list</em> of cluster-wide
 * properties that may not be overridden under {@code camunda.physical-tenants.<id>.*}. Every other
 * property is freely overridable (the physical-tenant model is "override anything except cluster
 * identity", so an allow-list would be enormous and perpetually out of date).
 *
 * <p>Enforcement is pure <em>key inspection</em> over the declared {@code physical-tenants.<id>.*}
 * keys — the same walk {@link PhysicalTenantResolver#discover(Environment)} does — with no value
 * comparison and no binding. A tenant that declares any key at or under a non-overridable property
 * fails resolution.
 *
 * <p>The list below enumerates every non-overridable child of {@code camunda.cluster}, {@code
 * camunda.system} and {@code camunda.license} (see {@link io.camunda.configuration.Camunda}). It is
 * a flat enumeration rather than the broader subtrees plus carve-outs: the overridable properties
 * ({@code cluster.partition-count}, {@code cluster.replication-factor}, {@code
 * system.clock-controlled}) are simply absent from the list. Matching is by ancestor, so listing a
 * parent (e.g. {@code cluster.network}) also forbids all of its descendants.
 *
 * <p>Keep this list in sync with {@code Camunda}'s {@code cluster}, {@code system} and {@code
 * license} sections when properties are added or removed.
 */
@NullMarked
final class PhysicalTenantOverridePolicyValidation {

  private static final ConfigurationPropertyName PHYSICAL_TENANTS_NAME =
      ConfigurationPropertyName.of(Camunda.PREFIX + ".physical-tenants");

  /**
   * Cluster-wide properties that may not be overridden per physical tenant. Enumerated from {@code
   * camunda.cluster.*}, {@code camunda.system.*} and {@code camunda.license.*}; the overridable
   * carve-outs ({@code cluster.partition-count}, {@code cluster.replication-factor}, {@code
   * system.clock-controlled}) are intentionally omitted.
   */
  private static final List<ConfigurationPropertyName> NON_OVERRIDABLE =
      Stream.of(
              // camunda.cluster.* — broker topology / cluster identity
              "cluster.metadata",
              "cluster.network",
              "cluster.initial-contact-points",
              "cluster.node-id-provider",
              "cluster.node-id",
              "cluster.size",
              "cluster.membership",
              "cluster.name",
              "cluster.cluster-id",
              "cluster.gateway-id",
              "cluster.raft",
              "cluster.compression-algorithm",
              "cluster.partitioning",
              "cluster.zone",
              "cluster.send-on-legacy-subject",
              "cluster.receive-on-legacy-subject",
              // camunda.system.* — system / process-wide settings
              "system.cpu-thread-count",
              "system.io-thread-count",
              "system.actor",
              "system.upgrade",
              "system.restore",
              // camunda.license.* — one license per installation
              "license.key")
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

  /**
   * Whether the given property name — expressed <em>relative to {@code camunda.}</em> (e.g. {@code
   * cluster.size}) — is non-overridable per physical tenant. Visible for testing so the golden-file
   * test can classify the full configuration surface through the real policy.
   */
  static boolean isNonOverridable(final ConfigurationPropertyName relative) {
    return NON_OVERRIDABLE.stream().anyMatch(prefix -> isUnder(prefix, relative));
  }

  private static boolean isUnder(
      final ConfigurationPropertyName prefix, final ConfigurationPropertyName name) {
    return prefix.equals(name) || prefix.isAncestorOf(name);
  }
}
