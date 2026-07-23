/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.config;

import java.nio.file.Path;
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
 */
@ConfigurationProperties(prefix = "lake.serving")
public record LakeServingProperties(
    String warehouseDir,
    String stateDir,
    @DefaultValue("500") int maxRows,
    @DefaultValue("15") int queryTimeoutSeconds) {

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
}
