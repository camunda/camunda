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
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
    // given a four-slot executor whose poll lane is reserved, keeping one slot for the poll path
    // and leaving the push path a budget of three
    final CountDownLatch releaseCommands = new CountDownLatch(1);
    final ExecutorService wrappedExecutor = Executors.newFixedThreadPool(4);
    try {
      final BlockingExecutor executor =
          new BlockingExecutor(wrappedExecutor, 4, Duration.ofMillis(50), 1);
      executor.reservePollLane(true);

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
      executor.reservePollLane(true);

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
    executor.reservePollLane(true);

    // when a pushed job takes the whole budget and finishes
    executor.execute(() -> {});

    // then the budget is back and the next pushed job can run, rather than the budget leaking
    final AtomicBoolean secondJobRan = new AtomicBoolean(false);
    executor.execute(() -> secondJobRan.set(true));
    assertThat(secondJobRan).isTrue();
  }

  @Test
  public void shouldBoundAWaitingPushByASingleTimeoutAcrossBothAcquisitions() {
    // given a two-slot executor with its poll lane reserved, so the push budget is a single slot
    // Wide margins keep the wall-clock bounds apart on a busy CI runner: a single deadline refuses
    // at 1 s, a fresh one only after 1.6 s, and the upper bound sits between them at 1.5 s.
    final Duration timeout = Duration.ofSeconds(1);
    final Duration budgetFreedAfter = Duration.ofMillis(600);
    final QueuedExecutor wrappedExecutor = new QueuedExecutor();
    final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    try {
      final BlockingExecutor executor = new BlockingExecutor(wrappedExecutor, 2, timeout, 1);
      executor.reservePollLane(true);

      // and a pushed job holding the whole budget, and a polled job holding the other slot
      executor.execute(() -> {});
      executor.executeWithoutWaiting(() -> {});

      // when another push waits for the budget, and the budget frees up part way through its
      // timeout while every capacity slot stays taken
      scheduler.schedule(
          () -> executor.reservePollLane(false),
          budgetFreedAfter.toMillis(),
          TimeUnit.MILLISECONDS);
      final long startedAt = System.nanoTime();
      assertThatThrownBy(() -> executor.execute(() -> {}))
          .isInstanceOf(RejectedExecutionException.class);
      final Duration waited = Duration.ofNanos(System.nanoTime() - startedAt);

      // then it is refused at the end of the one timeout it started with. Giving the capacity wait
      // a fresh timeout of its own would refuse it only after budgetFreedAfter + timeout.
      assertThat(waited)
          .isGreaterThanOrEqualTo(timeout.minusMillis(10))
          .isLessThan(budgetFreedAfter.plus(timeout).minusMillis(100));
      assertThat(executor.freeCapacity()).isZero();
    } finally {
      scheduler.shutdownNow();
    }
  }

  @Test
  public void shouldLetThePushPathUseEverySlotWhileThePollLaneIsNotReserved() {
    // given a four-slot executor that could reserve one slot, but whose poll lane is not reserved
    final QueuedExecutor wrappedExecutor = new QueuedExecutor();
    final BlockingExecutor executor =
        new BlockingExecutor(wrappedExecutor, 4, Duration.ofMillis(50), 1);

    // when the push path takes every slot
    for (int i = 0; i < 4; i++) {
      executor.execute(() -> {});
    }

    // then all of them were accepted, as before any slot could be reserved
    assertThat(executor.freeCapacity()).isZero();
    assertThat(wrappedExecutor.size()).isEqualTo(4);
  }

  @Test
  public void shouldGiveThePushPathEverySlotBackOnceThePollLaneIsReleased() {
    // given a four-slot executor whose reserved poll lane keeps the push path to three slots
    final QueuedExecutor wrappedExecutor = new QueuedExecutor();
    final BlockingExecutor executor =
        new BlockingExecutor(wrappedExecutor, 4, Duration.ofMillis(50), 1);
    executor.reservePollLane(true);
    for (int i = 0; i < 3; i++) {
      executor.execute(() -> {});
    }
    assertThatThrownBy(() -> executor.execute(() -> {}))
        .isInstanceOf(RejectedExecutionException.class);

    // when the poll lane is released
    executor.reservePollLane(false);

    // then the push path can take the last slot too
    executor.execute(() -> {});
    assertThat(executor.freeCapacity()).isZero();
  }

  @Test
  public void shouldHoldNewPushesBackWithoutInterruptingRunningOnesWhenTheLaneIsReservedLate() {
    // given a four-slot executor whose push path already holds every slot
    final QueuedExecutor wrappedExecutor = new QueuedExecutor();
    final BlockingExecutor executor =
        new BlockingExecutor(wrappedExecutor, 4, Duration.ofMillis(50), 1);
    for (int i = 0; i < 4; i++) {
      executor.execute(() -> {});
    }

    // when the poll lane is reserved while the push path holds more than its smaller budget, and
    // one pushed job finishes
    executor.reservePollLane(true);
    wrappedExecutor.runNext();

    // then the jobs still running were left alone, and the slot that freed up is kept for the
    // poll: a new push is refused, while a polled job takes it
    assertThat(wrappedExecutor.size()).isEqualTo(3);
    assertThat(executor.freeCapacity()).isEqualTo(1);
    assertThatThrownBy(() -> executor.execute(() -> {}))
        .isInstanceOf(RejectedExecutionException.class);
    executor.executeWithoutWaiting(() -> {});
    assertThat(executor.freeCapacity()).isZero();
  }

  @Test
  public void shouldIgnoreARepeatedPollLaneChange() {
    // given a four-slot executor that could reserve one slot
    final QueuedExecutor wrappedExecutor = new QueuedExecutor();
    final BlockingExecutor executor =
        new BlockingExecutor(wrappedExecutor, 4, Duration.ofMillis(50), 1);

    // when the lane is reserved twice in a row, then released once
    executor.reservePollLane(true);
    executor.reservePollLane(true);
    executor.reservePollLane(false);

    // then the push path has every slot back, rather than having lost the reservation twice
    for (int i = 0; i < 4; i++) {
      executor.execute(() -> {});
    }
    assertThat(executor.freeCapacity()).isZero();
  }

  @Test
  public void shouldGiveThePushBudgetBackWhenAPushIsRefusedForLackOfCapacity() {
    // given a four-slot executor with its poll lane reserved, whose every slot the poll path holds
    final QueuedExecutor wrappedExecutor = new QueuedExecutor();
    final BlockingExecutor executor =
        new BlockingExecutor(wrappedExecutor, 4, Duration.ofMillis(20), 1);
    executor.reservePollLane(true);
    for (int i = 0; i < 4; i++) {
      executor.executeWithoutWaiting(() -> {});
    }

    // when a push gets a budget permit but no capacity, and is refused
    assertThatThrownBy(() -> executor.execute(() -> {}))
        .isInstanceOf(RejectedExecutionException.class);
    wrappedExecutor.runAll();

    // then the push path still has its whole budget of three, rather than one fewer
    assertThat(acceptedPushes(executor, 4)).isEqualTo(3);
    assertThat(executor.freeCapacity()).isEqualTo(1);
  }

  @Test
  public void shouldGiveThePushBudgetBackWhenTheWrappedExecutorRefusesAPush() {
    // given a four-slot executor with its poll lane reserved, whose wrapped executor refuses work
    final AtomicBoolean refuse = new AtomicBoolean(true);
    final QueuedExecutor queue = new QueuedExecutor();
    final Executor wrappedExecutor =
        command -> {
          if (refuse.get()) {
            throw new RejectedExecutionException("simulated full handler pool");
          }
          queue.execute(command);
        };
    final BlockingExecutor executor =
        new BlockingExecutor(wrappedExecutor, 4, Duration.ofMillis(20), 1);
    executor.reservePollLane(true);

    // when more pushes than the push budget are refused by the wrapped executor
    for (int i = 0; i < 4; i++) {
      assertThatThrownBy(() -> executor.execute(() -> {}))
          .isInstanceOf(RejectedExecutionException.class);
    }
    refuse.set(false);

    // then each of them gave both its slot and its budget permit back
    assertThat(executor.freeCapacity()).isEqualTo(4);
    assertThat(acceptedPushes(executor, 4)).isEqualTo(3);
  }

  @Test
  public void shouldGiveThePushBudgetBackOnlyOnceWhenAPushedCommandFailsOnTheCallingThread() {
    // given a four-slot executor whose wrapped executor first runs commands on the calling thread
    final AtomicBoolean runOnCaller = new AtomicBoolean(true);
    final QueuedExecutor queue = new QueuedExecutor();
    final Executor wrappedExecutor =
        command -> {
          if (runOnCaller.get()) {
            command.run();
          } else {
            queue.execute(command);
          }
        };
    final BlockingExecutor executor =
        new BlockingExecutor(wrappedExecutor, 4, Duration.ofMillis(20), 1);

    // when a pushed command fails there, so both the command and the refusal give its permits back
    assertThatThrownBy(
            () ->
                executor.execute(
                    () -> {
                      throw new IllegalStateException("simulated handler failure");
                    }))
        .isInstanceOf(IllegalStateException.class);
    runOnCaller.set(false);

    // then the push budget was given back once: with the lane reserved the push path is still kept
    // to three slots, rather than reaching the slot kept for the poll
    executor.reservePollLane(true);
    assertThat(acceptedPushes(executor, 4)).isEqualTo(3);
    assertThat(executor.freeCapacity()).isEqualTo(1);
  }

  @Test
  public void shouldRejectAPushInterruptedWhileWaitingForThePushBudget() {
    // given a two-slot executor with its poll lane reserved, whose push budget of one is taken
    final QueuedExecutor wrappedExecutor = new QueuedExecutor();
    final BlockingExecutor executor =
        new BlockingExecutor(wrappedExecutor, 2, Duration.ofMinutes(1), 1);
    executor.reservePollLane(true);
    executor.execute(() -> {});

    // when another push waits for the budget and is interrupted
    final InterruptedPush push = InterruptedPush.start(executor);

    // then it is refused with its interrupt flag kept, and never took the slot left for the poll
    assertThat(push.failure()).isInstanceOf(RejectedExecutionException.class);
    assertThat(push.interruptFlagRestored()).isTrue();
    assertThat(executor.freeCapacity()).isEqualTo(1);
  }

  @Test
  public void shouldGiveThePushBudgetBackWhenAPushIsInterruptedWhileWaitingForCapacity() {
    // given a two-slot executor that could reserve one slot, whose every slot the poll path holds
    final QueuedExecutor wrappedExecutor = new QueuedExecutor();
    final BlockingExecutor executor =
        new BlockingExecutor(wrappedExecutor, 2, Duration.ofMinutes(1), 1);
    executor.executeWithoutWaiting(() -> {});
    executor.executeWithoutWaiting(() -> {});

    // when a push takes a budget permit, waits for capacity and is interrupted
    final InterruptedPush push = InterruptedPush.start(executor);
    assertThat(push.failure()).isInstanceOf(RejectedExecutionException.class);
    wrappedExecutor.runAll();

    // then its budget permit was given back, so the push path can take both slots again. A leaked
    // permit would leave the second push waiting for a budget that never frees up.
    executor.execute(() -> {});
    executor.execute(() -> {});
    assertThat(executor.freeCapacity()).isZero();
  }

  @Test
  public void shouldLetAWaitingPushInAsSoonAsThePollLaneIsReleased() throws Exception {
    // given a four-slot executor with its poll lane reserved and the push budget of three taken
    final QueuedExecutor wrappedExecutor = new QueuedExecutor();
    final BlockingExecutor executor =
        new BlockingExecutor(wrappedExecutor, 4, Duration.ofMinutes(1), 1);
    executor.reservePollLane(true);
    for (int i = 0; i < 3; i++) {
      executor.execute(() -> {});
    }
    final AtomicReference<Throwable> failure = new AtomicReference<>();
    final Thread caller =
        new Thread(
            () -> {
              try {
                executor.execute(() -> {});
              } catch (final Throwable t) {
                failure.set(t);
              }
            });
    caller.start();
    Awaitility.await("A fourth push should wait for the push budget")
        .until(caller::getState, Matchers.equalTo(Thread.State.TIMED_WAITING));

    // when the poll lane is released
    executor.reservePollLane(false);

    // then the waiting push takes the freed slot right away, long before its timeout
    caller.join(Duration.ofSeconds(10).toMillis());
    assertThat(caller.isAlive()).isFalse();
    assertThat(failure.get()).isNull();
    assertThat(executor.freeCapacity()).isZero();
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 4, 5})
  public void shouldReserveNothingWhenTheReservationLeavesNoPushOrPollSlot(final int reserved) {
    // given a four-slot executor asked to reserve nothing, every slot, or more than every slot
    final QueuedExecutor wrappedExecutor = new QueuedExecutor();
    final BlockingExecutor executor =
        new BlockingExecutor(wrappedExecutor, 4, Duration.ofMillis(20), reserved);

    // when the poll lane is reserved
    executor.reservePollLane(true);

    // then the push path can still use every slot, rather than none of them
    assertThat(acceptedPushes(executor, 5)).isEqualTo(4);
  }

  @Test
  public void shouldKeepItsCapacityAndPushBudgetExactWhilePushesPollsAndLaneChangesRace()
      throws Exception {
    // given an eight-slot executor that reserves two slots for the poll path
    final int slots = 8;
    final ExecutorService wrappedExecutor = Executors.newFixedThreadPool(slots);
    final ExecutorService callers = Executors.newFixedThreadPool(7);
    final AtomicInteger running = new AtomicInteger();
    final AtomicInteger mostRunning = new AtomicInteger();
    final Runnable job =
        () -> {
          mostRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
          running.decrementAndGet();
        };
    try {
      final BlockingExecutor executor =
          new BlockingExecutor(wrappedExecutor, slots, Duration.ofMillis(1), 2);
      final CountDownLatch start = new CountDownLatch(1);
      final List<Future<?>> tasks = new ArrayList<>();

      // when pushes, polls and lane changes race against each other
      for (int i = 0; i < 4; i++) {
        tasks.add(callers.submit(() -> repeat(start, () -> executor.execute(job))));
      }
      for (int i = 0; i < 2; i++) {
        tasks.add(callers.submit(() -> repeat(start, () -> executor.executeWithoutWaiting(job))));
      }
      tasks.add(
          callers.submit(
              () ->
                  repeat(
                      start,
                      () -> executor.reservePollLane(ThreadLocalRandom.current().nextBoolean()))));
      start.countDown();
      for (final Future<?> task : tasks) {
        task.get(30, TimeUnit.SECONDS);
      }
      Awaitility.await("Every job should finish and give its slot back")
          .until(executor::freeCapacity, Matchers.equalTo(slots));

      // then no more jobs ever ran at once than there are slots
      assertThat(mostRunning.get()).isLessThanOrEqualTo(slots);

      // and once the lane settles, the push path gets exactly the slots it should: all of them
      // while the lane is released, and all but the reserved two while it is reserved
      executor.reservePollLane(false);
      assertThat(acceptedBlockingPushes(executor, slots)).isEqualTo(slots);
      executor.reservePollLane(true);
      assertThat(acceptedBlockingPushes(executor, slots)).isEqualTo(slots - 2);
    } finally {
      callers.shutdownNow();
      wrappedExecutor.shutdownNow();
    }
  }

  private static void repeat(final CountDownLatch start, final Runnable action) {
    Uninterruptibles.awaitUninterruptibly(start);
    for (int i = 0; i < 2_000; i++) {
      try {
        action.run();
      } catch (final RejectedExecutionException ignored) {
        // refusals are expected while the paths compete for slots
      }
    }
  }

  /** Pushes commands that hold their slot until the first refusal, and counts the accepted ones. */
  private static int acceptedPushes(final BlockingExecutor executor, final int attempts) {
    int accepted = 0;
    for (int i = 0; i < attempts; i++) {
      try {
        executor.execute(() -> {});
        accepted++;
      } catch (final RejectedExecutionException e) {
        break;
      }
    }
    return accepted;
  }

  /**
   * Like {@link #acceptedPushes}, for an executor whose wrapped executor runs commands right away:
   * each command blocks until the count is done, and is then let go so its slot frees up again.
   * Tries one push more than the executor has slots.
   */
  private static int acceptedBlockingPushes(final BlockingExecutor executor, final int slots) {
    final CountDownLatch release = new CountDownLatch(1);
    int accepted = 0;
    try {
      for (int i = 0; i <= slots; i++) {
        try {
          executor.execute(() -> Uninterruptibles.awaitUninterruptibly(release));
          accepted++;
        } catch (final RejectedExecutionException e) {
          break;
        }
      }
    } finally {
      release.countDown();
    }
    Awaitility.await("Every blocking push should give its slot back")
        .until(executor::freeCapacity, Matchers.equalTo(slots));
    return accepted;
  }

  /** A push made from its own thread, interrupted once it waits, and awaited until it gives up. */
  private static final class InterruptedPush {
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final AtomicBoolean interruptFlagRestored = new AtomicBoolean(false);

    private static InterruptedPush start(final BlockingExecutor executor) {
      final InterruptedPush push = new InterruptedPush();
      final Thread caller =
          new Thread(
              () -> {
                try {
                  executor.execute(() -> {});
                } catch (final Throwable t) {
                  push.failure.set(t);
                } finally {
                  push.interruptFlagRestored.set(Thread.currentThread().isInterrupted());
                }
              });
      caller.start();
      Awaitility.await("The push should wait")
          .until(caller::getState, Matchers.equalTo(Thread.State.TIMED_WAITING));
      caller.interrupt();
      Awaitility.await("The push should give up once interrupted").until(() -> !caller.isAlive());
      return push;
    }

    private Throwable failure() {
      return failure.get();
    }

    private boolean interruptFlagRestored() {
      return interruptFlagRestored.get();
    }
  }

  /** Holds every command it is given until the test runs it, so each one keeps its slot. */
  private static final class QueuedExecutor implements Executor {
    private final Queue<Runnable> queued = new ConcurrentLinkedQueue<>();

    @Override
    public void execute(final Runnable command) {
      queued.add(command);
    }

    private void runNext() {
      queued.poll().run();
    }

    private int size() {
      return queued.size();
    }

    private void runAll() {
      Runnable command;
      while ((command = queued.poll()) != null) {
        command.run();
      }
    }
  }
}
