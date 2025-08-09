/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Helper class that ensures eventual creation of the object. It will try to create the object on a
 * startup and in case of failure it will keep retrying the creation in async way until it succeeds.
 *
 * @param <T> type of object to initialize
 */
public class SafeInitProxy<T> {
  private static final long DEFAULT_RETRY_DELAY_MS = 1_000;

  /** Reference to an object that has to be created. */
  private final AtomicReference<T> reference = new AtomicReference<>();

  /** Supplier function for object creation */
  private final Supplier<T> initSupplier;

  /** Listener function for creation errors. */
  private final Consumer<Exception> errorListener;

  /** Retry delay */
  private final long retryDelayMs;

  /** Executor that will run retry task with the configured delay. */
  private Executor delayedExecutor;

  public SafeInitProxy(final Supplier<T> initSupplier, final Consumer<Exception> errorListener) {
    this(initSupplier, errorListener, DEFAULT_RETRY_DELAY_MS);
  }

  public SafeInitProxy(
      final Supplier<T> initSupplier,
      final Consumer<Exception> errorListener,
      final long retryDelayMs) {
    this.initSupplier = initSupplier;
    this.errorListener = errorListener;
    this.retryDelayMs = retryDelayMs;

    try {
      reference.set(initSupplier.get());
    } catch (final Exception e) {
      errorListener.accept(e);

      // Failed to initialize, schedule async retries
      delayedExecutor = CompletableFuture.delayedExecutor(retryDelayMs, TimeUnit.MILLISECONDS);
      scheduleRetry();
    }
  }

  private void scheduleRetry() {
    CompletableFuture.runAsync(
        () -> {
          try {
            // try to initialize the object on retry
            reference.set(initSupplier.get());
          } catch (final Exception e) {
            // if initialization fails, notify error listener and schedule next retry
            errorListener.accept(e);
            scheduleRetry();
          }
        },
        delayedExecutor);
  }

  public <E extends Throwable> T orElseThrow(final Supplier<? extends E> exceptionSupplier)
      throws E {
    final T value = reference.get();
    if (value == null) {
      throw exceptionSupplier.get();
    }
    return value;
  }
}
