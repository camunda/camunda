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
      final var runtime = new DefaultScheduledTaskRuntime(BackPressureSignal.alwaysGreen());

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
      final var runtime = new DefaultScheduledTaskRuntime(BackPressureSignal.alwaysGreen());
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

      final var runtime = new DefaultScheduledTaskRuntime(BackPressureSignal.alwaysGreen());
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

      final var runtime = new DefaultScheduledTaskRuntime(BackPressureSignal.alwaysGreen());
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

      final var runtime = new DefaultScheduledTaskRuntime(BackPressureSignal.alwaysGreen());
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
    void shouldSweepOnDemandTaskOnceAfterRecovery() {
      // given
      final var clock = new FakeClock(1000);
      final var scheduleService = new FakeScheduleService(clock);
      final var context = TestProcessorContext.with(scheduleService, clock);
      final var fireTimes = new java.util.ArrayList<Long>();

      final var runtime = new DefaultScheduledTaskRuntime(BackPressureSignal.alwaysGreen());
      runtime.register(
          "task-a",
          new Schedule.OnDemand(Duration.ofMillis(100)),
          ctx -> {
            fireTimes.add(ctx.clock().millis());
            return Result.idle(ctx.resultBuilder());
          },
          TaskOptions.sync());

      // when
      runtime.onRecovered(context);
      scheduleService.advanceTo(1100); // recovery sweep fires at now + minDelay = 1100
      scheduleService.advanceTo(10_000); // no further runs — task stays dormant until nudged

      // then — an on-demand task reconciles durable state exactly once on recovery
      assertThat(fireTimes).containsExactly(1100L);
    }

    @Test
    void shouldRunOnDemandTaskAfterNudge() {
      // given
      final var clock = new FakeClock(1000);
      final var scheduleService = new FakeScheduleService(clock);
      final var context = TestProcessorContext.with(scheduleService, clock);
      final var fireTimes = new java.util.ArrayList<Long>();

      final var runtime = new DefaultScheduledTaskRuntime(BackPressureSignal.alwaysGreen());
      final var handle =
          runtime.register(
              "task-a",
              new Schedule.OnDemand(Duration.ofMillis(100)),
              ctx -> {
                fireTimes.add(ctx.clock().millis());
                return Result.idle(ctx.resultBuilder());
              },
              TaskOptions.sync());

      // when — drain the recovery sweep first, then nudge to verify the nudge itself triggers a run
      runtime.onRecovered(context);
      scheduleService.advanceTo(1100); // recovery sweep fires, task goes dormant
      handle.nudge(1200);
      scheduleService.advanceTo(1200);

      // then — recovery sweep at 1100, nudged run at 1200
      assertThat(fireTimes).containsExactly(1100L, 1200L);
    }

    @Test
    void shouldHonorEarlierNudgeAndIgnoreLaterOne() {
      // given
      final var clock = new FakeClock(1000);
      final var scheduleService = new FakeScheduleService(clock);
      final var context = TestProcessorContext.with(scheduleService, clock);
      final var fireTimes = new java.util.ArrayList<Long>();

      final var runtime = new DefaultScheduledTaskRuntime(BackPressureSignal.alwaysGreen());
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
      scheduleService.advanceTo(1050); // drain the recovery sweep (now + minDelay = 1050)

      // when
      handle.nudge(1200); // earlier
      handle.nudge(1500); // later — should be ignored
      scheduleService.advanceTo(1200);

      // then — recovery sweep at 1050, then the earlier nudge at 1200 (later nudge ignored)
      assertThat(fireTimes).containsExactly(1050L, 1200L);
    }

    @Test
    void shouldSweepOnDemandTaskOnceOnResumed() {
      // given
      final var clock = new FakeClock(1000);
      final var scheduleService = new FakeScheduleService(clock);
      final var context = TestProcessorContext.with(scheduleService, clock);
      final var fireTimes = new java.util.ArrayList<Long>();

      final var runtime = new DefaultScheduledTaskRuntime(BackPressureSignal.alwaysGreen());
      runtime.register(
          "task-a",
          new Schedule.OnDemand(Duration.ofMillis(50)),
          ctx -> {
            fireTimes.add(ctx.clock().millis());
            return Result.idle(ctx.resultBuilder());
          },
          TaskOptions.sync());
      runtime.onRecovered(context);
      scheduleService.advanceTo(1050); // drain the recovery sweep

      // when — lifecycle pause then resume; resume must trigger a fresh sweep even without a
      // retained nudge
      runtime.onPaused();
      runtime.onResumed();
      scheduleService.advanceTo(1100);

      // then — recovery sweep at 1050, resume sweep at 1050 + minDelay = 1100
      assertThat(fireTimes).containsExactly(1050L, 1100L);
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

      final var runtime = new DefaultScheduledTaskRuntime(BackPressureSignal.alwaysGreen());
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
      scheduleService.advanceTo(1050); // recovery sweep at now + minDelay = 1050

      // when — the recovery sweep returned NextDueAt(1550); next run should be at 1550
      scheduleService.advanceTo(1550);

      // then
      assertThat(fireTimes).containsExactly(1050L, 1550L);
    }

    @Test
    void shouldRescheduleImmediatelyOnMoreWorkPending() {
      // given
      final var clock = new FakeClock(1000);
      final var scheduleService = new FakeScheduleService(clock);
      final var context = TestProcessorContext.with(scheduleService, clock);
      final var fireTimes = new java.util.ArrayList<Long>();

      final var runtime = new DefaultScheduledTaskRuntime(BackPressureSignal.alwaysGreen());
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
      scheduleService.advanceTo(1050); // recovery sweep at now + minDelay = 1050

      // when — recovery sweep returned MoreWorkPending; next run should be at 1050+50=1100
      scheduleService.advanceTo(1100);

      // then
      assertThat(fireTimes).containsExactly(1050L, 1100L);
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

      final var runtime = new DefaultScheduledTaskRuntime(BackPressureSignal.alwaysGreen());
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

      final var runtime = new DefaultScheduledTaskRuntime(BackPressureSignal.alwaysGreen());
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
      scheduleService.advanceTo(1050); // drain the recovery sweep so the nudge below is retained

      runtime.pause("task-a");
      handle.nudge(1100); // retained while paused

      scheduleService.advanceTo(2000);
      assertThat(fireA).containsExactly(1050L); // only the recovery sweep so far

      // when
      runtime.resume("task-a");
      scheduleService.advanceTo(2050);

      // then — resumes from the retained nudge, floored to now + minDelay = 2050
      assertThat(fireA).containsExactly(1050L, 2050L);
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

      final var runtime = new DefaultScheduledTaskRuntime(BackPressureSignal.alwaysGreen());
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

      // when — recovery sweep fires at now + minDelay = 1010 and returns MoreWorkPending
      scheduleService.advanceTo(1010);
      scheduleService.advanceTo(1509); // before the throttle's 500ms floor (1010 + 500 = 1510)

      // then
      assertThat(fireTimes).containsExactly(1010L);

      // when — past the throttle floor
      scheduleService.advanceTo(1510);

      // then
      assertThat(fireTimes).containsExactly(1010L, 1510L);
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

      final var runtime = new DefaultScheduledTaskRuntime(BackPressureSignal.alwaysGreen());
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
  final class ActorYieldInvariantTest {

    @Test
    void shouldNotInvokeTaskRecursivelyOnMoreWorkPending() {
      // given
      final var clock = new FakeClock(1000);
      final var scheduleService = new FakeScheduleService(clock);
      final var context = TestProcessorContext.with(scheduleService, clock);
      final var depth = new java.util.concurrent.atomic.AtomicInteger();
      final var maxDepth = new java.util.concurrent.atomic.AtomicInteger();
      final var fireTimes = new java.util.ArrayList<Long>();

      final var runtime = new DefaultScheduledTaskRuntime(BackPressureSignal.alwaysGreen());
      runtime.register(
          "task-a",
          new Schedule.OnDemand(Duration.ofMillis(10)),
          ctx -> {
            final int d = depth.incrementAndGet();
            maxDepth.updateAndGet(prev -> Math.max(prev, d));
            fireTimes.add(ctx.clock().millis());
            try {
              if (fireTimes.size() < 5) {
                return Result.moreWorkPending(ctx.resultBuilder());
              }
              return Result.idle(ctx.resultBuilder());
            } finally {
              depth.decrementAndGet();
            }
          },
          TaskOptions.sync());
      runtime.onRecovered(context); // recovery sweep kicks off the first run

      // when
      // The fake scheduler dispatches tasks via advanceTo only; the runtime cannot
      // run the task itself between dispatches. Advance enough to drain 5 invocations.
      for (int i = 0; i < 50 && fireTimes.size() < 5; i++) {
        scheduleService.advanceTo(clock.millis() + 10);
      }

      // then — recursion never exceeded depth 1
      assertThat(maxDepth.get()).isEqualTo(1);
      assertThat(fireTimes).hasSize(5);
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

      final var runtime = new DefaultScheduledTaskRuntime(BackPressureSignal.alwaysGreen());
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

      final var runtime = new DefaultScheduledTaskRuntime(BackPressureSignal.alwaysGreen());
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
