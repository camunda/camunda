/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.impl.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.util.concurrent.Uninterruptibles;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

public class BlockingExecutorTest {

  @Test
  public void shouldExecuteRunnable() {
    // given
    final AtomicBoolean atomicBoolean = new AtomicBoolean(false);
    final BlockingExecutor executor = new BlockingExecutor(Runnable::run, 1, Duration.ofMillis(10));

    // when
    executor.execute(() -> atomicBoolean.set(true));

    // then
    assertThat(atomicBoolean).isTrue();
  }

  @Test
  public void shouldThrowRejectOnFull() {
    // given
    final Executor noop = command -> {};
    final BlockingExecutor executor = new BlockingExecutor(noop, 1, Duration.ofMillis(10));

    // when - then throw
    executor.execute(() -> {});
    assertThatThrownBy(() -> executor.execute(() -> {}))
        .isInstanceOf(RejectedExecutionException.class);
  }

  @Test
  public void shouldReleaseAndRun() {
    // given
    final ExecutorService wrappedExecutor = Executors.newSingleThreadExecutor();
    try {
      final BlockingExecutor executor =
          new BlockingExecutor(wrappedExecutor, 1, Duration.ofSeconds(1));
      final AtomicBoolean atomicBoolean = new AtomicBoolean(false);
      final CountDownLatch countDownLatch = new CountDownLatch(1);
      executor.execute(() -> Uninterruptibles.awaitUninterruptibly(countDownLatch));

      // when
      new Thread(() -> executor.execute(() -> atomicBoolean.set(true))).start();

      // then
      assertThat(atomicBoolean).isFalse();
      countDownLatch.countDown();

      Awaitility.await("Second runnable should be executed after latch is released")
          .untilAtomic(atomicBoolean, Matchers.equalTo(true));
    } finally {
      wrappedExecutor.shutdownNow();
    }
  }
}
