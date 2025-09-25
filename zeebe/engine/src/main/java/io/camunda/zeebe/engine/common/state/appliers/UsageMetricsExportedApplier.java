/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state.appliers;

import io.camunda.zeebe.engine.common.state.TypedEventApplier;
import io.camunda.zeebe.engine.common.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.common.state.mutable.MutableUsageMetricState;
import io.camunda.zeebe.protocol.impl.record.value.metrics.UsageMetricRecord;
import io.camunda.zeebe.protocol.record.intent.UsageMetricIntent;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UsageMetricsExportedApplier
    implements TypedEventApplier<UsageMetricIntent, UsageMetricRecord> {

  private static final Logger LOG = LoggerFactory.getLogger(UsageMetricsExportedApplier.class);

  private final MutableUsageMetricState usageMetricState;

  public UsageMetricsExportedApplier(final MutableProcessingState processingState) {
    usageMetricState = processingState.getUsageMetricState();
  }

  @Override
  public void applyState(final long key, final UsageMetricRecord record) {
    if (record.getEventType() == EventType.NONE) {
      LOG.debug("Reset active bucket {}", record.getResetTime());
      usageMetricState.resetActiveBucket(record.getResetTime());
    }
  }
}
