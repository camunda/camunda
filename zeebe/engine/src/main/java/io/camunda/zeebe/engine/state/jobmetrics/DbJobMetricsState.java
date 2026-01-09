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
import io.camunda.zeebe.engine.state.mutable.MutableJobMetricsState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.nio.charset.StandardCharsets;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

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

  /** Maximum batch record size in bytes (4 MiB) */
  public static final long MAX_BATCH_SIZE = 4 * 1024 * 1024;

  /** Metadata key: sum of all bytes string in STRING_ENCODING_CF */
  public static final String META_TOTAL_ENCODED_STRINGS_BYTE_SIZE =
      "__total_encoded_strings_byte_size__";

  /** Metadata key: number of unique keys in METRICS_CF */
  public static final String META_JOB_METRICS_NB = "__job_metrics_nb__";

  /** Metadata key: computed total size */
  public static final String META_BATCH_RECORD_TOTAL_SIZE = "__batch_record_total_size__";

  /** Metadata key: auto-increment counter for STRING_ENCODING_CF */
  public static final String META_COUNTER = "__counter__";

  /** Metadata key: flag indicating if size limit was exceeded (1 = true, 0 = false) */
  public static final String META_TOTAL_SIZE_EXCEEDED = "__total_size_exceeded__";

  public static final String META_BATCH_STARTING_TIME = "__batch_starting_time__";

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
  private final ConcurrentHashMap<String, Integer> stringEncodingCache;

  // In-memory cache for metrics (key: composite of indices, value: metrics)
  private final ConcurrentHashMap<MetricsCacheKey, MetricsValue> metricsCache;

  public DbJobMetricsState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final InstantSource clock) {
    this.clock = clock;

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
    stringEncodingCache = new ConcurrentHashMap<>();

    // Initialize metrics cache
    metricsCache = new ConcurrentHashMap<>();

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
        (key, value) ->
            consumer.accept(
                key.jobTypeIdx(), key.tenantIdx(), key.workerIdx(), value.copyMetrics()));
  }

  @Override
  public List<String> getEncodedStrings() {
    // Collect all entries sorted by their integer value
    final Map<Integer, String> sortedMap = new TreeMap<>();
    stringEncodingColumnFamily.forEach(
        (key, value) -> sortedMap.put(value.getValue(), key.toString()));

    return new ArrayList<>(sortedMap.values());
  }

  @Override
  public long getMetadata(final String key) {
    metadataKey.wrapString(key);
    final DbLong value = metadataColumnFamily.get(metadataKey);
    return value != null ? value.getValue() : 0L;
  }

  @Override
  public boolean isIncompleteBatch() {
    return getMetadata(META_TOTAL_SIZE_EXCEEDED) == 1L;
  }

  @Override
  public void incrementMetric(
      final String jobType,
      final String tenantId,
      final String workerName,
      final JobMetricsState status) {

    // Check if already truncated, skip if so
    if (isIncompleteBatch()) {
      return;
    }

    // Calculate size impact BEFORE making any changes
    final long sizeImpact = calculateSizeImpactInBytes(jobType, tenantId, workerName);
    final long currentSize = getMetadata(META_BATCH_RECORD_TOTAL_SIZE);

    if (currentSize + sizeImpact > MAX_BATCH_SIZE) {
      // Would exceed threshold, mark as truncated and skip
      setMetadataValue(META_TOTAL_SIZE_EXCEEDED, 1L);
      return;
    }

    // Safe to proceed, increment the metric
    incrementVerifiedMetric(jobType, tenantId, workerName, status);
    // Update batch record total size
    updateBatchRecordTotalSize();
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
    setMetadataValue(META_TOTAL_ENCODED_STRINGS_BYTE_SIZE, 0L);
    setMetadataValue(META_JOB_METRICS_NB, 0L);
    setMetadataValue(META_BATCH_RECORD_TOTAL_SIZE, 0L);
    setMetadataValue(META_COUNTER, 0L);
    setMetadataValue(META_TOTAL_SIZE_EXCEEDED, 0L);
    setMetadataValue(META_BATCH_STARTING_TIME, clock.millis());
  }

  private void incrementVerifiedMetric(
      final String jobType,
      final String tenantId,
      final String workerName,
      final JobMetricsState status) {
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
      incrementMetadataValue(META_JOB_METRICS_NB, 1);
    } else {
      // Copy cached value to our working instance
      copyMetricsValue(cachedValue, metricsValue);
    }

    // Increment the metric for the specified status
    metricsValue.incrementMetric(status, clock.millis());

    // Write back to column family
    metricsColumnFamily.upsert(metricsKey, metricsValue);
    // Update the cache
    metricsCache.put(cacheKey, metricsValue.copy());
  }

  /**
   * Calculates the size impact of adding a metric for the given strings. Only counts new strings
   * and new keys.
   *
   * @param jobType the job type string
   * @param tenantId the tenant ID string
   * @param workerName the worker name string
   * @return the size impact in bytes
   */
  private long calculateSizeImpactInBytes(
      final String jobType, final String tenantId, final String workerName) {
    long impact = 0;

    // Check if strings are new (would add to encoded strings size)
    final boolean jobTypeIsNew = !stringEncodingCache.containsKey(jobType);
    final boolean tenantIdIsNew = !stringEncodingCache.containsKey(tenantId);
    final boolean workerNameIsNew = !stringEncodingCache.containsKey(workerName);

    if (jobTypeIsNew) {
      impact += jobType.getBytes(StandardCharsets.UTF_8).length;
    }
    if (tenantIdIsNew) {
      impact += tenantId.getBytes(StandardCharsets.UTF_8).length;
    }
    if (workerNameIsNew) {
      impact += workerName.getBytes(StandardCharsets.UTF_8).length;
    }

    // Check if the key combination is new (would add key+value size)
    if (jobTypeIsNew || tenantIdIsNew || workerNameIsNew) {
      // At least one string is new, so key is definitely new
      impact += MetricsKey.TOTAL_SIZE_BYTES + MetricsValue.TOTAL_SIZE_BYTES;
    } else {
      // All strings exist, check if key combination exists in cache
      final int jobTypeIdx = stringEncodingCache.get(jobType);
      final int tenantIdx = stringEncodingCache.get(tenantId);
      final int workerIdx = stringEncodingCache.get(workerName);

      final MetricsCacheKey cacheKey = new MetricsCacheKey(jobTypeIdx, tenantIdx, workerIdx);
      if (!metricsCache.containsKey(cacheKey)) {
        impact += MetricsKey.TOTAL_SIZE_BYTES + MetricsValue.TOTAL_SIZE_BYTES;
      }
      // If key exists, no additional size impact (just updating existing record)
    }

    return impact;
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

    // Update total encoded strings size
    incrementMetadataValue(
        META_TOTAL_ENCODED_STRINGS_BYTE_SIZE, string.getBytes(StandardCharsets.UTF_8).length);

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
   * Increments a metadata value.
   *
   * @param key the metadata key
   * @param increment the amount to increment
   */
  private void incrementMetadataValue(final String key, final long increment) {
    final long currentValue = getMetadata(key);
    setMetadataValue(key, currentValue + increment);
  }

  /** Updates the batch record total size metadata. */
  private void updateBatchRecordTotalSize() {
    final long jobMetricsNb = getMetadata(META_JOB_METRICS_NB);
    final long totalEncodedStringsSize = getMetadata(META_TOTAL_ENCODED_STRINGS_BYTE_SIZE);
    // Formula: job_metrics_nb * ((sizeOf(int) + sizeOf(long)) + sizeOf(int) * JobState.size()) +
    // total_encoded_strings_size
    final long batchRecordTotalSize =
        jobMetricsNb * (MetricsKey.TOTAL_SIZE_BYTES + MetricsValue.TOTAL_SIZE_BYTES)
            + totalEncodedStringsSize;
    setMetadataValue(META_BATCH_RECORD_TOTAL_SIZE, batchRecordTotalSize);
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
