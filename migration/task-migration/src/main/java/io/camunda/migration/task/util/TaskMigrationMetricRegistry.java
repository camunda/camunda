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
  private static final String PROCESSES_MIGRATED_METRIC = METRIC_PREFIX + ".migrated";
  private static final String MIGRATION_ROUNDS = METRIC_PREFIX + ".rounds";
  private static final String MIGRATION_ROUND_DURATION = METRIC_PREFIX + ".round.time";
  private static final String MIGRATION_SINGLE_PROCESS_DURATION =
      METRIC_PREFIX + ".single.process.time";

  private final MeterRegistry meterRegistry;

  public TaskMigrationMetricRegistry(final MeterRegistry registry) {
    meterRegistry = registry;
    initializeMetrics();
  }

  public void incrementMigratedProcessCounter(final double value) {
    meterRegistry.counter(PROCESSES_MIGRATED_METRIC).increment(value);
  }

  public void incrementMigrationRoundCounter() {
    meterRegistry.counter(MIGRATION_ROUNDS).increment();
  }

  public <T> T measureMigrationRoundDuration(final Callable<T> callback) throws Exception {
    return measureExecutionTime(MIGRATION_ROUND_DURATION, callback);
  }

  public <T> T measureMigrationParseDuration(final Callable<T> callback) throws Exception {
    return measureExecutionTime(MIGRATION_SINGLE_PROCESS_DURATION, callback);
  }

  private <T> T measureExecutionTime(final String metricName, final Callable<T> callback)
      throws Exception {
    final Timer timer = meterRegistry.timer(metricName);
    return timer.recordCallable(callback);
  }

  private void initializeMetrics() {
    Counter.builder(PROCESSES_MIGRATED_METRIC)
        .description("total number of processes migrated")
        .register(meterRegistry);

    Counter.builder(MIGRATION_ROUNDS)
        .description("total number of migration rounds")
        .register(meterRegistry);

    Timer.builder(MIGRATION_ROUND_DURATION)
        .description("time taken for migrating a batch")
        .publishPercentileHistogram()
        .register(meterRegistry);

    Timer.builder(MIGRATION_SINGLE_PROCESS_DURATION)
        .description("time taken to process a single process definition")
        .publishPercentileHistogram()
        .register(meterRegistry);
  }
}
