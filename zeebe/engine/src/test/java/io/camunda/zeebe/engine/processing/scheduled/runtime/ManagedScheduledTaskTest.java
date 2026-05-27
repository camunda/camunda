/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scheduled.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.scheduled.api.Result;
import io.camunda.zeebe.engine.processing.scheduled.api.Schedule;
import io.camunda.zeebe.engine.processing.scheduled.api.ScheduledTask;
import io.camunda.zeebe.engine.processing.scheduled.api.TaskContext;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.SimpleProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ManagedScheduledTaskTest {

  private static final long FIXED_NOW_MS = 1_700_000_000_000L;

  private ProcessingScheduleService scheduleService;
  private InterPartitionCommandSender interPartitionSender;
  private ReadonlyStreamProcessorContext context;
  private StreamClock clock;

  @BeforeEach
  void setUp() {
    scheduleService = mock(ProcessingScheduleService.class);
    interPartitionSender = mock(InterPartitionCommandSender.class);
    context = mock(ReadonlyStreamProcessorContext.class);
    clock = StreamClock.uncontrolled(InstantSource.fixed(Instant.ofEpochMilli(FIXED_NOW_MS)));

    when(context.getScheduleService()).thenReturn(scheduleService);
    when(context.getClock()).thenReturn(clock);
    when(context.getPartitionId()).thenReturn(1);
    when(scheduleService.runAt(anyLong(), any(Task.class)))
        .thenReturn(mock(SimpleProcessingScheduleService.ScheduledTask.class));
    when(scheduleService.runAtAsync(anyLong(), any(Task.class)))
        .thenReturn(mock(SimpleProcessingScheduleService.ScheduledTask.class));
  }

  @Test
  void shouldScheduleImmediatelyOnRecoveredWhenFallbackConfigured() {
    // given
    final var task = newRecordingTask(Result.Builder::idle);
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.fixedRate(Duration.ofSeconds(30)),
            interPartitionSender,
            new SimpleMeterRegistry());

    // when
    managed.onRecovered(context);

    // then
    verify(scheduleService).runAt(anyLong(), any(Task.class));
  }

  @Test
  void shouldScheduleInitialRunOnRecoveredEvenWhenPureOnDemand() {
    // given
    final var task = newRecordingTask(Result.Builder::idle);
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.onDemand(Duration.ofMillis(100)),
            interPartitionSender,
            new SimpleMeterRegistry());

    // when
    managed.onRecovered(context);

    // then
    verify(scheduleService).runAt(eq(FIXED_NOW_MS + 100), any(Task.class));
  }

  @Test
  void shouldUseAsyncSchedulingWhenAsyncIsTrue() {
    // given
    final var task = newRecordingTask(Result.Builder::idle);
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.fixedRate(Duration.ofSeconds(30)).withAsync(true),
            interPartitionSender,
            new SimpleMeterRegistry());

    // when
    managed.onRecovered(context);

    // then
    verify(scheduleService).runAtAsync(anyLong(), any(Task.class));
    verify(scheduleService, never()).runAt(anyLong(), any(Task.class));
  }

  @Test
  void shouldScheduleAtAwaitDueAtTimestamp() {
    // given
    final long dueAt = FIXED_NOW_MS + 60_000;
    final var task = newRecordingTask(builder -> builder.awaitDueAt(dueAt));
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.onDemand(Duration.ofMillis(10)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);
    clearInvocations(scheduleService);

    // when
    managed.execute(mock(TaskResultBuilder.class));

    // then
    verify(scheduleService).runAt(eq(dueAt), any(Task.class));
  }

  @Test
  void shouldRescheduleImmediatelyOnYieldNow() {
    // given
    final var task = newRecordingTask(Result.Builder::yieldNow);
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.fixedRate(Duration.ofSeconds(30)).withYieldBudget(Duration.ofMillis(50)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);
    clearInvocations(scheduleService);

    // when
    managed.execute(mock(TaskResultBuilder.class));

    // then
    verify(scheduleService)
        .runAt(eq(FIXED_NOW_MS + Schedule.DEFAULT_MIN_RESOLUTION.toMillis()), any(Task.class));
  }

  @Test
  void shouldRescheduleAtFallbackOnIdle() {
    // given
    final Duration fallback = Duration.ofSeconds(30);
    final var task = newRecordingTask(Result.Builder::idle);
    final var managed =
        new ManagedScheduledTask<>(
            task, Schedule.fixedRate(fallback), interPartitionSender, new SimpleMeterRegistry());
    managed.onRecovered(context);
    clearInvocations(scheduleService);

    // when
    managed.execute(mock(TaskResultBuilder.class));

    // then
    verify(scheduleService).runAt(eq(FIXED_NOW_MS + fallback.toMillis()), any(Task.class));
  }

  @Test
  void shouldNotRescheduleOnIdleWhenPureOnDemand() {
    // given
    final var task = newRecordingTask(Result.Builder::idle);
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.onDemand(Duration.ofMillis(10)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);
    clearInvocations(scheduleService);

    // when
    managed.execute(mock(TaskResultBuilder.class));

    // then
    verifyNoInteractions(scheduleService);
  }

  @Test
  void shouldHonorRequestRunAfterInitialRunConsumed() {
    // given
    final var task = newRecordingTask(Result.Builder::idle);
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.onDemand(Duration.ofMillis(10)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);
    managed.execute(mock(TaskResultBuilder.class));
    clearInvocations(scheduleService);

    // when
    managed.requestRun(FIXED_NOW_MS + 1_000);

    // then
    verify(scheduleService).runAt(eq(FIXED_NOW_MS + 1_000), any(Task.class));
  }

  @Test
  void shouldClampRequestRunToMinResolution() {
    // given
    final var task = newRecordingTask(Result.Builder::idle);
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.onDemand(Duration.ofMillis(100)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);
    managed.execute(mock(TaskResultBuilder.class));
    clearInvocations(scheduleService);

    // when
    managed.requestRun(FIXED_NOW_MS);

    // then
    verify(scheduleService).runAt(eq(FIXED_NOW_MS + 100), any(Task.class));
  }

  @Test
  void shouldRescheduleEarlierAndCancelPrevious() {
    // given
    final SimpleProcessingScheduleService.ScheduledTask initialHandle =
        mock(SimpleProcessingScheduleService.ScheduledTask.class);
    final SimpleProcessingScheduleService.ScheduledTask firstHandle =
        mock(SimpleProcessingScheduleService.ScheduledTask.class);
    final SimpleProcessingScheduleService.ScheduledTask secondHandle =
        mock(SimpleProcessingScheduleService.ScheduledTask.class);
    when(scheduleService.runAt(anyLong(), any(Task.class)))
        .thenReturn(initialHandle)
        .thenReturn(firstHandle)
        .thenReturn(secondHandle);
    final var task = newRecordingTask(Result.Builder::idle);
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.onDemand(Duration.ofMillis(10)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);
    managed.execute(mock(TaskResultBuilder.class));

    // when
    managed.requestRun(FIXED_NOW_MS + 1_000);
    managed.requestRun(FIXED_NOW_MS + 100);

    // then
    verify(scheduleService).runAt(eq(FIXED_NOW_MS + 1_000), any(Task.class));
    verify(scheduleService).runAt(eq(FIXED_NOW_MS + 100), any(Task.class));
    verify(firstHandle).cancel();
    verify(secondHandle, never()).cancel();
  }

  @Test
  void shouldNotRescheduleWhenAlreadyScheduledEarlier() {
    // given
    final var task = newRecordingTask(Result.Builder::idle);
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.onDemand(Duration.ofMillis(10)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);
    managed.execute(mock(TaskResultBuilder.class));
    clearInvocations(scheduleService);

    // when
    managed.requestRun(FIXED_NOW_MS + 100);
    managed.requestRun(FIXED_NOW_MS + 1_000);

    // then
    verify(scheduleService, times(1)).runAt(anyLong(), any(Task.class));
  }

  @Test
  void shouldIgnoreRequestRunWhenNotEnabled() {
    // given
    final var task = newRecordingTask(Result.Builder::idle);
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.onDemand(Duration.ofMillis(10)),
            interPartitionSender,
            new SimpleMeterRegistry());

    // when
    managed.requestRun(FIXED_NOW_MS + 1_000);

    // then
    verifyNoInteractions(scheduleService);
  }

  @Test
  void shouldDisableAndCancelOnPaused() {
    // given
    final SimpleProcessingScheduleService.ScheduledTask handle =
        mock(SimpleProcessingScheduleService.ScheduledTask.class);
    when(scheduleService.runAt(anyLong(), any(Task.class))).thenReturn(handle);
    final var task = newRecordingTask(Result.Builder::idle);
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.onDemand(Duration.ofMillis(10)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);
    managed.requestRun(FIXED_NOW_MS + 100);

    // when
    managed.onPaused();

    // then
    verify(handle).cancel();
    clearInvocations(scheduleService);
    managed.requestRun(FIXED_NOW_MS + 100);
    verifyNoInteractions(scheduleService);
  }

  @Test
  void shouldReenableAndScheduleOnResumed() {
    // given
    final var task = newRecordingTask(Result.Builder::idle);
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.fixedRate(Duration.ofSeconds(30)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);
    managed.onPaused();
    clearInvocations(scheduleService);

    // when
    managed.onResumed();

    // then
    verify(scheduleService).runAt(anyLong(), any(Task.class));
  }

  @Test
  void shouldDisableOnCloseAndOnFailed() {
    // given
    final var task = newRecordingTask(Result.Builder::idle);
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.onDemand(Duration.ofMillis(10)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);

    // when
    managed.onClose();

    // then
    clearInvocations(scheduleService);
    managed.requestRun(FIXED_NOW_MS + 100);
    verifyNoInteractions(scheduleService);

    managed.onResumed();
    managed.onFailed();
    clearInvocations(scheduleService);
    managed.requestRun(FIXED_NOW_MS + 100);
    verifyNoInteractions(scheduleService);
  }

  @Test
  void shouldCatchExceptionsFromTaskAndReschedulePerFallback() {
    // given
    final ScheduledTask<Void> task =
        new ScheduledTask<>() {
          @Override
          public String name() {
            return "boom";
          }

          @Override
          public Result run(final TaskContext<Void> ctx) {
            throw new RuntimeException("boom");
          }
        };
    final Duration fallback = Duration.ofSeconds(30);
    final var managed =
        new ManagedScheduledTask<>(
            task, Schedule.fixedRate(fallback), interPartitionSender, new SimpleMeterRegistry());
    managed.onRecovered(context);
    clearInvocations(scheduleService);

    // when
    final TaskResultBuilder builder = mock(TaskResultBuilder.class);
    when(builder.build()).thenReturn(mock(TaskResult.class));
    final TaskResult result = managed.execute(builder);

    // then
    assertThat(result).isNotNull();
    verify(scheduleService).runAt(eq(FIXED_NOW_MS + fallback.toMillis()), any(Task.class));
  }

  @Test
  void shouldExposeContextToTask() {
    // given
    final AtomicReference<TaskContext<Void>> seen = new AtomicReference<>();
    final ScheduledTask<Void> task =
        new ScheduledTask<>() {
          @Override
          public String name() {
            return "capture";
          }

          @Override
          public Result run(final TaskContext<Void> ctx) {
            seen.set(ctx);
            return ctx.result().idle();
          }
        };
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.fixedRate(Duration.ofSeconds(30)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);

    // when
    managed.execute(mock(TaskResultBuilder.class));

    // then
    assertThat(seen.get()).isNotNull();
    assertThat(seen.get().clock().millis()).isEqualTo(FIXED_NOW_MS);
    assertThat(seen.get().partitionId()).isEqualTo(1);
    assertThat(seen.get().shouldYield()).isFalse();
  }

  @Test
  void shouldDelegateAppendToTaskResultBuilder() {
    // given
    final var record = new JobRecord();
    final TaskResultBuilder builder = mock(TaskResultBuilder.class);
    when(builder.appendCommandRecord(anyLong(), any(), any())).thenReturn(true);
    when(builder.build()).thenReturn(mock(TaskResult.class));
    final ScheduledTask<Void> task =
        new ScheduledTask<>() {
          @Override
          public String name() {
            return "appender";
          }

          @Override
          public Result run(final TaskContext<Void> ctx) {
            final Result.Builder<Void> r = ctx.result();
            r.append(42L, JobIntent.TIME_OUT, record);
            return r.idle();
          }
        };
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.fixedRate(Duration.ofSeconds(30)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);

    // when
    managed.execute(builder);

    // then
    verify(builder).appendCommandRecord(eq(42L), eq(JobIntent.TIME_OUT), eq(record));
  }

  @Test
  void shouldDelegateInterPartitionSendToSender() {
    // given
    final var record = new JobRecord();
    final ScheduledTask<Void> task =
        new ScheduledTask<>() {
          @Override
          public String name() {
            return "sender";
          }

          @Override
          public Result run(final TaskContext<Void> ctx) {
            final Result.Builder<Void> r = ctx.result();
            r.sendInterPartition(2, ValueType.JOB, JobIntent.CANCEL, 7L, record);
            return r.idle();
          }
        };
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.fixedRate(Duration.ofSeconds(30)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);

    // when
    managed.execute(mock(TaskResultBuilder.class));

    // then
    verify(interPartitionSender)
        .sendCommand(eq(2), eq(ValueType.JOB), eq(JobIntent.CANCEL), eq(7L), eq(record), eq(null));
  }

  @Test
  void shouldExposeShouldYieldOnceBudgetElapsed() {
    // given
    final var controlClock = StreamClock.controllable(InstantSource.system());
    controlClock.pinAt(Instant.ofEpochMilli(FIXED_NOW_MS));
    when(context.getClock()).thenReturn(controlClock);

    final AtomicReference<Boolean> beforeBudget = new AtomicReference<>();
    final AtomicReference<Boolean> afterBudget = new AtomicReference<>();
    final ScheduledTask<Void> task =
        new ScheduledTask<>() {
          @Override
          public String name() {
            return "yielder";
          }

          @Override
          public Result run(final TaskContext<Void> ctx) {
            beforeBudget.set(ctx.shouldYield());
            controlClock.pinAt(Instant.ofEpochMilli(FIXED_NOW_MS + 100));
            afterBudget.set(ctx.shouldYield());
            return ctx.result().yieldNow();
          }
        };
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.fixedRate(Duration.ofSeconds(30)).withYieldBudget(Duration.ofMillis(50)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);

    // when
    managed.execute(mock(TaskResultBuilder.class));

    // then
    assertThat(beforeBudget.get()).isFalse();
    assertThat(afterBudget.get()).isTrue();
  }

  @Test
  void shouldClearPendingNextRunBeforeExecutingTaskBody() {
    // given
    final var task = newRecordingTask(Result.Builder::idle);
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.fixedRate(Duration.ofSeconds(30)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);

    // when
    managed.execute(mock(TaskResultBuilder.class));
    managed.requestRun(FIXED_NOW_MS + 100);

    // then
    verify(scheduleService, atLeastOnce()).runAt(eq(FIXED_NOW_MS + 100), any(Task.class));
  }

  @Test
  void shouldExposeSavedCursorOnNextRunAfterYieldNow() {
    // given — a task that yields with a String cursor on the first run, then asserts the
    // runtime hands the same cursor back on the second run.
    final List<String> seenCursors = new ArrayList<>();
    final boolean[] firstRun = {true};
    final ScheduledTask<String> task =
        new ScheduledTask<>() {
          @Override
          public String name() {
            return "cursor-yield";
          }

          @Override
          public Result run(final TaskContext<String> ctx) {
            seenCursors.add(ctx.resumeCursor());
            if (firstRun[0]) {
              firstRun[0] = false;
              return ctx.result().yieldNow("saved");
            }
            return ctx.result().idle();
          }
        };
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.fixedRate(Duration.ofSeconds(30)).withYieldBudget(Duration.ofMillis(50)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);

    // when — run twice
    managed.execute(mock(TaskResultBuilder.class));
    managed.execute(mock(TaskResultBuilder.class));

    // then — first run saw null (no cursor yet), second run saw the saved cursor
    assertThat(seenCursors).containsExactly(null, "saved");
  }

  @Test
  void shouldClearSavedCursorAfterIdle() {
    // given — first run yields with a cursor, second run terminates with idle, third run should
    // see a null cursor again.
    final List<String> seenCursors = new ArrayList<>();
    final int[] runIndex = {0};
    final ScheduledTask<String> task =
        new ScheduledTask<>() {
          @Override
          public String name() {
            return "cursor-clear-on-idle";
          }

          @Override
          public Result run(final TaskContext<String> ctx) {
            seenCursors.add(ctx.resumeCursor());
            return switch (runIndex[0]++) {
              case 0 -> ctx.result().yieldNow("saved");
              case 1 -> ctx.result().idle();
              default -> ctx.result().idle();
            };
          }
        };
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.fixedRate(Duration.ofSeconds(30)).withYieldBudget(Duration.ofMillis(50)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);

    // when — run three times
    managed.execute(mock(TaskResultBuilder.class));
    managed.execute(mock(TaskResultBuilder.class));
    managed.execute(mock(TaskResultBuilder.class));

    // then — null, "saved" (carried from yield), null (cleared by idle)
    assertThat(seenCursors).containsExactly(null, "saved", null);
  }

  @Test
  void shouldClearSavedCursorAfterAwaitDueAt() {
    // given
    final List<String> seenCursors = new ArrayList<>();
    final int[] runIndex = {0};
    final ScheduledTask<String> task =
        new ScheduledTask<>() {
          @Override
          public String name() {
            return "cursor-clear-on-await";
          }

          @Override
          public Result run(final TaskContext<String> ctx) {
            seenCursors.add(ctx.resumeCursor());
            return switch (runIndex[0]++) {
              case 0 -> ctx.result().yieldNow("saved");
              case 1 -> ctx.result().awaitDueAt(FIXED_NOW_MS + 60_000);
              default -> ctx.result().idle();
            };
          }
        };
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.fixedRate(Duration.ofSeconds(30)).withYieldBudget(Duration.ofMillis(50)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);

    // when — run three times
    managed.execute(mock(TaskResultBuilder.class));
    managed.execute(mock(TaskResultBuilder.class));
    managed.execute(mock(TaskResultBuilder.class));

    // then — cursor cleared by awaitDueAt
    assertThat(seenCursors).containsExactly(null, "saved", null);
  }

  @Test
  void shouldClearSavedCursorOnPaused() {
    // given — yield with a cursor, then pause+resume; next run should see null.
    final List<String> seenCursors = new ArrayList<>();
    final int[] runIndex = {0};
    final ScheduledTask<String> task =
        new ScheduledTask<>() {
          @Override
          public String name() {
            return "cursor-clear-on-pause";
          }

          @Override
          public Result run(final TaskContext<String> ctx) {
            seenCursors.add(ctx.resumeCursor());
            return switch (runIndex[0]++) {
              case 0 -> ctx.result().yieldNow("saved");
              default -> ctx.result().idle();
            };
          }
        };
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.fixedRate(Duration.ofSeconds(30)).withYieldBudget(Duration.ofMillis(50)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);
    managed.execute(mock(TaskResultBuilder.class)); // first run: yields with "saved"

    // when — pause clears the cursor; resume re-arms; second run starts fresh
    managed.onPaused();
    managed.onResumed();
    managed.execute(mock(TaskResultBuilder.class));

    // then
    assertThat(seenCursors).containsExactly(null, null);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /**
   * Build a {@link ScheduledTask} that always returns the same {@link Result}, picked by applying
   * {@code terminal} to {@code ctx.result()}. Used wherever the previous test built an {@code
   * Outcome} singleton (e.g. {@code Outcome.IDLE}, {@code Outcome.YIELD_NOW}).
   */
  private static ScheduledTask<Void> newRecordingTask(
      final Function<Result.Builder<Void>, Result> terminal) {
    return new ScheduledTask<>() {
      @Override
      public String name() {
        return "recording";
      }

      @Override
      public Result run(final TaskContext<Void> ctx) {
        return terminal.apply(ctx.result());
      }
    };
  }
}
