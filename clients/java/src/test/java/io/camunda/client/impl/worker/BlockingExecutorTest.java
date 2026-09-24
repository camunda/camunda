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
import java.util.concurrent.atomic.AtomicInteger;
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

  @Test
  public void shouldRefuseCommandRightAwayWhenThereIsNoCapacityLeft() {
    // given an executor whose only capacity is taken, and that is allowed to wait a long time for
    // capacity to free up
    final Executor dropsCommands = command -> {};
    final BlockingExecutor executor = new BlockingExecutor(dropsCommands, 1, Duration.ofMinutes(5));
    executor.execute(() -> {});

    // when a command is handed over without waiting
    final long startedAt = System.nanoTime();
    assertThatThrownBy(() -> executor.executeWithoutWaiting(() -> {}))
        .isInstanceOf(RejectedExecutionException.class);

    // then it is refused on the spot instead of holding on to the calling thread
    assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofMinutes(1));
  }

  @Test
  public void shouldRunCommandWithoutWaitingWhenThereIsCapacityLeft() {
    // given
    final AtomicBoolean executed = new AtomicBoolean(false);
    final BlockingExecutor executor = new BlockingExecutor(Runnable::run, 1, Duration.ofMillis(10));

    // when
    executor.executeWithoutWaiting(() -> executed.set(true));

    // then
    assertThat(executed).isTrue();
  }

  @Test
  public void shouldNotifyTheListenerWithAccurateCapacityAfterReleasingASlot() {
    // given a single-slot executor whose listener reads the free capacity when it runs
    final BlockingExecutor executor = new BlockingExecutor(Runnable::run, 1, Duration.ofMillis(10));
    final AtomicInteger capacitySeenByListener = new AtomicInteger(-1);
    final AtomicInteger notifications = new AtomicInteger();
    executor.onCapacityAvailable(
        () -> {
          capacitySeenByListener.set(executor.freeCapacity());
          notifications.incrementAndGet();
        });

    // when a command takes the slot and finishes
    executor.execute(() -> {});

    // then the listener was told once, after the slot was back, and saw it as free. A worker sizes
    // its next poll from this callback, so a reading taken before the release would be one slot too
    // few.
    assertThat(notifications).hasValue(1);
    assertThat(capacitySeenByListener).hasValue(1);
  }

  @Test
  public void shouldReportNoJobsInFlightOnlyWhenEverySlotIsFree() {
    // given a two-slot executor with a command holding one slot
    final CountDownLatch releaseCommand = new CountDownLatch(1);
    final ExecutorService wrappedExecutor = Executors.newSingleThreadExecutor();
    try {
      final BlockingExecutor executor =
          new BlockingExecutor(wrappedExecutor, 2, Duration.ofSeconds(1));
      assertThat(executor.hasNoJobsInFlight()).isTrue();

      // when a command takes one of the two slots
      executor.execute(() -> Uninterruptibles.awaitUninterruptibly(releaseCommand));

      // then it reports a job in flight until that command gives its slot back
      assertThat(executor.hasNoJobsInFlight()).isFalse();
      releaseCommand.countDown();
      Awaitility.await("No jobs should be in flight once the command is done")
          .untilAsserted(() -> assertThat(executor.hasNoJobsInFlight()).isTrue());
    } finally {
      releaseCommand.countDown();
      wrappedExecutor.shutdownNow();
    }
  }

  @Test
  public void shouldReportTheCapacityItHasLeft() {
    // given
    final CountDownLatch releaseCommand = new CountDownLatch(1);
    final ExecutorService wrappedExecutor = Executors.newSingleThreadExecutor();
    try {
      final BlockingExecutor executor =
          new BlockingExecutor(wrappedExecutor, 2, Duration.ofSeconds(1));
      assertThat(executor.freeCapacity()).isEqualTo(2);

      // when a command takes one of the two slots
      executor.execute(() -> Uninterruptibles.awaitUninterruptibly(releaseCommand));

      // then that slot is gone until the command is done with it
      assertThat(executor.freeCapacity()).isEqualTo(1);
      releaseCommand.countDown();
      Awaitility.await("Capacity should be free again once the command is done")
          .untilAsserted(() -> assertThat(executor.freeCapacity()).isEqualTo(2));
    } finally {
      releaseCommand.countDown();
      wrappedExecutor.shutdownNow();
    }
  }

  @Test
  public void shouldKeepAReservedSlotReachableOnlyByThePollPath() {
    // given a four-slot executor that reserves one slot for the poll path, leaving the push path a
    // budget of three
    final CountDownLatch releaseCommands = new CountDownLatch(1);
    final ExecutorService wrappedExecutor = Executors.newFixedThreadPool(4);
    try {
      final BlockingExecutor executor =
          new BlockingExecutor(wrappedExecutor, 4, Duration.ofMillis(50), 1);

      // when the push path fills its whole budget with commands that hold their slots
      for (int i = 0; i < 3; i++) {
        executor.execute(() -> Uninterruptibles.awaitUninterruptibly(releaseCommands));
      }

      // then a fourth pushed job is refused even though a slot is still free
      assertThat(executor.freeCapacity()).isEqualTo(1);
      assertThatThrownBy(() -> executor.execute(() -> {}))
          .isInstanceOf(RejectedExecutionException.class);

      // and that free slot is the reserved one, reachable only by the poll path
      final AtomicBoolean polledJobRan = new AtomicBoolean(false);
      executor.executeWithoutWaiting(() -> polledJobRan.set(true));
      Awaitility.await("The reserved slot should let a polled job through")
          .untilAsserted(() -> assertThat(polledJobRan).isTrue());
    } finally {
      releaseCommands.countDown();
      wrappedExecutor.shutdownNow();
    }
  }

  @Test
  public void shouldLetThePollPathReachEverySlotIncludingReservedOnes() {
    // given a three-slot executor with one slot reserved for the poll path
    final CountDownLatch releaseCommands = new CountDownLatch(1);
    final ExecutorService wrappedExecutor = Executors.newFixedThreadPool(3);
    try {
      final BlockingExecutor executor =
          new BlockingExecutor(wrappedExecutor, 3, Duration.ofMillis(50), 1);

      // when the poll path takes every slot, the reserved one included
      for (int i = 0; i < 3; i++) {
        executor.executeWithoutWaiting(
            () -> Uninterruptibles.awaitUninterruptibly(releaseCommands));
      }

      // then there is no capacity left at all, so the reservation never fences the poll path off
      // from a slot the way it fences the push path
      assertThat(executor.freeCapacity()).isEqualTo(0);
      assertThatThrownBy(() -> executor.execute(() -> {}))
          .isInstanceOf(RejectedExecutionException.class);
    } finally {
      releaseCommands.countDown();
      wrappedExecutor.shutdownNow();
    }
  }

  @Test
  public void shouldLetThePushPathUseEverySlotWhenNoLaneIsReserved() {
    // given a two-slot executor built with the constructor that reserves nothing
    final CountDownLatch releaseCommands = new CountDownLatch(1);
    final ExecutorService wrappedExecutor = Executors.newFixedThreadPool(2);
    try {
      final BlockingExecutor executor =
          new BlockingExecutor(wrappedExecutor, 2, Duration.ofMillis(50));

      // when the push path takes both slots
      for (int i = 0; i < 2; i++) {
        executor.execute(() -> Uninterruptibles.awaitUninterruptibly(releaseCommands));
      }

      // then it was allowed all of the capacity, unchanged from the single-pool behaviour
      assertThat(executor.freeCapacity()).isEqualTo(0);
    } finally {
      releaseCommands.countDown();
      wrappedExecutor.shutdownNow();
    }
  }

  @Test
  public void shouldFreeThePushBudgetWhenAPushedJobFinishes() {
    // given a two-slot executor with one slot reserved, so the push budget is a single slot
    final BlockingExecutor executor =
        new BlockingExecutor(Runnable::run, 2, Duration.ofMillis(50), 1);

    // when a pushed job takes the whole budget and finishes
    executor.execute(() -> {});

    // then the budget is back and the next pushed job can run, rather than the budget leaking
    final AtomicBoolean secondJobRan = new AtomicBoolean(false);
    executor.execute(() -> secondJobRan.set(true));
    assertThat(secondJobRan).isTrue();
  }
}
