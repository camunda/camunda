/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

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
import io.camunda.zeebe.engine.api.Task;
import io.camunda.zeebe.engine.api.TaskResult;
import io.camunda.zeebe.engine.api.TaskResultBuilder;
import io.camunda.zeebe.engine.api.records.RecordBatch;
import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.logstreams.log.LogStreamBatchWriter;
import io.camunda.zeebe.logstreams.log.LogStreamBatchWriter.LogEntryBuilder;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.streamprocessor.ProcessingScheduleServiceImpl;
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
  private ProcessingScheduleServiceImpl processingScheduleService;

  @BeforeEach
  public void before() {
    clock = new ControlledActorClock();
    final var builder =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(Math.max(1, Runtime.getRuntime().availableProcessors() - 2))
            .setIoBoundActorThreadCount(2)
            .setActorClock(clock);

    actorScheduler = builder.build();
    actorScheduler.start();

    lifecycleSupplier = new LifecycleSupplier();
    writerAsyncSupplier = new WriterAsyncSupplier();
    processingScheduleService = new ProcessingScheduleServiceImpl("actorName", lifecycleSupplier,
        lifecycleSupplier, writerAsyncSupplier);
    actorScheduler.submitActor(processingScheduleService);
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
    processingScheduleService.runDelayed(Duration.ZERO, mockedTask);

    // then
    verify(mockedTask, TIMEOUT).execute(any());
  }

  @Test
  public void shouldExecuteScheduledTaskInRightOrder() {
    // given
    final var mockedTask = spy(new DummyTask());
    final var mockedTask2 = spy(new DummyTask());

    // when
    processingScheduleService.runDelayed(Duration.ZERO, mockedTask);
    processingScheduleService.runDelayed(Duration.ZERO, mockedTask2);

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
    processingScheduleService.runDelayed(Duration.ZERO, mockedTask);

    // then
    verify(mockedTask, never()).execute(any());
  }

  @Test
  public void shouldNotExecuteScheduledTaskIfAborted() {
    // given
    lifecycleSupplier.isAborted = true;
    final var mockedTask = spy(new DummyTask());

    // when
    processingScheduleService.runDelayed(Duration.ZERO, mockedTask);

    // then
    verify(mockedTask, never()).execute(any());
  }

  @Test
  public void shouldExecuteScheduledTaskInProcessing() {
    // given
    lifecycleSupplier.currentPhase = Phase.PAUSED;
    final var mockedTask = spy(new DummyTask());

    // when
    processingScheduleService.runDelayed(Duration.ZERO, mockedTask);
    verify(mockedTask, never()).execute(any());
    lifecycleSupplier.currentPhase = Phase.PROCESSING;

    // then
    verify(mockedTask, TIMEOUT).execute(any());
  }

  @Test
  public void shouldNotExecuteTasksWhenScheduledOnClosedActor() {
    // given
    lifecycleSupplier.currentPhase = Phase.PAUSED;
    final var notOpenScheduleService = new ProcessingScheduleServiceImpl("actorName", lifecycleSupplier,
        lifecycleSupplier, writerAsyncSupplier);
    final var mockedTask = spy(new DummyTask());

    // when
    notOpenScheduleService.runDelayed(Duration.ZERO, mockedTask);

    // then
    verify(mockedTask, never()).execute(any());
  }

  @Test
  public void shouldFailActorIfWriterCantBeRetrieved() {
    // given
    writerAsyncSupplier.writerFutureRef.set(CompletableActorFuture.completedExceptionally(new RuntimeException("expected")));
    final var notOpenScheduleService = new ProcessingScheduleServiceImpl("actorName", lifecycleSupplier,
        lifecycleSupplier, writerAsyncSupplier);

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
    processingScheduleService.runDelayed(
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

    processingScheduleService.runDelayed(
        Duration.ofMinutes(1),
        builder -> {
          Loggers.PROCESS_PROCESSOR_LOGGER.debug("Running second timer");
          builder.appendCommandRecord(2, ACTIVATE_ELEMENT, RECORD);
          return builder.build();
        });
    processingScheduleService.runDelayed(
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
    inOrder.verify(batchWriter, TIMEOUT).tryWrite();
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
    processingScheduleService.runAtFixedRate(
        Duration.ofMillis(10), mockedTask);

    // then
    verify(mockedTask, TIMEOUT.times(5)).execute(any());
  }

  @Test
  public void shouldNotRunScheduledTasksAfterClosed() {
    // given
    final var mockedTask = spy(new DummyTask());
    processingScheduleService.runDelayed(Duration.ofMillis(200), mockedTask);

    // when
    processingScheduleService.close();

    // then
    verify(mockedTask, never()).execute(any());
  }

  private static final class WriterAsyncSupplier implements Supplier<ActorFuture<LogStreamBatchWriter>> {
    AtomicReference<ActorFuture<LogStreamBatchWriter>> writerFutureRef = new AtomicReference<>(CompletableActorFuture.completed(mock(LogStreamBatchWriter.class)));

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
