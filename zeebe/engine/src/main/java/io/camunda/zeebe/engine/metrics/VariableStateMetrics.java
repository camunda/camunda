/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import static io.camunda.zeebe.engine.metrics.EngineMetricsDoc.VARIABLE_STATE_BYTES;

import io.camunda.zeebe.util.micrometer.StatefulGauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Exposes a gauge that reports the estimated total bytes occupied by live variables in the
 * partition's RocksDB state. The value is set by a periodic engine-side scan; see {@link
 * EngineMetricsDoc#VARIABLE_STATE_BYTES} for details.
 */
public final class VariableStateMetrics {

  private final StatefulGauge variableStateBytes;

  public VariableStateMetrics(final MeterRegistry meterRegistry) {
    variableStateBytes =
        StatefulGauge.builder(VARIABLE_STATE_BYTES.getName())
            .description(VARIABLE_STATE_BYTES.getDescription())
            .register(meterRegistry);
  }

  /**
   * Sets the current estimate of live variable state bytes. Must only be called from the actor
   * thread that owns the partition's RocksDB state, since the value is computed from a full scan of
   * the VARIABLES column family.
   */
  public void updateVariableStateSize(final long bytes) {
    variableStateBytes.set(bytes);
  }
}
