/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client.internal.consumer;

import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.client.Deserializer;
import io.camunda.eventbridge.client.Event;
import io.camunda.eventbridge.client.MessageConsumer;
import io.camunda.eventbridge.client.MessageHandler;
import io.camunda.eventbridge.client.TopicPartition;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Managed, handler-based consumer: drives an underlying {@link Consumer} on a single virtual thread
 * that polls, deserializes (via an optional {@link Deserializer}), dispatches each record to a
 * {@link MessageHandler}, and — with auto-commit on — commits the max processed offset per
 * partition after each batch. Analogous to the connector's record listener but generic.
 *
 * <p>The loop is submitted to the client's shared virtual-thread executor rather than to a
 * per-consumer one: the loop spends almost all its time parked on the long-poll, and a parked
 * virtual thread releases its carrier rather than tying up a platform thread. This consumer owns
 * only its submitted task ({@link #loop}); the executor's lifecycle belongs to the client. Virtual
 * threads are always daemon, so the loop never keeps the JVM alive.
 *
 * <p><b>Failure semantics:</b> a record's offset is recorded only after its handler returned, and a
 * deserialization or handler failure stops the consumer after committing the offsets of the records
 * that did succeed — the failed record and everything after it are redelivered to the next consumer
 * resuming from the committed offsets (at-least-once). The loop never commits past a failure.
 *
 * <p>{@link #close()} stops the loop, performs a final commit (when auto-commit is on), and closes
 * the underlying consumer, without touching the shared executor. Idempotent.
 *
 * @param <T> the record type delivered to the handler
 */
public final class ManagedConsumer<T> implements MessageConsumer {

  private static final Logger LOG = LoggerFactory.getLogger(ManagedConsumer.class);

  private final Consumer consumer;
  private final MessageHandler<T> handler;
  private final Deserializer<T> deserializer;
  private final boolean autoCommit;
  private final int pollSize;
  private final Duration pollTimeout;
  private final Future<?> loop;

  /** Counts down when the loop has fully exited, so {@link #close()} can await it. */
  private final CountDownLatch stopped = new CountDownLatch(1);

  /**
   * Commit progress per partition. The holders carry plain {@code long} offsets, so the per-record
   * bookkeeping neither boxes nor allocates; a new holder (and its {@link TopicPartition}) is
   * created only when a partition is seen for the first time.
   */
  private final Map<TopicPartition, PartitionProgress> progressByPartition = new HashMap<>();

  /**
   * The last partition dispatched to — records of one partition arrive in runs, so this memo makes
   * the per-record progress lookup allocation-free in the common case.
   */
  private PartitionProgress lastProgress;

  private volatile boolean running = true;

  public ManagedConsumer(
      final Consumer consumer,
      final ExecutorService executor,
      final MessageHandler<T> handler,
      final Deserializer<T> deserializer,
      final boolean autoCommit,
      final int pollSize,
      final Duration pollTimeout) {
    this.consumer = consumer;
    this.handler = handler;
    this.deserializer = deserializer;
    this.autoCommit = autoCommit;
    this.pollSize = pollSize;
    this.pollTimeout = pollTimeout;
    loop = executor.submit(this::runLoop);
  }

  @Override
  public Consumer consumer() {
    return consumer;
  }

  private void runLoop() {
    try {
      while (running) {
        final List<Event> batch;
        try {
          batch = consumer.poll(pollSize, pollTimeout);
        } catch (final RuntimeException e) {
          if (running) {
            LOG.warn("Poll failed; retrying", e);
          }
          continue;
        }

        for (final Event event : batch) {
          if (!dispatch(event)) {
            // Fail fast: the failed record was NOT recorded as processed, so the final commit
            // below stops just before it and the next consumer resuming from the committed
            // offsets redelivers it (at-least-once). Continuing instead would either commit
            // past the failure (silent loss) or starve the partition (fetch positions only
            // advance, so the record cannot be re-fetched by this consumer).
            running = false;
            break;
          }
        }

        if (autoCommit) {
          commitProcessed();
        }
      }
    } finally {
      stopped.countDown();
    }
  }

  /**
   * Deserializes (if configured), invokes the handler, and — only on success — records the
   * processed offset. Returns {@code false} when deserialization or the handler failed: the record
   * must not be committed, and the loop stops so it is redelivered rather than silently dropped
   * (recording before handling used to commit straight past a throwing handler).
   */
  @SuppressWarnings("unchecked")
  private boolean dispatch(final Event event) {
    final PartitionProgress progress = progressFor(event.topic(), event.partitionId());
    try {
      final T record =
          deserializer == null
              ? (T) event
              : deserializer.deserialize(event.payload(), event.partitionId(), event.position());
      handler.handle(record);
    } catch (final RuntimeException e) {
      LOG.error(
          "Handler failed for {} at offset {}; stopping this consumer — the record is not"
              + " committed and will be redelivered to a consumer resuming from the committed"
              + " offsets",
          progress.partition,
          event.position(),
          e);
      return false;
    }
    if (event.position() > progress.processed) {
      progress.processed = event.position();
    }
    return true;
  }

  /** The commit-progress holder for {@code (topic, partitionId)}, creating it on first sight. */
  private PartitionProgress progressFor(final String topic, final int partitionId) {
    final PartitionProgress memo = lastProgress;
    if (memo != null
        && memo.partition.partition() == partitionId
        && memo.partition.topic().equals(topic)) {
      return memo;
    }
    final TopicPartition tp = new TopicPartition(topic, partitionId);
    PartitionProgress progress = progressByPartition.get(tp);
    if (progress == null) {
      progress = new PartitionProgress(tp);
      progressByPartition.put(tp, progress);
    }
    lastProgress = progress;
    return progress;
  }

  /** Commits the max processed offset per partition that has advanced since the last commit. */
  private void commitProcessed() {
    for (final PartitionProgress progress : progressByPartition.values()) {
      if (progress.processed > progress.committed) {
        final TopicPartition tp = progress.partition;
        final long offset = progress.processed;
        consumer
            .commitOffset(tp.topic(), tp.partition(), offset)
            .exceptionally(
                error -> {
                  LOG.warn("Commit failed for {} at offset {}", tp, offset, error);
                  return null;
                });
        progress.committed = offset;
      }
    }
  }

  @Override
  public void close() {
    if (!running) {
      return;
    }
    running = false;
    loop.cancel(true); // interrupt the parked long-poll so the loop exits promptly
    try {
      stopped.await(5, TimeUnit.SECONDS);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    if (autoCommit) {
      commitProcessed();
    }
    consumer.close();
  }

  /** One partition's commit progress: the highest processed and the last committed offset. */
  private static final class PartitionProgress {

    private final TopicPartition partition;
    private long processed = Long.MIN_VALUE;
    private long committed = Long.MIN_VALUE;

    private PartitionProgress(final TopicPartition partition) {
      this.partition = partition;
    }
  }
}
