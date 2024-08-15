/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

public interface ScheduledTaskMetrics {
  void incrementScheduledTasks();

  void decrementScheduledTasks();

  void observeScheduledTaskExecution(final long delay);

  static ScheduledTaskMetrics noop() {
    return new ScheduledTaskMetrics() {
      @Override
      public void incrementScheduledTasks() {}

      @Override
      public void decrementScheduledTasks() {}

      @Override
      public void observeScheduledTaskExecution(final long delay) {}
    };
  }

  static ScheduledTaskMetrics of(final MeterRegistry registry, final int partition) {
    return new ScheduledTaskMetricsImpl(registry, partition);
  }

  final class ScheduledTaskMetricsImpl implements ScheduledTaskMetrics {
    private final LongAdder scheduledTasksCounter = new LongAdder();
    private final Timer scheduledTaskDelay;

    private ScheduledTaskMetricsImpl(final MeterRegistry registry, final int partition) {
      final var tags = Tags.of("partition", String.valueOf(partition));
      Gauge.builder("zeebe.processing.scheduling.tasks", scheduledTasksCounter, LongAdder::sum)
          .tags(tags)
          .description("The number of currently scheduled tasks")
          .register(registry);
      scheduledTaskDelay =
          Timer.builder("zeebe.processing.scheduling.delay")
              .description("The delay of scheduled tasks")
              .tags(tags)
              .publishPercentileHistogram()
              .register(registry);
    }

    @Override
    public void incrementScheduledTasks() {
      scheduledTasksCounter.increment();
    }

    @Override
    public void decrementScheduledTasks() {
      scheduledTasksCounter.decrement();
    }

    @Override
    public void observeScheduledTaskExecution(final long delay) {
      scheduledTaskDelay.record(delay, TimeUnit.MILLISECONDS);
    }
  }
}
