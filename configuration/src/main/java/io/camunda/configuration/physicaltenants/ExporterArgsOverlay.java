/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Exporter;
import io.camunda.configuration.UnifiedConfigurationException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Three-layer deep-merge for per-tenant exporter configuration.
 *
 * <p>Layer 1 — {@link #deepMerge}: pure, generic, recursive map merge; key-agnostic.
 *
 * <p>Layer 2 — {@link #normalizeConfigKeys}: recursive lowercase + strip-dashes; composes with
 * layer 1 as {@code deepMerge(normalize(base), normalize(override))}.
 *
 * <p>Layer 3 — {@link #overlay}: exporter-specific orchestration: field overlay for {@code
 * className}/{@code jarPath}, deep-merge for {@code args}, divergence-only validation.
 */
@NullMarked
final class ExporterArgsOverlay {

  private ExporterArgsOverlay() {}

  /**
   * Pure recursive deep-merge. When both maps have a key whose values are both {@link Map}s,
   * recurse. Otherwise the override value replaces the base (scalars and lists alike — no append).
   * Disjoint keys survive; there is no removal.
   *
   * <p>No key normalization is performed here — keep this method key-agnostic so it remains safe
   * for case-sensitive maps. Compose with {@link #normalizeConfigKeys} at the call site.
   *
   * <p>Un-overridden nested maps are shallow-copied from the input (not deep-cloned); safe as used
   * since callers pass immutable Spring-bound maps.
   */
  static Map<String, Object> deepMerge(
      final Map<String, Object> base, final Map<String, Object> override) {
    final Map<String, Object> result = new LinkedHashMap<>(base);
    for (final var entry : override.entrySet()) {
      final Object overrideVal = entry.getValue();
      final Object baseVal = result.get(entry.getKey());
      if (baseVal instanceof Map && overrideVal instanceof Map) {
        result.put(
            entry.getKey(), deepMerge(toStringObjectMap(baseVal), toStringObjectMap(overrideVal)));
      } else {
        result.put(entry.getKey(), overrideVal);
      }
    }
    return Collections.unmodifiableMap(result);
  }

  /**
   * Mirrors the key normalization in {@code
   * io.camunda.zeebe.broker.exporter.context.ExporterConfiguration#normalizeKeys} applied
   * downstream when args are deserialized. Recursively strips dashes and lowercases all map keys
   * (using {@link Locale#ROOT}), so that relaxed config-key forms ({@code indexPrefix} and {@code
   * index-prefix}) collapse into one normalized key before merge.
   *
   * <p>Underscores are intentionally preserved — {@code INDEX_PREFIX} normalizes to {@code
   * index_prefix}, not {@code indexprefix}, consistent with the downstream normalization.
   *
   * <p>When two keys in the same map collapse to the same normalized form (e.g. {@code indexPrefix}
   * and {@code index-prefix} both become {@code indexprefix}), the last entry wins silently.
   *
   * <p>Normalization is not type-aware: it rewrites the keys of every nested {@link Map}, including
   * user-data maps inside {@code args}. This is unavoidable at the config layer, where the value
   * type is {@code Map<String,Object>}.
   */
  static Map<String, Object> normalizeConfigKeys(final Map<String, Object> map) {
    final Map<String, Object> result = new LinkedHashMap<>();
    for (final var entry : map.entrySet()) {
      final String key = entry.getKey().replace("-", "").toLowerCase(Locale.ROOT);
      final Object value = entry.getValue();
      result.put(key, value instanceof Map ? normalizeConfigKeys(toStringObjectMap(value)) : value);
    }
    return Collections.unmodifiableMap(result);
  }

  /**
   * Computes the merged {@code Map<String, Exporter>} for a physical tenant.
   *
   * <p>Root exporters are authoritative for default values. Tenant-declared exporters are obtained
   * via a targeted re-bind of only {@code physical-tenants.<id>.data.exporters} (so they contain
   * only what the tenant explicitly set). For each id in the union:
   *
   * <ul>
   *   <li>{@code className} / {@code jarPath} — tenant value if set, else root (plain field
   *       overlay).
   *   <li>{@code args} — deep-merged after normalization.
   * </ul>
   *
   * <p>Fails fast with a {@link UnifiedConfigurationException} when root explicitly declares {@code
   * className} or {@code jarPath} for an id and the tenant sets a <em>different</em> value. Absent
   * values on either side are not rejected.
   */
  static Map<String, Exporter> overlay(
      final String tenantId,
      final Map<String, Exporter> rootExporters,
      final Map<String, Exporter> tenantDeclaredExporters) {
    final Map<String, Exporter> merged = new LinkedHashMap<>();
    final var allIds = new LinkedHashSet<>(rootExporters.keySet());
    allIds.addAll(tenantDeclaredExporters.keySet());
    for (final String id : allIds) {
      merged.put(
          id, mergeExporter(tenantId, id, rootExporters.get(id), tenantDeclaredExporters.get(id)));
    }
    return Collections.unmodifiableMap(merged);
  }

  private static Exporter mergeExporter(
      final String tenantId,
      final String exporterId,
      final @Nullable Exporter root,
      final @Nullable Exporter tenant) {
    final @Nullable String rootClassName = root != null ? root.getClassName() : null;
    final @Nullable String rootJarPath = root != null ? root.getJarPath() : null;
    final @Nullable String tenantClassName = tenant != null ? tenant.getClassName() : null;
    final @Nullable String tenantJarPath = tenant != null ? tenant.getJarPath() : null;

    // Verify that the physical tenant does not declare the same exporterId as in at the root level,
    // but with different className or jarPath.
    // These fields can still be empty at root and tenant level, as for CamundaExporter and
    // RdbmsExporter, we have auto-configuration mode that allows setting only a subset of the
    // exporter properties. The full validation of the exporter configuration is done downstream in
    // the broker.
    checkDivergence(tenantId, exporterId, "className", rootClassName, tenantClassName);
    checkDivergence(tenantId, exporterId, "jarPath", rootJarPath, tenantJarPath);

    final Map<String, Object> rootArgs =
        (root != null && root.getArgs() != null) ? root.getArgs() : Map.of();
    final Map<String, Object> tenantArgs =
        (tenant != null && tenant.getArgs() != null) ? tenant.getArgs() : Map.of();

    final Exporter result = new Exporter();
    result.setClassName(tenantClassName != null ? tenantClassName : rootClassName);
    result.setJarPath(tenantJarPath != null ? tenantJarPath : rootJarPath);
    result.setArgs(deepMerge(normalizeConfigKeys(rootArgs), normalizeConfigKeys(tenantArgs)));
    return result;
  }

  private static void checkDivergence(
      final String tenantId,
      final String exporterId,
      final String field,
      final @Nullable String rootValue,
      final @Nullable String tenantValue) {
    if (rootValue != null && tenantValue != null && !rootValue.equals(tenantValue)) {
      throw new UnifiedConfigurationException(
          String.format(
              "Physical tenant '%s' sets exporter '%s'.%s='%s' but root declares '%s'."
                  + " A tenant may not override an exporter field that root explicitly declares.",
              tenantId, exporterId, field, tenantValue, rootValue));
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> toStringObjectMap(final Object value) {
    return (Map<String, Object>) value;
  }
}
