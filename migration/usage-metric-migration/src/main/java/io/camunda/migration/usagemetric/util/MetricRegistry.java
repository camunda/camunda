/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.usagemetric.util;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.Callable;

public class MetricRegistry {

  private static final String METRIC_PREFIX = "camunda.migration.usagemetric";
  private static final String OPERATE_SUFFIX = METRIC_PREFIX + ".operate";
  private static final String OPERATE_REINDEX_TASK = OPERATE_SUFFIX + ".reindex.time";
  private static final String TASKLIST_PREFIX = METRIC_PREFIX + ".tasklist";
  private static final String TASKLIST_TASK_IMPORTER_FINISHED =
      TASKLIST_PREFIX + ".task.importer.finished";
  private static final String TASKLIST_REINDEX_TASK = TASKLIST_PREFIX + ".reindex.time";
  private final MeterRegistry meterRegistry;

  public MetricRegistry(final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    initializeMetrics();
  }

  private void initializeMetrics() {
    Timer.builder(OPERATE_REINDEX_TASK)
        .description("time taken to send operate reindex task")
        .publishPercentileHistogram()
        .register(meterRegistry);

    Timer.builder(TASKLIST_REINDEX_TASK)
        .description("time taken to send tasklist reindex task")
        .publishPercentileHistogram()
        .register(meterRegistry);

    Timer.builder(TASKLIST_TASK_IMPORTER_FINISHED)
        .description("time taken for tasklist task importer to finish")
        .publishPercentileHistogram()
        .register(meterRegistry);
  }

  public <T> T measureOperateReindexTask(final Callable<T> callable) throws Exception {
    return meterRegistry.timer(OPERATE_REINDEX_TASK).recordCallable(callable);
  }

  public <T> T measureTasklistReindexTask(final Callable<T> callable) throws Exception {
    return meterRegistry.timer(TASKLIST_REINDEX_TASK).recordCallable(callable);
  }

  public <T> T measureTasklistTaskImporterFinished(final Callable<T> callable) throws Exception {
    return meterRegistry.timer(TASKLIST_TASK_IMPORTER_FINISHED).recordCallable(callable);
  }
}
