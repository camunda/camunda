/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import static io.camunda.zeebe.engine.util.RecordToWrite.command;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ACTIVATE_ELEMENT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.engine.util.StreamPlatform;
import io.camunda.zeebe.engine.util.StreamPlatformExtension;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
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
public class ProcessingScheduleServiceTest {


  private static final long TIMEOUT_MILLIS = 2_000L;
  private static final VerificationWithTimeout TIMEOUT = timeout(TIMEOUT_MILLIS);

  private static final ProcessInstanceRecord RECORD = Records.processInstance(1);

  @SuppressWarnings("unused") // injected by the extension
  private StreamPlatform streamPlatform;
  private DummyProcessor dummyProcessor;

  @BeforeEach
  public void before() {
    dummyProcessor = new DummyProcessor();
  }

  @AfterEach
  public void clean() {
    dummyProcessor.continueProcessing();
  }

  @Test
  public void shouldExecuteScheduledTask() {
    // given
    streamPlatform.withRecordProcessors(List.of(dummyProcessor)).startStreamProcessor();
    final var mockedTask = mock(Task.class);

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
    final var mockedTask = mock(Task.class);

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
    final var mockedTask = mock(Task.class);

    // when
    dummyProcessor.scheduleService.runDelayed(Duration.ZERO, mockedTask);
    verify(mockedTask, never()).execute(any());
    dummyProcessor.continueProcessing();

    // then
    verify(mockedTask, TIMEOUT).execute(any());
  }

  @Test
  public void shouldWriteRecordAfterTaskWasExecuted() {
    // given
    final var dummyProcessorSpy = spy(dummyProcessor);
    streamPlatform.withRecordProcessors(List.of(dummyProcessorSpy)).startStreamProcessor();

    // when
    dummyProcessorSpy.scheduleService.runDelayed(Duration.ZERO,
        (builder) -> builder.appendCommandRecord(1, ACTIVATE_ELEMENT, RECORD).build());

    // then
    verify(dummyProcessorSpy, TIMEOUT)
        .process(Mockito.argThat(record -> record.getKey() == 1), any());
  }

  private final class DummyProcessor implements RecordProcessor {

    private ProcessingScheduleService scheduleService;
    private CountDownLatch latch;

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
      // do nothing
    }

    @Override
    public ProcessingResult process(final TypedRecord record,
        final ProcessingResultBuilder processingResultBuilder) {
      if (latch != null) {
        try {
          latch.await();
        } catch (final InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
      return EmptyProcessingResult.INSTANCE;
    }

    @Override
    public ProcessingResult onProcessingError(final Throwable processingException,
        final TypedRecord record,
        final ProcessingResultBuilder processingResultBuilder) {
      return EmptyProcessingResult.INSTANCE;
    }

    public void blockProcessing() {
      latch = new CountDownLatch(1);
    }

    public void continueProcessing() {
      if (latch != null) {
        latch.countDown();
      }
    }
  }

}
