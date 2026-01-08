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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * RocksDB-based implementation of job metrics state management.
 *
 * <p>Uses three column families:
 *
 * <ul>
 *   <li>JOB_METRICS: stores aggregated job metrics per type/tenant/worker combination
 *   <li>JOB_METRICS_STRING_ENCODING: string-to-integer dictionary for space optimization
 *   <li>JOB_METRICS_META: stores metadata and statistics
 * </ul>
 */
public class DbJobMetricsState implements MutableJobMetricsState {

  /** Maximum batch record size in bytes (4 MiB) */
  public static final long MAX_BATCH_SIZE = 4 * 1024 * 1024;

  /** Metadata key: sum of all bytes string in STRING_ENCODING_CF */
  public static final String META_TOTAL_ENCODED_STRINGS_SIZE =
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
  private final DbString metadataKey;
  private final DbLong metadataValue;

  // In-memory cache for string encoding (for fast lookups)
  private final HashMap<String, Integer> stringEncodingCache;
  private final InstantSource clock;

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
    metaColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOB_METRICS_META, transactionContext, metadataKey, metadataValue);

    // Initialize string encoding cache
    stringEncodingCache = new HashMap<>();

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
    stringEncodingCache.forEach((key, value) -> sortedMap.put(value, key));

    return new ArrayList<>(sortedMap.values());
  }

  @Override
  public long getMetadata(final String key) {
    metadataKey.wrapString(key);
    final DbLong value = metaColumnFamily.get(metadataKey);
    return value != null ? value.getValue() : ZERO;
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

    // If already exceeded size limit, skip processing
    if (isIncompleteBatch()) {
      return;
    }

    final MetricContext ctx = resolveMetricContext(jobType, tenantId, workerName);

    if (wouldExceedSizeLimit(ctx)) {
      setMetadataValue(META_TOTAL_SIZE_EXCEEDED, 1L);
      return;
    }

    persistMetricIncrement(ctx, status);
  }

  @Override
  public void cleanUp() {
    // Delete all keys in metrics column family
    metricsColumnFamily.forEach((mk, mv) -> metricsColumnFamily.deleteExisting(mk));

    // Delete all keys in string encoding column family
    stringEncodingColumnFamily.forEach(
        (dbString, dbInt) -> stringEncodingColumnFamily.deleteExisting(dbString));

    // Clear the cache
    stringEncodingCache.clear();

    // Reset all metadata values
    metaColumnFamily.forEach((dbString, dbLong) -> metaColumnFamily.deleteExisting(dbString));

    setMetadataValue(META_BATCH_STARTING_TIME, clock.millis());
  }

  /** Resolves all context needed for a metric increment in a single pass. */
  private MetricContext resolveMetricContext(
      final String jobType, final String tenantId, final String workerName) {

    final boolean jobTypeIsNew = !stringEncodingCache.containsKey(jobType);
    final boolean tenantIdIsNew = !stringEncodingCache.containsKey(tenantId);
    final boolean workerNameIsNew = !stringEncodingCache.containsKey(workerName);

    if (jobTypeIsNew || tenantIdIsNew || workerNameIsNew) {
      // At least one string is new, so key is definitely new
      return new MetricContext(
          jobType, tenantId, workerName, jobTypeIsNew, tenantIdIsNew, workerNameIsNew, true, null);
    }

    // All strings exist - fetch existing value (single DB read)
    metricsKey.set(
        stringEncodingCache.get(jobType),
        stringEncodingCache.get(tenantId),
        stringEncodingCache.get(workerName));
    final MetricsValue existingValue = metricsColumnFamily.get(metricsKey);

    return new MetricContext(
        jobType, tenantId, workerName, false, false, false, existingValue == null, existingValue);
  }

  /** Checks if adding this metric would exceed the size limit. */
  private boolean wouldExceedSizeLimit(final MetricContext ctx) {
    final long sizeImpact = calculateSizeImpactInBytes(ctx);
    final long currentSize = getMetadata(META_BATCH_RECORD_TOTAL_SIZE);
    return currentSize + sizeImpact > MAX_BATCH_SIZE;
  }

  /** Calculates the size impact of adding a metric. */
  private long calculateSizeImpactInBytes(final MetricContext ctx) {
    long sizeInBytes = 0;
    if (ctx.jobTypeIsNew()) {
      sizeInBytes += ctx.jobType().getBytes(StandardCharsets.UTF_8).length;
    }
    if (ctx.tenantIdIsNew()) {
      sizeInBytes += ctx.tenantId().getBytes(StandardCharsets.UTF_8).length;
    }
    if (ctx.workerNameIsNew()) {
      sizeInBytes += ctx.workerName().getBytes(StandardCharsets.UTF_8).length;
    }
    if (ctx.isNewKey()) {
      sizeInBytes += MetricsKey.TOTAL_SIZE_BYTES + MetricsValue.TOTAL_SIZE_BYTES;
    }
    return sizeInBytes;
  }

  /** Persists the metric increment to the database. */
  private void persistMetricIncrement(final MetricContext ctx, final JobMetricsState status) {
    final int jobTypeIdx = getOrCreateStringIndex(ctx.jobType());
    final int tenantIdx = getOrCreateStringIndex(ctx.tenantId());
    final int workerIdx = getOrCreateStringIndex(ctx.workerName());

    metricsKey.set(jobTypeIdx, tenantIdx, workerIdx);

    if (ctx.isNewKey()) {
      metricsValue.reset();
      incrementMetadataValue(META_JOB_METRICS_NB, 1);
    } else {
      copyMetricsValue(ctx.existingValue(), metricsValue);
    }

    metricsValue.incrementMetric(status, clock.millis());
    metricsColumnFamily.upsert(metricsKey, metricsValue);
    updateBatchRecordTotalSize();
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
        META_TOTAL_ENCODED_STRINGS_SIZE, string.getBytes(StandardCharsets.UTF_8).length);

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
    metaColumnFamily.upsert(metadataKey, metadataValue);
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

  /**
   * Holds pre-resolved context for a metric increment operation. Avoids repeated lookups and
   * enables single-pass processing.
   */
  private record MetricContext(
      String jobType,
      String tenantId,
      String workerName,
      boolean jobTypeIsNew,
      boolean tenantIdIsNew,
      boolean workerNameIsNew,
      boolean isNewKey,
      MetricsValue existingValue) {}
}
