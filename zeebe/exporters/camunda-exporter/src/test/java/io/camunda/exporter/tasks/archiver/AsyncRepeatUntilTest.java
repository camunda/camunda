/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class AsyncRepeatUntilTest {

  @Test
  void shouldCompleteWhenTaskCompletes() {
    // given
    // when
    final var future =
        AsyncRepeatUntil.repeatUntil(() -> CompletableFuture.completedFuture(1), result -> true);

    // then
    assertThat(future.isDone()).isTrue();
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
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCompletedExceptionally()).isTrue();
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
    verify(taskSupplier, times(2)).get();

    // when
    // complete the second task, which should complete the future
    future2.complete(2);

    // then
    assertThat(future.isDone()).isTrue();
    verify(taskSupplier, times(2)).get();

    future.join();
  }
}
