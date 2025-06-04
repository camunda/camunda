/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.metrics;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbEnumValue;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.mutable.MutableUsageMetricState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.IntervalType;
import java.time.Duration;

public class DbUsageMetricState implements MutableUsageMetricState {

  private final Duration usageMetricsExportInterval;

  private final ColumnFamily<DbEnumValue<IntervalType>, UsageMetricStateValue>
      metricsBucketColumnFamily;
  private final DbEnumValue<IntervalType> metricsBucketKey;

  public DbUsageMetricState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final EngineConfiguration config) {

    usageMetricsExportInterval = config.getUsageMetricsExportInterval();

    metricsBucketKey = new DbEnumValue<>(IntervalType.class);
    metricsBucketColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.USAGE_METRICS,
            transactionContext,
            metricsBucketKey,
            new UsageMetricStateValue());
  }

  @Override
  public UsageMetricStateValue getRollingBucket() {
    setRollingBucketKeys();
    return metricsBucketColumnFamily.get(metricsBucketKey);
  }

  public void updateRollingBucket(final UsageMetricStateValue bucket) {
    setRollingBucketKeys();
    metricsBucketColumnFamily.update(metricsBucketKey, bucket);
  }

  private void setRollingBucketKeys() {
    metricsBucketKey.setValue(IntervalType.ROLLING);
  }

  @Override
  public void recordRPIMetric(final String tenantId) {
    updateRollingBucket(getRollingBucket().recordRPI(tenantId));
  }

  @Override
  public void resetRollingBucket(final long resetTime) {
    setRollingBucketKeys();
    metricsBucketColumnFamily.deleteIfExists(metricsBucketKey);
    final var bucket =
        new UsageMetricStateValue()
            .setFromTime(resetTime)
            .setToTime(resetTime + usageMetricsExportInterval.toMillis());
    metricsBucketColumnFamily.insert(metricsBucketKey, bucket);
  }
}
