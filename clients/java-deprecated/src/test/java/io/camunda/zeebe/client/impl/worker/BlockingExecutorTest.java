/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
