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
package io.camunda.client.impl.worker;

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
import java.util.concurrent.atomic.AtomicReference;
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
  public void shouldReleaseCapacityWhenWrappedExecutorRejectsCommand() {
    // given a wrapped executor that refuses the first command and runs every later one
    final AtomicBoolean refuseNextCommand = new AtomicBoolean(true);
    final Executor wrappedExecutor =
        command -> {
          if (refuseNextCommand.compareAndSet(true, false)) {
            throw new RejectedExecutionException("Wrapped executor is saturated");
          }
          command.run();
        };
    final BlockingExecutor executor =
        new BlockingExecutor(wrappedExecutor, 1, Duration.ofMillis(10));

    // when the wrapped executor refuses the command
    assertThatThrownBy(() -> executor.execute(() -> {}))
        .isInstanceOf(RejectedExecutionException.class);

    // then the capacity taken for that command is free again
    final AtomicBoolean executed = new AtomicBoolean(false);
    executor.execute(() -> executed.set(true));
    assertThat(executed).isTrue();
  }

  @Test
  public void shouldKeepCapacityLimitWhenCommandFailsOnTheCallingThread() {
    // given an executor whose wrapped executor runs commands on the calling thread
    final AtomicReference<Executor> wrappedExecutor = new AtomicReference<>(Runnable::run);
    final BlockingExecutor executor =
        new BlockingExecutor(
            command -> wrappedExecutor.get().execute(command), 1, Duration.ofMillis(10));

    // when a command fails, so that the failure reaches the caller the same way a refusal would
    assertThatThrownBy(
            () ->
                executor.execute(
                    () -> {
                      throw new IllegalStateException("Command failed");
                    }))
        .isInstanceOf(IllegalStateException.class);

    // then the one capacity slot the command took is free again, but only once: an executor that
    // holds on to a command holds on to its capacity too, leaving nothing for a second command
    wrappedExecutor.set(command -> {});
    executor.execute(() -> {});
    assertThatThrownBy(() -> executor.execute(() -> {}))
        .isInstanceOf(RejectedExecutionException.class);
  }

  @Test
  public void shouldRejectCommandWhenInterruptedWhileWaitingForCapacity() {
    // given an executor whose only capacity is taken
    final Executor dropsCommands = command -> {};
    final BlockingExecutor executor = new BlockingExecutor(dropsCommands, 1, Duration.ofMinutes(1));
    executor.execute(() -> {});

    final AtomicBoolean executed = new AtomicBoolean(false);
    final AtomicBoolean interruptFlagRestored = new AtomicBoolean(false);
    final AtomicReference<Throwable> failure = new AtomicReference<>();
    final Thread caller =
        new Thread(
            () -> {
              try {
                executor.execute(() -> executed.set(true));
              } catch (final Throwable t) {
                failure.set(t);
              } finally {
                interruptFlagRestored.set(Thread.currentThread().isInterrupted());
              }
            });
    caller.start();
    Awaitility.await("Caller should be waiting for capacity")
        .until(caller::getState, Matchers.equalTo(Thread.State.TIMED_WAITING));

    // when the caller is interrupted before capacity becomes available
    caller.interrupt();
    Awaitility.await("Caller should stop waiting once interrupted").until(() -> !caller.isAlive());

    // then the command is refused instead of being dropped without notice
    assertThat(failure.get()).isInstanceOf(RejectedExecutionException.class);
    assertThat(executed).isFalse();
    assertThat(interruptFlagRestored).isTrue();
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
