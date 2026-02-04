/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.jobmetrics;

import io.camunda.zeebe.engine.state.mutable.MutableJobMetricsState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.JobMetricsExportState;
import java.util.List;

/**
 * A no-op implementation of {@link MutableJobMetricsState} that does nothing.
 *
 * <p>This implementation is used when job metrics export is disabled via configuration. All methods
 * are empty or return default values, effectively disabling the collection and export of job
 * metrics without impacting the rest of the system.
 */
public class NoopJobMetricsState implements MutableJobMetricsState {

  /** No-op: does not increment any metric counter. */
  @Override
  public void incrementMetric(final JobRecord jobRecord, final JobMetricsExportState status) {}

  /** No-op: does not perform any cleanup since no state is stored. */
  @Override
  public void cleanUp() {}

  /** No-op: does not iterate over any metrics since none are collected. */
  @Override
  public void forEach(final MetricsConsumer consumer) {}

  /** Returns an empty list since no encoded strings are stored. */
  @Override
  public List<String> getEncodedStrings() {
    return List.of();
  }

  /** Returns 0 for any metadata key since no metadata is stored. */
  @Override
  public long getMetadata(final String key) {
    return 0;
  }

  /** Always returns false since there is no batch to be incomplete. */
  @Override
  public boolean isIncompleteBatch() {
    return false;
  }
}
