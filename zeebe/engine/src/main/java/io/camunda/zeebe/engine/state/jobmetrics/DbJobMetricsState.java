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
 *   <li>JOB_METRICS: stores aggregated job metrics per worker/tenant/type combination
 *   <li>JOB_METRICS_STRING_ENCODING: string-to-integer dictionary for space optimization
 *   <li>JOB_METRICS_META: stores metadata and statistics
 * </ul>
 */
public class DbJobMetricsState implements MutableJobMetricsState {

  /** Metadata key: sum of all bytes string in STRING_ENCODING_CF */
  public static final String META_TOTAL_ENCODED_STRINGS_SIZE =
      "__total_encoded_strings_byte_size__";

  /** Metadata key: number of unique keys in METRICS_CF */
  public static final String META_JOB_METRICS_NB = "__job_metrics_nb__";

  /** Metadata key: computed total size */
  public static final String META_BATCH_RECORD_TOTAL_SIZE = "__batch_record_total_size__";

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
  private final ColumnFamily<DbString, DbLong> metaColumnFamily;
  private final DbString metaKey;
  private final DbLong metaValue;

  // In-memory cache for string encoding (for fast lookups)
  private final ConcurrentHashMap<String, Integer> stringEncodingCache;

  public DbJobMetricsState(
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
    metaKey = new DbString();
    metaValue = new DbLong();
    metaColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOB_METRICS_META, transactionContext, metaKey, metaValue);

    // Initialize string encoding cache
    stringEncodingCache = new ConcurrentHashMap<>();

    // Populate cache from existing data
    populateStringEncodingCache();
  }

  private void populateStringEncodingCache() {
    stringEncodingColumnFamily.forEach(
        (key, value) -> stringEncodingCache.put(key.toString(), value.getValue()));
  }

  @Override
  public void forEach(final MetricsConsumer consumer) {
    metricsColumnFamily.forEach(
        (key, value) ->
            consumer.accept(
                key.getJobTypeIndex(),
                key.getTenantIdIndex(),
                key.getWorkerNameIndex(),
                value.copyMetrics()));
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
    metaKey.wrapString(key);
    final DbLong value = metaColumnFamily.get(metaKey);
    return value != null ? value.getValue() : 0L;
  }

  @Override
  public void incrementMetric(
      final String jobType, final String tenantId, final String workerName, final JobState status) {
    // Get or create integer indices for strings
    final int jobTypeIdx = getOrCreateStringIndex(jobType);
    final int tenantIdx = getOrCreateStringIndex(tenantId);
    final int workerIdx = getOrCreateStringIndex(workerName);

    // Build the key
    metricsKey.set(jobTypeIdx, tenantIdx, workerIdx);

    // Check if key exists
    final MetricsValue existingValue = metricsColumnFamily.get(metricsKey);
    final boolean isNewKey = existingValue == null;

    if (isNewKey) {
      // Create new metrics value
      metricsValue.reset();
      incrementMetadataValue(META_JOB_METRICS_NB, 1);
    } else {
      // Copy existing value to our working instance
      copyMetricsValue(existingValue, metricsValue);
    }

    // Increment the metric for the specified status
    final long timestamp = System.currentTimeMillis();
    metricsValue.incrementMetric(status, timestamp);

    // Write back to column family
    if (isNewKey) {
      metricsColumnFamily.insert(metricsKey, metricsValue);
    } else {
      metricsColumnFamily.update(metricsKey, metricsValue);
    }

    // Update batch record total size
    updateBatchRecordTotalSize();
  }

  @Override
  public void flush() {
    // Delete all keys in metrics column family
    final List<MetricsKey> metricsKeysToDelete = new ArrayList<>();
    metricsColumnFamily.forEach(
        (key, value) -> {
          final MetricsKey keyToDelete =
              new MetricsKey(
                  key.getJobTypeIndex(), key.getTenantIdIndex(), key.getWorkerNameIndex());
          metricsKeysToDelete.add(keyToDelete);
        });
    for (final MetricsKey keyToDelete : metricsKeysToDelete) {
      metricsColumnFamily.deleteExisting(keyToDelete);
    }

    // Delete all keys in string encoding column family
    final List<String> stringsToDelete = new ArrayList<>();
    stringEncodingColumnFamily.forEach((key, value) -> stringsToDelete.add(key.toString()));
    for (final String stringToDelete : stringsToDelete) {
      stringEncodingKey.wrapString(stringToDelete);
      stringEncodingColumnFamily.deleteExisting(stringEncodingKey);
    }

    // Clear the cache
    stringEncodingCache.clear();

    // Reset all metadata values to 0
    setMetadataValue(META_TOTAL_ENCODED_STRINGS_SIZE, 0L);
    setMetadataValue(META_JOB_METRICS_NB, 0L);
    setMetadataValue(META_BATCH_RECORD_TOTAL_SIZE, 0L);
    setMetadataValue(META_COUNTER, 0L);
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
    incrementMetadataValue(META_TOTAL_ENCODED_STRINGS_SIZE, string.getBytes().length);

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
    metaKey.wrapString(key);
    metaValue.wrapLong(value);
    metaColumnFamily.upsert(metaKey, metaValue);
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
    final long totalEncodedStringsSize = getMetadata(META_TOTAL_ENCODED_STRINGS_SIZE);
    // Formula: job_metrics_nb * (12 + 96) + total_encoded_strings_size
    final long batchRecordTotalSize =
        jobMetricsNb * (MetricsKey.BYTES + MetricsValue.BYTES) + totalEncodedStringsSize;
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
}
