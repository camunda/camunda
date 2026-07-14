/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.zeebe.connector;

import io.camunda.eventbridge.client.TopicPartition;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processes records on a pool of worker threads while preserving per-partition order: records of a
 * given partition run sequentially (chained), records of different partitions run in parallel.
 *
 * <p>Decouples handler work from polling so a slow handler does not throttle fetching.
 * Back-pressure is applied by bounding the number of in-flight records — {@link #submit(List)}
 * blocks once the bound is reached, which in turn pauses the caller's poll loop. Consecutive
 * records of the same partition within a submitted batch are coalesced into a single chained task,
 * so dispatch overhead (future, closure) is paid per run rather than per record.
 *
 * <p>Handlers may run concurrently for records of <em>different</em> partitions, so handlers that
 * share mutable state across partitions must be thread-safe.
 *
 * <p><b>Failure semantics:</b> a record whose handler throws is <em>not</em> recorded as completed,
 * and neither is any later record of that partition — the partition's committed offset freezes at
 * the last success, so the failed record and everything after it are redelivered to a consumer
 * resuming from the committed offsets (at-least-once) instead of being silently dropped. Other
 * partitions keep processing and committing. Subsequent records of a failed partition are drained
 * without dispatching (their permits are released) since their handler effects could never be
 * committed by this processor anyway.
 */
final class PartitionedRecordProcessor implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(PartitionedRecordProcessor.class);
  private static final CompletableFuture<Void> COMPLETED = CompletableFuture.completedFuture(null);

  private final RecordDispatcher dispatcher;
  private final ExecutorService workers;
  private final Semaphore inFlight;

  /** Per-partition tail of the processing chain. Mutated only by the submitting (poll) thread. */
  private final Map<TopicPartition, CompletableFuture<Void>> tails = new ConcurrentHashMap<>();

  /**
   * Cached {@link TopicPartition} instances per (topic, partitionId), so submitting does not
   * allocate one per record. Touched only by the submitting (poll) thread.
   */
  private final Map<String, Map<Integer, TopicPartition>> partitions = new HashMap<>();

  /** Latest record whose handler has completed, per partition. Written by worker threads. */
  private final Map<TopicPartition, ZeebeRecord> completed = new ConcurrentHashMap<>();

  /**
   * Partitions with a failed record: their completed offset must never advance past the last
   * success, so later records are drained without dispatching. Written by worker threads.
   */
  private final Set<TopicPartition> failed = ConcurrentHashMap.newKeySet();

  PartitionedRecordProcessor(
      final RecordDispatcher dispatcher, final int workerCount, final int maxInFlight) {
    this.dispatcher = dispatcher;
    workers = Executors.newFixedThreadPool(workerCount, daemonThreadFactory());
    inFlight = new Semaphore(maxInFlight);
  }

  /**
   * Enqueues a poll batch for processing, coalescing consecutive records of the same partition into
   * one chained task per run — one future and one closure per run instead of per record, while runs
   * of different partitions still fan out across the workers. Blocks if the in-flight bound is
   * reached, applying back-pressure to the caller.
   *
   * @throws InterruptedException if interrupted while waiting for an in-flight permit
   */
  void submit(final List<ZeebeRecord> batch) throws InterruptedException {
    final int size = batch.size();
    int start = 0;
    while (start < size) {
      final ZeebeRecord first = batch.get(start);
      // Block only while no unscheduled records are held: every record already claimed for a run
      // has been handed to the workers, so a blocking wait is always satisfiable by their permits.
      inFlight.acquire();
      int end = start + 1;
      while (end < size && samePartition(batch.get(end), first) && inFlight.tryAcquire()) {
        end++;
      }
      schedule(partitionOf(first), batch, start, end);
      start = end;
    }
  }

  /**
   * Enqueues a single record for processing on its partition's lane — a run of one. Blocks if the
   * in-flight bound is reached, applying back-pressure to the caller.
   *
   * @throws InterruptedException if interrupted while waiting for an in-flight permit
   */
  void submit(final ZeebeRecord record) throws InterruptedException {
    inFlight.acquire();
    schedule(partitionOf(record), List.of(record), 0, 1);
  }

  /** Chains one task processing {@code batch[from, to)} onto the partition's lane. */
  private void schedule(
      final TopicPartition partition, final List<ZeebeRecord> batch, final int from, final int to) {
    final CompletableFuture<Void> tail = tails.getOrDefault(partition, COMPLETED);
    final CompletableFuture<Void> next =
        tail.handleAsync(
            (ignored, previousError) -> {
              process(partition, batch, from, to);
              return null;
            },
            workers);
    tails.put(partition, next);
  }

  /** Dispatches one run's records in order, releasing each record's permit as it settles. */
  private void process(
      final TopicPartition partition, final List<ZeebeRecord> batch, final int from, final int to) {
    int index = from;
    if (!failed.contains(partition)) {
      while (index < to) {
        final ZeebeRecord record = batch.get(index);
        try {
          dispatcher.dispatch(record.record());
        } catch (final RuntimeException e) {
          failed.add(partition);
          LOG.warn(
              "Handler failed for {} at offset {}; the partition's committed offset stays at the"
                  + " last success, so this record and everything after it are redelivered to a"
                  + " consumer resuming from the committed offsets",
              partition,
              record.offset(),
              e);
          break;
        }
        completed.put(partition, record);
        index++;
        inFlight.release();
      }
    }
    // The failed record and everything drained after a failure still return their permits.
    if (index < to) {
      inFlight.release(to - index);
    }
  }

  private static boolean samePartition(final ZeebeRecord record, final ZeebeRecord other) {
    return record.partitionId() == other.partitionId() && record.topic().equals(other.topic());
  }

  private TopicPartition partitionOf(final ZeebeRecord record) {
    Map<Integer, TopicPartition> byId = partitions.get(record.topic());
    if (byId == null) {
      byId = new HashMap<>();
      partitions.put(record.topic(), byId);
    }
    TopicPartition partition = byId.get(record.partitionId());
    if (partition == null) {
      partition = new TopicPartition(record.topic(), record.partitionId());
      byId.put(record.partitionId(), partition);
    }
    return partition;
  }

  /** The latest completed record per partition, for committing progress. */
  Map<TopicPartition, ZeebeRecord> completedOffsets() {
    return completed;
  }

  @Override
  public void close() {
    workers.shutdown();
    try {
      if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
        workers.shutdownNow();
      }
    } catch (final InterruptedException e) {
      workers.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  private static ThreadFactory daemonThreadFactory() {
    final AtomicInteger counter = new AtomicInteger();
    return runnable -> {
      final Thread thread =
          new Thread(runnable, "zeebe-record-worker-" + counter.getAndIncrement());
      thread.setDaemon(true);
      return thread;
    };
  }
}
