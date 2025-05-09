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
import io.camunda.zeebe.engine.state.mutable.MutableUsageMetricState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.IntervalType;
import java.time.Duration;
import java.time.InstantSource;

public class DbUsageMetricState implements MutableUsageMetricState {

  private final Duration exportInterval;

  private final ColumnFamily<DbEnumValue<IntervalType>, PersistedUsageMetrics>
      metricsBucketColumnFamily;
  private final DbEnumValue<IntervalType> metricsBucketKey;
  private final InstantSource clock;

  public DbUsageMetricState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final InstantSource clock,
      final Duration exportInterval) {

    this.clock = clock;
    this.exportInterval = exportInterval;

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
  public void deleteActiveBucket() {
    setActiveBucketKeys();
    metricsBucketColumnFamily.deleteExisting(metricsBucketKey);
  }

  private PersistedUsageMetrics getOrCreateActiveBucket() {
    var bucket = getActiveBucket();
    if (bucket == null) {
      final long millis = clock.millis();
      bucket =
          new PersistedUsageMetrics()
              .setFromTime(millis)
              .setToTime(millis + exportInterval.toMillis());
      metricsBucketColumnFamily.insert(metricsBucketKey, bucket);
    }
    return bucket;
  }
}
