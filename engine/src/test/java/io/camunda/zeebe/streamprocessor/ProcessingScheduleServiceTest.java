/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ACTIVATE_ELEMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.api.ProcessingScheduleService;
import io.camunda.zeebe.engine.api.SimpleProcessingScheduleService;
import io.camunda.zeebe.engine.api.Task;
import io.camunda.zeebe.engine.api.TaskResult;
import io.camunda.zeebe.engine.api.TaskResultBuilder;
import io.camunda.zeebe.engine.api.records.RecordBatch;
import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.logstreams.log.LogStreamBatchWriter;
import io.camunda.zeebe.logstreams.log.LogStreamBatchWriter.LogEntryBuilder;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.streamprocessor.StreamProcessor.Phase;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;

class ProcessingScheduleServiceTest {

  private static final ProcessInstanceRecord RECORD = Records.processInstance(1);

  @RegisterExtension
  ControlledActorSchedulerExtension actorScheduler = new ControlledActorSchedulerExtension();

  private LifecycleSupplier lifecycleSupplier;
  private WriterAsyncSupplier writerAsyncSupplier;
  private TestScheduleServiceActorDecorator scheduleService;

  @BeforeEach
  void before() {
    lifecycleSupplier = new LifecycleSupplier();
    writerAsyncSupplier = new WriterAsyncSupplier();
    final var processingScheduleService =
        new ProcessingScheduleServiceImpl(
            lifecycleSupplier, lifecycleSupplier, writerAsyncSupplier);

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
            lifecycleSupplier, lifecycleSupplier, writerAsyncSupplier);
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
    writerAsyncSupplier.writerFutureRef.set(
        CompletableActorFuture.completedExceptionally(new RuntimeException("expected")));
    final var notOpenScheduleService =
        new TestScheduleServiceActorDecorator(
            new ProcessingScheduleServiceImpl(
                lifecycleSupplier, lifecycleSupplier, writerAsyncSupplier));

    // when
    final var actorFuture = actorScheduler.submitActor(notOpenScheduleService);
    actorScheduler.workUntilDone();

    // then
    assertThatThrownBy(actorFuture::join).hasMessageContaining("expected");
  }

  @Test
  void shouldWriteRecordAfterTaskWasExecuted() {
    // given
    final var batchWriter = writerAsyncSupplier.get().join();
    when(batchWriter.canWriteAdditionalEvent(anyInt(), anyInt())).thenReturn(true);
    final var logEntryBuilder = mock(LogEntryBuilder.class, Mockito.RETURNS_DEEP_STUBS);
    when(batchWriter.event()).thenReturn(logEntryBuilder);

    // when
    scheduleService.runDelayed(
        Duration.ZERO,
        (builder) -> {
          builder.appendCommandRecord(1, ACTIVATE_ELEMENT, RECORD);
          return builder.build();
        });
    actorScheduler.workUntilDone();

    // then
    verify(batchWriter).event();
    verify(logEntryBuilder).key(1);
    verify(batchWriter).tryWrite();
  }

  @RegressionTest("https://github.com/camunda/zeebe/issues/10240")
  void shouldPreserveOrderingOfWritesEvenWithRetries() throws InterruptedException {
    // given
    final var batchWriter = writerAsyncSupplier.get().join();
    when(batchWriter.canWriteAdditionalEvent(anyInt(), anyInt())).thenReturn(true);
    final var logEntryBuilder = mock(LogEntryBuilder.class, Mockito.RETURNS_DEEP_STUBS);
    when(batchWriter.event()).thenReturn(logEntryBuilder);
    final CountDownLatch timersExecuted = new CountDownLatch(2);

    // when - in order to make sure we would interleave tasks without the fix for #10240, we need to
    // make sure we retry at least twice, such that the second task can be executed in between both
    // invocations. ensure both tasks have an expiry far away enough such that they expire on
    // different ticks, as tasks expiring on the same tick will be submitted in a non-deterministic
    // order
    final var counter = new AtomicInteger(0);
    when(batchWriter.tryWrite())
        .then(
            i -> {
              final var invocationCount = counter.incrementAndGet();
              // wait a sufficiently high enough invocation count to ensure the second timer is
              // expired, gets scheduled, and then the executions are interleaved. this is quite
              // hard to do in a deterministic controlled way because of the way our timers are
              // scheduled
              if (invocationCount < 5000) {
                return -1L;
              }

              Loggers.PROCESS_PROCESSOR_LOGGER.debug("End tryWrite loop");
              timersExecuted.countDown();
              return 0L;
            });

    scheduleService.runDelayed(
        Duration.ofMinutes(1),
        builder -> {
          Loggers.PROCESS_PROCESSOR_LOGGER.debug("Running second timer");
          builder.appendCommandRecord(2, ACTIVATE_ELEMENT, RECORD);
          return builder.build();
        });
    scheduleService.runDelayed(
        Duration.ZERO,
        builder -> {
          Loggers.PROCESS_PROCESSOR_LOGGER.debug("Running first timer");
          // force trigger second task
          actorScheduler.updateClock(Duration.ofMinutes(1));
          builder.appendCommandRecord(1, ACTIVATE_ELEMENT, RECORD);
          return builder.build();
        });
    actorScheduler.workUntilDone();

    // then
    assertThat(timersExecuted.await(10, TimeUnit.SECONDS))
        .describedAs("Both timers have completed execution")
        .isTrue();
    final var inOrder = inOrder(batchWriter, logEntryBuilder);

    inOrder.verify(batchWriter).event();
    inOrder.verify(logEntryBuilder).key(1);
    inOrder.verify(batchWriter, times(5000)).tryWrite();
    inOrder.verify(batchWriter).event();
    inOrder.verify(logEntryBuilder).key(2);
    inOrder.verify(batchWriter).tryWrite();
    inOrder.verifyNoMoreInteractions();
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
    public void runDelayed(final Duration delay, final Runnable task) {
      actor.submit(() -> processingScheduleService.runDelayed(delay, task));
    }

    @Override
    public void runDelayed(final Duration delay, final Task task) {
      actor.submit(() -> processingScheduleService.runDelayed(delay, task));
    }

    @Override
    public void runAtFixedRate(final Duration delay, final Task task) {
      actor.submit(() -> processingScheduleService.runAtFixedRate(delay, task));
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
  }

  private static final class WriterAsyncSupplier
      implements Supplier<ActorFuture<LogStreamBatchWriter>> {
    AtomicReference<ActorFuture<LogStreamBatchWriter>> writerFutureRef =
        new AtomicReference<>(CompletableActorFuture.completed(mock(LogStreamBatchWriter.class)));

    @Override
    public ActorFuture<LogStreamBatchWriter> get() {
      return writerFutureRef.get();
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
}
