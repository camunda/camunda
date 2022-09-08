/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import static io.camunda.zeebe.engine.util.RecordToWrite.command;
import static io.camunda.zeebe.engine.util.RecordToWrite.event;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ACTIVATE_ELEMENT;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.api.EmptyProcessingResult;
import io.camunda.zeebe.engine.api.ProcessingResult;
import io.camunda.zeebe.engine.api.ProcessingResultBuilder;
import io.camunda.zeebe.engine.api.ProcessingScheduleService;
import io.camunda.zeebe.engine.api.RecordProcessor;
import io.camunda.zeebe.engine.api.RecordProcessorContext;
import io.camunda.zeebe.engine.api.Task;
import io.camunda.zeebe.engine.api.TaskResult;
import io.camunda.zeebe.engine.api.TaskResultBuilder;
import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.engine.api.records.RecordBatch;
import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.engine.util.StreamPlatform;
import io.camunda.zeebe.engine.util.StreamPlatformExtension;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.verification.VerificationWithTimeout;

@ExtendWith(StreamPlatformExtension.class)
public class ProcessingScheduleServiceTest {

  private static final long TIMEOUT_MILLIS = 2_000L;
  private static final VerificationWithTimeout TIMEOUT = timeout(TIMEOUT_MILLIS);

  private static final ProcessInstanceRecord RECORD = Records.processInstance(1);

  @SuppressWarnings("unused") // injected by the extension
  private StreamPlatform streamPlatform;

  @SuppressWarnings("unused") // injected by the extension
  private ControlledActorClock clock;

  private DummyProcessor dummyProcessor;

  @BeforeEach
  public void before() {
    dummyProcessor = new DummyProcessor();
  }

  @AfterEach
  public void clean() {
    dummyProcessor.continueReplay();
    dummyProcessor.continueProcessing();
  }

  @Test
  public void shouldExecuteScheduledTask() {
    // given
    streamPlatform.withRecordProcessors(List.of(dummyProcessor)).startStreamProcessor();
    final var mockedTask = spy(new DummyTask());

    // when
    dummyProcessor.scheduleService.runDelayed(Duration.ZERO, mockedTask);

    // then
    verify(mockedTask, TIMEOUT).execute(any());
  }

  @Test
  public void shouldNotExecuteScheduledTaskIfProcessingIsOngoing() {
    // given
    dummyProcessor.blockProcessing();
    streamPlatform.writeBatch(command().processInstance(ACTIVATE_ELEMENT, RECORD));
    streamPlatform.withRecordProcessors(List.of(dummyProcessor)).startStreamProcessor();
    final var mockedTask = spy(new DummyTask());

    // when
    dummyProcessor.scheduleService.runDelayed(Duration.ZERO, mockedTask);

    // then
    verify(mockedTask, never()).execute(any());
  }

  @Test
  public void shouldExecuteScheduledTaskAfterProcessing() {
    // given
    dummyProcessor.blockProcessing();
    streamPlatform.writeBatch(command().processInstance(ACTIVATE_ELEMENT, RECORD));
    streamPlatform.withRecordProcessors(List.of(dummyProcessor)).startStreamProcessor();
    final var mockedTask = spy(new DummyTask());

    // when
    dummyProcessor.scheduleService.runDelayed(Duration.ZERO, mockedTask);
    verify(mockedTask, never()).execute(any());
    dummyProcessor.continueProcessing();

    // then
    verify(mockedTask, TIMEOUT).execute(any());
  }

