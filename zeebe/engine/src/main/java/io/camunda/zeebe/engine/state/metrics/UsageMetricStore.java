/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.metrics;

import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import org.jspecify.annotations.Nullable;

/**
 * Storage of the active usage metrics bucket, without any domain decision: the counters of the
 * created process instances and evaluated decisions per tenant, and the tenant's unique task
 * assignees, since the last reset.
 */
public interface UsageMetricStore {

  /**
   * Returns the active bucket, or {@code null} if no metric was recorded and the bucket was never
   * reset. The returned bucket may be backed by a buffer that is reused by the next read; copy it
   * to keep it.
   */
  @Nullable PersistedUsageMetrics getActiveBucket();

  /**
   * Increments the counter of the tenant by one, creating the bucket and the counter if needed.
   *
   * @param eventType {@link EventType#RPI} or {@link EventType#EDI}
   */
  void incrementCounter(EventType eventType, String tenantId);

  /** Adds the assignee to the tenant's unique task users, creating the bucket if needed. */
  void addAssignee(String tenantId, long assigneeHash);

  /** Replaces the active bucket with an empty one that starts at the given time. */
  void resetActiveBucket(long fromTime);
}
