/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.metrics.variable;

import io.camunda.zeebe.engine.metrics.VariableStateMetrics;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Periodically scans the VARIABLES column family and updates the {@link VariableStateMetrics} gauge
 * with the total bytes (key + value lengths) currently occupied.
 *
 * <p>Variable deletions are invisible to exporters (no {@code VARIABLE:DELETED} event), so this
 * gauge must be maintained engine-side where state can be read directly.
 *
 * <p>The scan is registered via {@link
 * io.camunda.zeebe.stream.api.scheduling.SimpleProcessingScheduleService#runAtFixedRate(Duration,
 * Runnable)}, which schedules the runnable on the partition's processing schedule service. This
 * keeps the RocksDB iteration aligned with the schedule service that already owns the column family
 * handle and avoids cross-thread access issues.
 */
public final class VariableStateMetricsScheduler
    implements Runnable, StreamProcessorLifecycleAware {

  private static final Logger LOG = LoggerFactory.getLogger(VariableStateMetricsScheduler.class);

  private final Duration interval;
  private final VariableState variableState;
  private final VariableStateMetrics metrics;

  public VariableStateMetricsScheduler(
      final Duration interval,
      final VariableState variableState,
      final VariableStateMetrics metrics) {
    this.interval = interval;
    this.variableState = variableState;
    this.metrics = metrics;
  }

  @Override
  public void run() {
    try {
      final long bytes = variableState.calculateVariableStateSize();
      metrics.updateVariableStateSize(bytes);
    } catch (final Exception e) {
      // Don't let a transient failure propagate and cancel the recurring schedule.
      LOG.warn("Failed to compute variable state size; skipping this iteration.", e);
    }
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    context.getScheduleService().runAtFixedRate(interval, this);
  }
}
