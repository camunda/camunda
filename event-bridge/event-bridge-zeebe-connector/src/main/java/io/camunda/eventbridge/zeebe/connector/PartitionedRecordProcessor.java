/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.zeebe.connector;

import io.camunda.eventbridge.client.TopicPartition;
import java.util.Map;
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
 * Back-pressure is applied by bounding the number of in-flight records — {@link
 * #submit(ZeebeRecord)} blocks once the bound is reached, which in turn pauses the caller's poll
 * loop.
 *
 * <p>Handlers may run concurrently for records of <em>different</em> partitions, so handlers that
 * share mutable state across partitions must be thread-safe.
 */
final class PartitionedRecordProcessor implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(PartitionedRecordProcessor.class);
  private static final CompletableFuture<Void> COMPLETED = CompletableFuture.completedFuture(null);

  private final RecordDispatcher dispatcher;
  private final ExecutorService workers;
  private final Semaphore inFlight;

  /** Per-partition tail of the processing chain. Mutated only by the submitting (poll) thread. */
  private final Map<TopicPartition, CompletableFuture<Void>> tails = new ConcurrentHashMap<>();

  /** Latest record whose handler has completed, per partition. Written by worker threads. */
  private final Map<TopicPartition, ZeebeRecord> completed = new ConcurrentHashMap<>();

  PartitionedRecordProcessor(
      final RecordDispatcher dispatcher, final int workerCount, final int maxInFlight) {
    this.dispatcher = dispatcher;
    workers = Executors.newFixedThreadPool(workerCount, daemonThreadFactory());
    inFlight = new Semaphore(maxInFlight);
  }

  /**
   * Enqueues a record for processing on its partition's lane. Blocks if the in-flight bound is
   * reached, applying back-pressure to the caller.
   *
   * @throws InterruptedException if interrupted while waiting for an in-flight permit
   */
  void submit(final ZeebeRecord record) throws InterruptedException {
    inFlight.acquire();
    final TopicPartition partition = new TopicPartition(record.topic(), record.partitionId());
    final CompletableFuture<Void> tail = tails.getOrDefault(partition, COMPLETED);
    final CompletableFuture<Void> next =
        tail.handleAsync(
            (ignored, previousError) -> {
              try {
                dispatcher.dispatch(record.record());
              } catch (final RuntimeException e) {
                LOG.warn("Handler failed for record at offset {}", record.offset(), e);
              } finally {
                completed.put(partition, record);
                inFlight.release();
              }
              return null;
            },
            workers);
    tails.put(partition, next);
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
