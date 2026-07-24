/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.spring.utils.InvalidPhysicalTenantIdException;
import io.camunda.spring.utils.PhysicalTenantConfigUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.core.env.Environment;

/**
 * Override policy for physical-tenant configuration: an explicit <em>deny-list</em> of cluster-wide
 * and identity-security properties that may not be overridden under {@code
 * camunda.physical-tenants.<id>.*}. Every other property is freely overridable (the physical-tenant
 * model is "override anything except cluster identity and security policy", so an allow-list would
 * be enormous and perpetually out of date).
 *
 * <p>Enforcement is pure <em>key inspection</em> over the declared {@code physical-tenants.<id>.*}
 * keys — the same walk {@link PhysicalTenantResolver#discover(Environment)} does — with no value
 * comparison and no binding. A tenant that declares any key at or under a non-overridable property
 * fails resolution.
 *
 * <p>The list below enumerates every non-overridable child of {@code camunda.cluster}, {@code
 * camunda.system}, {@code camunda.license}, and the identity-security subtrees of {@code
 * camunda.security} (see {@link io.camunda.configuration.Camunda}). It is a flat enumeration rather
 * than broader subtrees plus carve-outs: the overridable properties ({@code
 * cluster.partition-count}, {@code cluster.replication-factor}, {@code system.clock-controlled})
 * are simply absent from the list. Matching is by ancestor, so listing a parent (e.g. {@code
 * cluster.network}) also forbids all of its descendants.
 *
 * <p>Keep this list in sync with {@code Camunda}'s {@code cluster}, {@code system}, {@code
 * license}, and {@code security} sections when properties are added or removed.
 */
@NullMarked
final class PhysicalTenantOverridePolicyValidation {

  /**
   * Cluster-wide and identity-security properties that may not be overridden per physical tenant.
   * Enumerated from {@code camunda.cluster.*}, {@code camunda.system.*}, {@code camunda.license.*},
   * and the identity-security subtrees of {@code camunda.security.*}; the overridable carve-outs
   * ({@code cluster.partition-count}, {@code cluster.replication-factor}, {@code
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
              "license.key",
              // camunda.api.*
              "api.rest.executor",
              // camunda.data.* cluster wide
              "data.secondary-storage.rdbms.max-varchar-field-length",
              // camunda.security.* — identity-security settings that must apply uniformly
              "security.authentication.method",
              "security.authentication.unprotected-api",
              "security.authentication.webapp-enabled",
              "security.authentication.catch-all-unhandled-paths-enabled",
              "security.csrf",
              "security.http-headers",
              // forward declaration — property lands with #54898
              "security.cluster-admin",
              "security.multi-tenancy",
              "security.session",
              "security.transport-layer-security.cluster")
          .map(ConfigurationPropertyName::of)
          .toList();

  private PhysicalTenantOverridePolicyValidation() {}

  static void validate(final Environment environment) {
    // tenant id -> the forbidden relative property names it declares
    final Map<String, List<String>> violationsByTenant = new LinkedHashMap<>();
    try {
      PhysicalTenantConfigUtil.forEachTenantProperty(
          environment,
          (tenantId, relative) -> collectViolation(tenantId, relative, violationsByTenant));
    } catch (final InvalidPhysicalTenantIdException e) {
      throw new UnifiedConfigurationException(e);
    }
    if (!violationsByTenant.isEmpty()) {
      final String detail =
          violationsByTenant.entrySet().stream()
              .map(entry -> entry.getKey() + "=" + entry.getValue())
              .collect(Collectors.joining(", "));
      throw new UnifiedConfigurationException(
          "Cluster-wide and identity security properties may not be overridden per physical "
              + "tenant; configure them once under the root 'camunda.*'. "
              + "Forbidden tenant-level overrides: "
              + detail);
    }
  }

  private static void collectViolation(
      final String tenantId,
      final ConfigurationPropertyName relative,
      final Map<String, List<String>> violationsByTenant) {
    if (relative.isEmpty()) {
      // only the tenant id segment, no relative property to classify
      return;
    }
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
