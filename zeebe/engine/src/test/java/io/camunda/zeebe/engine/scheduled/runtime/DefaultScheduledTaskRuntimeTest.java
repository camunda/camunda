/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scheduled.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DefaultScheduledTaskRuntimeTest {

  @Nested
  final class RegistrationTest {

    @Test
    void shouldReturnHandleForNewRegistration() {
      // given
      final var runtime = new DefaultScheduledTaskRuntime();

      // when
      final var handle =
          runtime.register(
              "task-a",
              new Schedule.Periodic(Duration.ofMillis(100)),
              ctx -> Result.idle(ctx.resultBuilder()),
              TaskOptions.sync());

      // then
      assertThat(handle).isNotNull();
    }

    @Test
    void shouldThrowWhenRegisteringDuplicateName() {
      // given
      final var runtime = new DefaultScheduledTaskRuntime();
      runtime.register(
          "task-a",
          new Schedule.Periodic(Duration.ofMillis(100)),
          ctx -> Result.idle(ctx.resultBuilder()),
          TaskOptions.sync());

      // when / then
      assertThatThrownBy(
              () ->
                  runtime.register(
                      "task-a",
                      new Schedule.Periodic(Duration.ofMillis(100)),
                      ctx -> Result.idle(ctx.resultBuilder()),
                      TaskOptions.sync()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("task-a");
    }
  }

  @Nested
  final class LifecycleTest {

    @Test
    void shouldSchedulePeriodicTaskAfterOnRecovered() {
      // given
      final var clock = new FakeClock(1000);
      final var scheduleService = new FakeScheduleService(clock);
      final var context = TestProcessorContext.with(scheduleService, clock);
      final var runs = new java.util.concurrent.atomic.AtomicInteger();

      final var runtime = new DefaultScheduledTaskRuntime();
      runtime.register(
          "task-a",
          new Schedule.Periodic(Duration.ofMillis(100)),
          ctx -> {
            runs.incrementAndGet();
            return Result.idle(ctx.resultBuilder());
          },
          TaskOptions.sync());

      // when
      runtime.onRecovered(context);
      scheduleService.advanceTo(1100);

      // then
      assertThat(runs.get()).isEqualTo(1);
    }

    @Test
    void shouldCancelAllScheduledRunsOnPaused() {
      // given
      final var clock = new FakeClock(1000);
      final var scheduleService = new FakeScheduleService(clock);
      final var context = TestProcessorContext.with(scheduleService, clock);
      final var fireTimes = new java.util.ArrayList<Long>();

      final var runtime = new DefaultScheduledTaskRuntime();
      runtime.register(
          "task-a",
          new Schedule.Periodic(Duration.ofMillis(100)),
          ctx -> {
            fireTimes.add(ctx.clock().millis());
            return Result.idle(ctx.resultBuilder());
          },
          TaskOptions.sync());
      runtime.onRecovered(context);

      // when
      runtime.onPaused();
      scheduleService.advanceTo(2000);

      // then
      assertThat(fireTimes).isEmpty();
    }

    @Test
    void shouldRestoreSchedulingOnResumed() {
      // given
      final var clock = new FakeClock(1000);
      final var scheduleService = new FakeScheduleService(clock);
      final var context = TestProcessorContext.with(scheduleService, clock);
      final var fireTimes = new java.util.ArrayList<Long>();

      final var runtime = new DefaultScheduledTaskRuntime();
      runtime.register(
          "task-a",
          new Schedule.Periodic(Duration.ofMillis(100)),
          ctx -> {
            fireTimes.add(ctx.clock().millis());
            return Result.idle(ctx.resultBuilder());
          },
          TaskOptions.sync());
      runtime.onRecovered(context);
      runtime.onPaused();
      scheduleService.advanceTo(2000);
      assertThat(fireTimes).isEmpty();

      // when
      runtime.onResumed();
      scheduleService.advanceTo(2100);

      // then — schedule resumed at now + interval = 2100
      assertThat(fireTimes).containsExactly(2100L);
    }
  }

  @Nested
  final class OnDemandScheduleTest {

    @Test
    void shouldNotRunOnDemandTaskAfterRecoveryWithoutNudge() {
      // given
      final var clock = new FakeClock(1000);
      final var scheduleService = new FakeScheduleService(clock);
      final var context = TestProcessorContext.with(scheduleService, clock);
      final var runs = new java.util.concurrent.atomic.AtomicInteger();

      final var runtime = new DefaultScheduledTaskRuntime();
      runtime.register(
          "task-a",
          new Schedule.OnDemand(Duration.ofMillis(100)),
          ctx -> {
            runs.incrementAndGet();
            return Result.idle(ctx.resultBuilder());
          },
          TaskOptions.sync());

      // when
      runtime.onRecovered(context);
      scheduleService.advanceTo(10_000);

      // then
      assertThat(runs.get()).isZero();
    }

    @Test
    void shouldRunOnDemandTaskAfterNudge() {
      // given
      final var clock = new FakeClock(1000);
      final var scheduleService = new FakeScheduleService(clock);
      final var context = TestProcessorContext.with(scheduleService, clock);
      final var runs = new java.util.concurrent.atomic.AtomicInteger();

      final var runtime = new DefaultScheduledTaskRuntime();
      final var handle =
          runtime.register(
              "task-a",
              new Schedule.OnDemand(Duration.ofMillis(100)),
              ctx -> {
                runs.incrementAndGet();
                return Result.idle(ctx.resultBuilder());
              },
              TaskOptions.sync());

      // when
      runtime.onRecovered(context);
      handle.nudge(1050);
      scheduleService.advanceTo(1200);

      // then
      assertThat(runs.get()).isEqualTo(1);
    }

    @Test
    void shouldHonorEarlierNudgeAndIgnoreLaterOne() {
      // given
      final var clock = new FakeClock(1000);
      final var scheduleService = new FakeScheduleService(clock);
      final var context = TestProcessorContext.with(scheduleService, clock);
      final var fireTimes = new java.util.ArrayList<Long>();

      final var runtime = new DefaultScheduledTaskRuntime();
      final var handle =
          runtime.register(
              "task-a",
              new Schedule.OnDemand(Duration.ofMillis(50)),
              ctx -> {
                fireTimes.add(ctx.clock().millis());
                return Result.idle(ctx.resultBuilder());
              },
              TaskOptions.sync());
      runtime.onRecovered(context);

      // when
      handle.nudge(1200); // earlier
      handle.nudge(1500); // later — should be ignored
      scheduleService.advanceTo(1200);

      // then
      assertThat(fireTimes).containsExactly(1200L);
    }
  }

  @Nested
  final class HintHandlingTest {

    @Test
    void shouldScheduleNextRunAtHintedTimestamp() {
      // given
      final var clock = new FakeClock(1000);
      final var scheduleService = new FakeScheduleService(clock);
      final var context = TestProcessorContext.with(scheduleService, clock);
      final var fireTimes = new java.util.ArrayList<Long>();

      final var runtime = new DefaultScheduledTaskRuntime();
      final var handle =
          runtime.register(
              "task-a",
              new Schedule.OnDemand(Duration.ofMillis(50)),
              ctx -> {
                final long now = ctx.clock().millis();
                fireTimes.add(now);
                if (fireTimes.size() == 1) {
                  return Result.nextDueAt(now + 500, ctx.resultBuilder());
                }
                return Result.idle(ctx.resultBuilder());
              },
              TaskOptions.sync());
      runtime.onRecovered(context);
      handle.nudge(1100);
      scheduleService.advanceTo(1100);

      // when — the first run returned NextDueAt(1600); next run should be at 1600
      scheduleService.advanceTo(1600);

      // then
      assertThat(fireTimes).containsExactly(1100L, 1600L);
    }

    @Test
    void shouldRescheduleImmediatelyOnMoreWorkPending() {
      // given
      final var clock = new FakeClock(1000);
      final var scheduleService = new FakeScheduleService(clock);
      final var context = TestProcessorContext.with(scheduleService, clock);
      final var fireTimes = new java.util.ArrayList<Long>();

      final var runtime = new DefaultScheduledTaskRuntime();
      final var handle =
          runtime.register(
              "task-a",
              new Schedule.OnDemand(Duration.ofMillis(50)),
              ctx -> {
                final long now = ctx.clock().millis();
                fireTimes.add(now);
                if (fireTimes.size() == 1) {
                  return Result.moreWorkPending(ctx.resultBuilder());
                }
                return Result.idle(ctx.resultBuilder());
              },
              TaskOptions.sync());
      runtime.onRecovered(context);
      handle.nudge(1100);
      scheduleService.advanceTo(1100);

      // when — first run returned MoreWorkPending; next run should be at 1100+50=1150
      scheduleService.advanceTo(1150);

      // then
      assertThat(fireTimes).containsExactly(1100L, 1150L);
    }
  }

  @Nested
  final class PauseResumeTest {

    @Test
    void shouldStopScheduledRunsOfPausedTaskOnly() {
      // given
      final var clock = new FakeClock(1000);
      final var scheduleService = new FakeScheduleService(clock);
      final var context = TestProcessorContext.with(scheduleService, clock);
      final var fireA = new java.util.ArrayList<Long>();
      final var fireB = new java.util.ArrayList<Long>();

      final var runtime = new DefaultScheduledTaskRuntime();
      runtime.register(
          "task-a",
          new Schedule.Periodic(Duration.ofMillis(100)),
          ctx -> {
            fireA.add(ctx.clock().millis());
            return Result.idle(ctx.resultBuilder());
          },
          TaskOptions.sync());
      runtime.register(
          "task-b",
          new Schedule.Periodic(Duration.ofMillis(100)),
          ctx -> {
            fireB.add(ctx.clock().millis());
            return Result.idle(ctx.resultBuilder());
          },
          TaskOptions.sync());
      runtime.onRecovered(context);

      // when
      runtime.pause("task-a");
      scheduleService.advanceTo(1100);

      // then — only task-b ran
      assertThat(fireA).isEmpty();
      assertThat(fireB).containsExactly(1100L);
    }

    @Test
    void shouldResumePausedTaskFromRetainedNudge() {
      // given
      final var clock = new FakeClock(1000);
      final var scheduleService = new FakeScheduleService(clock);
      final var context = TestProcessorContext.with(scheduleService, clock);
      final var fireA = new java.util.ArrayList<Long>();

      final var runtime = new DefaultScheduledTaskRuntime();
      final var handle =
          runtime.register(
              "task-a",
              new Schedule.OnDemand(Duration.ofMillis(50)),
              ctx -> {
                fireA.add(ctx.clock().millis());
                return Result.idle(ctx.resultBuilder());
              },
              TaskOptions.sync());
      runtime.onRecovered(context);

      runtime.pause("task-a");
      handle.nudge(1100); // retained while paused

      scheduleService.advanceTo(2000);
      assertThat(fireA).isEmpty();

      // when
      runtime.resume("task-a");
      scheduleService.advanceTo(2050);

      // then — runs at now + minDelay = 2050 (floor)
      assertThat(fireA).containsExactly(2050L);
    }
  }

  @Nested
  final class ThrottleTest {

    @Test
    void shouldEnforceMinIntervalFloor() {
      // given
      final var clock = new FakeClock(1000);
      final var scheduleService = new FakeScheduleService(clock);
      final var context = TestProcessorContext.with(scheduleService, clock);
      final var fireTimes = new java.util.ArrayList<Long>();

      final var runtime = new DefaultScheduledTaskRuntime();
      final var handle =
          runtime.register(
              "task-a",
              new Schedule.OnDemand(Duration.ofMillis(10)),
              ctx -> {
                final long now = ctx.clock().millis();
                fireTimes.add(now);
                if (fireTimes.size() == 1) {
                  return Result.moreWorkPending(ctx.resultBuilder());
                }
                return Result.idle(ctx.resultBuilder());
              },
              TaskOptions.sync());
      runtime.onRecovered(context);
      runtime.throttle("task-a", ThrottlePolicy.minInterval(Duration.ofMillis(500)));
      handle.nudge(1100);

      // when
      scheduleService.advanceTo(1100);
      scheduleService.advanceTo(1599); // before throttle's 500ms floor

      // then
      assertThat(fireTimes).containsExactly(1100L);

      // when — past the throttle floor
      scheduleService.advanceTo(1600);

      // then
      assertThat(fireTimes).containsExactly(1100L, 1600L);
    }
  }

  @Nested
  final class ResolutionFloorTest {

    @Test
    void shouldNotRunBeforeMinResolutionEvenIfNudgedInThePast() {
      // given
      final var clock = new FakeClock(1000);
      final var scheduleService = new FakeScheduleService(clock);
      final var context = TestProcessorContext.with(scheduleService, clock);
      final var fireTimes = new java.util.ArrayList<Long>();

      final var runtime = new DefaultScheduledTaskRuntime();
      final var handle =
          runtime.register(
              "task-a",
              new Schedule.OnDemand(Duration.ofMillis(100)),
              ctx -> {
                fireTimes.add(ctx.clock().millis());
                return Result.idle(ctx.resultBuilder());
              },
              TaskOptions.sync());
      runtime.onRecovered(context);

      // when — nudge in the past
      handle.nudge(500);
      scheduleService.advanceTo(1099);

      // then — must not have fired yet
      assertThat(fireTimes).isEmpty();

      // when — advance past the floor
      scheduleService.advanceTo(1100);

      // then
      assertThat(fireTimes).containsExactly(1100L);
    }
  }

  @Nested
  final class AsyncOptionTest {

    @Test
    void shouldScheduleAsyncWhenRunAsyncOptionIsTrue() {
      // given
      final var clock = new FakeClock(1000);
      final var scheduleService = new FakeScheduleService(clock);
      final var context = TestProcessorContext.with(scheduleService, clock);

      final var runtime = new DefaultScheduledTaskRuntime();
      runtime.register(
          "task-a",
          new Schedule.Periodic(Duration.ofMillis(100)),
          ctx -> Result.idle(ctx.resultBuilder()),
          TaskOptions.async(
              io.camunda.zeebe.stream.api.scheduling.AsyncTaskGroup.ASYNC_PROCESSING));

      // when
      runtime.onRecovered(context);

      // then
      assertThat(scheduleService.asyncCount()).isEqualTo(1);
      assertThat(scheduleService.syncCount()).isZero();
    }

    @Test
    void shouldScheduleSyncWhenRunAsyncOptionIsFalse() {
      // given
      final var clock = new FakeClock(1000);
      final var scheduleService = new FakeScheduleService(clock);
      final var context = TestProcessorContext.with(scheduleService, clock);

      final var runtime = new DefaultScheduledTaskRuntime();
      runtime.register(
          "task-a",
          new Schedule.Periodic(Duration.ofMillis(100)),
          ctx -> Result.idle(ctx.resultBuilder()),
          TaskOptions.sync());

      // when
      runtime.onRecovered(context);

      // then
      assertThat(scheduleService.syncCount()).isEqualTo(1);
      assertThat(scheduleService.asyncCount()).isZero();
    }
  }
}
