/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableJobMetricsState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.jobmetrics.JobMetricsBatchRecord;
import io.camunda.zeebe.protocol.record.intent.JobMetricsBatchIntent;

public class JobMetricsBatchExportedApplier
    implements TypedEventApplier<JobMetricsBatchIntent, JobMetricsBatchRecord> {

  private final MutableJobMetricsState jobMetricsState;

  public JobMetricsBatchExportedApplier(final MutableProcessingState state) {
    jobMetricsState = state.getJobMetricsState();
  }

  @Override
  public void applyState(final long key, final JobMetricsBatchRecord value) {
    jobMetricsState.resetAllMetrics();
  }
}
