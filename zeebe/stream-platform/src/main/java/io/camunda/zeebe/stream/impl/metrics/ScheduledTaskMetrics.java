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
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

public interface ScheduledTaskMetrics {

  void incrementScheduledTasks();

  void decrementScheduledTasks();

  void observeScheduledTaskDelay(final long delay);

  void observeScheduledTaskDuration(long l);

  static ScheduledTaskMetrics noop() {
    return new ScheduledTaskMetrics() {
      @Override
      public void incrementScheduledTasks() {}

      @Override
      public void decrementScheduledTasks() {}

      @Override
      public void observeScheduledTaskDelay(final long delay) {}

      @Override
      public void observeScheduledTaskDuration(final long duration) {}
    };
  }

  static ScheduledTaskMetrics of(final MeterRegistry registry) {
    return new ScheduledTaskMetricsImpl(registry);
  }

  final class ScheduledTaskMetricsImpl implements ScheduledTaskMetrics {
    private final LongAdder scheduledTasksCounter = new LongAdder();
    private final Timer scheduledTaskDelay;
    private final Timer scheduledTaskDuration;

    private ScheduledTaskMetricsImpl(final MeterRegistry registry) {
      Gauge.builder("zeebe.processing.scheduling.tasks", scheduledTasksCounter, LongAdder::sum)
          .description("The number of currently scheduled tasks")
          .register(registry);
      scheduledTaskDelay =
          Timer.builder("zeebe.processing.scheduling.delay")
              .description("The delay of scheduled tasks")
              .publishPercentileHistogram()
              .register(registry);
      scheduledTaskDuration =
          Timer.builder("zeebe.processing.scheduling.duration")
              .description("The duration of scheduled tasks")
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
    public void observeScheduledTaskDelay(final long delay) {
      scheduledTaskDelay.record(delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public void observeScheduledTaskDuration(final long duration) {
      scheduledTaskDuration.record(duration, TimeUnit.MILLISECONDS);
    }
  }
}
