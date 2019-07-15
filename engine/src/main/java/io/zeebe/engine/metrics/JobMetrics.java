/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.metrics;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

public class JobMetrics {

  private static final Counter JOB_EVENTS =
      Counter.build()
          .namespace("zeebe")
          .name("job_events_total")
          .help("Number of job events")
          .labelNames("action", "partition")
          .register();

  private static final Gauge PENDING_JOBS =
      Gauge.build()
          .namespace("zeebe")
          .name("pending_jobs_total")
          .help("Number of pending jobs")
          .labelNames("partition")
          .register();

  private final String partitionIdLabel;

  public JobMetrics(int partitionId) {
    this.partitionIdLabel = String.valueOf(partitionId);
  }

  private void jobEvent(String action) {
    JOB_EVENTS.labels(action, partitionIdLabel).inc();
  }

  public void jobCreated() {
    jobEvent("created");
    PENDING_JOBS.labels(partitionIdLabel).inc();
  }

  private void jobFinished() {
    PENDING_JOBS.labels(partitionIdLabel).dec();
  }

  public void jobActivated() {
    jobEvent("activated");
  }

  public void jobTimedOut() {
    jobEvent("timed out");
  }

  public void jobCompleted() {
    jobEvent("completed");
    jobFinished();
  }

  public void jobFailed() {
    jobEvent("failed");
  }

  public void jobCanceled() {
    jobEvent("canceled");
    jobFinished();
  }
}
