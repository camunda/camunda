/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SemaphoreLeasedSchedulerTest {

  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Test
  void shouldReleasePermitWhenTaskCompletes() throws Exception {
    // given
    final var released = new CountDownLatch(1);
    final var semaphore = releaseSignallingSemaphore(1, released);

    // when
    final var result = SemaphoreLeasedScheduler.schedule(() -> "done", executor, semaphore);

    // then
    assertThat(result.join()).isEqualTo("done");
    assertThat(released.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(semaphore.availablePermits()).isOne();
  }

  @Test
  void shouldReleasePermitWhenTaskThrows() throws Exception {
    // given
    final var released = new CountDownLatch(1);
    final var semaphore = releaseSignallingSemaphore(1, released);

    // when
    final var result =
        SemaphoreLeasedScheduler.schedule(
            () -> {
              throw new RuntimeException("boom");
            },
            executor,
            semaphore);

    // then
    assertThatThrownBy(result::join).hasRootCauseMessage("boom");
    assertThat(released.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(semaphore.availablePermits()).isOne();
  }

  @Test
  void shouldNotReleasePermitWhenAcquireIsInterrupted() throws Exception {
    // given - a semaphore with no available permits; the worker thread will block in acquire().
    // The latch signals when the worker has entered acquire() and is about to (or already does)
    // block, so the interrupt below is guaranteed to hit a blocked/blocking acquire.
    final var enteredAcquire = new CountDownLatch(1);
    final var releaseCalled = new CountDownLatch(1);
    final var semaphore =
        new Semaphore(1) {
          @Override
          public void acquire() throws InterruptedException {
            enteredAcquire.countDown();
            super.acquire();
          }

          @Override
          public void release() {
            super.release();
            releaseCalled.countDown();
          }
        };
    semaphore.acquireUninterruptibly(); // drain the only permit
    assertThat(semaphore.availablePermits()).isZero();

    // when - a task is scheduled while no permit is available, then the worker is interrupted
    final var result = SemaphoreLeasedScheduler.schedule(() -> "done", executor, semaphore);
    assertThat(enteredAcquire.await(5, TimeUnit.SECONDS)).isTrue();
    executor.shutdownNow();

    // then - the future fails with an InterruptedException and the permit is NOT released
    assertThatThrownBy(result::join)
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(InterruptedException.class);
    // release() is never invoked, so the drained permit count stays at zero
    assertThat(releaseCalled.await(500, TimeUnit.MILLISECONDS)).isFalse();
    assertThat(semaphore.availablePermits()).isZero();
  }

  @Test
  void shouldNeverExceedPermitLimitUnderConcurrentLoad() throws Exception {
    // given - far more concurrent tasks than permits, on a bounded pool. schedule() acquires, runs
    // and releases within a single submitted unit of work, so a freed permit immediately unblocks a
    // worker parked in acquire() - no extra thread is needed to release, hence no deadlock even
    // when
    // every pool thread is contending for a permit.
    final int permits = 4;
    final int tasks = 200; // multiple of permits so the barrier batches divide evenly
    final var semaphore = new Semaphore(permits);
    final var pool = Executors.newFixedThreadPool(permits * 4);

    // A barrier of `permits` parties forces exactly that many tasks to run simultaneously: it only
    // trips once `permits` tasks are inside the body at the same time. If the scheduler ever let
    // fewer permits through, the barrier would never trip and the test would time out; if it let
    // more through, the observed concurrency below would exceed the limit.
    final var barrier = new CyclicBarrier(permits);
    final var running = new AtomicInteger();
    final var maxObserved = new AtomicInteger();

    try {
      // when - all tasks are scheduled concurrently
      final List<CompletableFuture<Integer>> futures =
          IntStream.range(0, tasks)
              .mapToObj(
                  i ->
                      SemaphoreLeasedScheduler.schedule(
                          () -> {
                            final int concurrent = running.incrementAndGet();
                            maxObserved.accumulateAndGet(concurrent, Math::max);
                            try {
                              barrier.await(10, TimeUnit.SECONDS);
                              return i;
                            } catch (final Exception e) {
                              throw new RuntimeException(e);
                            } finally {
                              running.decrementAndGet();
                            }
                          },
                          pool,
                          semaphore))
              .toList();

      // then - all tasks complete, never more than `permits` ran at once, and the limit was fully
      // used (the barrier could only trip because exactly `permits` tasks ran together)
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);
      assertThat(futures.stream().map(CompletableFuture::join))
          .containsExactlyInAnyOrderElementsOf(IntStream.range(0, tasks).boxed().toList());
      assertThat(maxObserved.get()).isEqualTo(permits);
      assertThat(running.get()).isZero();
      // every acquired permit was released
      assertThat(semaphore.availablePermits()).isEqualTo(permits);
    } finally {
      pool.shutdownNow();
    }
  }

  private static Semaphore releaseSignallingSemaphore(
      final int permits, final CountDownLatch released) {
    return new Semaphore(permits) {
      @Override
      public void release() {
        super.release();
        released.countDown();
      }
    };
  }
}
