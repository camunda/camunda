/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.exporter.metrics;

import static io.camunda.zeebe.broker.exporter.metrics.MetricsExporter.TIME_TO_LIVE;

import io.prometheus.client.Histogram;

public class ExecutionLatencyMetrics {

  private static final Histogram PROCESS_INSTANCE_EXECUTION =
      Histogram.build()
          .namespace("zeebe")
          .name("process_instance_execution_time")
          .help("The execution time of processing a complete process instance")
          .labelNames("partition")
          // min 50ms, max 1 minute
          .buckets(0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1, 2, 5, 7, 10, 15, 20, 25, 30, 40, 50, 60)
          .register();

  private static final Histogram JOB_LIFE_TIME =
      Histogram.build()
          .namespace("zeebe")
          .name("job_life_time")
          .help("The life time of an job")
          .labelNames("partition")
          // min 5ms, max 30 seconds
          .buckets(
              0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1, 2, 5, 7, 10, 15, 20, 25, 30)
          .register();

  private static final Histogram JOB_ACTIVATION_TIME =
      Histogram.build()
          .namespace("zeebe")
          .name("job_activation_time")
          .help("The time until an job was activated")
          .labelNames("partition")
          .register();

  public void observeProcessInstanceExecutionTime(
      final int partitionId, final long creationTimeMs, final long completionTimeMs) {
    PROCESS_INSTANCE_EXECUTION
        .labels(Integer.toString(partitionId))
        .observe(latencyInSeconds(creationTimeMs, completionTimeMs));
  }

  public void observeJobLifeTime(
      final int partitionId, final long creationTimeMs, final long completionTimeMs) {
    JOB_LIFE_TIME
        .labels(Integer.toString(partitionId))
        .observe(latencyInSeconds(creationTimeMs, completionTimeMs));
  }

  public void observeJobActivationTime(
      final int partitionId, final long creationTimeMs, final long activationTimeMs) {
    JOB_ACTIVATION_TIME
        .labels(Integer.toString(partitionId))
        .observe(latencyInSeconds(creationTimeMs, activationTimeMs));
  }

  public Histogram getJobLifeTime() {
    return JOB_LIFE_TIME;
  }

  /**
   * Takes start and end time in milliseconds and calculates the difference (latency) in seconds.
   *
   * <p>The time is clamped down based on the maximum time to live in the metrics exporter, i.e. how
   * long we'll track a given job or process instance (to avoid buffering forever). If we already
   * evicted the start time, then we'll just use whatever is TIME_TO_LIVE as the observation, as
   * otherwise we get crazy numbers like billions of seconds or whatever the time since the unix
   * timestamp, which totally skews everything.
   *
   * @param startTimeMs the start time in milliseconds
   * @param endTimeMs the end time in milliseconds
   * @return the latency in seconds
   */
  private static double latencyInSeconds(final long startTimeMs, final long endTimeMs) {
    return Math.min(TIME_TO_LIVE.toMillis(), ((endTimeMs - startTimeMs) / 1000f));
  }
}
