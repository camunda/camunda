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
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.verification.VerificationWithTimeout;

@ExtendWith(StreamPlatformExtension.class)
public class ProcessingScheduleServiceIntegrationTest {

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
    streamPlatform = null;
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
  public void shouldNotExecuteTaskWhichAreScheduledDuringReplay() {
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
    inOrder.verify(mockedTask, never()).execute(any());
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

  @Test
  public void shouldCloseWhenStreamProcessorClosed() throws Exception {
    // given
    final var dummyProcessorSpy = spy(dummyProcessor);
    streamPlatform.withRecordProcessors(List.of(dummyProcessorSpy)).startStreamProcessor();

    // when
    streamPlatform.closeStreamProcessor();

    // then
    assertThat(streamPlatform.getStreamProcessor().isClosed()).isTrue();
    assertThat(((Actor) dummyProcessorSpy.scheduleService).isActorClosed()).isTrue();
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
