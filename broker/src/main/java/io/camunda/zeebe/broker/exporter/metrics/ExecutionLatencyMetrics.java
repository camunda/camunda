/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.exporter.metrics;

import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;

public class ExecutionLatencyMetrics {

  private static final Histogram PROCESS_INSTANCE_EXECUTION =
      Histogram.build()
          .namespace("zeebe")
          .name("process_instance_execution_time")
          .help("The execution time of processing a complete process instance")
          .labelNames("partition")
          .buckets(0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 10.0, 15.0, 30.0, 45.0, 60.0)
          .register();
  private static final Histogram JOB_LIFE_TIME =
      Histogram.build()
          .namespace("zeebe")
          .name("job_life_time")
          .help("The life time of an job")
          .labelNames("partition")
          .buckets(0.10f, 0.2, 0.4, 0.8, 1.6, 3.2, 6.4, 12.8, 25.6, 51.2)
          .buckets(0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 10.0, 15.0, 30.0, 45.0)
          .register();
  private static final Histogram JOB_ACTIVATION_TIME =
      Histogram.build()
          .namespace("zeebe")
          .name("job_activation_time")
          .help("The time until an job was activated")
          .labelNames("partition")
          .buckets(0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 10.0, 15.0, 30.0)
          .register();
  private static final Gauge CURRENT_CACHED_INSTANCE =
      Gauge.build()
          .namespace("zeebe")
          .name("execution_latency_current_cached_instances")
          .help(
              "The current cached instances for counting their execution latency. If only short-lived instances are handled this can be seen or observed as the current active instance count.")
          .labelNames("partition", "type")
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

  public void setCurrentJobsCount(final int partitionId, final int count) {
    CURRENT_CACHED_INSTANCE.labels(Integer.toString(partitionId), "jobs").set(count);
  }

  public void setCurrentProcessInstanceCount(final int partitionId, final int count) {
    CURRENT_CACHED_INSTANCE.labels(Integer.toString(partitionId), "processInstances").set(count);
  }

  public Histogram getJobLifeTime() {
    return JOB_LIFE_TIME;
  }

  /**
   * Takes start and end time in milliseconds and calculates the difference (latency) in seconds.
   *
   * @param startTimeMs the start time in milliseconds
   * @param endTimeMs the end time in milliseconds
   * @return the latency in seconds
   */
  private static double latencyInSeconds(final long startTimeMs, final long endTimeMs) {
    return ((endTimeMs - startTimeMs) / 1000f);
  }
}
