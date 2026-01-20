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
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.mutable.MutableJobMetricsState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.JobMetricsExportState;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * RocksDB-based implementation of job metrics state management.
 *
 * <p>Uses three column families:
 *
 * <ul>
 *   <li>JOB_METRICS: stores aggregated job metrics per tenant/type/worker combination
 *   <li>JOB_METRICS_STRING_ENCODING: string-to-integer dictionary for space optimization
 *   <li>JOB_METRICS_META: stores metadata and statistics
 * </ul>
 */
public class DbJobMetricsState implements MutableJobMetricsState {

  /** Metadata key: auto-increment counter for STRING_ENCODING_CF */
  public static final String META_COUNTER = "__counter__";

  /** Metadata key: flag indicating if size limit was exceeded (1 = true, 0 = false) */
  public static final String META_SIZE_LIMITS_EXCEEDED = "__size_limits_exceeded__";

  public static final String META_BATCH_STARTING_TIME = "__batch_starting_time__";
  public static final String META_BATCH_ENDING_TIME = "__batch_ending_time__";

  public static final long ZERO = 0L;

  public final InstantSource clock;
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
  private final int maxJobTypeLength;
  private final int maxTenantIdLength;
  private final int maxWorkerNameLength;
  private final int maxUniqueJobMetricsKeys;

  public DbJobMetricsState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final InstantSource clock,
      final EngineConfiguration engineConfiguration) {
    this.clock = clock;

    maxJobTypeLength = engineConfiguration.getMaxJobTypeLength();
    maxTenantIdLength = engineConfiguration.getMaxTenantIdLength();
    maxWorkerNameLength = engineConfiguration.getMaxWorkerNameLength();
    maxUniqueJobMetricsKeys = engineConfiguration.getMaxUniqueJobMetricsKeys();

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
  public void forEach(final MetricsConsumer consumer) {
    metricsCache.forEach(
        (metricsCacheKey, metricsValue1) ->
            consumer.accept(
                metricsCacheKey.jobTypeIdx(),
                metricsCacheKey.tenantIdx(),
                metricsCacheKey.workerIdx(),
                metricsValue1.getMetrics()));
  }

  @Override
  public List<String> getEncodedStrings() {
    // Use the in-memory cache and sort by index value
    return stringEncodingCache.entrySet().stream()
        .sorted(Map.Entry.comparingByValue())
        .map(Map.Entry::getKey)
        .toList();
  }

  @Override
  public long getMetadata(final String key) {
    metadataKey.wrapString(key);
    final DbLong value = metadataColumnFamily.get(metadataKey);
    return value != null ? value.getValue() : 0L;
  }

  @Override
  public boolean isIncompleteBatch() {
    return getMetadata(META_SIZE_LIMITS_EXCEEDED) == 1L;
  }

  /**
   * Increments the job metrics for the given combination of job type, tenant ID, worker name.
   *
   * <p>IMPORTANT: If this creates a NEW key in METRICS column family, increments
   * __job_metrics_number__
   *
   * @param status the job status to increment
   */
  @Override
  public void incrementMetric(final JobRecord jobRecord, final JobMetricsExportState status) {
    final var jobType = jobRecord.getType();
    final var tenantId = jobRecord.getTenantId();
    final var workerName = jobRecord.getWorker();

    if (sizeLimitsExceeded(jobType, tenantId, workerName)) {
      // Would exceed threshold, mark as truncated and skip
      setMetadataValue(META_SIZE_LIMITS_EXCEEDED, 1L);
      return;
    }

    // Safe to proceed, increment the metric
    incrementVerifiedMetric(jobType, tenantId, workerName, status);
  }

  @Override
  public void cleanUp() {
    // Delete all keys in metrics column family
    metricsColumnFamily.forEach((mk, mv) -> metricsColumnFamily.deleteExisting(mk));

    // Delete all keys in string encoding column family
    stringEncodingColumnFamily.forEach(
        (dbString, dbInt) -> stringEncodingColumnFamily.deleteExisting(dbString));

    // Clear the caches
    stringEncodingCache.clear();
    metricsCache.clear();

    // Reset all metadata values to 0
    setMetadataValue(META_SIZE_LIMITS_EXCEEDED, 0L);
    setMetadataValue(META_COUNTER, 0L);
    setMetadataValue(META_BATCH_ENDING_TIME, -1L);
    setMetadataValue(META_BATCH_STARTING_TIME, -1L);
  }

  private boolean sizeLimitsExceeded(
      final String jobType, final String tenantId, final String workerName) {
    return jobType.length() > maxJobTypeLength
        || tenantId.length() > maxTenantIdLength
        || workerName.length() > maxWorkerNameLength
        || uniqueKeysLimitExceeded(jobType, tenantId, workerName);
  }

  private boolean uniqueKeysLimitExceeded(
      final String jobType, final String tenantId, final String workerName) {
    return metricsCache.size() + 1 > maxUniqueJobMetricsKeys
        && !metricsCache.containsKey(
            new MetricsCacheKey(
                Optional.ofNullable(stringEncodingCache.get(jobType)).orElse(-1),
                Optional.ofNullable(stringEncodingCache.get(tenantId)).orElse(-1),
                Optional.ofNullable(stringEncodingCache.get(workerName)).orElse(-1)));
  }

  private void incrementVerifiedMetric(
      final String jobType,
      final String tenantId,
      final String workerName,
      final JobMetricsExportState status) {
    // Safe to proceed with the increment
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

    // Get current epoch millis
    final long currentEpochMillis = clock.millis();

    // Increment the metric for the specified status
    metricsValue.incrementMetric(status, currentEpochMillis);
    // Write back to column family
    metricsColumnFamily.upsert(metricsKey, metricsValue);
    // Update the cache
    metricsCache.put(cacheKey, metricsValue.copy());

    if (metricsCache.size() == 1) {
      // First entry, set batch starting time
      setMetadataValue(META_BATCH_STARTING_TIME, currentEpochMillis);
    }
    setMetadataValue(META_BATCH_ENDING_TIME, currentEpochMillis);
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
    setMetadataValue(META_COUNTER, currentCounter + 1);
    return currentCounter;
  }

  /**
   * Sets a metadata value.
   *
   * @param key the metadata key
   * @param value the value to set
   */
  private void setMetadataValue(final String key, final long value) {
    metadataKey.wrapString(key);
    metadataValue.wrapLong(value);
    metadataColumnFamily.upsert(metadataKey, metadataValue);
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
