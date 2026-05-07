/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import io.camunda.zeebe.exporter.api.ExporterException;
import java.time.InstantSource;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PendingFlush {
  private static final Logger LOGGER = LoggerFactory.getLogger(PendingFlush.class);

  private final Executor executor;
  private final InstantSource clock;
  private final Runnable flush;
  private final long lastPosition;
  private final AtomicReference<State> state = new AtomicReference<>(null);
  private final AtomicReference<Long> flushTimeMillis = new AtomicReference<>(null);
  private CompletableFuture<Void> pending;

  public PendingFlush(
      final Executor executor,
      final InstantSource clock,
      final Runnable flush,
      final long lastPosition) {
    this.executor = executor;
    this.clock = clock;
    this.flush = flush;
    this.lastPosition = lastPosition;
    start();
  }

  private void start() {
    updateState(State.PENDING);
    flushTimeMillis.set(null);
    pending =
        CompletableFuture.runAsync(
            () -> {
              try {
                flush.run();
              } finally {
                flushTimeMillis.set(clock.millis());
              }
            },
            executor);
  }

  public long getLastPosition() {
    return lastPosition;
  }

  public OptionalLong maybeFlushTimeMillis() {
    final Long flushTime = flushTimeMillis.get();
    return flushTime != null ? OptionalLong.of(flushTime) : OptionalLong.empty();
  }

  public void waitForCompletion() {
    final State currentState = state.get();
    switch (currentState) {
      case PENDING -> waitForPending();
      case ERRORED -> retryAndWait();
      case COMPLETED -> {
        // do nothing, batch request already completed successfully
      }
      default -> {
        throw new IllegalStateException("Unexpected state: " + currentState);
      }
    }
  }

  private void retryAndWait() {
    LOGGER.debug("Retrying failed flush for position {}", lastPosition);
    start();
    waitForPending();
  }

  private void waitForPending() {
    try {
      pending.join();
      updateState(State.COMPLETED);
    } catch (final CompletionException e) {
      updateState(State.ERRORED); // fail now, but allow retry next time
      final Throwable cause = e.getCause();
      if (cause instanceof ExporterException) {
        throw (ExporterException) cause;
      } else {
        throw new IllegalStateException("Unexpected exception during flush", cause);
      }
    }
  }

  private void updateState(final State newState) {
    state.updateAndGet(
        existing -> {
          if (newState == State.PENDING && (existing == null || existing == State.ERRORED)) {
            return newState;
          }
          if (existing == State.PENDING) {
            return newState;
          }
          throw new IllegalStateException(
              "Invalid state transition from " + existing + " to " + newState);
        });
  }

  enum State {
    PENDING,
    ERRORED,
    COMPLETED;
  }
}
