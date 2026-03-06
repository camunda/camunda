/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.zeebe.cache;

import io.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A per-partition sliding window cache that buffers imported Zeebe records for the last {@value
 * #WINDOW_DURATION_SECONDS} seconds. Every {@value #WINDOW_DURATION_SECONDS} seconds the cache
 * evicts stale batches and logs a summary of what remains in the window. Intended to act as a
 * downstream consumer/producer after records have been written to Elasticsearch.
 */
public class ZeebeImportSlidingWindowCache {

  static final long WINDOW_DURATION_SECONDS = 10;
  private static final Logger LOG = LoggerFactory.getLogger(ZeebeImportSlidingWindowCache.class);

  private final int partitionId;
  private final String recordType;
  private final CopyOnWriteArrayList<BatchEntry> entries = new CopyOnWriteArrayList<>();
  private final ScheduledExecutorService scheduler;

  public ZeebeImportSlidingWindowCache(final int partitionId, final String recordType) {
    this.partitionId = partitionId;
    this.recordType = recordType;
    this.scheduler =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              final Thread thread =
                  new Thread(
                      r, "zeebe-sliding-window-cache-partition-" + partitionId + "-" + recordType);
              thread.setDaemon(true);
              return thread;
            });
    scheduler.scheduleAtFixedRate(
        this::evictAndLog, WINDOW_DURATION_SECONDS, WINDOW_DURATION_SECONDS, TimeUnit.SECONDS);
  }

  /**
   * Accepts a batch of newly imported records and adds them to the sliding window cache.
   *
   * @param records the records that were just written to Elasticsearch
   */
  public void accept(final List<? extends ZeebeRecordDto<?, ?>> records) {
    if (records == null || records.isEmpty()) {
      return;
    }
    entries.add(new BatchEntry(System.currentTimeMillis(), new ArrayList<>(records)));
  }

  /** Evicts entries that have fallen outside the sliding window and logs the current state. */
  void evictAndLog() {
    final long cutoffMs = System.currentTimeMillis() - (WINDOW_DURATION_SECONDS * 1_000L);
    entries.removeIf(entry -> entry.receivedAtMs() < cutoffMs);

    final long totalRecordCount = entries.stream().mapToLong(e -> e.records().size()).sum();
    LOG.info(
        "Sliding window cache [partition={}, recordType={}]: {} record(s) in the last {}s window"
            + " across {} batch(es)",
        partitionId,
        recordType,
        totalRecordCount,
        WINDOW_DURATION_SECONDS,
        entries.size());
  }

  /**
   * Returns the number of records currently in the sliding window. Useful for testing and
   * monitoring.
   */
  public long getWindowRecordCount() {
    final long cutoffMs = System.currentTimeMillis() - (WINDOW_DURATION_SECONDS * 1_000L);
    return entries.stream()
        .filter(e -> e.receivedAtMs() >= cutoffMs)
        .mapToLong(e -> e.records().size())
        .sum();
  }

  /** Shuts down the background eviction/logging scheduler gracefully. */
  public void shutdown() {
    scheduler.shutdown();
    try {
      if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
        LOG.warn(
            "Sliding window cache scheduler for partition={}, recordType={} did not terminate"
                + " within 5 seconds; forcing shutdown.",
            partitionId,
            recordType);
        scheduler.shutdownNow();
      }
    } catch (final InterruptedException e) {
      LOG.warn(
          "Interrupted while waiting for sliding window cache scheduler to terminate"
              + " [partition={}, recordType={}].",
          partitionId,
          recordType);
      Thread.currentThread().interrupt();
      scheduler.shutdownNow();
    }
  }

  private record BatchEntry(long receivedAtMs, List<? extends ZeebeRecordDto<?, ?>> records) {}
}
