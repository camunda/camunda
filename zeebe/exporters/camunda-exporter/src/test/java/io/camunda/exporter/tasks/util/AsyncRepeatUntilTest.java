/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

class AsyncRepeatUntilTest {

  @Test
  void shouldCompleteWhenTaskCompletes() {
    // given
    // when
    final var future =
        AsyncRepeatUntil.repeatUntil(() -> CompletableFuture.completedFuture(1), result -> true);

    // then
    assertThat(future).succeedsWithin(Duration.ofSeconds(2));
    // ensure no exceptions are thrown
    future.join();
  }

  @Test
  void shouldNotCompleteWhenTaskDoesNotComplete() {
    // given
    // when
    final var future = AsyncRepeatUntil.repeatUntil(CompletableFuture::new, result -> true);

    // then
    assertThat(future.isDone()).isFalse();
  }

  @Test
  void shouldCompleteExceptionallyWhenTasksFailsAsync() {
    // given
    // when
    final var future =
        AsyncRepeatUntil.repeatUntil(
            // task fails in an async manner
            () -> CompletableFuture.failedFuture(new RuntimeException("future failed")),
            result -> true);

    // then
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(future::join)
        .isInstanceOf(CompletionException.class)
        .cause()
        .hasMessageContaining("future failed");
  }

  @Test
  void shouldCompleteExceptionallyWhenTasksFailsSync() {
    // given
    // when
    final var future =
        AsyncRepeatUntil.repeatUntil(
            () -> {
              // task fails, but in a non-async way
              throw new RuntimeException("task failed");
            },
            result -> true);

    // then
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(future::join)
        .isInstanceOf(CompletionException.class)
        .cause()
        .hasMessageContaining("task failed");
  }

  @Test
  void shouldCompleteExceptionallyWhenUntilFails() {
    // given
    // when
    final var future =
        AsyncRepeatUntil.repeatUntil(
            () -> CompletableFuture.completedFuture(1),
            result -> {
              throw new RuntimeException("until failed");
            });

    // then
    //    assertThat(future.isDone()).isTrue();
    assertThat(future).failsWithin(Duration.ofSeconds(2));
    assertThatThrownBy(future::join)
        .isInstanceOf(CompletionException.class)
        .cause()
        .hasMessageContaining("until failed");
  }

  @Test
  void shouldCompleteWhenConditionMet() {
    // given
    final var counter = new AtomicInteger();

    // when
    final var future =
        AsyncRepeatUntil.repeatUntil(
            () -> CompletableFuture.completedFuture(counter.getAndIncrement()),
            result -> result >= 5);

    future.join();

    // then
    assertThat(counter.get()).isEqualTo(6);
  }

  @Test
  void shouldRunTasksSerially() {
    // given
    final CompletableFuture<Integer> future1 = new CompletableFuture<>();
    final CompletableFuture<Integer> future2 = new CompletableFuture<>();

    final Supplier<CompletableFuture<Integer>> taskSupplier = mock(Supplier.class);

    when(taskSupplier.get()).thenReturn(future1).thenReturn(future2);

    // when
    final var future = AsyncRepeatUntil.repeatUntil(taskSupplier, result -> result >= 2);

    // then
    assertThat(future.isDone()).isFalse();
    verify(taskSupplier).get();

    // when
    // complete the first task, which should trigger the second task to be called
    future1.complete(1);

    // then
    assertThat(future.isDone()).isFalse();
    Awaitility.await().untilAsserted(() -> verify(taskSupplier, times(2)).get());

    // when
    // complete the second task, which should complete the future
    future2.complete(2);

    // then
    assertThat(future).succeedsWithin(Duration.ofSeconds(2));
    verify(taskSupplier, times(2)).get();

    future.join();
  }

  @Test
  void shouldContinueOnCompletingThreadInsteadOfCommonPool() {
    // given
    // tasks are completed from a dedicated thread, so that the thread used to run the next
    // task reveals whether the continuation was handed off to the common pool
    final var completer = Executors.newSingleThreadExecutor(r -> new Thread(r, "test-completer"));
    final var taskThreadNames = new CopyOnWriteArrayList<String>();
    final var pendingTasks = new ConcurrentLinkedQueue<CompletableFuture<Integer>>();
    final Supplier<CompletableFuture<Integer>> taskSupplier =
        () -> {
          taskThreadNames.add(Thread.currentThread().getName());
          final var task = new CompletableFuture<Integer>();
          pendingTasks.add(task);
          return task;
        };

    try {
      // when
      final var future = AsyncRepeatUntil.repeatUntil(taskSupplier, result -> result >= 3);
      for (int i = 1; i <= 3; i++) {
        final var result = i;
        Awaitility.await().until(() -> !pendingTasks.isEmpty());
        final var task = pendingTasks.poll();
        completer.execute(() -> task.complete(result));
      }

      // then
      assertThat(future).succeedsWithin(Duration.ofSeconds(5));
      assertThat(taskThreadNames).hasSize(3);
      // the first task runs on the calling thread; every subsequent one must run on the thread
      // that completed the previous task, never on a common pool thread
      final var threadNamesAfterFirstTask = taskThreadNames.subList(1, taskThreadNames.size());
      assertThat(threadNamesAfterFirstTask).containsOnly("test-completer");
    } finally {
      completer.shutdownNow();
    }
  }

  @Test
  void shouldNotOverflowStackWhenTasksCompleteSynchronously() {
    // given
    final var counter = new AtomicInteger();

    // when
    final var future =
        AsyncRepeatUntil.repeatUntil(
            () -> CompletableFuture.completedFuture(counter.incrementAndGet()),
            result -> result >= 100_000);

    // then
    assertThat(future).succeedsWithin(Duration.ofSeconds(10));
    assertThat(counter).hasValue(100_000);
  }
}
