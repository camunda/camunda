/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.exporter.metrics;

import io.prometheus.client.Histogram;

public class ExecutionLatencyMetrics {

  private static final Histogram PROCESS_INSTANCE_EXECUTION =
      Histogram.build()
          .namespace("zeebe")
          .name("process_instance_execution_time")
          .help("The execution time of processing a complete process instance")
          .labelNames("partition")
          .register();

  private static final Histogram JOB_LIFE_TIME =
      Histogram.build()
          .namespace("zeebe")
          .name("job_life_time")
          .help("The life time of an job")
          .labelNames("partition")
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
