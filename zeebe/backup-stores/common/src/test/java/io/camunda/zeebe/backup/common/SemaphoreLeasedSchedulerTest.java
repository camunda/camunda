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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
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
  void shouldReleasePermitWhenAsyncFutureFails() throws Exception {
    // given
    final var released = new CountDownLatch(1);
    final var semaphore = releaseSignallingSemaphore(1, released);

    // when
    final var result =
        SemaphoreLeasedScheduler.scheduleAsync(
            () -> CompletableFuture.failedFuture(new RuntimeException("boom")),
            executor,
            semaphore);

    // then
    assertThatThrownBy(result::join).hasRootCauseMessage("boom");
    assertThat(released.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(semaphore.availablePermits()).isOne();
  }

  @Test
  void shouldHoldPermitUntilAsyncFutureCompletes() throws Exception {
    // given
    final var acquired = new CountDownLatch(1);
    final var released = new CountDownLatch(1);
    final var semaphore =
        new Semaphore(1) {
          @Override
          public void acquire() throws InterruptedException {
            super.acquire();
            acquired.countDown();
          }

          @Override
          public void release() {
            super.release();
            released.countDown();
          }
        };
    final var inner = new CompletableFuture<String>();

    // when - the task returns a future that is not yet completed
    final var result = SemaphoreLeasedScheduler.scheduleAsync(() -> inner, executor, semaphore);

    // then - the permit is held while the inner future is pending
    assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(semaphore.availablePermits()).isZero();
    assertThat(result).isNotDone();

    // when - the inner future completes
    inner.complete("done");

    // then - the result completes and the permit is released
    assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo("done");
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
