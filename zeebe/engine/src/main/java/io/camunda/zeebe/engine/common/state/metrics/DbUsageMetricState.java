/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state.metrics;

import static io.camunda.zeebe.util.HashUtil.getStringHashValue;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbEnumValue;
import io.camunda.zeebe.engine.common.state.mutable.MutableUsageMetricState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.IntervalType;

public class DbUsageMetricState implements MutableUsageMetricState {

  private final ColumnFamily<DbEnumValue<IntervalType>, PersistedUsageMetrics>
      metricsBucketColumnFamily;
  private final DbEnumValue<IntervalType> metricsBucketKey;

  public DbUsageMetricState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {

    metricsBucketKey = new DbEnumValue<>(IntervalType.class);
    metricsBucketColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.USAGE_METRICS,
            transactionContext,
            metricsBucketKey,
            new PersistedUsageMetrics());
  }

  @Override
  public PersistedUsageMetrics getActiveBucket() {
    setActiveBucketKeys();
    return metricsBucketColumnFamily.get(metricsBucketKey);
  }

  public void updateActiveBucket(final PersistedUsageMetrics bucket) {
    setActiveBucketKeys();
    metricsBucketColumnFamily.update(metricsBucketKey, bucket);
  }

  private void setActiveBucketKeys() {
    metricsBucketKey.setValue(IntervalType.ACTIVE);
  }

  @Override
  public void recordRPIMetric(final String tenantId) {
    updateActiveBucket(getOrCreateActiveBucket().recordRPI(tenantId));
  }

  @Override
  public void recordEDIMetric(final String tenantId) {
    updateActiveBucket(getOrCreateActiveBucket().recordEDI(tenantId));
  }

  @Override
  public void recordTUMetric(final String tenantId, final String assignee) {
    final long assigneeHashed = getStringHashValue(assignee);
    updateActiveBucket(getOrCreateActiveBucket().recordTU(tenantId, assigneeHashed));
  }

  @Override
  public void resetActiveBucket(final long resetTime) {
    setActiveBucketKeys();
    metricsBucketColumnFamily.deleteIfExists(metricsBucketKey);
    final var bucket = new PersistedUsageMetrics().setFromTime(resetTime);

    metricsBucketColumnFamily.insert(metricsBucketKey, bucket);
  }

  public PersistedUsageMetrics getOrCreateActiveBucket() {
    setActiveBucketKeys();

    final var existingBucket = metricsBucketColumnFamily.get(metricsBucketKey);
    if (existingBucket != null) {
      return existingBucket;
    }

    final var bucket = new PersistedUsageMetrics();
    metricsBucketColumnFamily.insert(metricsBucketKey, bucket);
    return bucket;
  }
}
