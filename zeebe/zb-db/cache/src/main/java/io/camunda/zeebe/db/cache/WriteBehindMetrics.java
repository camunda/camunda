/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.BACKPRESSURE_WAIT;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.CACHE_EVICTIONS;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.CACHE_GETS;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.CACHE_LOADS;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.CACHE_SIZE;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.COMMITTED_KEYS;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.COMMIT_LOCK_WAIT;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.FAILURES;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.LOCKED_READS;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.PENDING_BYTES;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.PENDING_TRANSACTIONS;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.READ_RETRIES;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.SCAN_HOLD;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.SNAPSHOT_CATCH_UP;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.SNAPSHOT_CHECKPOINT;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.UNFLUSHED_KEYS;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.UNFLUSHED_READS;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.WRITE_KEYS;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.WRITE_LATENCY;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.WRITE_RETRIES;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.WRITE_TRANSACTIONS;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.WRITTEN_KEYS;

import io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.WriteBehindKeyNames;
import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.ehcache.core.statistics.CacheStatistics;
import org.ehcache.core.statistics.TierStatistics;

/**
 * The write-behind cache's meters. They're registered on the storage's meter registry, which
 * carries the partition's tags and goes away when the storage closes.
 */
final class WriteBehindMetrics {

  /** The name EhCache gives its heap tier in the tier statistics. */
  private static final String HEAP_TIER = "OnHeap";

  private final MeterRegistry registry;
  private final Counter cacheGets;
  private final Counter cacheLoads;
  private final Counter unflushedReads;
  private final Counter readRetries;
  private final Counter lockedReads;
  private final Counter committedKeys;
  private final Counter writtenUpserts;
  private final Counter writtenDeletes;
  private final Counter writeRetries;
  private final Counter failures;
  private final Timer commitLockWait;
  private final Timer backpressureWait;
  private final Timer writeLatency;
  private final Timer scanHold;
  private final Timer snapshotCatchUp;
  private final Timer snapshotCheckpoint;
  private final DistributionSummary writeTransactions;
  private final DistributionSummary writeKeys;

  WriteBehindMetrics(final MeterRegistry registry) {
    this.registry = registry;
    cacheGets = counter(CACHE_GETS);
    cacheLoads = counter(CACHE_LOADS);
    unflushedReads = counter(UNFLUSHED_READS);
    readRetries = counter(READ_RETRIES);
    lockedReads = counter(LOCKED_READS);
    committedKeys = counter(COMMITTED_KEYS);
    writtenUpserts =
        counterBuilder(WRITTEN_KEYS)
            .tag(WriteBehindKeyNames.OPERATION.asString(), "upsert")
            .register(registry);
    writtenDeletes =
        counterBuilder(WRITTEN_KEYS)
            .tag(WriteBehindKeyNames.OPERATION.asString(), "delete")
            .register(registry);
    writeRetries = counter(WRITE_RETRIES);
    failures = counter(FAILURES);
    commitLockWait = timer(COMMIT_LOCK_WAIT);
    backpressureWait = timer(BACKPRESSURE_WAIT);
    writeLatency = timer(WRITE_LATENCY);
    scanHold = timer(SCAN_HOLD);
    snapshotCatchUp = timer(SNAPSHOT_CATCH_UP);
    snapshotCheckpoint = timer(SNAPSHOT_CHECKPOINT);
    writeTransactions = MicrometerUtil.buildSummary(WRITE_TRANSACTIONS).register(registry);
    writeKeys = MicrometerUtil.buildSummary(WRITE_KEYS).register(registry);
  }

  /**
   * Reads evictions and size from the statistics of the read cache's heap tier. Gets and loads are
   * counted by the store and the loader instead: EhCache counts a get that the loader answered as a
   * hit.
   */
  void registerReadCache(final CacheStatistics statistics) {
    final TierStatistics heap = statistics.getTierStatistics().get(HEAP_TIER);
    if (heap == null) {
      throw new IllegalStateException(
          "Expected the read cache to have a %s tier, but it has %s"
              .formatted(HEAP_TIER, statistics.getTierStatistics().keySet()));
    }
    FunctionCounter.builder(CACHE_EVICTIONS.getName(), heap, TierStatistics::getEvictions)
        .description(CACHE_EVICTIONS.getDescription())
        .register(registry);
    gauge(CACHE_SIZE, heap::getOccupiedByteSize);
  }

  void registerPending(
      final LongSupplier unflushedKeys,
      final LongSupplier pendingTransactions,
      final LongSupplier pendingBytes) {
    gauge(UNFLUSHED_KEYS, unflushedKeys);
    gauge(PENDING_TRANSACTIONS, pendingTransactions);
    gauge(PENDING_BYTES, pendingBytes);
  }

  void observeCacheGet() {
    cacheGets.increment();
  }

  void observeCacheLoad() {
    cacheLoads.increment();
  }

  void observeUnflushedRead() {
    unflushedReads.increment();
  }

  void observeReadRetry() {
    readRetries.increment();
  }

  void observeLockedRead() {
    lockedReads.increment();
  }

  void observeCommit(final int keys, final long lockWaitNanos) {
    committedKeys.increment(keys);
    commitLockWait.record(lockWaitNanos, TimeUnit.NANOSECONDS);
  }

  void observeBackpressureWait(final long nanos) {
    backpressureWait.record(nanos, TimeUnit.NANOSECONDS);
  }

  void observeWrite(
      final int transactions, final int upserts, final int deletes, final long nanos) {
    writeTransactions.record(transactions);
    writeKeys.record(upserts + deletes);
    writtenUpserts.increment(upserts);
    writtenDeletes.increment(deletes);
    writeLatency.record(nanos, TimeUnit.NANOSECONDS);
  }

  void observeWriteRetry() {
    writeRetries.increment();
  }

  void observeFailure() {
    failures.increment();
  }

  void observeScanHold(final long nanos) {
    scanHold.record(nanos, TimeUnit.NANOSECONDS);
  }

  void observeSnapshotCatchUp(final long nanos) {
    snapshotCatchUp.record(nanos, TimeUnit.NANOSECONDS);
  }

  void timeSnapshotCheckpoint(final Runnable checkpoint) {
    snapshotCheckpoint.record(checkpoint);
  }

  private Counter counter(final ExtendedMeterDocumentation documentation) {
    return counterBuilder(documentation).register(registry);
  }

  private static Counter.Builder counterBuilder(final ExtendedMeterDocumentation documentation) {
    return Counter.builder(documentation.getName()).description(documentation.getDescription());
  }

  private Timer timer(final ExtendedMeterDocumentation documentation) {
    return MicrometerUtil.buildTimer(documentation).register(registry);
  }

  private void gauge(final ExtendedMeterDocumentation documentation, final LongSupplier value) {
    MicrometerUtil.buildGauge(documentation, value::getAsLong)
        .baseUnit(documentation.getBaseUnit())
        .register(registry);
  }
}
