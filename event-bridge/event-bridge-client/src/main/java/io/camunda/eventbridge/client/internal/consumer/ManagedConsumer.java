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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Managed, handler-based consumer: drives an underlying {@link Consumer} on a background daemon
 * thread that polls, deserializes (via an optional {@link Deserializer}), dispatches each record to
 * a {@link MessageHandler}, and — with auto-commit on — commits the max processed offset per
 * partition after each batch. Analogous to the connector's record listener but generic.
 *
 * <p>{@link #close()} stops the loop, performs a final commit (when auto-commit is on), and closes
 * the underlying consumer. Idempotent.
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
  private final Thread thread;

  /** Highest processed offset per partition, committed once auto-commit fires. */
  private final Map<TopicPartition, Long> processed = new HashMap<>();

  private final Map<TopicPartition, Long> lastCommitted = new HashMap<>();

  private volatile boolean running = true;

  public ManagedConsumer(
      final Consumer consumer,
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
    thread = new Thread(this::runLoop, "eventbridge-consumer-" + consumer.getGroupId());
    thread.setDaemon(true);
    thread.start();
  }

  @Override
  public Consumer consumer() {
    return consumer;
  }

  private void runLoop() {
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
    thread.interrupt();
    try {
      thread.join(Duration.ofSeconds(5).toMillis());
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    if (autoCommit) {
      commitProcessed();
    }
    consumer.close();
  }
}
