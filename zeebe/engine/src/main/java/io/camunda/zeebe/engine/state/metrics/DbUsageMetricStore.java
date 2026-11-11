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
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.IntervalType;
import org.jspecify.annotations.Nullable;

/** Stores the active usage metrics bucket as one value of a RocksDB column family. */
public final class DbUsageMetricStore implements UsageMetricStore {

  private final ColumnFamily<DbEnumValue<IntervalType>, PersistedUsageMetrics>
      metricsBucketColumnFamily;
  private final DbEnumValue<IntervalType> metricsBucketKey;

  public DbUsageMetricStore(
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
  public @Nullable PersistedUsageMetrics getActiveBucket() {
    setActiveBucketKeys();
    return metricsBucketColumnFamily.get(metricsBucketKey);
  }

  @Override
  public void incrementCounter(final EventType eventType, final String tenantId) {
    final var bucket = getOrCreateActiveBucket();
    switch (eventType) {
      case RPI -> bucket.recordRPI(tenantId);
      case EDI -> bucket.recordEDI(tenantId);
      default ->
          throw new IllegalArgumentException(
              "Expected a counter event type RPI or EDI, but got " + eventType);
    }
    updateActiveBucket(bucket);
  }

  @Override
  public void addAssignee(final String tenantId, final long assigneeHash) {
    updateActiveBucket(getOrCreateActiveBucket().recordTU(tenantId, assigneeHash));
  }

  @Override
  public void resetActiveBucket(final long fromTime) {
    setActiveBucketKeys();
    metricsBucketColumnFamily.deleteIfExists(metricsBucketKey);
    final var bucket = new PersistedUsageMetrics().setFromTime(fromTime);

    metricsBucketColumnFamily.insert(metricsBucketKey, bucket);
  }

  private void updateActiveBucket(final PersistedUsageMetrics bucket) {
    setActiveBucketKeys();
    metricsBucketColumnFamily.update(metricsBucketKey, bucket);
  }

  private void setActiveBucketKeys() {
    metricsBucketKey.setValue(IntervalType.ACTIVE);
  }

  private PersistedUsageMetrics getOrCreateActiveBucket() {
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
