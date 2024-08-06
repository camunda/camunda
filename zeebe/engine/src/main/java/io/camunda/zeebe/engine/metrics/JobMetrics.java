/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.camunda.zeebe.protocol.record.value.JobKind;
import io.prometheus.client.Counter;

public final class JobMetrics {

  private static final Counter JOB_EVENTS =
      Counter.build()
          .namespace("zeebe")
          .name("job_events_total")
          .help("Number of job events")
          .labelNames("action", "partition", "type", "job_kind")
          .register();

  private final String partitionIdLabel;

  public JobMetrics(final int partitionId) {
    partitionIdLabel = String.valueOf(partitionId);
  }

  private void jobEvent(final String action, final String type, final JobKind jobKind) {
    JOB_EVENTS.labels(action, partitionIdLabel, type, jobKind.name()).inc();
  }

  public void jobCreated(final String type, final JobKind jobKind) {
    jobEvent("created", type, jobKind);
  }

  public void jobActivated(final String type, final JobKind jobKind, final int activatedJobs) {
    JOB_EVENTS.labels("activated", partitionIdLabel, type, jobKind.name()).inc(activatedJobs);
  }

  public void jobTimedOut(final String type, final JobKind jobKind) {
    jobEvent("timed out", type, jobKind);
  }

  public void jobCompleted(final String type, final JobKind jobKind) {
    jobEvent("completed", type, jobKind);
  }

  public void jobFailed(final String type, final JobKind jobKind) {
    jobEvent("failed", type, jobKind);
  }

  public void jobCanceled(final String type, final JobKind jobKind) {
    jobEvent("canceled", type, jobKind);
  }

  public void jobErrorThrown(final String type, final JobKind jobKind) {
    jobEvent("error thrown", type, jobKind);
  }

  public void jobNotification(final String type, final JobKind jobKind) {
    jobEvent("workers notified", type, jobKind);
  }

  public void jobPush(final String type, final JobKind jobKind) {
    jobEvent("pushed", type, jobKind);
  }

  /** Clears the metrics counter. You probably only want to use this during testing. */
  static void clear() {
    JOB_EVENTS.clear();
  }
}
