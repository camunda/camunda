/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.task.util;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.Callable;

public class TaskMigrationMetricRegistry {
  private static final String METRIC_PREFIX = "camunda.migration.tasks";
  private static final String TASKS_UPDATED_METRIC = METRIC_PREFIX + ".migrated";
  private static final String UPDATE_ROUNDS = METRIC_PREFIX + ".rounds";
  private static final String UPDATE_ROUND_DURATION = METRIC_PREFIX + ".round.time";
  private static final String SINGLE_TASK_CONSOLIDATION_DURATION =
      METRIC_PREFIX + ".single.task.time";

  private final MeterRegistry meterRegistry;

  public TaskMigrationMetricRegistry(final MeterRegistry registry) {
    meterRegistry = registry;
    initializeMetrics();
  }

  public void incrementUpdatedTaskCounter(final double value) {
    meterRegistry.counter(TASKS_UPDATED_METRIC).increment(value);
  }

  public void incrementTaskUpdateRoundCounter() {
    meterRegistry.counter(UPDATE_ROUNDS).increment();
  }

  public <T> T measureTaskUpdateRoundDuration(final Callable<T> callback) throws Exception {
    return measureExecutionTime(UPDATE_ROUND_DURATION, callback);
  }

  public <T> T measureTaskConsolidationDuration(final Callable<T> callback) throws Exception {
    return measureExecutionTime(SINGLE_TASK_CONSOLIDATION_DURATION, callback);
  }

  private <T> T measureExecutionTime(final String metricName, final Callable<T> callback)
      throws Exception {
    final Timer timer = meterRegistry.timer(metricName);
    return timer.recordCallable(callback);
  }

  private void initializeMetrics() {
    Counter.builder(TASKS_UPDATED_METRIC)
        .description("total number of tasks updated")
        .register(meterRegistry);

    Counter.builder(UPDATE_ROUNDS)
        .description("total number of task batch update rounds")
        .register(meterRegistry);

    Timer.builder(UPDATE_ROUND_DURATION)
        .description("time taken for migrating a task batch")
        .publishPercentileHistogram()
        .register(meterRegistry);

    Timer.builder(SINGLE_TASK_CONSOLIDATION_DURATION)
        .description("time taken to process a single task definition")
        .publishPercentileHistogram()
        .register(meterRegistry);
  }
}
