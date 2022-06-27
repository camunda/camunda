/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.scheduler;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;

final class ActorMetrics {

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

  private static final Counter EXECUTION_COUNT =
      Counter.build()
          .namespace("zeebe")
          .name("actor_task_execution_count")
          .help("Number of times a certain actor task was executed successfully")
          .labelNames("actorName")
          .register();

  private static final Gauge JOB_QUEUE_LENGTH =
      Gauge.build()
          .namespace("zeebe")
          .name("actor_task_queue_length")
          .help("The length of the job queue for an actor task")
          .labelNames("actorName")
          .register();
  private final boolean enabled;

  public ActorMetrics(final boolean metricsEnabled) {
    enabled = metricsEnabled;
  }

  Histogram.Timer startExecutionTimer(final String name) {
    if (!enabled) {
      return null;
    }
    return EXECUTION_LATENCY.labels(name).startTimer();
  }

  void countExecution(final String name) {
    if (enabled) {
      EXECUTION_COUNT.labels(name).inc();
    }
  }

  void updateJobQueueLength(final String actorName, final int length) {
    if (enabled) {
      JOB_QUEUE_LENGTH.labels(actorName).set(length);
    }
  }

  public void observeJobSchedulingLatency(final long waitTimeNs, final String subscriptionType) {
    if (enabled) {
      SCHEDULING_LATENCY.labels(subscriptionType).observe(waitTimeNs / 1_000_000_000f);
    }
  }
}
