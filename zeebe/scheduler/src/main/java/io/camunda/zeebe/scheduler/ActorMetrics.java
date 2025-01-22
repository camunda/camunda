/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class ActorMetrics {

  private static final io.micrometer.core.instrument.MeterRegistry METER_REGISTRY =
      io.micrometer.core.instrument.Metrics.globalRegistry;

  private static final String EXECUTION_COUNT_NAME = "zeebe_actor_task_execution_count";
  private static final ConcurrentHashMap<String, AtomicInteger> JOB_QUEUE_LENGTHS =
      new ConcurrentHashMap<>();
  private static final String JOB_QUEUE_NAME = "zeebe_actor_task_queue_length";

  static {
    io.micrometer.core.instrument.Timer.builder("zeebe_actor_task_execution_latency")
        .description("Execution time of a certain actor task")
        .sla(generateExponentialBuckets(1 / 1_000_000f, 4, 12))
        .register(METER_REGISTRY);

    io.micrometer.core.instrument.Timer.builder("zeebe_actor_job_scheduling_latency")
        .description("Time between scheduling and executing a job")
        .sla(generateExponentialBuckets(0.0001, 4, 10))
        .register(METER_REGISTRY);
  }

  private final boolean enabled;

  public ActorMetrics(final boolean metricsEnabled) {
    enabled = metricsEnabled;
    if (enabled) {
      io.micrometer.core.instrument.Counter.builder(EXECUTION_COUNT_NAME)
          .description("Number of times a certain actor task was executed successfully")
          .register(METER_REGISTRY);
    }
  }

  private static Duration[] generateExponentialBuckets(
      final double start, final int factor, final int count) {
    final Duration[] buckets = new Duration[count];
    for (int i = 0; i < count; i++) {
      buckets[i] = Duration.ofNanos((long) (start * Math.pow(factor, i) * 1_000_000_000));
    }
    return buckets;
  }

  Timer.Sample startExecutionTimer() {
    if (!enabled) {
      return null;
    }
    return Timer.start(METER_REGISTRY);
  }

  void stopExecutionTimer(final Timer.Sample sample, final String name) {
    if (enabled) {
      final Timer executionLatencyTimer =
          METER_REGISTRY.timer("zeebe_actor_task_execution_latency", "actorName", name);
      sample.stop(executionLatencyTimer);
    }
  }

  void countExecution(final String name) {
    if (enabled) {
      METER_REGISTRY.counter(EXECUTION_COUNT_NAME, "actorName", name).increment();
    }
  }

  void updateJobQueueLength(final String actorName, final int length) {
    if (enabled) {
      JOB_QUEUE_LENGTHS
          .computeIfAbsent(
              actorName,
              k -> {
                final AtomicInteger newJobQueueLength = new AtomicInteger();
                io.micrometer.core.instrument.Gauge.builder(
                        JOB_QUEUE_NAME, newJobQueueLength, AtomicInteger::get)
                    .description("The length of the job queue for an actor task")
                    .tag("actorName", actorName)
                    .register(METER_REGISTRY);
                return newJobQueueLength;
              })
          .set(length);
    }
  }

  public void observeJobSchedulingLatency(final long waitTimeNs, final String subscriptionType) {
    if (enabled) {
      final Timer executionLatencyTimer =
          METER_REGISTRY.timer(
              "zeebe_actor_job_scheduling_latency", "subscriptionType", subscriptionType);
      final Duration duration = Duration.ofNanos(waitTimeNs);
      executionLatencyTimer.record(duration);
    }
  }

  public boolean isEnabled() {
    return enabled;
  }
}
