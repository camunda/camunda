/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.zeebe.exporter.api.ExporterConfigMerger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Shared {@link ExporterConfigMerger} discovery and invocation for the two places that deep-merge
 * one exporter's args over another's: the legacy-to-unified migration in {@code
 * BrokerBasedPropertiesOverride} and the per-physical-tenant resolution in {@code
 * PhysicalTenantExporterConfigurations} (ADR-0008 §2/§5).
 *
 * <p>Both have the same shape — a base entry, an overlay entry that wins per key, and a class-aware
 * merger that is only consulted when the exporter class ships one — so the SPI lookup, the
 * exactly-one-claimant rule, the defensive input copying and the failure wrapping live here rather
 * than being restated at each call site. Callers differ only in the {@code context} they pass,
 * which names the entry being merged in any error raised (for example {@code "exporter 'foo'"} or
 * {@code "exporter 'foo' of physical tenant 't1'"}).
 */
@NullMarked
public final class ExporterArgsMergers {

  private ExporterArgsMergers() {}

  /** Loads every {@link ExporterConfigMerger} on the classpath. */
  public static List<ExporterConfigMerger> load() {
    return ServiceLoader.load(ExporterConfigMerger.class).stream()
        .map(ServiceLoader.Provider::get)
        .toList();
  }

  /**
   * Returns the single merger claiming {@code className}, or {@code null} when the class ships none
   * — in which case the caller replaces the base args wholesale, since partial inheritance is only
   * offered for classes whose config model a merger can introspect.
   *
   * @throws UnifiedConfigurationException if more than one merger claims the class
   */
  public static @Nullable ExporterConfigMerger find(
      final List<ExporterConfigMerger> mergers,
      final @Nullable String className,
      final String context) {
    if (className == null) {
      return null;
    }
    final List<ExporterConfigMerger> claimants =
        mergers.stream().filter(merger -> merger.supports(className)).toList();
    if (claimants.size() > 1) {
      throw new UnifiedConfigurationException(
          String.format(
              "Multiple ExporterConfigMerger implementations claim exporter class '%s' (%s): %s. "
                  + "Exactly one merger may support a given exporter class.",
              className, context, claimants.stream().map(m -> m.getClass().getName()).toList()));
    }
    return claimants.isEmpty() ? null : claimants.getFirst();
  }

  /**
   * Deep-merges {@code overlay} over {@code base} using {@code merger}, with {@code overlay}
   * winning per key and {@code base} filling the gaps.
   *
   * <p>A merger is third-party SPI code, so it is handed recursively immutable copies rather than
   * the caller's live args maps: the SPI contract forbids mutating its inputs, and this enforces it
   * for nested maps and lists too (exporter args are typically nested, e.g. {@code connect}/{@code
   * index}/{@code history}).
   *
   * @throws UnifiedConfigurationException wrapping any {@link RuntimeException} the merger throws
   */
  public static @Nullable Map<String, Object> merge(
      final ExporterConfigMerger merger,
      final @Nullable Map<String, Object> base,
      final @Nullable Map<String, Object> overlay,
      final String context) {
    try {
      return merger.merge(immutableArgsCopy(base), immutableArgsCopy(overlay));
    } catch (final RuntimeException e) {
      // the cause carries the detail; the top-level message stays stable and never renders "null"
      throw new UnifiedConfigurationException(
          String.format("Failed to merge exporter args for %s", context), e);
    }
  }

  private static Map<String, Object> immutableArgsCopy(final @Nullable Map<String, Object> args) {
    if (args == null) {
      return Map.of();
    }
    final Map<String, Object> copy = new LinkedHashMap<>(args.size());
    args.forEach((key, value) -> copy.put(key, immutableValueCopy(value)));
    return Collections.unmodifiableMap(copy);
  }

  private static @Nullable Object immutableValueCopy(final @Nullable Object value) {
    if (value instanceof final Map<?, ?> map) {
      final Map<Object, Object> copy = new LinkedHashMap<>(map.size());
      map.forEach((key, nestedValue) -> copy.put(key, immutableValueCopy(nestedValue)));
      return Collections.unmodifiableMap(copy);
    }
    if (value instanceof final List<?> list) {
      final List<Object> copy = new ArrayList<>(list.size());
      list.forEach(item -> copy.add(immutableValueCopy(item)));
      return Collections.unmodifiableList(copy);
    }
    return value;
  }
}
