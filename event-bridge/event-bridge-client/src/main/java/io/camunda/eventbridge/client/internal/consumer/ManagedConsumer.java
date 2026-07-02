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

  /** Highest processed offset per partition, committed once auto-commit fires. */
  private final Map<TopicPartition, Long> processed = new HashMap<>();

  private final Map<TopicPartition, Long> lastCommitted = new HashMap<>();

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
          dispatch(event);
        }

        if (autoCommit) {
          commitProcessed();
        }
      }
    } finally {
      stopped.countDown();
    }
  }

  /** Deserializes (if configured), records the processed offset, and invokes the handler. */
  @SuppressWarnings("unchecked")
  private void dispatch(final Event event) {
    final T record =
        deserializer == null
            ? (T) event
            : deserializer.deserialize(event.payload(), event.partitionId(), event.position());
    final TopicPartition tp = new TopicPartition(event.topic(), event.partitionId());
    processed.merge(tp, event.position(), Math::max);
    try {
      handler.handle(record);
    } catch (final RuntimeException e) {
      LOG.warn("Handler failed for {} at offset {}", tp, event.position(), e);
    }
  }

  /** Commits the max processed offset per partition that has advanced since the last commit. */
  private void commitProcessed() {
    for (final Map.Entry<TopicPartition, Long> entry : processed.entrySet()) {
      final TopicPartition tp = entry.getKey();
      final long offset = entry.getValue();
      final Long committed = lastCommitted.get(tp);
      if (committed == null || offset > committed) {
        consumer
            .commitOffset(tp.topic(), tp.partition(), offset)
            .exceptionally(
                error -> {
                  LOG.warn("Commit failed for {} at offset {}", tp, offset, error);
                  return null;
                });
        lastCommitted.put(tp, offset);
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
}
