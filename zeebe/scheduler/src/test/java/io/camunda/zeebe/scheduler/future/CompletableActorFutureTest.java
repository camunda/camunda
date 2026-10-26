/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class CompletableActorFutureTest {

  @AutoClose private static final ExecutorService EXECUTOR = Executors.newWorkStealingPool();

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
  public class Callbacks {

    @Test
    void shouldNotifySingleCallback() {
      // given
      final var future = new CompletableActorFuture<String>();
      final var received = new ArrayList<String>();
      future.onComplete((value, error) -> received.add(value), Runnable::run);

      // when
      future.complete("done");

      // then
      assertThat(received).containsExactly("done");
      assertThat(future.pendingCallbackCount()).isZero();
    }

    @Test
    void shouldNotifyAllCallbacksInRegistrationOrder() {
      // given
      final var future = new CompletableActorFuture<String>();
      final var order = new ArrayList<Integer>();
      for (int i = 0; i < 5; i++) {
        final int index = i;
        future.onComplete((value, error) -> order.add(index), Runnable::run);
      }
      assertThat(future.pendingCallbackCount()).isEqualTo(5);

      // when
      future.complete("done");

      // then
      assertThat(order).containsExactly(0, 1, 2, 3, 4);
      assertThat(future.pendingCallbackCount()).isZero();
    }

    @Test
    void shouldNotifyCallbackAddedByCallback() {
      // given
      final var future = new CompletableActorFuture<String>();
      final var received = new ArrayList<String>();
      future.onComplete(
          (value, error) -> future.onComplete((v, e) -> received.add(v), Runnable::run),
          Runnable::run);

      // when
      future.complete("done");

      // then
      assertThat(received).containsExactly("done");
    }

    @Test
    void shouldAcceptCallbacksAfterReuse() {
      // given
      final var future = new CompletableActorFuture<String>();
      future.onComplete((value, error) -> {}, Runnable::run);
      future.close();
      future.setAwaitingResult();
      final var received = new ArrayList<String>();

      // when
      future.onComplete((value, error) -> received.add(value), Runnable::run);
      future.complete("again");

      // then
      assertThat(received).containsExactly("again");
    }
  }

  @Nested
  public class BlockingGet {

    @Test
    void shouldNotRetainCallbacksForRepeatedTimeouts() {
      // given
      final var future = new CompletableActorFuture<String>();

      // when
      for (int i = 0; i < 100; i++) {
        assertThatThrownBy(() -> future.get(1, TimeUnit.MILLISECONDS))
            .isInstanceOf(TimeoutException.class);
      }

      // then
      assertThat(future.pendingCallbackCount()).isLessThanOrEqualTo(1);
    }

    @Test
    void shouldNotRetainCallbacksForInterruptedWaits() {
      // given
      final var future = new CompletableActorFuture<String>();

      // when
      for (int i = 0; i < 100; i++) {
        Thread.currentThread().interrupt();
        assertThatThrownBy(() -> future.get(1, TimeUnit.MINUTES))
            .isInstanceOf(InterruptedException.class);
      }

      // then
      assertThat(future.pendingCallbackCount()).isLessThanOrEqualTo(1);
    }

    @Test
    void shouldNotRetainCallbacksWhenWaitersRaceToInstallLatch() throws Exception {
      // given
      final var threads = 8;
      final var barrier = new CyclicBarrier(threads);
      final var executor = Executors.newFixedThreadPool(threads);
      try {
        for (int round = 0; round < 200; round++) {
          final var future = new CompletableActorFuture<String>();

          // when all waiters hit the not yet existing latch at the same time
          final var waiters =
              IntStream.range(0, threads)
                  .mapToObj(
                      i ->
                          executor.submit(
                              () -> {
                                barrier.await();
                                assertThatThrownBy(() -> future.get(1, TimeUnit.MILLISECONDS))
                                    .isInstanceOf(TimeoutException.class);
                                return null;
                              }))
                  .toList();
          for (final var waiter : waiters) {
            waiter.get(30, TimeUnit.SECONDS);
          }

          // then
          assertThat(future.pendingCallbackCount()).isLessThanOrEqualTo(1);
        }
      } finally {
        executor.shutdownNow();
      }
    }

    @Test
    void shouldWakeUpAllWaitersOnCompletion() throws Exception {
      // given
      final var future = new CompletableActorFuture<String>();
      final var executor = Executors.newFixedThreadPool(3);
      try {
        final var waiters =
            IntStream.range(0, 3)
                .mapToObj(i -> executor.submit(() -> future.get(10, TimeUnit.SECONDS)))
                .toList();

        // when
        future.complete("done");

        // then
        for (final var waiter : waiters) {
          assertThat(waiter.get(10, TimeUnit.SECONDS)).isEqualTo("done");
        }
      } finally {
        executor.shutdownNow();
      }
    }

    @Test
    void shouldWaitAgainAfterFutureIsReused() throws Exception {
      // given
      final var future = new CompletableActorFuture<String>();
      future.complete("first");
      assertThat(future.get()).isEqualTo("first");
      future.close();
      future.setAwaitingResult();
      assertThatThrownBy(() -> future.get(1, TimeUnit.MILLISECONDS))
          .isInstanceOf(TimeoutException.class);

      // when
      final var executor = Executors.newSingleThreadExecutor();
      try {
        final var waiter = executor.submit(() -> future.get(10, TimeUnit.SECONDS));
        future.complete("second");

        // then
        assertThat(waiter.get(10, TimeUnit.SECONDS)).isEqualTo("second");
      } finally {
        executor.shutdownNow();
      }
    }
  }
}
