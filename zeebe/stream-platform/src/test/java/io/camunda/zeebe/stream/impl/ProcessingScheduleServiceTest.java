/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ACTIVATE_ELEMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.ScheduledCommandCache.NoopScheduledCommandCache;
import io.camunda.zeebe.stream.api.scheduling.SimpleProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import io.camunda.zeebe.stream.impl.StreamProcessor.Phase;
import io.camunda.zeebe.stream.impl.TestScheduledCommandCache.TestCommandCache;
import io.camunda.zeebe.stream.impl.metrics.ScheduledTaskMetrics;
import io.camunda.zeebe.stream.impl.records.RecordBatch;
import io.camunda.zeebe.stream.util.Records;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import io.camunda.zeebe.util.Either;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ProcessingScheduleServiceTest {

  @RegisterExtension
  ControlledActorSchedulerExtension actorScheduler = new ControlledActorSchedulerExtension();

  private final TestCommandCache commandCache = new TestCommandCache();
  private LifecycleSupplier lifecycleSupplier;
  private final TestWriter testWriter = new TestWriter();
  private TestScheduleServiceActorDecorator scheduleService;

  @BeforeEach
  void before() {
    lifecycleSupplier = new LifecycleSupplier();
    final var processingScheduleService =
        new ProcessingScheduleServiceImpl(
            lifecycleSupplier,
            lifecycleSupplier,
            () -> testWriter,
            commandCache,
            actorScheduler.getClock(),
            Duration.ofSeconds(1),
            ScheduledTaskMetrics.noop());

    scheduleService = new TestScheduleServiceActorDecorator(processingScheduleService);
    actorScheduler.submitActor(scheduleService);
    actorScheduler.workUntilDone();
  }

  @Test
  void shouldExecuteScheduledTask() {
    // given
    final var mockedTask = spy(new DummyTask());

    // when
    scheduleService.runDelayed(Duration.ZERO, mockedTask);
    actorScheduler.workUntilDone();

    // then
    verify(mockedTask).execute(any());
  }

  @Test
  void shouldExecuteScheduledTaskInRightOrder() {
    // given
    final var mockedTask = spy(new DummyTask());
    final var mockedTask2 = spy(new DummyTask());

    // when
    scheduleService.runDelayed(Duration.ZERO, mockedTask);
    scheduleService.runDelayed(Duration.ZERO, mockedTask2);
    actorScheduler.workUntilDone();

    // then
    final var inOrder = inOrder(mockedTask, mockedTask2);
    inOrder.verify(mockedTask).execute(any());
    inOrder.verify(mockedTask2).execute(any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldNotExecuteScheduledTaskIfNotInProcessingPhase() {
    // given
    lifecycleSupplier.currentPhase = Phase.INITIAL;
    final var mockedTask = spy(new DummyTask());

    // when
    scheduleService.runDelayed(Duration.ZERO, mockedTask);
    // The task will be resubmitted infinitely. So workUntilDone will never return.
    actorScheduler.resume();

    // then
    verify(mockedTask, never()).execute(any());
  }

  @Test
  void shouldNotExecuteScheduledTaskIfAborted() {
    // given
    lifecycleSupplier.isAborted = true;
    final var mockedTask = spy(new DummyTask());

    // when
    scheduleService.runDelayed(Duration.ZERO, mockedTask);
    actorScheduler.workUntilDone();

    // then
    verify(mockedTask, never()).execute(any());
  }

  @Test
  void shouldExecuteScheduledTaskInProcessing() {
    // given
    lifecycleSupplier.currentPhase = Phase.PAUSED;
    final var mockedTask = spy(new DummyTask());

    // when
    scheduleService.runDelayed(Duration.ZERO, mockedTask);
    // The task will be resubmitted infinitely. So workUntilDone will never return.
    actorScheduler.resume();
    verify(mockedTask, never()).execute(any());
    lifecycleSupplier.currentPhase = Phase.PROCESSING;

    // then
    verify(mockedTask, timeout(2_000)).execute(any());
  }

  @Test
  void shouldNotExecuteTasksWhenScheduledOnClosedActor() {
    // given
    lifecycleSupplier.currentPhase = Phase.PAUSED;
    final var notOpenScheduleService =
        new ProcessingScheduleServiceImpl(
            lifecycleSupplier,
            lifecycleSupplier,
            () -> testWriter,
            new NoopScheduledCommandCache(),
            InstantSource.system(),
            Duration.ofSeconds(1),
            ScheduledTaskMetrics.noop());
    final var mockedTask = spy(new DummyTask());

    // when
    notOpenScheduleService.runDelayed(Duration.ZERO, mockedTask);
    actorScheduler.workUntilDone();

    // then
    verify(mockedTask, never()).execute(any());
  }

  @Test
  void shouldFailActorIfWriterCantBeRetrieved() {
    // given
    final var notOpenScheduleService =
        new TestScheduleServiceActorDecorator(
            new ProcessingScheduleServiceImpl(
                lifecycleSupplier,
                lifecycleSupplier,
                () -> {
                  throw new RuntimeException("expected");
                },
                new NoopScheduledCommandCache(),
                InstantSource.system(),
                Duration.ofSeconds(1),
                ScheduledTaskMetrics.noop()));

    // when
    final var actorFuture = actorScheduler.submitActor(notOpenScheduleService);
    actorScheduler.workUntilDone();

    // then
    assertThatThrownBy(actorFuture::join).hasMessageContaining("expected");
  }

  @Test
  void shouldWriteRecordAfterTaskWasExecuted() {
    // given

    // when
    scheduleService.runDelayed(
        Duration.ZERO,
        (builder) -> {
          builder.appendCommandRecord(1, ACTIVATE_ELEMENT, Records.processInstance(1));
          return builder.build();
        });
    actorScheduler.workUntilDone();

    // then

    assertThat(testWriter.entries)
        .describedAs("Record is written to the log stream")
        .map(LogAppendEntry::key)
        .containsExactly(1L);
  }

  @RegressionTest("https://github.com/camunda/camunda/issues/10240")
  void shouldPreserveOrderingOfWritesEvenWithRetries() {
    // given - in order to make sure we would interleave tasks without the fix for #10240, we need
    // to make sure we retry at least twice, such that the second task can be executed in between
    // both invocations. ensure both tasks have an expiry far away enough such that they expire on
    // different ticks, as tasks expiring on the same tick will be submitted in a
    // non-deterministic
    // order
    final var counter = new AtomicInteger(0);
    testWriter.acceptWrites.set(
        () -> {
          final var invocationCount = counter.incrementAndGet();
          // wait a sufficiently high enough invocation count to ensure the second timer is
          // expired, gets scheduled, and then the executions are interleaved. this is quite
          // hard to do in a deterministic controlled way because of the way our timers are
          // scheduled
          if (invocationCount < 5000) {
            return false;
          }

          Loggers.STREAM_PROCESSING.debug("End tryWrite loop");
          return true;
        });

    // when
    scheduleService.runDelayed(
        Duration.ofMinutes(1),
        builder -> {
          Loggers.STREAM_PROCESSING.debug("Running second timer");
          builder.appendCommandRecord(2, ACTIVATE_ELEMENT, Records.processInstance(1));
          return builder.build();
        });
    scheduleService.runDelayed(
        Duration.ZERO,
        builder -> {
          Loggers.STREAM_PROCESSING.debug("Running first timer");
          // force trigger second task
          actorScheduler.updateClock(Duration.ofMinutes(1));
          builder.appendCommandRecord(1, ACTIVATE_ELEMENT, Records.processInstance(1));
          return builder.build();
        });
    actorScheduler.workUntilDone();

    // then
    assertThat(testWriter.entries)
        .describedAs("Both timers have executed")
        .hasSize(2)
        .map(LogAppendEntry::key)
        .containsExactly(1L, 2L);
    assertThat(counter)
        .as("should have invoked 4999 times before accepting both writes")
        .hasValue(5001);
  }

  @Test
  void shouldScheduleOnFixedRate() {
    // given
    final var mockedTask = spy(new DummyTask());

    // when
    scheduleService.runAtFixedRate(Duration.ofSeconds(1), mockedTask);
    actorScheduler.workUntilDone();
    for (int i = 0; i < 5; i++) {
      actorScheduler.updateClock(Duration.ofSeconds(1));
      actorScheduler.workUntilDone();
    }

    // then
    verify(mockedTask, times(5)).execute(any());
  }

  @Test
  void shouldNotRunScheduledTasksAfterClosed() {
    // given
    final var mockedTask = spy(new DummyTask());
    scheduleService.runDelayed(Duration.ofMillis(200), mockedTask);

    // when
    final var closed = scheduleService.closeAsync();
    actorScheduler.workUntilDone();
    closed.join();

    // then
    verify(mockedTask, never()).execute(any());
  }

  @Test
  void shouldCacheWrittenCommands() {
    // given

    // when
    scheduleService.runDelayed(
        Duration.ZERO,
        (builder) -> {
          builder.appendCommandRecord(1, ACTIVATE_ELEMENT, Records.processInstance(1));
          return builder.build();
        });
    actorScheduler.workUntilDone();

    // then - it's sufficient to assert it was staged for caching, and then the staged cache was
    // persisted
    assertThat(commandCache.stagedCache().contains(ACTIVATE_ELEMENT, 1)).isTrue();
    assertThat(commandCache.stagedCache().persisted()).isTrue();
    assertThat(commandCache.contains(ACTIVATE_ELEMENT, 1)).isTrue();
    assertThat(testWriter.entries).extracting(LogAppendEntry::key).containsExactly(1L);
  }

  @Test
  void shouldNotWriteCachedCommands() {
    // given
    commandCache.add(ACTIVATE_ELEMENT, 1);

    // when
    scheduleService.runDelayed(
        Duration.ZERO,
        (builder) -> {
          builder.appendCommandRecord(1, ACTIVATE_ELEMENT, Records.processInstance(1));
          return builder.build();
        });
    actorScheduler.workUntilDone();

    // then
    assertThat(testWriter.entries).isEmpty();
  }

  @Test
  void shouldNotCacheWrittenCommandsIfWriteFails() {
    // given
    testWriter.acceptWrites.set(
        () -> {
          throw new RuntimeException("failure");
        });

    // when
    scheduleService.runDelayed(
        Duration.ZERO,
        (builder) -> {
          builder.appendCommandRecord(1, ACTIVATE_ELEMENT, Records.processInstance(1));
          return builder.build();
        });
    actorScheduler.workUntilDone();

    // then - write was staged for caching, but not persisted due to error
    assertThat(commandCache.stagedCache().contains(ACTIVATE_ELEMENT, 1)).isTrue();
    assertThat(commandCache.stagedCache().persisted()).isFalse();
    assertThat(commandCache.contains(ACTIVATE_ELEMENT, 1)).isFalse();
  }

  @Test
  void shouldNotExecuteCancelledDelayedTask() {
    // given
    final var mockedTask = spy(new DummyTask());

    final var scheduledTask = scheduleService.runDelayed(Duration.ofMinutes(1), mockedTask);
    actorScheduler.workUntilDone(); // Ensure task is scheduled

    scheduledTask.cancel();
    actorScheduler.workUntilDone(); // Ensure task is cancelled

    // when
    actorScheduler.updateClock(Duration.ofMinutes(2));
    actorScheduler.workUntilDone(); // Would execute task if not cancelled

    // then
    verify(mockedTask, never()).execute(any());
  }

  @Test
  void shouldInitializeMetrics() {
    // given
    final var registry = new SimpleMeterRegistry();
    final var scheduleService =
        new TestScheduleServiceActorDecorator(
            new ProcessingScheduleServiceImpl(
                lifecycleSupplier,
                lifecycleSupplier,
                () -> testWriter,
                commandCache,
                actorScheduler.getClock(),
                Duration.ofSeconds(1),
                ScheduledTaskMetrics.of(registry)));

    // when
    actorScheduler.submitActor(scheduleService);
    actorScheduler.workUntilDone();

    // then
    assertThat(registry.getMeters()).isNotEmpty();
    assertThat(registry.get("zeebe.processing.scheduling.tasks").gauge().value()).isEqualTo(0);
    assertThat(registry.get("zeebe.processing.scheduling.delay").timer().count()).isEqualTo(0);
    assertThat(registry.get("zeebe.processing.scheduling.duration").timer().count()).isEqualTo(0);
  }

  @Test
  void shouldUpdateMetricsOnScheduling() {
    // given
    final var registry = new SimpleMeterRegistry();
    final var scheduleService =
        new TestScheduleServiceActorDecorator(
            new ProcessingScheduleServiceImpl(
                lifecycleSupplier,
                lifecycleSupplier,
                () -> testWriter,
                commandCache,
                actorScheduler.getClock(),
                Duration.ofSeconds(1),
                ScheduledTaskMetrics.of(registry)));
    actorScheduler.submitActor(scheduleService);
    actorScheduler.workUntilDone();

    // when -- scheduling a task
    scheduleService.runDelayed(Duration.ofMinutes(1), new DummyTask());
    actorScheduler.workUntilDone();

    // then -- count is increased
    assertThat(registry.get("zeebe.processing.scheduling.tasks").gauge().value()).isEqualTo(1);
  }

  @Test
  void shouldUpdateMetricsOnExecution() {
    // given
    final var registry = new SimpleMeterRegistry();
    final var scheduleService =
        new TestScheduleServiceActorDecorator(
            new ProcessingScheduleServiceImpl(
                lifecycleSupplier,
                lifecycleSupplier,
                () -> testWriter,
                commandCache,
                actorScheduler.getClock(),
                Duration.ofSeconds(1),
                ScheduledTaskMetrics.of(registry)));
    actorScheduler.submitActor(scheduleService);
    actorScheduler.workUntilDone();

    // when -- scheduling and executing a task
    scheduleService.runDelayed(Duration.ofMinutes(1), new DummyTask());
    actorScheduler.workUntilDone();
    actorScheduler.updateClock(Duration.ofMinutes(2));
    actorScheduler.workUntilDone();

    // then
    assertThat(registry.get("zeebe.processing.scheduling.tasks").gauge().value()).isEqualTo(0);
    assertThat(registry.get("zeebe.processing.scheduling.delay").timer().count()).isEqualTo(1);
    assertThat(registry.get("zeebe.processing.scheduling.delay").timer().max(TimeUnit.MINUTES))
        .isCloseTo(1, Percentage.withPercentage(20));
    assertThat(registry.get("zeebe.processing.scheduling.duration").timer().count()).isEqualTo(1);
  }

  /**
   * This decorator is an actor and implements {@link ProcessingScheduleService} and delegates to
   * {@link ProcessingScheduleServiceImpl}, on each call it will submit an extra job to the related
   * Actor in order to schedule the work on the same ActorThread. This is needed since we are not
   * allowed to schedule timers from outside to the actor.
   *
   * <p>Note: This is an actor scheduler limitation, since the used way how we schedule timers are
   * not thread safe, so this need to happen on the same thread, meaning on the same actor.
   */
  private static final class TestScheduleServiceActorDecorator extends Actor
      implements SimpleProcessingScheduleService {
    private final ProcessingScheduleServiceImpl processingScheduleService;

    public TestScheduleServiceActorDecorator(
        final ProcessingScheduleServiceImpl processingScheduleService) {
      this.processingScheduleService = processingScheduleService;
    }

    @Override
    protected void onActorStarting() {
      final var openFuture = processingScheduleService.open(actor);
      actor.runOnCompletionBlockingCurrentPhase(
          openFuture,
          (v, t) -> {
            if (t != null) {
              actor.fail(t);
            }
          });
    }

    @Override
    public ScheduledTask runDelayed(final Duration delay, final Runnable task) {
      final var futureScheduledTask =
          actor.call(() -> processingScheduleService.runDelayed(delay, task));
      return () ->
          actor.run(
              () ->
                  actor.runOnCompletion(
                      futureScheduledTask,
                      (scheduledTask, throwable) -> {
                        if (scheduledTask != null) {
                          scheduledTask.cancel();
                        }
                      }));
    }

    @Override
    public ScheduledTask runDelayed(final Duration delay, final Task task) {
      final var futureScheduledTask =
          actor.call(() -> processingScheduleService.runDelayed(delay, task));
      return () ->
          actor.run(
              () ->
                  actor.runOnCompletion(
                      futureScheduledTask,
                      (scheduledTask, throwable) -> {
                        if (scheduledTask != null) {
                          scheduledTask.cancel();
                        }
                      }));
    }

    @Override
    public ScheduledTask runAt(final long timestamp, final Task task) {
      final var futureScheduledTask =
          actor.call(() -> processingScheduleService.runAt(timestamp, task));
      return () ->
          actor.run(
              () ->
                  actor.runOnCompletion(
                      futureScheduledTask,
                      (scheduledTask, throwable) -> {
                        if (scheduledTask != null) {
                          scheduledTask.cancel();
                        }
                      }));
    }

    @Override
    public ScheduledTask runAt(final long timestamp, final Runnable task) {
      final var futureScheduledTask =
          actor.call(() -> processingScheduleService.runAt(timestamp, task));
      return () ->
          actor.run(
              () ->
                  actor.runOnCompletion(
                      futureScheduledTask,
                      (scheduledTask, throwable) -> {
                        if (scheduledTask != null) {
                          scheduledTask.cancel();
                        }
                      }));
    }

    @Override
    public void runAtFixedRate(final Duration delay, final Task task) {
      actor.submit(() -> processingScheduleService.runAtFixedRate(delay, task));
    }
  }

  private static final class LifecycleSupplier implements Supplier<Phase>, BooleanSupplier {

    volatile Phase currentPhase = Phase.PROCESSING;
    volatile boolean isAborted = false;

    @Override
    public boolean getAsBoolean() {
      return isAborted;
    }

    @Override
    public Phase get() {
      return currentPhase;
    }
  }

  private static final class DummyTask implements Task {
    @Override
    public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
      return RecordBatch::empty;
    }
  }

  private static final class TestWriter implements LogStreamWriter {
    private final List<LogAppendEntry> entries = new CopyOnWriteArrayList<>();
    private final AtomicReference<BooleanSupplier> acceptWrites = new AtomicReference<>(() -> true);

    @Override
    public boolean canWriteEvents(final int eventCount, final int batchSize) {
      return true;
    }

    @Override
    public Either<WriteFailure, Long> tryWrite(
        final WriteContext context,
        final List<LogAppendEntry> appendEntries,
        final long sourcePosition) {
      if (!acceptWrites.get().getAsBoolean()) {
        return Either.left(WriteFailure.WRITE_LIMIT_EXHAUSTED);
      }

      entries.addAll(appendEntries);
      return Either.right((long) entries.size());
    }
  }

  @Nested
  class RunAtTests {

    @BeforeEach
    void before() {
      actorScheduler.setClockTime(100);
    }

    @Test
    void shouldNotExecuteScheduledTaskBeforeTimestamp() {
      // given
      final var mockedTask = spy(new DummyTask());

      // when
      scheduleService.runAt(90, mockedTask);
      actorScheduler.workUntilDone();

      // then
      verify(mockedTask).execute(any());
    }

    @Test
    void shouldExecuteScheduledTaskAtTimestamp() {
      // given
      final var mockedTask = spy(new DummyTask());

      // when
      scheduleService.runAt(100, mockedTask);
      actorScheduler.workUntilDone();

      // then
      verify(mockedTask).execute(any());
    }

    @Test
    void shouldExecuteScheduledTaskInOrderOfTimestamp() {
      // given
      final var mockedTask = spy(new DummyTask());
      final var mockedTask2 = spy(new DummyTask());

      // when
      scheduleService.runAt(100, mockedTask);
      scheduleService.runAt(90, mockedTask2);
      actorScheduler.workUntilDone();

      // then
      final var inOrder = inOrder(mockedTask2, mockedTask);
      inOrder.verify(mockedTask2).execute(any());
      inOrder.verify(mockedTask).execute(any());
      inOrder.verifyNoMoreInteractions();
    }

    @Test
    void shouldNotExecuteScheduledTaskIfNotInProcessingPhase() {
      // given
      lifecycleSupplier.currentPhase = Phase.INITIAL;
      final var mockedTask = spy(new DummyTask());

      // when
      scheduleService.runAt(100, mockedTask);
      // The task will be resubmitted infinitely. So workUntilDone will never return.
      actorScheduler.resume();

      // then
      verify(mockedTask, never()).execute(any());
    }

    @Test
    void shouldNotExecuteScheduledTaskIfAborted() {
      // given
      lifecycleSupplier.isAborted = true;
      final var mockedTask = spy(new DummyTask());

      // when
      scheduleService.runAt(100, mockedTask);
      actorScheduler.workUntilDone();

      // then
      verify(mockedTask, never()).execute(any());
    }

    @Test
    void shouldExecuteScheduledTaskInProcessing() {
      // given
      lifecycleSupplier.currentPhase = Phase.PAUSED;
      final var mockedTask = spy(new DummyTask());

      // when
      scheduleService.runAt(100, mockedTask);
      // The task will be resubmitted infinitely. So workUntilDone will never return.
      actorScheduler.resume();
      verify(mockedTask, never()).execute(any());
      lifecycleSupplier.currentPhase = Phase.PROCESSING;

      // then
      verify(mockedTask, timeout(2_000)).execute(any());
    }

    @Test
    void shouldNotExecuteTasksWhenScheduledOnClosedActor() {
      // given
      lifecycleSupplier.currentPhase = Phase.PAUSED;
      final var notOpenScheduleService =
          new ProcessingScheduleServiceImpl(
              lifecycleSupplier,
              lifecycleSupplier,
              () -> testWriter,
              new NoopScheduledCommandCache(),
              InstantSource.system(),
              Duration.ofSeconds(1),
              ScheduledTaskMetrics.noop());
      final var mockedTask = spy(new DummyTask());

      // when
      notOpenScheduleService.runAt(100, mockedTask);
      actorScheduler.workUntilDone();

      // then
      verify(mockedTask, never()).execute(any());
    }

    @Test
    void shouldWriteRecordAfterTaskWasExecuted() {
      // given

      // when
      scheduleService.runAt(
          100,
          (builder) -> {
            builder.appendCommandRecord(1, ACTIVATE_ELEMENT, Records.processInstance(1));
            return builder.build();
          });
      actorScheduler.workUntilDone();

      // then

      assertThat(testWriter.entries)
          .describedAs("Record is written to the log stream")
          .map(LogAppendEntry::key)
          .containsExactly(1L);
    }
  }
}
