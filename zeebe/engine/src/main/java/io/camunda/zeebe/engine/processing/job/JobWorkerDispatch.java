/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.JobAction;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** The single place that decides whether a job may be served to a job worker at all. */
@NullMarked
public final class JobWorkerDispatch {

  private JobWorkerDispatch() {}

  /**
   * The reason this job is served to no job worker, or {@code null} when any subscribed worker may
   * take it. Every path that hands jobs out — the poll that collects a batch and the push that
   * wakes a stream — asks this, so a job withheld from workers is withheld from all of them.
   *
   * <p>A job stands in for the process a stubbed call activity calls: no worker could run it, and
   * whoever created the process instance completes it. A job carrying a reservation token belongs
   * to an instance whose creator claimed its jobs: that creator drives it, or hands it back with a
   * release.
   *
   * <p>Returns a {@link JobAction} rather than a boolean because both callers count the reason, and
   * {@code null} rather than an {@link java.util.Optional} because the poll asks this once per
   * activatable job.
   */
  public static @Nullable JobAction withheldFromWorkersReason(final JobRecord job) {
    if (job.isCallActivityStub()) {
      return JobAction.SKIPPED_CALL_ACTIVITY_STUB;
    }
    if (job.hasJobReservationToken()) {
      return JobAction.SKIPPED_RESERVED;
    }
    return null;
  }
}
