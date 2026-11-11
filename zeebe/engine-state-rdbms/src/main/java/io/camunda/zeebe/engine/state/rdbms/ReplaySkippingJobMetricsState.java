/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import io.camunda.zeebe.engine.state.jobmetrics.MetricsConsumer;
import io.camunda.zeebe.engine.state.mutable.MutableJobMetricsState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.JobMetricsExportState;
import java.util.List;
import org.jspecify.annotations.NullMarked;

/**
 * Leaves the job metrics rows alone while the replay applies events whose changes the database
 * already contains (see {@link JdbcTransactionContext#isReplayingCommittedChanges()}): the mutators
 * do nothing, and the reads see the rows in the database's later state. Otherwise, every call is
 * delegated.
 */
@NullMarked
final class ReplaySkippingJobMetricsState implements MutableJobMetricsState {

  private final MutableJobMetricsState delegate;
  private final JdbcTransactionContext context;

  ReplaySkippingJobMetricsState(
      final MutableJobMetricsState delegate, final JdbcTransactionContext context) {
    this.delegate = delegate;
    this.context = context;
  }

  private boolean skip() {
    return context.isReplayingCommittedChanges();
  }

  @Override
  public void incrementMetric(final JobRecord jobRecord, final JobMetricsExportState status) {
    if (!skip()) {
      delegate.incrementMetric(jobRecord, status);
    }
  }

  @Override
  public void cleanUp() {
    if (!skip()) {
      delegate.cleanUp();
    }
  }

  @Override
  public void forEach(final MetricsConsumer consumer) {
    delegate.forEach(consumer);
  }

  @Override
  public List<String> getEncodedStrings() {
    return delegate.getEncodedStrings();
  }

  @Override
  public long getMetadata(final String key) {
    return delegate.getMetadata(key);
  }

  @Override
  public boolean isIncompleteBatch() {
    return delegate.isIncompleteBatch();
  }
}
