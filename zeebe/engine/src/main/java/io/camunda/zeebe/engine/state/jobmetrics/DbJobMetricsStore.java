/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.jobmetrics;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.value.JobMetricsExportState;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Stores the job metrics in RocksDB. The metrics are keyed by integers, which a dictionary maps to
 * the job type, tenant and worker strings to keep the keys small. The dictionary and the in-memory
 * caches of the dictionary and of the metrics are details of this store; its callers only see
 * strings.
 *
 * <p>Uses three column families:
 *
 * <ul>
 *   <li>JOB_METRICS: stores aggregated job metrics per tenant/type/worker combination
 *   <li>JOB_METRICS_STRING_ENCODING: string-to-integer dictionary for space optimization
 *   <li>JOB_METRICS_META: stores metadata and statistics
 * </ul>
 */
public final class DbJobMetricsStore implements JobMetricsStore {

  /** Metadata key: auto-increment counter for STRING_ENCODING_CF */
  public static final String META_COUNTER = "__counter__";

  // Column family for metrics: MetricsKey -> MetricsValue
  private final ColumnFamily<MetricsKey, MetricsValue> metricsColumnFamily;
  private final MetricsKey metricsKey;
  private final MetricsValue metricsValue;

  // Column family for string encoding: DbString -> DbInt
  private final ColumnFamily<DbString, DbInt> stringEncodingColumnFamily;
  private final DbString stringEncodingKey;
  private final DbInt stringEncodingValue;

  // Column family for metadata: DbString -> DbLong
  private final ColumnFamily<DbString, DbLong> metadataColumnFamily;
  private final DbString metadataKey;
  private final DbLong metadataValue;

  // In-memory cache for string encoding (for fast lookups)
  private final Map<String, Integer> stringEncodingCache;

  // In-memory cache for metrics (key: composite of indices, value: metrics)
  private final Map<MetricsCacheKey, MetricsValue> metricsCache;

  public DbJobMetricsStore(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {

    // Initialize metrics column family
    metricsKey = new MetricsKey();
    metricsValue = new MetricsValue();
    metricsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOB_METRICS, transactionContext, metricsKey, metricsValue);

    // Initialize string encoding column family
    stringEncodingKey = new DbString();
    stringEncodingValue = new DbInt();
    stringEncodingColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOB_METRICS_STRING_ENCODING,
            transactionContext,
            stringEncodingKey,
            stringEncodingValue);

