/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset;

import io.camunda.analytics.meter.BoundMeter;
import io.camunda.analytics.meter.PushdownSpec;
import java.util.Optional;

/**
 * One meter of a cube: the arithmetic inside a cell and the serving column(s) it writes — nothing
 * more (ADR 0009). A meter is <em>not</em> a pipeline identity: it has no stream, no routing key,
 * and no per-tier materialisation of its own. Its slot position in the cube's composite accumulator
 * is its index in {@link CompiledDataset#meters()} (declaration order); the cube's tiers live on
 * the dataset ({@link CompiledDataset#tiers()}).
 */
public record CompiledMeter(String meterName, BoundMeter<?, ?> bound) {

  /**
   * The meter's pushdown capability (carried from its {@link BoundMeter}): present when the store
   * can hold it as native numeric columns and aggregate them, empty for a blob (sketch / summary).
   */
  public Optional<PushdownSpec<?, ?>> pushdown() {
    return bound.pushdown().map(spec -> spec);
  }
}
