/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ACTIVATE_ELEMENT;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.stream.api.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.Task;
import io.camunda.zeebe.stream.api.TaskResult;
import io.camunda.zeebe.stream.api.TaskResultBuilder;
import io.camunda.zeebe.stream.api.records.RecordBatch;
import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.logstreams.log.LogStreamBatchWriter;
import io.camunda.zeebe.logstreams.log.LogStreamBatchWriter.LogEntryBuilder;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.streamprocessor.StreamProcessor.Phase;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.agrona.LangUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.verification.VerificationWithTimeout;

public class ProcessingScheduleServiceTest {

  private static final long TIMEOUT_MILLIS = 2_000L;
  private static final VerificationWithTimeout TIMEOUT = timeout(TIMEOUT_MILLIS);

  private static final ProcessInstanceRecord RECORD = Records.processInstance(1);

  private ControlledActorClock clock;
  private ActorScheduler actorScheduler;
  private LifecycleSupplier lifecycleSupplier;
  private WriterAsyncSupplier writerAsyncSupplier;
  private TestScheduleServiceActorDecorator scheduleService;

  @BeforeEach
  public void before() {
    clock = new ControlledActorClock();
    final var builder =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(
                Math.max(1, Runtime.getRuntime().availableProcessors() - 2))
            .setIoBoundActorThreadCount(2)
            .setActorClock(clock);

    actorScheduler = builder.build();
    actorScheduler.start();

    lifecycleSupplier = new LifecycleSupplier();
    writerAsyncSupplier = new WriterAsyncSupplier();
    final var processingScheduleService =
        new ProcessingScheduleServiceImpl(
            lifecycleSupplier, lifecycleSupplier, writerAsyncSupplier);

    scheduleService = new TestScheduleServiceActorDecorator(processingScheduleService);
    actorScheduler.submitActor(scheduleService);
  }

  @AfterEach
  public void clean() {
    try {
      actorScheduler.close();
    } catch (final Exception e) {
      LangUtil.rethrowUnchecked(e);
    }

    actorScheduler = null;
  }

  @Test
  public void shouldExecuteScheduledTask() {
    // given
    final var mockedTask = spy(new DummyTask());

    // when
    scheduleService.runDelayed(Duration.ZERO, mockedTask);

    // then
    verify(mockedTask, TIMEOUT).execute(any());
  }

  @Test
  public void shouldExecuteScheduledTaskInRightOrder() {
    // given
    final var mockedTask = spy(new DummyTask());
    final var mockedTask2 = spy(new DummyTask());

    // when
    scheduleService.runDelayed(Duration.ZERO, mockedTask);
    scheduleService.runDelayed(Duration.ZERO, mockedTask2);

    // then
    final var inOrder = inOrder(mockedTask, mockedTask2);
    inOrder.verify(mockedTask, TIMEOUT).execute(any());
    inOrder.verify(mockedTask2, TIMEOUT).execute(any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldNotExecuteScheduledTaskIfNotInProcessingPhase() {
    // given
    lifecycleSupplier.currentPhase = Phase.INITIAL;
    final var mockedTask = spy(new DummyTask());

    // when
    scheduleService.runDelayed(Duration.ZERO, mockedTask);

    // then
    verify(mockedTask, never()).execute(any());
  }

  @Test
  public void shouldNotExecuteScheduledTaskIfAborted() {
    // given
    lifecycleSupplier.isAborted = true;
    final var mockedTask = spy(new DummyTask());

    // when
    scheduleService.runDelayed(Duration.ZERO, mockedTask);

    // then
    verify(mockedTask, never()).execute(any());
  }

  @Test
  public void shouldExecuteScheduledTaskInProcessing() {
    // given
    lifecycleSupplier.currentPhase = Phase.PAUSED;
    final var mockedTask = spy(new DummyTask());

    // when
    scheduleService.runDelayed(Duration.ZERO, mockedTask);
    verify(mockedTask, never()).execute(any());
    lifecycleSupplier.currentPhase = Phase.PROCESSING;

    // then
    verify(mockedTask, TIMEOUT).execute(any());
  }

  @Test
  public void shouldNotExecuteTasksWhenScheduledOnClosedActor() {
    // given
    lifecycleSupplier.currentPhase = Phase.PAUSED;
    final var notOpenScheduleService =
        new ProcessingScheduleServiceImpl(
            lifecycleSupplier, lifecycleSupplier, writerAsyncSupplier);
    final var mockedTask = spy(new DummyTask());

    // when
    notOpenScheduleService.runDelayed(Duration.ZERO, mockedTask);

    // then
    verify(mockedTask, never()).execute(any());
  }

  @Test
  public void shouldFailActorIfWriterCantBeRetrieved() {
    // given
    writerAsyncSupplier.writerFutureRef.set(
        CompletableActorFuture.completedExceptionally(new RuntimeException("expected")));
    final var notOpenScheduleService =
        new TestScheduleServiceActorDecorator(
            new ProcessingScheduleServiceImpl(
                lifecycleSupplier, lifecycleSupplier, writerAsyncSupplier));

    // when
    final var actorFuture = actorScheduler.submitActor(notOpenScheduleService);

    // then
    assertThatThrownBy(actorFuture::join).hasMessageContaining("expected");
  }

  @Test
  public void shouldWriteRecordAfterTaskWasExecuted() {
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

    // then
    verify(batchWriter, TIMEOUT).event();
    verify(logEntryBuilder, TIMEOUT).key(1);
    verify(batchWriter, TIMEOUT).tryWrite();
  }

  @RegressionTest("https://github.com/camunda/zeebe/issues/10240")
  public void shouldPreserveOrderingOfWritesEvenWithRetries() {
    // given
    final var batchWriter = writerAsyncSupplier.get().join();
    when(batchWriter.canWriteAdditionalEvent(anyInt(), anyInt())).thenReturn(true);
    final var logEntryBuilder = mock(LogEntryBuilder.class, Mockito.RETURNS_DEEP_STUBS);
    when(batchWriter.event()).thenReturn(logEntryBuilder);

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
          clock.addTime(Duration.ofMinutes(1));
          builder.appendCommandRecord(1, ACTIVATE_ELEMENT, RECORD);
          return builder.build();
        });

    // then
    final var inOrder = inOrder(batchWriter, logEntryBuilder);

    inOrder.verify(batchWriter, TIMEOUT).event();
    inOrder.verify(logEntryBuilder, TIMEOUT).key(1);
    inOrder.verify(batchWriter, TIMEOUT.times(5000)).tryWrite();
    inOrder.verify(batchWriter, TIMEOUT).event();
    inOrder.verify(logEntryBuilder, TIMEOUT).key(2);
    inOrder.verify(batchWriter, TIMEOUT).tryWrite();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldScheduleOnFixedRate() {
    // given
    final var mockedTask = spy(new DummyTask());

    // when
    scheduleService.runAtFixedRate(Duration.ofMillis(10), mockedTask);

    // then
    verify(mockedTask, TIMEOUT.atLeast(5)).execute(any());
  }

  @Test
  public void shouldNotRunScheduledTasksAfterClosed() {
    // given
    final var mockedTask = spy(new DummyTask());
    scheduleService.runDelayed(Duration.ofMillis(200), mockedTask);

    // when
    scheduleService.close();

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
      implements ProcessingScheduleService {
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
