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
 * camunda.system}, {@code camunda.license}, the identity-security subtrees of {@code
 * camunda.security}, and — per the broker-configuration review in #56648 — every {@code BrokerCfg}
 * section where a per-tenant override is either architecturally inert (read from a single
 * broker-wide singleton, e.g. the embedded gateway or the shared RocksDB memory pool, regardless of
 * what a tenant declares) or a deliberate uniform-cluster policy call (see {@link
 * io.camunda.configuration.Camunda}). It is a flat enumeration rather than broader subtrees plus
 * carve-outs: the overridable properties ({@code cluster.partition-count}, {@code
 * system.clock-controlled}) are simply absent from the list. Matching is by ancestor, so listing a
 * parent (e.g. {@code cluster.network}) also forbids all of its descendants.
 *
 * <p>{@code cluster.raft.*} is denied even though {@code RaftPartitionFactory} builds each
 * partition from the per-tenant {@code BrokerCfg} and would honor a per-tenant value correctly —
 * this is a deliberate "uniform Raft tuning across tenants" policy decision, not a technical
 * constraint (#56648).
 *
 * <p>Keep this list in sync with {@code Camunda}'s {@code cluster}, {@code system}, {@code
 * license}, {@code security} , {@code api} and {@code data} sections when properties are added or
 * removed.
 */
@NullMarked
final class PhysicalTenantOverridePolicyValidation {

  /**
   * Cluster-wide and identity-security properties that may not be overridden per physical tenant.
   * Enumerated from {@code camunda.cluster.*}, {@code camunda.system.*}, {@code camunda.license.*},
   * the identity-security subtrees of {@code camunda.security.*}, and the {@code BrokerCfg}
   * sections denied by the #56648 review; the overridable carve-outs ({@code
   * cluster.partition-count}, {@code system.clock-controlled}) are intentionally omitted.
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
              // uniform-tuning policy call, not a technical constraint — see class javadoc
              "cluster.raft",
              "cluster.compression-algorithm",
              "cluster.partitioning",
              "cluster.zone",
              "cluster.send-on-legacy-subject",
              "cluster.receive-on-legacy-subject",
              // #56648: replication factor is a cluster-wide resilience property (how many
              // copies of a partition's log are kept for fault tolerance) — a cluster-level
              // concern, not a per-tenant one, so it is not meaningful to vary it per physical
              // tenant. It is also currently silently ignored if set per tenant:
              // StaticConfigurationGenerator resolves it from the root BrokerCfg only, unlike
              // its partition-count sibling.
              "cluster.replication-factor",
              // camunda.system.* — system / process-wide settings
              "system.cpu-thread-count",
              "system.io-thread-count",
              "system.actor",
              "system.upgrade",
              "system.restore",
              // camunda.license.* — one license per installation
              "license.key",
              // camunda.api.* — a broker process has exactly one embedded gateway; a per-tenant
              // override of its bind address/port/SSL/long-polling settings is silently ignored
              // (#56648)
              "api.rest.executor",
              "api.grpc",
              "api.long-polling",
              // camunda.data.* cluster wide
              "data.secondary-storage.rdbms.max-varchar-field-length",
              // #56648: a per-tenant override of the partition data directory is not allowed,
              // because this directory refers to the root directory shared by all partitions and
              // physical tenants. The root directory also consists of shared data such as
              // cluster-configuration.
              "data.primary-storage.directory",
              "data.primary-storage.versioned-directory-retention-count",
              // #56648: DiskSpaceUsageMonitorActor is a single broker-wide actor built before any
              // per-tenant BrokerCfg exists, so every disk.* knob is silently ignored per tenant
              "data.primary-storage.disk",
              // #56648: RocksDbResources sizes one shared block-cache/write-buffer pool from the
              // root config by design ("adding physical tenants does not multiply RocksDB memory
              // usage") — a per-tenant override of these three is silently ignored; sibling
              // rocks-db.* knobs (column-family-options, max-open-files, ...) remain overridable
              // since they are applied per partition
              "data.primary-storage.rocks-db.memory-limit",
              "data.primary-storage.rocks-db.memory-allocation-strategy",
              "data.primary-storage.rocks-db.memory-fraction",
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
