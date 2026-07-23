/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.config;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the lake serving app, bound with the prefix {@code lake.serving}.
 *
 * @param warehouseDir root of the lake writer's warehouse directory. REQUIRED, no default: guessing
 *     a path here would silently serve the wrong (or a stale demo) warehouse, so this app fails
 *     fast at startup (see the compact constructor) rather than picking one for you. Lake tables
 *     are discovered under {@code <warehouseDir>/lake/} — see {@link
 *     io.camunda.analytics.lake.serving.duckdb.LakeViewRegistry}.
 * @param stateDir optional root of the lake writer's open-state snapshot directory. Not yet read by
 *     this scaffold; reserved for the open-state views a follow-up lane adds.
 * @param maxRows hard cap on the number of rows any query can return (default 500)
 * @param queryTimeoutSeconds best-effort per-statement timeout so a runaway free-form query can
 *     never hang a request forever (default 15)
 * @param maxExplainScanRows above this estimated row count (summed {@code cnt} over the requested
 *     window from the entity's {@code _metrics} partials), {@code POST /api/investigate} skips its
 *     single-scan {@code cohort-compare} rung and reports a {@code SCAN_DEFERRED} finding instead
 *     of running an unbounded raw-table scan (default 5,000,000).
 * @param dimKinds per-dim-name overrides of the semantic-kind overlay {@link #effectiveDimKinds()}
 *     exposes over {@code GET /api/registry} — merged on top of (not replacing) the hardcoded
 *     defaults, so setting one key never drops the rest. {@code null}/unset means "no overrides".
 */
@ConfigurationProperties(prefix = "lake.serving")
public record LakeServingProperties(
    String warehouseDir,
    String stateDir,
    @DefaultValue("500") int maxRows,
    @DefaultValue("15") int queryTimeoutSeconds,
    @DefaultValue("5000000") long maxExplainScanRows,
    Map<String, String> dimKinds) {

  /**
   * Built-in dim-name -> semantic-kind mapping, overridable per key via {@link #dimKinds}. Kept as
   * plain strings (not an enum) so a future dim kind needs no code change on this side, only a
   * config override.
   */
  private static final Map<String, String> DEFAULT_DIM_KINDS =
      Map.of(
          "variant_hash", "VARIANT",
          "flow_id", "FLOW",
          "source_element_id", "ELEMENT",
          "target_element_id", "ELEMENT",
          "element_id", "ELEMENT",
          "version", "VERSION",
          "process_id", "PROCESS",
          "object_type", "OBJECT_TYPE");

  public LakeServingProperties {
    if (warehouseDir == null || warehouseDir.isBlank()) {
      throw new IllegalStateException(
          "lake.serving.warehouse-dir must be set -- point it at the same warehouse directory "
              + "the lake writer (analytics-lake) uses, e.g. "
              + "-Dlake.serving.warehouse-dir=/path/to/warehouse. There is no default.");
    }
  }

  public Path warehouseDirPath() {
    return Path.of(warehouseDir);
  }

  public Optional<Path> stateDirPath() {
    return stateDir == null || stateDir.isBlank()
        ? Optional.empty()
        : Optional.of(Path.of(stateDir));
  }

  /** {@link #dimKinds} merged on top of {@link #DEFAULT_DIM_KINDS}; never {@code null}. */
  public Map<String, String> effectiveDimKinds() {
    final Map<String, String> merged = new LinkedHashMap<>(DEFAULT_DIM_KINDS);
    if (dimKinds != null) {
      merged.putAll(dimKinds);
    }
    return Map.copyOf(merged);
  }
}
