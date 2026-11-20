/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.common;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.common.BackupReadRateLimiter.Scheduler;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/** Blocks the common pool, so it must not run next to other tests. */
@Isolated
final class BackupReadRateLimiterSchedulerTest {

  @Test
  void shouldFreeSlotsWhileTheCommonPoolIsBusy() throws Exception {
    // given - closing a backup store blocks common pool threads for up to a minute
    final int parallelism = ForkJoinPool.commonPool().getParallelism();
    final var blocked = new CountDownLatch(parallelism);
    final var unblock = new CountDownLatch(1);
    for (int i = 0; i < parallelism; i++) {
      ForkJoinPool.commonPool()
          .execute(
              () -> {
                blocked.countDown();
                awaitUninterruptibly(unblock);
              });
    }
    final var released = new CountDownLatch(1);

    try {
      assertThat(blocked.await(30, TimeUnit.SECONDS)).isTrue();

      // when
      Scheduler.DELAYED.schedule(TimeUnit.MILLISECONDS.toNanos(1), released::countDown);

      // then - freeing a slot must not wait for those threads
      assertThat(released.await(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS)).isTrue();
    } finally {
      unblock.countDown();
    }
  }

  private static void awaitUninterruptibly(final CountDownLatch latch) {
    boolean interrupted = false;
    while (true) {
      try {
        latch.await();
        break;
      } catch (final InterruptedException e) {
        interrupted = true;
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }
}
