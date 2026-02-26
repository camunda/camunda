/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SearchEngineSchemaInitializerTest {

  private SearchEngineSchemaInitializer createInitializer() {
    return new SearchEngineSchemaInitializer(null, new SimpleMeterRegistry(), true);
  }

  /** Sets the private {@code isShutdown} flag on the initializer via reflection. */
  private static void setShutdown(
      final SearchEngineSchemaInitializer initializer, final boolean value) {
    try {
      final Field field = SearchEngineSchemaInitializer.class.getDeclaredField("isShutdown");
      field.setAccessible(true);
      ((AtomicBoolean) field.get(initializer)).set(value);
    } catch (final ReflectiveOperationException e) {
      throw new RuntimeException("Failed to set isShutdown via reflection", e);
    }
  }

  @Nested
  class SynchronousInitialization {

    private ExecutorService executor;

    @AfterEach
    void tearDown() {
      if (executor != null && !executor.isShutdown()) {
        executor.shutdownNow();
      }
      // Clear interrupt flag to avoid leaking to other tests
      Thread.interrupted();
    }

    @Test
    void shouldCompleteSuccessfully() throws Exception {
      // given
      final var initializer = createInitializer();
      executor = Executors.newSingleThreadExecutor();
      final var future = CompletableFuture.completedFuture((Void) null);

      // when
      initializer.synchronousSchemaInitialization(future, executor);

      // then — no exception thrown, executor is closed
      assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    void shouldNotInterruptThreadOnShutdownTriggeredInterrupt() throws Exception {
      // given — simulate shutdown-triggered interrupt
      final var initializer = createInitializer();
      executor = Executors.newSingleThreadExecutor();
      setShutdown(initializer, true);

      // Use an incomplete future so that future.get() blocks and sees the interrupt flag
      final var blockingFuture = new CompletableFuture<Void>();

      // when — interrupt the thread so future.get() throws InterruptedException
      Thread.currentThread().interrupt();
      initializer.synchronousSchemaInitialization(blockingFuture, executor);

      // then — thread should NOT be interrupted (shutdown swallows the interrupt)
      assertThat(Thread.currentThread().isInterrupted()).isFalse();
      assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    void shouldReInterruptThreadOnNonShutdownInterrupt() throws Exception {
      // given — NOT a shutdown, something else interrupted
      final var initializer = createInitializer();
      executor = Executors.newSingleThreadExecutor();
      // isShutdown remains false (default)

      final var blockingFuture = new CompletableFuture<Void>();

      // when — interrupt the current thread so future.get() throws InterruptedException
      Thread.currentThread().interrupt();
      initializer.synchronousSchemaInitialization(blockingFuture, executor);

      // then — thread should be re-interrupted
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    void shouldSuppressExceptionDuringShutdown() {
      // given — shutdown is in progress and the future fails with a non-interrupt exception.
      // CompletableFuture.get() wraps the cause in ExecutionException, which falls into
      // the catch(Exception) block where isShutdown=true causes a silent return.
      final var initializer = createInitializer();
      executor = Executors.newSingleThreadExecutor();
      setShutdown(initializer, true);

      final var future = new CompletableFuture<Void>();
      future.completeExceptionally(new RuntimeException("ES connection failed"));

      // when/then — should not throw, exception is suppressed during shutdown
      assertThatNoException()
          .isThrownBy(() -> initializer.synchronousSchemaInitialization(future, executor));
      assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    void shouldPropagateExceptionWhenNotShutdown() {
      // given — NOT a shutdown, the future fails with a real error.
      // CompletableFuture.get() wraps the cause in ExecutionException.
      final var initializer = createInitializer();
      executor = Executors.newSingleThreadExecutor();
      // isShutdown remains false

      final var cause = new RuntimeException("ES connection refused");
      final var future = new CompletableFuture<Void>();
      future.completeExceptionally(cause);

      // when/then — ExecutionException wrapping the cause should propagate
      assertThatThrownBy(() -> initializer.synchronousSchemaInitialization(future, executor))
          .isInstanceOf(ExecutionException.class)
          .hasCause(cause);
      assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    void shouldAlwaysCloseExecutorEvenOnException() {
      // given
      final var initializer = createInitializer();
      executor = Executors.newSingleThreadExecutor();

      final var future = new CompletableFuture<Void>();
      future.completeExceptionally(new RuntimeException("schema init failed"));

      // when
      try {
        initializer.synchronousSchemaInitialization(future, executor);
      } catch (final Exception ignored) {
        // expected
      }

      // then — executor must be closed regardless
      assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    void shouldAlwaysCloseExecutorOnInterrupt() throws Exception {
      // given
      final var initializer = createInitializer();
      executor = Executors.newSingleThreadExecutor();

      final var blockingFuture = new CompletableFuture<Void>();
      Thread.currentThread().interrupt();

      // when
      initializer.synchronousSchemaInitialization(blockingFuture, executor);

      // then — executor must be closed regardless
      assertThat(executor.isShutdown()).isTrue();
    }
  }

  @Nested
  class AsynchronousInitialization {

    @Test
    void shouldCloseExecutorOnSuccess() throws Exception {
      // given
      final var initializer = createInitializer();
      final var executor = Executors.newSingleThreadExecutor();
      final var future = CompletableFuture.completedFuture((Void) null);

      // when
      initializer.asyncSchemaInitialization(future, executor);

      // then — wait a bit for the whenCompleteAsync callback to run
      executor.awaitTermination(2, TimeUnit.SECONDS);
      assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    void shouldCloseExecutorOnFailure() throws Exception {
      // given
      final var initializer = createInitializer();
      final var executor = Executors.newSingleThreadExecutor();
      final var future = new CompletableFuture<Void>();
      future.completeExceptionally(new RuntimeException("ES unavailable"));

      // when
      initializer.asyncSchemaInitialization(future, executor);

      // then — wait a bit for the whenCompleteAsync callback to run
      executor.awaitTermination(2, TimeUnit.SECONDS);
      assertThat(executor.isShutdown()).isTrue();
    }
  }
}
