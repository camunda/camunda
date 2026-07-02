/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics;

import io.camunda.analytics.streaming.StreamProcessor;
import io.camunda.analytics.streaming.aggregate.TransactionRunner;
import io.camunda.eventbridge.analytics.projection.BaseProjectionStore;
import io.camunda.eventbridge.client.TopicPartition;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecord;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecordConsumer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives the consumer through one {@link StreamProcessor}: poll the assigned {@code zeebe-records}
 * partitions, fold each record once (the base-projection {@code ProcessExecutionProjector}), fan
 * the derived facts out to the registered rollups, and converge the serving store on two clocks — a
 * per-batch {@code flush} (sink freshness only) and a periodic {@code checkpoint} (make the
 * in-memory working state durable and advance the source offset). Decoupling the two lets many
 * batches coalesce into one durable write.
 *
 * <p>On start the consumer is seeked to the base projection's checkpointed positions, so a restart
 * resumes the fold from where it left off (start-from-offset) — no replay on the common path, and
 * the source log itself is the recovery log if local state is lost. Run multiple instances with the
 * same consumer group and distinct consumer ids: the bridge coordinator assigns source partitions
 * across them and rebalances on membership change. The checkpoint order (rollup state, then base
 * projection position, then source commit) keeps local state at or ahead of the committed offset,
 * so a crash between checkpoints replays the tail rather than losing it, and the durable rollups
 * dedup any replayed facts.
 */
public final class WindowedAnalyticsPipeline implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(WindowedAnalyticsPipeline.class);
  // Pull large batches per sweep: fetches are capped by FETCH_MAX_BYTES (1 MiB/partition), and a
  // big batch amortizes the poll round-trip, the single flush, and the per-partition commit over
  // many records. A small cap (was 100) made the loop latency-bound — the consumer sat mostly idle
  // waiting on round-trips while the log grew, so it fell behind under realistic load.
  private static final int MAX_RECORDS = 5000;
  private static final Duration POLL_TIMEOUT = Duration.ofMillis(500);
  private static final Duration ERROR_BACKOFF = Duration.ofSeconds(1);
  // The commit interval: how often the in-memory working state is made durable and the source
  // offset advanced, decoupled from the per-batch sink flush. Longer = more coalescing of repeated
  // cell updates into one durable write (higher throughput) and fewer commit round-trips, at the
  // cost of replaying at most this much source on a crash (the record-cache throughput/latency
  // dial). Overridable for tuning.
  private static final long CHECKPOINT_INTERVAL_NANOS =
      Long.getLong("analytics.checkpointIntervalMs", 1000L) * 1_000_000L;

  private final ZeebeRecordConsumer sourceConsumer;
  private final StreamProcessor<ZeebeRecord> processor;
  private final BaseProjectionStore projectionStore;
  private final String sourceTopic;
  private final TransactionRunner checkpointTx;

  private volatile boolean running;
  private Thread thread;

  public WindowedAnalyticsPipeline(
      final ZeebeRecordConsumer sourceConsumer,
      final StreamProcessor<ZeebeRecord> processor,
      final BaseProjectionStore projectionStore,
      final String sourceTopic,
      final TransactionRunner checkpointTx) {
    this.sourceConsumer = sourceConsumer;
    this.processor = processor;
    this.projectionStore = projectionStore;
    this.sourceTopic = sourceTopic;
    this.checkpointTx = checkpointTx;
  }

  public void start() {
    running = true;
    seekToCheckpointedPositions();
    thread = new Thread(this::run, "analytics-pipeline");
    thread.setDaemon(true);
    thread.start();
  }

  /** Resume each partition just past the last position the base projection durably folded. */
  private void seekToCheckpointedPositions() {
    final Map<Integer, Long> consumed = projectionStore.consumedPositions();
    if (consumed.isEmpty()) {
      return;
    }
    final Map<TopicPartition, Long> startPositions = new HashMap<>();
    consumed.forEach(
        (partition, position) ->
            startPositions.put(new TopicPartition(sourceTopic, partition), position + 1));
    sourceConsumer.seek(startPositions);
    LOG.info("Resuming {} partition(s) from checkpointed positions {}", consumed.size(), consumed);
  }

  private void run() {
    processor.init();
    // Accumulated across batches within one commit interval: the max offset per partition (the
    // base-projection consumed position) and the highest record per partition (what to commit).
    final Map<Integer, Long> pendingMaxByPartition = new HashMap<>();
    final Map<Integer, ZeebeRecord> pendingLastByPartition = new LinkedHashMap<>();
    long lastCheckpointNanos = System.nanoTime();
    while (running) {
      try {
        final List<ZeebeRecord> records = sourceConsumer.poll(MAX_RECORDS, POLL_TIMEOUT);
        if (!records.isEmpty()) {
          // fold the whole batch into the rollups' in-memory working set (no durable writes yet)
          for (final ZeebeRecord record : records) {
            processor.process(record);
          }
          // converge the serving store every batch so the dashboard stays fresh (sink upsert only)
          processor.flush();
          // Track the highest offset/record per partition for the next checkpoint. Offsets only
          // advance via max and records arrive in offset order per partition, so the last
          // occurrence per partition is the highest.
          for (final ZeebeRecord record : records) {
            pendingMaxByPartition.merge(record.partitionId(), record.offset(), Math::max);
            pendingLastByPartition.put(record.partitionId(), record);
          }
        }
        if (System.nanoTime() - lastCheckpointNanos >= CHECKPOINT_INTERVAL_NANOS
            && !pendingMaxByPartition.isEmpty()) {
          checkpoint(pendingMaxByPartition, pendingLastByPartition);
          pendingMaxByPartition.clear();
          pendingLastByPartition.clear();
          lastCheckpointNanos = System.nanoTime();
        }
      } catch (final RuntimeException e) {
        LOG.warn("Analytics pipeline poll failed; backing off", e);
        sleep();
      }
    }
  }

  /**
   * Make the interval's work durable as one atomic cut over the shared RocksDB, then advance the
   * source offset last. The rollups' cells + offsets and the base projection's working state +
   * consumed position all commit in a single transaction (their per-store writes join it,
   * reentrantly), so they can never diverge. The source offset is committed only after that cut is
   * durable (commit-input-offset-last), so local state is never behind the committed offset; a
   * crash replays the tail and the rollups' dedup plus the idempotent sink reconcile it.
   */
  private void checkpoint(
      final Map<Integer, Long> maxByPartition, final Map<Integer, ZeebeRecord> lastByPartition) {
    checkpointTx.runInTransaction(
        () -> {
          processor.checkpoint();
          projectionStore.checkpoint();
          maxByPartition.forEach(projectionStore::setConsumedPosition);
        });
    final List<CompletableFuture<Void>> commits = new ArrayList<>();
    for (final ZeebeRecord record : lastByPartition.values()) {
      commits.add(sourceConsumer.commit(record));
    }
    CompletableFuture.allOf(commits.toArray(new CompletableFuture[0])).join();
  }

  private static void sleep() {
    try {
      Thread.sleep(ERROR_BACKOFF.toMillis());
    } catch (final InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void close() {
    running = false;
    if (thread != null) {
      try {
        thread.join(Duration.ofSeconds(5).toMillis());
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    processor.close();
  }
}
