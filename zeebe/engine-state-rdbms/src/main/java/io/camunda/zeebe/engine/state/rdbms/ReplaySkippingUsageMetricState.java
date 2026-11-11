/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import io.camunda.zeebe.engine.state.metrics.PersistedUsageMetrics;
import io.camunda.zeebe.engine.state.mutable.MutableUsageMetricState;
import org.jspecify.annotations.NullMarked;

/**
 * Leaves the usage metric rows alone while the replay applies events whose changes the database
 * already contains (see {@link JdbcTransactionContext#isReplayingCommittedChanges()}): the mutators
 * do nothing, and the reads see the rows in the database's later state. Otherwise, every call is
 * delegated.
 */
@NullMarked
final class ReplaySkippingUsageMetricState implements MutableUsageMetricState {

  private final MutableUsageMetricState delegate;
  private final JdbcTransactionContext context;

  ReplaySkippingUsageMetricState(
      final MutableUsageMetricState delegate, final JdbcTransactionContext context) {
    this.delegate = delegate;
    this.context = context;
  }

  private boolean skip() {
    return context.isReplayingCommittedChanges();
  }

  @Override
  public void recordRPIMetric(final String tenantId) {
    if (!skip()) {
      delegate.recordRPIMetric(tenantId);
    }
  }

  @Override
  public void recordEDIMetric(final String tenantId) {
    if (!skip()) {
      delegate.recordEDIMetric(tenantId);
    }
  }

  @Override
  public void recordTUMetric(final String tenantId, final String assignee) {
    if (!skip()) {
      delegate.recordTUMetric(tenantId, assignee);
    }
  }

  @Override
  public void resetActiveBucket(final long fromTime) {
    if (!skip()) {
      delegate.resetActiveBucket(fromTime);
    }
  }

  @Override
  public PersistedUsageMetrics getActiveBucket() {
    return delegate.getActiveBucket();
  }
}