  @Test
  public void shouldNotExecuteScheduledTaskIfOnReplay() {
    // given
    dummyProcessor.blockReplay();
    streamPlatform.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));
    streamPlatform
        .withRecordProcessors(List.of(dummyProcessor))
        .startStreamProcessorNotAwaitOpening();
    final var mockedTask = spy(new DummyTask());

    // when
    dummyProcessor.scheduleService.runDelayed(Duration.ZERO, mockedTask);

    // then
    verify(mockedTask, never()).execute(any());
  }

  @Test
  public void shouldExecuteScheduledTaskAfterReplay() {
    // given
    final var processor = spy(dummyProcessor);
    processor.blockReplay();
    streamPlatform.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));
    streamPlatform.withRecordProcessors(List.of(processor)).startStreamProcessorNotAwaitOpening();
    final var mockedTask = spy(new DummyTask());

    // when
    processor.scheduleService.runDelayed(Duration.ZERO, mockedTask);
    processor.continueReplay();

    // then
    final var inOrder = inOrder(processor, mockedTask);
    inOrder.verify(processor, TIMEOUT).init(any());
    inOrder.verify(processor, TIMEOUT).replay(any());
    inOrder.verify(mockedTask, TIMEOUT).execute(any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldNotExecuteScheduledTaskIfSuspended() {
    // given
    streamPlatform.withRecordProcessors(List.of(dummyProcessor)).startStreamProcessor();
    streamPlatform.pauseProcessing();
    final var mockedTask = spy(new DummyTask());

    // when
    dummyProcessor.scheduleService.runDelayed(Duration.ZERO, mockedTask);

    // then
    verify(mockedTask, never()).execute(any());
  }

  @Test
  public void shouldExecuteScheduledTaskAfterResumed() {
    // given
    streamPlatform.withRecordProcessors(List.of(dummyProcessor)).startStreamProcessor();
    streamPlatform.pauseProcessing();
    final var mockedTask = spy(new DummyTask());

    // when
    dummyProcessor.scheduleService.runDelayed(Duration.ZERO, mockedTask);
    streamPlatform.resumeProcessing();

    // then
    verify(mockedTask, TIMEOUT).execute(any());
  }

  @Test
  public void shouldWriteRecordAfterTaskWasExecuted() {
    // given
    final var dummyProcessorSpy = spy(dummyProcessor);
    streamPlatform.withRecordProcessors(List.of(dummyProcessorSpy)).startStreamProcessor();

    // when
    dummyProcessorSpy.scheduleService.runDelayed(
        Duration.ZERO,
        (builder) -> {
          builder.appendCommandRecord(1, ACTIVATE_ELEMENT, RECORD);
          return builder.build();
        });

    // then
    verify(dummyProcessorSpy, TIMEOUT)
        .process(Mockito.argThat(record -> record.getKey() == 1), any());
  }

  @Test
  public void shouldScheduleOnFixedRate() {
    // given
    final var dummyProcessorSpy = spy(dummyProcessor);
    streamPlatform.withRecordProcessors(List.of(dummyProcessorSpy)).startStreamProcessor();

    // when
    dummyProcessorSpy.scheduleService.runAtFixedRate(
        Duration.ofMillis(100),
        (builder) -> {
          builder.appendCommandRecord(1, ACTIVATE_ELEMENT, RECORD);
          return builder.build();
        });

    // then
    verify(dummyProcessorSpy, TIMEOUT.times(5))
        .process(Mockito.argThat(record -> record.getKey() == 1), any());
  }

  @RegressionTest("https://github.com/camunda/zeebe/issues/10240")
  void shouldPreserveOrderingOfWritesEvenWithRetries() {
    // given
    final var dummyProcessorSpy = spy(dummyProcessor);
    final var syncLogStream = spy(streamPlatform.createLogStream("stream", 1));
    final var logStream = spy(syncLogStream.getAsyncLogStream());
    final var batchWriter = spy(syncLogStream.newLogStreamBatchWriter());

    when(syncLogStream.getAsyncLogStream()).thenReturn(logStream);
    when(logStream.newLogStreamBatchWriter())
        .thenReturn(CompletableActorFuture.completed(batchWriter));
    streamPlatform
        .withRecordProcessors(List.of(dummyProcessorSpy))
        .buildStreamProcessor(syncLogStream, true);

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

              Loggers.PROCESS_PROCESSOR_LOGGER.debug("Calling real method");
              return i.callRealMethod();
            });

    dummyProcessorSpy.scheduleService.runDelayed(
        Duration.ZERO,
        builder -> {
          // force trigger second task
          clock.addTime(Duration.ofMinutes(1));
          builder.appendCommandRecord(1, ACTIVATE_ELEMENT, RECORD);
          return builder.build();
        });
    dummyProcessorSpy.scheduleService.runDelayed(
        Duration.ofMinutes(1),
        builder -> {
          Loggers.PROCESS_PROCESSOR_LOGGER.debug("Running second timer");
          builder.appendCommandRecord(2, ACTIVATE_ELEMENT, RECORD);
          return builder.build();
        });

    // then
    Awaitility.await("until both records are written to the stream")
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> assertThat(streamPlatform.events(syncLogStream.getLogName())).hasSize(2));
    assertThat(streamPlatform.events(syncLogStream.getLogName()))
        .as("records were written in order of submitted tasks")
        .extracting(LoggedEvent::getKey)
        .containsExactly(1L, 2L);
  }

  private static final class DummyTask implements Task {
    @Override
    public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
      return RecordBatch::empty;
    }
  }

  private static final class DummyProcessor implements RecordProcessor {

    private ProcessingScheduleService scheduleService;
    private CountDownLatch processingLatch;
    private CountDownLatch replayLatch;

    @Override
    public void init(final RecordProcessorContext recordProcessorContext) {
      scheduleService = recordProcessorContext.getScheduleService();
    }

    @Override
    public boolean accepts(final ValueType valueType) {
      return true;
    }

    @Override
    public void replay(final TypedRecord record) {
      if (replayLatch != null) {
        try {
          replayLatch.await();
        } catch (final InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    }

    @Override
    public ProcessingResult process(
        final TypedRecord record, final ProcessingResultBuilder processingResultBuilder) {
      if (processingLatch != null) {
        try {
          processingLatch.await();
        } catch (final InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
      return EmptyProcessingResult.INSTANCE;
    }

    @Override
    public ProcessingResult onProcessingError(
        final Throwable processingException,
        final TypedRecord record,
        final ProcessingResultBuilder processingResultBuilder) {
      return EmptyProcessingResult.INSTANCE;
    }

    public void blockProcessing() {
      processingLatch = new CountDownLatch(1);
    }

    public void continueProcessing() {
      if (processingLatch != null) {
        processingLatch.countDown();
      }
    }

    public void blockReplay() {
      replayLatch = new CountDownLatch(1);
    }

    public void continueReplay() {
      if (replayLatch != null) {
        replayLatch.countDown();
      }
    }
  }
}
