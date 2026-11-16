/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.lang.Thread.State;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
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
  class ThrowingCallback {
    @Test
    void shouldNotifyRemainingCallbacksWhenCallbackThrowsError() {
      // given
      final var future = new CompletableActorFuture<Integer>();
      final var notified = new AtomicReference<Integer>();
      future.onComplete(
          (value, error) -> {
            throw new AssertionError("callback failure");
          },
          Runnable::run);
      future.onComplete((value, error) -> notified.set(value), Runnable::run);

      // when
      future.complete(1);

      // then
      assertThat(notified).hasValue(1);
    }

    @Test
    void shouldNotThrowCallbackFailureToCompleter() {
      // given
      final var future = new CompletableActorFuture<Integer>();
      future.onComplete(
          (value, error) -> {
            throw new RuntimeException("callback failure");
          },
          Runnable::run);

      // when
      final var thrown = catchThrowable(() -> future.complete(1));

      // then
      assertThat(thrown).isNull();
      assertThat(future).succeedsWithin(Duration.ZERO).isEqualTo(1);
    }

    @Test
    void shouldWakeBlockedWaiter() {
      // given
      final var future = new CompletableActorFuture<Integer>();
      future.onComplete(
          (value, error) -> {
            throw new RuntimeException("callback failure");
          },
          Runnable::run);
      final var waiter = new Thread(future::join);
      waiter.setDaemon(true);
      waiter.start();
      try {
        Awaitility.await("waiter blocks on the future")
            .until(() -> waiter.getState() == State.TIMED_WAITING);

        // when
        future.complete(1);

        // then
        Awaitility.await("waiter is woken up").until(() -> !waiter.isAlive());
      } finally {
        waiter.interrupt();
      }
    }
  }

  @Nested
  class FailingContinuation {
    @Test
    void shouldNotThrowExecutorRejectionWhenRegisteredOnCompletedFuture() {
      // given
      final var future = CompletableActorFuture.completed(1);

      // when
      final var thrown =
          catchThrowable(
              () ->
                  future.onComplete(
                      (value, error) -> {},
                      task -> {
                        throw new RejectedExecutionException("executor is shut down");
                      }));

      // then
      assertThat(thrown).isNull();
    }

    @Test
    void shouldFailChainedFutureWhenExecutorRejects() {
      // given
      final var future = CompletableActorFuture.completed(1);

      // when
      final var next =
          future.andThen(value -> CompletableActorFuture.completed(2), rejectingExecutor());

      // then
      assertThat(next)
          .failsWithin(Duration.ZERO)
          .withThrowableThat()
          .havingCause()
          .isInstanceOf(RejectedExecutionException.class);
    }

    @Test
    void shouldFailChainedFutureWhenExecutorRejectsItsResult() {
      // given
      final var future = CompletableActorFuture.completed(1);
      final var accepted = new AtomicBoolean();
      final Executor executor =
          task -> {
            if (!accepted.compareAndSet(false, true)) {
              throw new RejectedExecutionException("executor is shut down");
            }
            task.run();
          };

      // when
      final var next = future.andThen(value -> CompletableActorFuture.completed(2), executor);

      // then
      assertThat(next)
          .failsWithin(Duration.ZERO)
          .withThrowableThat()
          .havingCause()
          .isInstanceOf(RejectedExecutionException.class);
    }

    @Test
    void shouldFailAppliedFutureWhenMappingThrowsError() {
      // given
      final var future = CompletableActorFuture.completed(1);

      // when
      final var next =
          future.thenApply(
              value -> {
                throw new AssertionError("mapping failure");
              },
              Runnable::run);

      // then
      assertThat(next)
          .failsWithin(Duration.ZERO)
          .withThrowableThat()
          .havingRootCause()
          .isInstanceOf(AssertionError.class);
    }

    @Test
    void shouldFailChainedFutureWhenContinuationThrowsError() {
      // given
      final var future = CompletableActorFuture.completed(1);

      // when
      final ActorFuture<Integer> next =
          future.andThen(
              value -> {
                throw new AssertionError("continuation failure");
              },
              Runnable::run);

      // then
      assertThat(next)
          .failsWithin(Duration.ZERO)
          .withThrowableThat()
          .havingRootCause()
          .isInstanceOf(AssertionError.class);
    }

    @Test
    void shouldFailAppliedFutureWhenExecutorThrowsError() {
      // given
      final var future = CompletableActorFuture.completed(1);

      // when
      final var next =
          future.thenApply(
              value -> value + 1,
              task -> {
                throw new AssertionError("executor failure");
              });

      // then
      assertThat(next)
          .failsWithin(Duration.ZERO)
          .withThrowableThat()
          .havingCause()
          .isInstanceOf(AssertionError.class);
    }

    private static Executor rejectingExecutor() {
      return task -> {
        throw new RejectedExecutionException("executor is shut down");
      };
    }
  }
}
