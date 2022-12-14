/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
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
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import io.camunda.zeebe.stream.impl.StreamProcessor.Phase;
import io.camunda.zeebe.stream.impl.records.RecordBatch;
import io.camunda.zeebe.stream.util.Records;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.agrona.LangUtil;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.verification.VerificationWithTimeout;

public class ProcessingScheduleServiceTest {

  private static final long TIMEOUT_MILLIS = 2_000L;
  private static final VerificationWithTimeout TIMEOUT = timeout(TIMEOUT_MILLIS);

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

    // when
    scheduleService.runDelayed(
        Duration.ZERO,
        (builder) -> {
          builder.appendCommandRecord(1, ACTIVATE_ELEMENT, Records.processInstance(1));
          return builder.build();
        });

    // then

    Awaitility.await("until the entry is written")
        .untilAsserted(
            () ->
                assertThat(writerAsyncSupplier.writer.entries)
                    .map(LogAppendEntry::key)
                    .containsExactly(1L));
  }

  @RegressionTest("https://github.com/camunda/zeebe/issues/10240")
  public void shouldPreserveOrderingOfWritesEvenWithRetries() {
    // given - in order to make sure we would interleave tasks without the fix for #10240, we need
    // to make sure we retry at least twice, such that the second task can be executed in between
    // both invocations. ensure both tasks have an expiry far away enough such that they expire on
    // different ticks, as tasks expiring on the same tick will be submitted in a non-deterministic
    // order
    final var counter = new AtomicInteger(0);
    writerAsyncSupplier.writer.acceptWrites.set(
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
          clock.addTime(Duration.ofMinutes(1));
          builder.appendCommandRecord(1, ACTIVATE_ELEMENT, Records.processInstance(1));
          return builder.build();
        });

    // then
    Awaitility.await("until both entries are written")
        .untilAsserted(
            () ->
                assertThat(writerAsyncSupplier.writer.entries)
                    .hasSize(2)
                    .map(LogAppendEntry::key)
                    .containsExactly(1L, 2L));
    assertThat(counter)
        .as("should have invoked 4999 times before accepting both writes")
        .hasValue(5001);
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

  private static final class WriterAsyncSupplier implements Supplier<ActorFuture<LogStreamWriter>> {
    private final TestWriter writer = new TestWriter();
    AtomicReference<ActorFuture<LogStreamWriter>> writerFutureRef =
        new AtomicReference<>(CompletableActorFuture.completed(writer));

    @Override
    public ActorFuture<LogStreamWriter> get() {
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

  private static final class TestWriter implements LogStreamWriter {
    private final List<LogAppendEntry> entries = new CopyOnWriteArrayList<>();
    private final AtomicReference<BooleanSupplier> acceptWrites = new AtomicReference<>(() -> true);

    @Override
    public boolean canWriteEvents(final int eventCount, final int batchSize) {
      return true;
    }

    @Override
    public long tryWrite(final List<LogAppendEntry> appendEntries, final long sourcePosition) {
      if (!acceptWrites.get().getAsBoolean()) {
        return -1;
      }

      entries.addAll(appendEntries);
      return entries.size();
    }
  }
}
