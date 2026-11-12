/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class CompletableActorFutureTest {

  @AutoClose private static final ExecutorService EXECUTOR = Executors.newWorkStealingPool();

  @Nested
  public class CompletionTimestamp {
    @Test
    public void shouldBeSetWhenCreatedCompleted() {
      // when
      final var future = CompletableActorFuture.completed();

      // then
      assertThat(future.getCompletedAt()).isNotZero();
    }

    @Test
    public void shouldBeSetWhenCreatedCompletedExceptionally() {
      // when
      final var future = CompletableActorFuture.completedExceptionally(new RuntimeException());

      // then
      assertThat(future.getCompletedAt()).isNotZero();
    }

    @Test
    public void shouldBeSetWhenCompletedExceptionally() {
      // given
      final var future = new CompletableActorFuture<Void>();

      // when
      future.completeExceptionally(new RuntimeException());

      // then
      assertThat(future.getCompletedAt()).isNotZero();
    }
  }

  @Nested
  public class Catching {
    @Test
    public void shouldReturnExceptionalFutureWhenSupplierThrows() {
      // given
      final Supplier<ActorFuture<Void>> supplier =
          () -> {
            throw new RuntimeException("Expected");
          };

      // when
      final var future = CompletableActorFuture.catching(supplier);

      assertThat(future.getException()).hasMessage("Expected");
    }

    @Test
    public void shouldReturnFutureWhenSupplierCompletes() {
      // given
      final Supplier<ActorFuture<String>> supplier =
          () -> CompletableActorFuture.completed("value");

      // when
      final var future = CompletableActorFuture.catching(supplier);

      // then
      assertThat(future.join()).isEqualTo("value");
    }
  }

  @Nested
  class VoidContinuations {
    @Test
    public void shouldConsumeFutureResult() {
      // given
      final var consumed = new AtomicReference<String>();

      // when
      final var future =
          CompletableActorFuture.completed("value").thenAccept(consumed::set, EXECUTOR);

      // then
      assertThat(future.join()).isNull();
      assertThat(consumed).hasValue("value");
    }

    @Test
    public void shouldRunAfterFutureCompletes() {
      // given
      final var ran = new AtomicBoolean();

      // when
      final var future =
          CompletableActorFuture.completed("value").thenRun(() -> ran.set(true), EXECUTOR);

      // then
      assertThat(future.join()).isNull();
      assertThat(ran).isTrue();
    }

    @Test
    public void shouldPropagateFailureWithoutRunningContinuation() {
      // given
      final var ran = new AtomicBoolean();
      final var exception = new RuntimeException("Expected");

      // when
      final var future =
          CompletableActorFuture.completedExceptionally(exception)
              .thenRun(() -> ran.set(true), EXECUTOR);

      // then
      assertThat(future)
          .failsWithin(Duration.ofSeconds(5))
          .withThrowableThat()
          .withMessageContaining("Expected");
      assertThat(ran).isFalse();
    }
  }

  @Nested
  class TraverseIgnoring {
    @Test
    public void shouldReturnCompletedFutureWhenCollectionIsEmpty() {
      assertThat(
              CompletableActorFuture.traverseIgnoring(
                  List.of(),
                  a ->
                      CompletableActorFuture.<Void>completedExceptionally(
                          new RuntimeException("Expected")),
                  EXECUTOR))
          .isDone();
    }

    @Test
    public void shouldRunAllFuturesSequentially() {
      // given
      final var count = 1000;
      final var list = new ArrayBlockingQueue<>(count);
      final var elements = IntStream.range(0, count).boxed().toList();
      // when
      CompletableActorFuture.traverseIgnoring(
              elements,
              i -> {
                list.add(i);
                return CompletableActorFuture.completed();
              },
              EXECUTOR)
          .join();
      // then
      assertThat(list).containsExactlyElementsOf(elements);
    }

    @Test
    public void shouldStopAndReturnErrorIfAFutureFails() {
      // given
      final var map = new ConcurrentHashMap<>();
      // when
      final var future =
          CompletableActorFuture.traverseIgnoring(
              List.of(1, 2, 3),
              i -> {
                if (i % 2 == 0) {
                  return CompletableActorFuture.completedExceptionally(
                      new RuntimeException("Expected"));
                }
                map.put(i, i);
                return CompletableActorFuture.completed();
              },
              EXECUTOR);
      // then
      assertThat(future)
          .failsWithin(Duration.ofSeconds(5))
          .withThrowableThat()
          .withMessageContaining("Expected");
      // does not contain 3 as the computation is not spawned
      assertThat(map.keySet()).containsExactly(1);
    }
  }

  @Nested
  class BlockingGet {
    private final List<Thread> threads = new ArrayList<>();

    @AfterEach
    void tearDown() throws InterruptedException {
      for (final Thread thread : threads) {
        thread.interrupt();
        thread.join();
      }
    }

    @Test
    void shouldWakeUpAllBlockedThreadsOnCompletion() {
      // given
      final var future = new CompletableActorFuture<String>();
      final var results =
          IntStream.range(0, 3)
              .mapToObj(i -> blockOn(() -> future.get(1, TimeUnit.MINUTES)))
              .toList();

      // when
      future.complete("done");

      // then
      assertThat(results)
          .allSatisfy(
              result -> assertThat(result).succeedsWithin(Duration.ofSeconds(5)).isEqualTo("done"));
    }

    @Test
    void shouldReturnFailureToBlockedThread() {
      // given
      final var future = new CompletableActorFuture<String>();
      final var failure = new RuntimeException("expected");
      final var result = blockOn(() -> future.get(1, TimeUnit.MINUTES));

      // when
      future.completeExceptionally(failure);

      // then
      assertThat(result)
          .failsWithin(Duration.ofSeconds(5))
          .withThrowableThat()
          .havingRootCause()
          .isSameAs(failure);
    }

    @Test
    void shouldThrowWhenInterruptedWhileWaiting() {
      // given
      final var future = new CompletableActorFuture<String>();
      final var result = blockOn(() -> future.get(1, TimeUnit.MINUTES));

      // when
      threads.getFirst().interrupt();

      // then
      assertThat(result)
          .failsWithin(Duration.ofSeconds(5))
          .withThrowableThat()
          .withCauseInstanceOf(InterruptedException.class);
    }

    @Test
    void shouldWakeUpBlockedThreadWhenClosedFutureIsReusedAndCompleted() {
      // given
      final var future = new CompletableActorFuture<String>();
      final var result = blockOn(() -> future.get(1, TimeUnit.MINUTES));

      // when - closing wakes the thread without completing the future, so it has to keep waiting
      future.close();
      future.setAwaitingResult();
      awaitBlocked(threads.getFirst());
      future.complete("reused");

      // then
      assertThat(result).succeedsWithin(Duration.ofSeconds(5)).isEqualTo("reused");
    }

    /** Runs the call on a new non-actor thread and returns once that thread is blocked. */
    private <T> CompletableFuture<T> blockOn(final Callable<T> call) {
      final var result = new CompletableFuture<T>();
      final var thread =
          new Thread(
              () -> {
                try {
                  result.complete(call.call());
                } catch (final Exception e) {
                  result.completeExceptionally(e);
                }
              });
      threads.add(thread);
      thread.start();
      awaitBlocked(thread);
      return result;
    }

    private void awaitBlocked(final Thread thread) {
      await()
          .until(
              () ->
                  thread.getState() == Thread.State.WAITING
                      || thread.getState() == Thread.State.TIMED_WAITING);
    }
  }

  @Nested
  class Callbacks {
    @Test
    void shouldNotifyCallbacksInRegistrationOrder() {
      // given
      final var future = new CompletableActorFuture<String>();
      final var notified = new ArrayList<Integer>();
      IntStream.range(0, 5)
          .forEach(i -> future.onComplete((value, error) -> notified.add(i), Runnable::run));

      // when
      future.complete("done");

      // then
      assertThat(notified).containsExactly(0, 1, 2, 3, 4);
    }

    @Test
    void shouldNotifyCallbackRegisteredAfterCompletion() {
      // given
      final var future = new CompletableActorFuture<String>();
      future.complete("done");
      final var result = new AtomicReference<String>();

      // when
      future.onComplete((value, error) -> result.set(value), Runnable::run);

      // then
      assertThat(result).hasValue("done");
    }
  }
}