    // Initialize meta column family
    metadataKey = new DbString();
    metadataValue = new DbLong();
    metadataColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOB_METRICS_META, transactionContext, metadataKey, metadataValue);

    // Initialize string encoding cache
    stringEncodingCache = new HashMap<>();

    // Initialize metrics cache
    metricsCache = new HashMap<>();

    // Populate caches from existing data
    populateStringEncodingCache();
    populateMetricsCache();
  }

  private void populateStringEncodingCache() {
    stringEncodingColumnFamily.forEach(
        (key, value) -> stringEncodingCache.put(key.toString(), value.getValue()));
  }

  private void populateMetricsCache() {
    metricsColumnFamily.forEach(
        (key, value) ->
            metricsCache.put(
                new MetricsCacheKey(
                    key.getJobTypeIndex(), key.getTenantIdIndex(), key.getWorkerNameIndex()),
                value.copy()));
  }

  @Override
  public int countKeys() {
    return metricsCache.size();
  }

  @Override
  public boolean containsKey(final String jobType, final String tenantId, final String workerName) {
    return metricsCache.containsKey(
        new MetricsCacheKey(
            Optional.ofNullable(stringEncodingCache.get(jobType)).orElse(-1),
            Optional.ofNullable(stringEncodingCache.get(tenantId)).orElse(-1),
            Optional.ofNullable(stringEncodingCache.get(workerName)).orElse(-1)));
  }

  @Override
  public void forEach(final MetricsVisitor visitor) {
    final Map<Integer, String> strings = new HashMap<>();
    stringEncodingCache.forEach((string, index) -> strings.put(index, string));
    metricsCache.forEach(
        (metricsCacheKey, metricsValue1) ->
            visitor.accept(
                strings.get(metricsCacheKey.jobTypeIdx()),
                strings.get(metricsCacheKey.tenantIdx()),
                strings.get(metricsCacheKey.workerIdx()),
                metricsValue1.getMetrics()));
  }

  @Override
  public long getMetadata(final String key) {
    metadataKey.wrapString(key);
    final DbLong value = metadataColumnFamily.get(metadataKey);
    return value != null ? value.getValue() : 0L;
  }

  @Override
  public void setMetadata(final String key, final long value) {
    metadataKey.wrapString(key);
    metadataValue.wrapLong(value);
    metadataColumnFamily.upsert(metadataKey, metadataValue);
  }

  @Override
  public void increment(
      final String jobType,
      final String tenantId,
      final String workerName,
      final JobMetricsExportState status,
      final long timestamp) {
    final int jobTypeIdx = getOrCreateStringIndex(jobType);
    final int tenantIdx = getOrCreateStringIndex(tenantId);
    final int workerIdx = getOrCreateStringIndex(workerName);

    // Build the key
    metricsKey.set(jobTypeIdx, tenantIdx, workerIdx);
    final MetricsCacheKey cacheKey = new MetricsCacheKey(jobTypeIdx, tenantIdx, workerIdx);

    // Check if key exists in cache
    final MetricsValue cachedValue = metricsCache.get(cacheKey);
    final boolean isNewKey = cachedValue == null;

    if (isNewKey) {
      // Create new metrics value
      metricsValue.reset();
    } else {
      // Copy cached value to our working instance
      copyMetricsValue(cachedValue, metricsValue);
    }

    // Increment the metric for the specified status
    metricsValue.incrementMetric(status, timestamp);
    // Write back to column family
    metricsColumnFamily.upsert(metricsKey, metricsValue);
    // Update the cache
    metricsCache.put(cacheKey, metricsValue.copy());
  }

  @Override
  public void deleteMetrics() {
    // Delete all keys in metrics column family
    metricsColumnFamily.forEachKey(metricsColumnFamily::deleteExisting);

    // Delete all keys in string encoding column family
    stringEncodingColumnFamily.forEachKey(stringEncodingColumnFamily::deleteExisting);

    // Clear the caches
    stringEncodingCache.clear();
    metricsCache.clear();

    // The dictionary starts again
    setMetadata(META_COUNTER, 0L);
  }

  /**
   * Gets or creates an integer index for a string.
   *
   * @param string the string to encode
   * @return the integer index for the string
   */
  private int getOrCreateStringIndex(final String string) {
    // Check cache first
    final Integer cachedIndex = stringEncodingCache.get(string);
    if (cachedIndex != null) {
      return cachedIndex;
    }

    // Check database
    stringEncodingKey.wrapString(string);
    final DbInt existingValue = stringEncodingColumnFamily.get(stringEncodingKey);
    if (existingValue != null) {
      final int index = existingValue.getValue();
      stringEncodingCache.put(string, index);
      return index;
    }

    // Create new index
    final int newIndex = (int) getNextCounter();
    stringEncodingValue.wrapInt(newIndex);
    stringEncodingColumnFamily.insert(stringEncodingKey, stringEncodingValue);
    // Add to cache
    stringEncodingCache.put(string, newIndex);

    return newIndex;
  }

  /**
   * Gets the next counter value and increments it.
   *
   * @return the next available integer for string encoding
   */
  private long getNextCounter() {
    final long currentCounter = getMetadata(META_COUNTER);
    setMetadata(META_COUNTER, currentCounter + 1);
    return currentCounter;
  }

  /**
   * Copies metrics from source to destination.
   *
   * @param source the source metrics value
   * @param destination the destination metrics value
   */
  private void copyMetricsValue(final MetricsValue source, final MetricsValue destination) {
    final StatusMetrics[] sourceMetrics = source.getMetrics();
    final StatusMetrics[] destMetrics = destination.getMetrics();
    for (int i = 0; i < sourceMetrics.length; i++) {
      destMetrics[i].setCount(sourceMetrics[i].getCount());
      destMetrics[i].setLastUpdatedAt(sourceMetrics[i].getLastUpdatedAt());
    }
  }

  /** Immutable key for the metrics cache. */
  private record MetricsCacheKey(int jobTypeIdx, int tenantIdx, int workerIdx) {}
}
