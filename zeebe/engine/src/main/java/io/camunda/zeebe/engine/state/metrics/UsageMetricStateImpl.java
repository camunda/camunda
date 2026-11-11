/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.metrics;

import static io.camunda.zeebe.engine.state.metrics.HashUtil.getStringHashValue;

import io.camunda.zeebe.engine.state.mutable.MutableUsageMetricState;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import org.jspecify.annotations.Nullable;

/**
 * The usage metric state over a {@link UsageMetricStore}: task assignees are recorded as hashes, so
 * the bucket never holds user names.
 */
public final class UsageMetricStateImpl implements MutableUsageMetricState {

  private final UsageMetricStore store;

  public UsageMetricStateImpl(final UsageMetricStore store) {
    this.store = store;
  }

  @Override
  public @Nullable PersistedUsageMetrics getActiveBucket() {
    return store.getActiveBucket();
  }

  @Override
  public void recordRPIMetric(final String tenantId) {
    store.incrementCounter(EventType.RPI, tenantId);
  }

  @Override
  public void recordEDIMetric(final String tenantId) {
    store.incrementCounter(EventType.EDI, tenantId);
  }

  @Override
  public void recordTUMetric(final String tenantId, final String assignee) {
    store.addAssignee(tenantId, getStringHashValue(assignee));
  }

  @Override
  public void resetActiveBucket(final long resetTime) {
    store.resetActiveBucket(resetTime);
  }
}
