/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import io.prometheus.client.Histogram;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class ActorMetrics {

  private static final io.micrometer.core.instrument.MeterRegistry meterRegistry =
      io.micrometer.core.instrument.Metrics.globalRegistry;

  //  private static final Duration[] EXECUTION_LATENCY_BUCKETS =
  //      Stream.of(1, 1000, 2000, 5000, 10000, 30000, 60000, 120000, 180000, 300000, 600000)
  //          .map(Duration::ofMillis)
  //          .toArray(Duration[]::new);

  private static final Histogram EXECUTION_LATENCY =
      Histogram.build()
          // goes up to ~26 seconds while being more fine-grained in the <5ms range.
          .exponentialBuckets(0.0001, 4, 10)
          .namespace("zeebe")
          .name("actor_task_execution_latency")
          .help("Execution time of a certain actor task")
          .labelNames("actorName")
          .register();

  private static final Histogram SCHEDULING_LATENCY =
      Histogram.build()
          .exponentialBuckets(1 / 1_000_000f, 4, 12)
          .namespace("zeebe")
          .name("actor_job_scheduling_latency")
          .help("Time between scheduling and executing a job")
          .labelNames("subscriptionType")
          .register();

  private static final String EXECUTION_COUNT_NAME = "zeebe_actor_task_execution_count";

  private static final ConcurrentHashMap<String, AtomicInteger> JOB_QUEUE_LENGTHS =
      new ConcurrentHashMap<>();
  private static final String JOB_QUEUE_NAME = "zeebe_actor_task_queue_length";

  private final boolean enabled;

  public ActorMetrics(final boolean metricsEnabled) {
    enabled = metricsEnabled;
    if (enabled) {
      io.micrometer.core.instrument.Counter.builder(EXECUTION_COUNT_NAME)
          .description("Number of times a certain actor task was executed successfully")
          .register(meterRegistry);
    }
  }

  Histogram.Timer startExecutionTimer(final String name) {
    if (!enabled) {
      return null;
    }
    return EXECUTION_LATENCY.labels(name).startTimer();
  }

  void countExecution(final String name) {
    if (enabled) {
      meterRegistry.counter(EXECUTION_COUNT_NAME, "actorName", name).increment();
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
                    .register(meterRegistry);
                return newJobQueueLength;
              })
          .set(length);
    }
  }

  public void observeJobSchedulingLatency(final long waitTimeNs, final String subscriptionType) {
    if (enabled) {
      SCHEDULING_LATENCY.labels(subscriptionType).observe(waitTimeNs / 1_000_000_000f);
    }
  }

  public boolean isEnabled() {
    return enabled;
  }
}
