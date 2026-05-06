/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scheduled.runtime;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;

/**
 * Per-task metrics for scheduled tasks. All metrics are tagged with the task's {@code name} so
 * operators can distinguish e.g. timer-due-date from job-timeout in dashboards.
 *
 * <p>This deliberately replaces the previous, unlabeled aggregate metrics on the
 * scheduling-infrastructure layer (see {@code ScheduledTaskMetrics} in stream-platform) for the
 * five tasks migrated to the new API.
 */
final class ScheduledTaskMetrics {

  private static final String TAG_TASK = "task";

  private final Counter runs;
  private final Counter errors;
  private final Counter yields;
  private final Counter itemsAppended;
  private final Counter itemsSent;
  private final Timer duration;

  ScheduledTaskMetrics(final MeterRegistry registry, final String taskName) {
    final Tags tags = Tags.of(TAG_TASK, taskName);
    runs =
        Counter.builder("zeebe.engine.scheduled_task.runs")
            .description("Number of completed scheduled-task runs")
            .tags(tags)
            .register(registry);
    errors =
        Counter.builder("zeebe.engine.scheduled_task.errors")
            .description("Number of scheduled-task runs that threw an exception")
            .tags(tags)
            .register(registry);
    yields =
        Counter.builder("zeebe.engine.scheduled_task.yields")
            .description("Number of times a task yielded mid-iteration")
            .tags(tags)
            .register(registry);
    itemsAppended =
        Counter.builder("zeebe.engine.scheduled_task.items_appended")
            .description("Number of follow-up command records appended to the local log")
            .tags(tags)
            .register(registry);
    itemsSent =
        Counter.builder("zeebe.engine.scheduled_task.items_sent")
            .description("Number of inter-partition command sends issued")
            .tags(tags)
            .register(registry);
    duration =
        Timer.builder("zeebe.engine.scheduled_task.duration")
            .description("Wall-clock execution time of a single scheduled-task run")
            .tags(tags)
            .register(registry);
  }

  void recordRun(final Duration runDuration) {
    runs.increment();
    duration.record(runDuration);
  }

  void recordError() {
    errors.increment();
  }

  void recordYield() {
    yields.increment();
  }

  void recordAppend() {
    itemsAppended.increment();
  }

  void recordInterPartitionSend() {
    itemsSent.increment();
  }
}
