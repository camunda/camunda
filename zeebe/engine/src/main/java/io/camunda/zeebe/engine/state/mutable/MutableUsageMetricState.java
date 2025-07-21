/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.UsageMetricState;

public interface MutableUsageMetricState extends UsageMetricState {

  void recordRPIMetric(final String tenantId);

  void recordEDIMetric(final String tenantId);

  void recordTUMetric(final String tenantId, final String assignee);

  void resetActiveBucket(final long fromTime);

  void updateActiveBucketTime(long resetTime);
}
