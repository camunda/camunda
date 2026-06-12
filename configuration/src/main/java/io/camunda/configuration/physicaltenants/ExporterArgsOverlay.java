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
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Three-layer deep-merge for per-tenant exporter configuration.
 *
 * <p>Layer 1 — {@link #deepMerge}: pure, generic, recursive map merge; key-agnostic.
 *
 * <p>Layer 2 — {@link #canonicalizeConfigKeys}: recursive lowercase + strip-dashes; composes with
 * layer 1 as {@code deepMerge(canonicalize(base), canonicalize(override))}.
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
   * for case-sensitive maps. Compose with {@link #canonicalizeConfigKeys} at the call site.
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
   * Recursively lowercases and strips dashes from all map keys, so that relaxed config-key forms
   * ({@code indexPrefix}, {@code index-prefix}, {@code INDEX_PREFIX}) collapse into one key before
   * merge.
   */
  static Map<String, Object> canonicalizeConfigKeys(final Map<String, Object> map) {
    final Map<String, Object> result = new LinkedHashMap<>();
    for (final var entry : map.entrySet()) {
      final String key = entry.getKey().toLowerCase().replace("-", "");
      final Object value = entry.getValue();
      result.put(
          key, value instanceof Map ? canonicalizeConfigKeys(toStringObjectMap(value)) : value);
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
   *   <li>{@code args} — deep-merged after canonicalization.
   * </ul>
   *
   * <p>Fails fast with a {@link UnifiedConfigurationException} when root explicitly declares {@code
   * className} or {@code jarPath} for an id and the tenant sets a <em>different</em> value. Absent
   * values on either side are not rejected.
   */
  static Map<String, Exporter> overlay(
      final String tenantId,
      final Map<String, Exporter> rootExporters,
      final Map<String, Exporter> tenantDeclared) {
    final Map<String, Exporter> merged = new LinkedHashMap<>();
    final var allIds = new LinkedHashSet<>(rootExporters.keySet());
    allIds.addAll(tenantDeclared.keySet());
    for (final String id : allIds) {
      merged.put(id, mergeExporter(tenantId, id, rootExporters.get(id), tenantDeclared.get(id)));
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

    checkDivergence(tenantId, exporterId, "className", rootClassName, tenantClassName);
    checkDivergence(tenantId, exporterId, "jarPath", rootJarPath, tenantJarPath);

    final Map<String, Object> rootArgs =
        (root != null && root.getArgs() != null) ? root.getArgs() : Map.of();
    final Map<String, Object> tenantArgs =
        (tenant != null && tenant.getArgs() != null) ? tenant.getArgs() : Map.of();

    final Exporter result = new Exporter();
    result.setClassName(tenantClassName != null ? tenantClassName : rootClassName);
    result.setJarPath(tenantJarPath != null ? tenantJarPath : rootJarPath);
    result.setArgs(deepMerge(canonicalizeConfigKeys(rootArgs), canonicalizeConfigKeys(tenantArgs)));
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
                  + " A tenant may not re-class an exporter the root explicitly declares.",
              tenantId, exporterId, field, tenantValue, rootValue));
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> toStringObjectMap(final Object value) {
    return (Map<String, Object>) value;
  }
}
