/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.stream.impl;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ACTIVATE_ELEMENT;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.COMPLETE_ELEMENT;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.stream.api.EmptyProcessingResult;
import io.camunda.zeebe.stream.api.PostCommitTask;
import io.camunda.zeebe.stream.api.ProcessingResult;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.RecordProcessor;
import io.camunda.zeebe.stream.api.RecordProcessorContext;
import io.camunda.zeebe.stream.api.records.ExceededBatchRecordSizeException;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.impl.records.RecordBatchEntry;
import io.camunda.zeebe.stream.impl.state.DbKeyGenerator;
import io.camunda.zeebe.stream.util.RecordToWrite;
import io.camunda.zeebe.stream.util.Records;
import io.camunda.zeebe.util.exception.RecoverableException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.verification.VerificationWithTimeout;

@ExtendWith(StreamPlatformExtension.class)
public final class StreamProcessorTest {

  private static final long TIMEOUT_MILLIS = 2_000L;
  private static final VerificationWithTimeout TIMEOUT = timeout(TIMEOUT_MILLIS);

  @SuppressWarnings("unused") // injected by the extension
  private StreamPlatform streamPlatform;

  private ControlledActorClock actorClock;

  @Test
  public void shouldCallStreamProcessorLifecycle() throws Exception {
    // given

    // when
    streamPlatform.startStreamProcessor();
    streamPlatform.pauseProcessing();
    streamPlatform.resumeProcessing();
    streamPlatform.closeStreamProcessor();

    // then
    final var mockProcessorLifecycleAware = streamPlatform.getMockProcessorLifecycleAware();

    final InOrder inOrder = inOrder(mockProcessorLifecycleAware);
    inOrder.verify(mockProcessorLifecycleAware, TIMEOUT).onRecovered(ArgumentMatchers.any());
    inOrder.verify(mockProcessorLifecycleAware, TIMEOUT).onPaused();
    inOrder.verify(mockProcessorLifecycleAware, TIMEOUT).onResumed();
    inOrder.verify(mockProcessorLifecycleAware, TIMEOUT).onClose();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldCallOnPausedBeforeOnResumedNoMatterWhenResumedWasCalled() throws Exception {
    // given

    // when
    streamPlatform.startStreamProcessor();
    streamPlatform.resumeProcessing();
    streamPlatform.pauseProcessing();
    streamPlatform.resumeProcessing();
    streamPlatform.closeStreamProcessor();

    // then
    final var mockProcessorLifecycleAware = streamPlatform.getMockProcessorLifecycleAware();

    final InOrder inOrder = inOrder(mockProcessorLifecycleAware);
    inOrder.verify(mockProcessorLifecycleAware, TIMEOUT).onRecovered(ArgumentMatchers.any());
    inOrder.verify(mockProcessorLifecycleAware, TIMEOUT).onPaused();
    inOrder.verify(mockProcessorLifecycleAware, TIMEOUT).onResumed();
    inOrder.verify(mockProcessorLifecycleAware, TIMEOUT).onClose();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldCallStreamProcessorLifecycleOnFail() {
    // given
    final var mockProcessorLifecycleAware = streamPlatform.getMockProcessorLifecycleAware();
    doThrow(new RuntimeException("force fail"))
        .when(mockProcessorLifecycleAware)
        .onRecovered(any());

    // when
    streamPlatform.startStreamProcessor();

    // then
    verify(mockProcessorLifecycleAware, TIMEOUT).onFailed();
  }

  @Test
  public void shouldCallRecordProcessorLifecycle() {
    // given
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.event()
            .processInstance(ELEMENT_ACTIVATING, Records.processInstance(1))
            .causedBy(0));
    final var defaultRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    streamPlatform.startStreamProcessor();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.event()
            .processInstance(ELEMENT_ACTIVATING, Records.processInstance(1))
            .causedBy(0));

    // then
    final var inOrder = inOrder(defaultRecordProcessor);
    inOrder.verify(defaultRecordProcessor, TIMEOUT).init(any());
    inOrder.verify(defaultRecordProcessor, TIMEOUT).accepts(ValueType.PROCESS_INSTANCE);
    inOrder.verify(defaultRecordProcessor, TIMEOUT).replay(any());
    inOrder.verify(defaultRecordProcessor, TIMEOUT).accepts(ValueType.PROCESS_INSTANCE);
    inOrder.verify(defaultRecordProcessor, TIMEOUT).process(any(), any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldCallOnErrorWhenProcessingFails() {
    // given
    final var defaultRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final var processingError = new RuntimeException("processing error");
    doThrow(processingError).when(defaultRecordProcessor).process(any(), any());
    streamPlatform.startStreamProcessor();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.event()
            .processInstance(ELEMENT_ACTIVATING, Records.processInstance(1))
            .causedBy(0));

    // then
    final var inOrder = inOrder(defaultRecordProcessor);
    inOrder.verify(defaultRecordProcessor, TIMEOUT).init(any());
    inOrder.verify(defaultRecordProcessor, TIMEOUT).accepts(ValueType.PROCESS_INSTANCE);
    inOrder.verify(defaultRecordProcessor, TIMEOUT).process(any(), any());
    inOrder
        .verify(defaultRecordProcessor, TIMEOUT)
        .onProcessingError(eq(processingError), any(), any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldLoopWhenOnErrorFails() {
    // given
    final var defaultRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final var processingError = new RuntimeException("processing error");
    doThrow(processingError).when(defaultRecordProcessor).process(any(), any());
    doThrow(processingError).when(defaultRecordProcessor).onProcessingError(any(), any(), any());
    streamPlatform.startStreamProcessor();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.event()
            .processInstance(ELEMENT_ACTIVATING, Records.processInstance(1))
            .causedBy(0));

    // then
    final var inOrder = inOrder(defaultRecordProcessor);
    inOrder.verify(defaultRecordProcessor, TIMEOUT).init(any());
    inOrder.verify(defaultRecordProcessor, TIMEOUT).accepts(ValueType.PROCESS_INSTANCE);
    inOrder.verify(defaultRecordProcessor, TIMEOUT).process(any(), any());
    inOrder
        .verify(defaultRecordProcessor, TIMEOUT.atLeast(5))
        .onProcessingError(eq(processingError), any(), any());
  }

  @Test
  public void shouldRetryProcessingRecordOnRecoverableException() {
    // given
    final var defaultRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final var processingError = new RecoverableException("processing error");
    doThrow(processingError).when(defaultRecordProcessor).process(any(), any());
    streamPlatform.startStreamProcessor();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.event()
            .processInstance(ELEMENT_ACTIVATING, Records.processInstance(1))
            .causedBy(0));

    // then
    verify(defaultRecordProcessor, TIMEOUT.atLeast(2)).process(any(), any());
  }

  @Test
  public void shouldNotCallProcessWhenNotAcceptingRecord() {
    // given
    final var defaultRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    when(defaultRecordProcessor.accepts(any())).thenReturn(false);
    streamPlatform.startStreamProcessor();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.event()
            .processInstance(ELEMENT_ACTIVATING, Records.processInstance(1))
            .causedBy(0));

    // then
    verify(defaultRecordProcessor, TIMEOUT).accepts(any());
    verify(defaultRecordProcessor, never()).process(any(), any());
  }

  @Test
  public void shouldProcessOnlyCommands() {
    // given
    final var defaultRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    streamPlatform.startStreamProcessor();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.event()
            .processInstance(ELEMENT_ACTIVATING, Records.processInstance(1))
            .causedBy(0),
        RecordToWrite.rejection().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    verify(defaultRecordProcessor, TIMEOUT.times(1)).process(any(), any());
  }

  @Test
  public void shouldProcessFollowUpEventsAndCommands() {
    // given
    final var defaultRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final var resultBuilderCaptor = ArgumentCaptor.forClass(ProcessingResultBuilder.class);
    when(defaultRecordProcessor.process(any(), resultBuilderCaptor.capture()))
        .thenAnswer(
            (invocation) -> {
              final var resultBuilder = resultBuilderCaptor.getValue();
              resultBuilder.appendRecordReturnEither(
                  1,
                  RecordType.EVENT,
                  ACTIVATE_ELEMENT,
                  RejectionType.NULL_VAL,
                  "",
                  Records.processInstance(1));
              resultBuilder.appendRecordReturnEither(
                  2,
                  RecordType.COMMAND,
                  ACTIVATE_ELEMENT,
                  RejectionType.NULL_VAL,
                  "",
                  Records.processInstance(1));
              return resultBuilder.build();
            })
        .thenAnswer(
            (invocation) -> {
              final var resultBuilder = resultBuilderCaptor.getValue();
              resultBuilder.appendRecordReturnEither(
                  3,
                  RecordType.EVENT,
                  ACTIVATE_ELEMENT,
                  RejectionType.NULL_VAL,
                  "",
                  Records.processInstance(1));
              return resultBuilder.build();
            });

    streamPlatform.startStreamProcessor();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    verify(defaultRecordProcessor, TIMEOUT.times(2)).process(any(), any());
    await("Last written position should be updated")
        .untilAsserted(
            () -> assertThat(streamPlatform.getLogStream().getLastWrittenPosition()).isEqualTo(4));
    await("Last processed position should be updated")
        .untilAsserted(
            () ->
                assertThat(
                        streamPlatform.getStreamProcessor().getLastProcessedPositionAsync().join())
                    .isEqualTo(1));

    final var logStreamReader = streamPlatform.getLogStream().newLogStreamReader();
    logStreamReader.seekToFirstEvent();
    final var firstRecord = logStreamReader.next();
    assertThat(firstRecord.getSourceEventPosition()).isEqualTo(-1);
    final var firstRecordPosition = firstRecord.getPosition();

    await("should write follow up events")
        .untilAsserted(() -> assertThat(logStreamReader.hasNext()).isTrue());
    while (logStreamReader.hasNext()) {
      assertThat(logStreamReader.next().getSourceEventPosition()).isEqualTo(firstRecordPosition);
    }
  }

  @Test
  public void shouldSetSourcePointerForFollowUpRecords() {
    // given
    final var defaultRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final var resultBuilder = new BufferedProcessingResultBuilder((c, v) -> true);
    resultBuilder.appendRecordReturnEither(
        1,
        RecordType.EVENT,
        ACTIVATE_ELEMENT,
        RejectionType.NULL_VAL,
        "",
        Records.processInstance(1));
    resultBuilder.appendRecordReturnEither(
        2,
        RecordType.COMMAND,
        COMPLETE_ELEMENT,
        RejectionType.NULL_VAL,
        "",
        Records.processInstance(1));

    when(defaultRecordProcessor.process(any(), any())).thenReturn(resultBuilder.build());

    streamPlatform.startStreamProcessor();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    verify(defaultRecordProcessor, TIMEOUT.times(2)).process(any(), any());

    final var logStreamReader = streamPlatform.getLogStream().newLogStreamReader();
    logStreamReader.seekToFirstEvent();
    final var firstRecord = logStreamReader.next();
    assertThat(firstRecord.getSourceEventPosition()).isEqualTo(-1);
    final var firstRecordPosition = firstRecord.getPosition();

    await("should write follow up events")
        .untilAsserted(() -> assertThat(logStreamReader.hasNext()).isTrue());
    assertThat(logStreamReader.hasNext()).isTrue();
    assertThat(logStreamReader.next().getSourceEventPosition()).isEqualTo(firstRecordPosition);
    assertThat(logStreamReader.hasNext()).isTrue();
    assertThat(logStreamReader.next().getSourceEventPosition()).isEqualTo(firstRecordPosition);
  }

  @Test
  public void shouldBlockProcessingIfSchedulingBlocks() throws InterruptedException {
    // given
    final var mockProcessorLifecycleAware = streamPlatform.getMockProcessorLifecycleAware();
    final CountDownLatch asyncServiceLatch = new CountDownLatch(1);
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    doAnswer(
            (invocationOnMock) -> {
              final var context = (ReadonlyStreamProcessorContext) invocationOnMock.getArgument(0);

              context
                  .getScheduleService()
                  .runAtFixedRate(
                      Duration.ZERO,
                      (taskResultBuilder) -> {
                        try {
                          asyncServiceLatch.countDown();
                          countDownLatch.await();
                        } catch (final InterruptedException e) {
                          throw new RuntimeException(e);
                        }
                        return taskResultBuilder.build();
                      });

              return invocationOnMock.callRealMethod();
            })
        .when(mockProcessorLifecycleAware)
        .onRecovered(any());

    final var defaultRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    streamPlatform.startStreamProcessor();

    // when
    assertThat(asyncServiceLatch.await(10, TimeUnit.SECONDS)).isTrue();
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    verify(defaultRecordProcessor, timeout(500).times(0)).process(any(), any());
    // free processor
    countDownLatch.countDown();
  }

  @Test
  public void shouldProcessEvenIfAsyncSchedulingBlocks() throws InterruptedException {
    // given
    final var mockProcessorLifecycleAware = streamPlatform.getMockProcessorLifecycleAware();
    final CountDownLatch asyncServiceLatch = new CountDownLatch(1);
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    doAnswer(
            (invocationOnMock) -> {
              final var context = (ReadonlyStreamProcessorContext) invocationOnMock.getArgument(0);

              context
                  .getScheduleService()
                  .runAtFixedRateAsync(
                      Duration.ZERO,
                      (taskResultBuilder) -> {
                        try {
                          asyncServiceLatch.countDown();
                          countDownLatch.await();
                        } catch (final InterruptedException e) {
                          throw new RuntimeException(e);
                        }
                        return taskResultBuilder.build();
                      });

              return invocationOnMock.callRealMethod();
            })
        .when(mockProcessorLifecycleAware)
        .onRecovered(any());

    final var defaultRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    streamPlatform.startStreamProcessor();

    // when
    assertThat(asyncServiceLatch.await(10, TimeUnit.SECONDS)).isTrue();
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    verify(defaultRecordProcessor, TIMEOUT.times(1)).process(any(), any());
    // free schedule service
    countDownLatch.countDown();
  }

  @Disabled("Should be enabled when https://github.com/camunda/zeebe/issues/11849 is fixed")
  @Test
  public void shouldRunAsyncSchedulingEvenIfProcessingIsBlocked() throws InterruptedException {
    // given
    final var mockProcessorLifecycleAware = streamPlatform.getMockProcessorLifecycleAware();
    final CountDownLatch asyncServiceLatch = new CountDownLatch(1);
    final CountDownLatch processorLatch = new CountDownLatch(1);
    final CountDownLatch waitLatch = new CountDownLatch(1);
    doAnswer(
            (invocationOnMock) -> {
              final var context = (ReadonlyStreamProcessorContext) invocationOnMock.getArgument(0);

              context
                  .getScheduleService()
                  .runAtFixedRateAsync(
                      Duration.ofMinutes(1),
                      (taskResultBuilder) -> {
                        asyncServiceLatch.countDown();
                        return taskResultBuilder.build();
                      });

              return invocationOnMock.callRealMethod();
            })
        .when(mockProcessorLifecycleAware)
        .onRecovered(any());

    final var defaultRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    doAnswer(
            (invocationOnMock -> {
              try {
                processorLatch.countDown();
                waitLatch.await();
              } catch (final InterruptedException e) {
                throw new RuntimeException(e);
              }
              return invocationOnMock.callRealMethod();
            }))
        .when(defaultRecordProcessor)
        .process(any(), any());
    streamPlatform.startStreamProcessor();

    try {
      // when
      streamPlatform.writeBatch(
          RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));
      assertThat(processorLatch.await(5, TimeUnit.SECONDS)).isTrue();

      // then
      await("ProcessScheduleService should still work")
          .timeout(Duration.ofSeconds(5))
          .until(
              () -> {
                actorClock.addTime(Duration.ofMillis(100));
                return asyncServiceLatch.await(100, TimeUnit.MILLISECONDS);
              });
      verify(defaultRecordProcessor, TIMEOUT).process(any(), any());

    } finally {
      // free schedule service
      waitLatch.countDown();
    }
  }

  @Test
  public void shouldExecutePostCommitTask() {
    // given
    final var defaultMockedRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final var mockPostCommitTask = mock(PostCommitTask.class);
    when(mockPostCommitTask.flush()).thenReturn(true);
    final var resultBuilder = new BufferedProcessingResultBuilder((c, s) -> true);
    resultBuilder.appendPostCommitTask(mockPostCommitTask);
    when(defaultMockedRecordProcessor.process(any(), any())).thenReturn(resultBuilder.build());
    streamPlatform.startStreamProcessor();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    verify(mockPostCommitTask, TIMEOUT).flush();
  }

  @Test
  public void shouldRepeatExecutePostCommitTask() {
    // given
    final var defaultMockedRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final var mockPostCommitTask = mock(PostCommitTask.class);
    when(mockPostCommitTask.flush()).thenReturn(false);

    final var resultBuilder = new BufferedProcessingResultBuilder((c, s) -> true);
    resultBuilder.appendPostCommitTask(mockPostCommitTask);
    when(defaultMockedRecordProcessor.process(any(), any())).thenReturn(resultBuilder.build());

    streamPlatform.startStreamProcessor();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    verify(mockPostCommitTask, TIMEOUT.atLeast(5)).flush();
  }

  @Test
  public void shouldNotRepeatPostCommitOnException() throws Exception {
    // given
    final var defaultMockedRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final var mockPostCommitTask = mock(PostCommitTask.class);
    when(mockPostCommitTask.flush()).thenThrow(new RuntimeException("expected"));

    final var resultBuilder = new BufferedProcessingResultBuilder((c, s) -> true);
    resultBuilder.appendPostCommitTask(mockPostCommitTask);
    when(defaultMockedRecordProcessor.process(any(), any()))
        .thenReturn(resultBuilder.build())
        .thenReturn(EmptyProcessingResult.INSTANCE);

    streamPlatform.startStreamProcessor();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    verify(defaultMockedRecordProcessor, TIMEOUT.times(2)).process(any(), any());
    verify(mockPostCommitTask, TIMEOUT.times(1)).flush();
  }

  @Test
  public void shouldUpdateStateOnSuccessfulProcessing() {
    // given
    final var testProcessor = spy(new TestProcessor());
    testProcessor.processingAction =
        (ctx) -> {
          final var zeebeDb = ctx.getZeebeDb();
          final var keyGenerator = new DbKeyGenerator(1, zeebeDb, ctx.getTransactionContext());
          keyGenerator.nextKey();
          keyGenerator.nextKey();
          keyGenerator.nextKey();
        };
    // in order to not mark the processing as skipped we need to return a result
    testProcessor.processingResult =
        new BufferedProcessingResultBuilder((c, s) -> true)
            .appendPostCommitTask(() -> true)
            .build();
    doCallRealMethod()
        .doReturn(EmptyProcessingResult.INSTANCE)
        .when(testProcessor)
        .process(any(), any());
    streamPlatform.withRecordProcessors(List.of(testProcessor)).startStreamProcessor();

    final var zeebeDb = testProcessor.recordProcessorContext.getZeebeDb();
    final var keyGenerator = new DbKeyGenerator(1, zeebeDb, zeebeDb.createContext());
    final var firstKey = keyGenerator.nextKey();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    verify(testProcessor, TIMEOUT.times(2)).process(any(), any());

    final var nextKey = keyGenerator.nextKey();
    AssertionsForClassTypes.assertThat(nextKey).isEqualTo(firstKey + 4);
  }

  @Test
  public void shouldNotUpdateStateOnExceptionInProcessing() {
    // given
    final var testProcessor = spy(new TestProcessor());
    testProcessor.processingAction =
        (ctx) -> {
          final var zeebeDb = ctx.getZeebeDb();
          final var keyGenerator = new DbKeyGenerator(1, zeebeDb, ctx.getTransactionContext());
          keyGenerator.nextKey();
          keyGenerator.nextKey();
          keyGenerator.nextKey();

          throw new RuntimeException("expected");
        };
    // in order to not mark the processing as skipped we need to return a result
    testProcessor.processingResult = new BufferedProcessingResultBuilder((c, s) -> true).build();
    doCallRealMethod()
        .doReturn(EmptyProcessingResult.INSTANCE)
        .when(testProcessor)
        .process(any(), any());
    streamPlatform.withRecordProcessors(List.of(testProcessor)).startStreamProcessor();

    final var zeebeDb = testProcessor.recordProcessorContext.getZeebeDb();
    final var keyGenerator = new DbKeyGenerator(1, zeebeDb, zeebeDb.createContext());
    final var firstKey = keyGenerator.nextKey();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    verify(testProcessor, TIMEOUT.times(2)).process(any(), any());

    final var nextKey = keyGenerator.nextKey();
    AssertionsForClassTypes.assertThat(nextKey).isEqualTo(firstKey + 1);
  }

  @Test
  public void shouldUpdateStateOnProcessingErrorCall() {
    // given
    final var testProcessor = spy(new TestProcessor());
    testProcessor.processingAction =
        (ctx) -> {
          throw new RuntimeException("expected");
        };
    testProcessor.onProcessingErrorAction =
        (ctx) -> {
          final var zeebeDb = ctx.getZeebeDb();
          final var keyGenerator = new DbKeyGenerator(1, zeebeDb, ctx.getTransactionContext());
          keyGenerator.nextKey();
          keyGenerator.nextKey();
          keyGenerator.nextKey();
        };
    doCallRealMethod()
        .doReturn(EmptyProcessingResult.INSTANCE)
        .when(testProcessor)
        .process(any(), any());
    streamPlatform.withRecordProcessors(List.of(testProcessor)).startStreamProcessor();

    final var zeebeDb = testProcessor.recordProcessorContext.getZeebeDb();
    final var keyGenerator = new DbKeyGenerator(1, zeebeDb, zeebeDb.createContext());
    final var firstKey = keyGenerator.nextKey();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    verify(testProcessor, TIMEOUT.times(2)).process(any(), any());

    final var nextKey = keyGenerator.nextKey();
    AssertionsForClassTypes.assertThat(nextKey).isEqualTo(firstKey + 4);
  }

  @Test
  public void shouldNotUpdateStateOnExceptionOnProcessingErrorCall() {
    // given
    final var testProcessor = spy(new TestProcessor());
    testProcessor.processingAction =
        (ctx) -> {
          throw new RuntimeException("expected");
        };
    testProcessor.onProcessingErrorAction =
        (ctx) -> {
          final var zeebeDb = ctx.getZeebeDb();
          final var keyGenerator = new DbKeyGenerator(1, zeebeDb, ctx.getTransactionContext());
          keyGenerator.nextKey();
          keyGenerator.nextKey();
          keyGenerator.nextKey();

          throw new RuntimeException("expected");
        };
    doCallRealMethod()
        .doReturn(EmptyProcessingResult.INSTANCE)
        .when(testProcessor)
        .process(any(), any());
    doCallRealMethod()
        .doReturn(EmptyProcessingResult.INSTANCE)
        .when(testProcessor)
        .onProcessingError(any(), any(), any());
    streamPlatform.withRecordProcessors(List.of(testProcessor)).startStreamProcessor();

    final var zeebeDb = testProcessor.recordProcessorContext.getZeebeDb();
    final var keyGenerator = new DbKeyGenerator(1, zeebeDb, zeebeDb.createContext());
    final var firstKey = keyGenerator.nextKey();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    verify(testProcessor, TIMEOUT.times(2)).process(any(), any());

    final var nextKey = keyGenerator.nextKey();
    AssertionsForClassTypes.assertThat(nextKey).isEqualTo(firstKey + 1);
  }

  @Test
  public void shouldWriteResponse() {
    // given
    final var defaultMockedRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();

    final var resultBuilder = new BufferedProcessingResultBuilder((c, s) -> true);
    resultBuilder.withResponse(
        RecordType.EVENT,
        3,
        ELEMENT_ACTIVATING,
        Records.processInstance(1),
        ValueType.PROCESS_INSTANCE,
        RejectionType.NULL_VAL,
        "",
        1,
        12);
    when(defaultMockedRecordProcessor.process(any(), any()))
        .thenReturn(resultBuilder.build())
        .thenReturn(EmptyProcessingResult.INSTANCE);

    streamPlatform.startStreamProcessor();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    verify(defaultMockedRecordProcessor, TIMEOUT.times(2)).process(any(), any());

    final var commandResponseWriter = streamPlatform.getMockCommandResponseWriter();

    verify(commandResponseWriter, TIMEOUT.times(1)).key(3);
    verify(commandResponseWriter, TIMEOUT.times(1))
        .intent(ProcessInstanceIntent.ELEMENT_ACTIVATING);
    verify(commandResponseWriter, TIMEOUT.times(1)).recordType(RecordType.EVENT);
    verify(commandResponseWriter, TIMEOUT.times(1)).valueType(ValueType.PROCESS_INSTANCE);
    verify(commandResponseWriter, TIMEOUT.times(1)).tryWriteResponse(anyInt(), anyLong());
  }

  @Test
  public void shouldWriteMultipleResponses() {
    // given
    final var defaultMockedRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();

    final var resultBuilder = new BufferedProcessingResultBuilder((c, s) -> true);
    resultBuilder
        .withResponse(
            RecordType.EVENT,
            3,
            ELEMENT_ACTIVATING,
            Records.processInstance(1),
            ValueType.PROCESS_INSTANCE,
            RejectionType.NULL_VAL,
            "",
            1,
            12)
        .appendRecord(
            4,
            RecordType.COMMAND,
            ELEMENT_ACTIVATING,
            RejectionType.NULL_VAL,
            "",
            Records.processInstance(1));
    final var secondResultBuilder = new BufferedProcessingResultBuilder((c, s) -> true);
    secondResultBuilder.withResponse(
        RecordType.EVENT,
        4,
        ELEMENT_ACTIVATING,
        Records.processInstance(1),
        ValueType.PROCESS_INSTANCE,
        RejectionType.NULL_VAL,
        "",
        2,
        12);

    when(defaultMockedRecordProcessor.process(any(), any()))
        .thenReturn(resultBuilder.build())
        .thenReturn(secondResultBuilder.build());

    streamPlatform.startStreamProcessor();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    verify(defaultMockedRecordProcessor, TIMEOUT.times(2)).process(any(), any());

    final var commandResponseWriter = streamPlatform.getMockCommandResponseWriter();

    verify(commandResponseWriter, TIMEOUT.times(1)).key(3);
    verify(commandResponseWriter, TIMEOUT.times(1)).key(4);
  }

  @Test
  public void shouldWriteResponseOnFailedEventProcessing() {
    // given
    final var defaultMockedRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    when(defaultMockedRecordProcessor.process(any(), any())).thenThrow(new RuntimeException());

    final var resultBuilder = new BufferedProcessingResultBuilder((c, s) -> true);
    resultBuilder.withResponse(
        RecordType.EVENT,
        3,
        ELEMENT_ACTIVATING,
        Records.processInstance(1),
        ValueType.PROCESS_INSTANCE,
        RejectionType.NULL_VAL,
        "",
        1,
        12);
    when(defaultMockedRecordProcessor.onProcessingError(any(), any(), any()))
        .thenReturn(resultBuilder.build());

    streamPlatform.startStreamProcessor();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    verify(defaultMockedRecordProcessor, TIMEOUT.times(1)).process(any(), any());
    verify(defaultMockedRecordProcessor, TIMEOUT.times(1)).onProcessingError(any(), any(), any());

    final var commandResponseWriter = streamPlatform.getMockCommandResponseWriter();

    verify(commandResponseWriter, TIMEOUT.times(1)).key(3);
    verify(commandResponseWriter, TIMEOUT.times(1))
        .intent(ProcessInstanceIntent.ELEMENT_ACTIVATING);
    verify(commandResponseWriter, TIMEOUT.times(1)).recordType(RecordType.EVENT);
    verify(commandResponseWriter, TIMEOUT.times(1)).valueType(ValueType.PROCESS_INSTANCE);
    verify(commandResponseWriter, TIMEOUT.times(1)).tryWriteResponse(anyInt(), anyLong());
  }

  @Test
  public void shouldContinueProcessingAfterFailedProcessing() {
    // given
    final var defaultMockedRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    when(defaultMockedRecordProcessor.process(any(), any()))
        .thenThrow(new RuntimeException())
        .thenReturn(EmptyProcessingResult.INSTANCE);

    streamPlatform.startStreamProcessor();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    verify(defaultMockedRecordProcessor, TIMEOUT.times(2)).process(any(), any());
    verify(defaultMockedRecordProcessor, TIMEOUT.times(1)).onProcessingError(any(), any(), any());
  }

  @Test
  public void shouldBeAbleToWriteRejectionOnErrorHandling() {
    // given
    final var defaultMockedRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    when(defaultMockedRecordProcessor.process(any(), any())).thenThrow(new RuntimeException());

    final var resultBuilder = new BufferedProcessingResultBuilder((c, s) -> true);
    resultBuilder.appendRecordReturnEither(
        1,
        RecordType.COMMAND_REJECTION,
        ACTIVATE_ELEMENT,
        RejectionType.NULL_VAL,
        "",
        Records.processInstance(1));
    when(defaultMockedRecordProcessor.onProcessingError(any(), any(), any()))
        .thenReturn(resultBuilder.build());

    streamPlatform.startStreamProcessor();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    verify(defaultMockedRecordProcessor, TIMEOUT.times(1)).process(any(), any());
    verify(defaultMockedRecordProcessor, TIMEOUT.times(1)).onProcessingError(any(), any(), any());

    final var logStreamReader = streamPlatform.getLogStream().newLogStreamReader();
    logStreamReader.seekToFirstEvent();
    logStreamReader.next(); // command

    // rejection
    await("should write rejection to log")
        .untilAsserted(() -> assertThat(logStreamReader.hasNext()).isTrue());
    final var record = logStreamReader.next();
    final var recordMetadata = new RecordMetadata();
    record.readMetadata(recordMetadata);
    assertThat(recordMetadata.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(record.getSourceEventPosition()).isEqualTo(1);
  }

  @Test
  public void shouldInvokeOnProcessedListenerWhenReturnResult() {
    // given
    final var mockStreamProcessorListener = streamPlatform.getMockStreamProcessorListener();
    final var defaultMockedRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();

    final var resultBuilder = new BufferedProcessingResultBuilder((c, s) -> true);
    resultBuilder.appendPostCommitTask(() -> true);
    when(defaultMockedRecordProcessor.process(any(), any())).thenReturn(resultBuilder.build());
    streamPlatform.startStreamProcessor();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then

    verify(defaultMockedRecordProcessor, TIMEOUT.times(2)).process(any(), any());
    verify(mockStreamProcessorListener, TIMEOUT.times(2)).onProcessed(any());
  }

  @Test
  public void shouldInvokeSkippedOnProcessedListenerWhenReturnEmptyResult() {
    // given
    final var mockStreamProcessorListener = streamPlatform.getMockStreamProcessorListener();
    final var defaultMockedRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final var resultBuilder = new BufferedProcessingResultBuilder((c, s) -> true);
    when(defaultMockedRecordProcessor.process(any(), any())).thenReturn(resultBuilder.build());
    streamPlatform.startStreamProcessor();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    verify(defaultMockedRecordProcessor, TIMEOUT.times(2)).process(any(), any());
    verify(mockStreamProcessorListener, TIMEOUT.times(2)).onSkipped(any());
  }

  @Test
  public void shouldNotSkipWhenResultContainsTaskOnly() {
    // given
    final var mockStreamProcessorListener = streamPlatform.getMockStreamProcessorListener();
    final var defaultMockedRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final var resultBuilder = new BufferedProcessingResultBuilder((c, s) -> true);
    resultBuilder.appendPostCommitTask(() -> true);
    when(defaultMockedRecordProcessor.process(any(), any())).thenReturn(resultBuilder.build());
    streamPlatform.startStreamProcessor();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    verify(defaultMockedRecordProcessor, TIMEOUT.times(2)).process(any(), any());
    verify(mockStreamProcessorListener, TIMEOUT.times(0)).onSkipped(any());
  }

  @Test
  public void shouldNotSkipWhenResultContainsRecordOnly() {
    // given
    final var mockStreamProcessorListener = streamPlatform.getMockStreamProcessorListener();
    final var defaultMockedRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final var resultBuilder = new BufferedProcessingResultBuilder((c, s) -> true);
    resultBuilder.appendRecordReturnEither(
        1,
        RecordType.EVENT,
        ELEMENT_ACTIVATING,
        RejectionType.NULL_VAL,
        "",
        Records.processInstance(1));
    when(defaultMockedRecordProcessor.process(any(), any())).thenReturn(resultBuilder.build());
    streamPlatform.startStreamProcessor();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    verify(defaultMockedRecordProcessor, TIMEOUT.times(2)).process(any(), any());
    final var loggedEventArgumentCaptor = ArgumentCaptor.forClass(LoggedEvent.class);
    verify(mockStreamProcessorListener, TIMEOUT.times(2))
        .onSkipped(loggedEventArgumentCaptor.capture());

    Assertions.assertThat(loggedEventArgumentCaptor.getAllValues())
        .extracting(
            loggedEvent -> {
              final RecordMetadata recordMetadata = new RecordMetadata();
              loggedEvent.readMetadata(recordMetadata);
              return recordMetadata.getRecordType();
            })
        .containsOnly(RecordType.EVENT);
  }

  @Test
  public void shouldNotSkipWhenResultContainsResponseOnly() {
    // given
    final var mockStreamProcessorListener = streamPlatform.getMockStreamProcessorListener();
    final var defaultMockedRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final var resultBuilder = new BufferedProcessingResultBuilder((c, s) -> true);
    resultBuilder.withResponse(
        RecordType.EVENT,
        1,
        ELEMENT_ACTIVATING,
        Records.processInstance(1),
        ValueType.PROCESS_INSTANCE,
        RejectionType.NULL_VAL,
        "",
        -1,
        -1);
    when(defaultMockedRecordProcessor.process(any(), any())).thenReturn(resultBuilder.build());
    streamPlatform.startStreamProcessor();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    verify(defaultMockedRecordProcessor, TIMEOUT.times(2)).process(any(), any());
    verify(mockStreamProcessorListener, TIMEOUT.times(0)).onSkipped(any());
  }

  @Test
  public void shouldPauseProcessing() {
    // given
    streamPlatform.startStreamProcessor();
    streamPlatform.pauseProcessing();

    final var mockProcessorLifecycleAware = streamPlatform.getMockProcessorLifecycleAware();
    verify(mockProcessorLifecycleAware, TIMEOUT).onRecovered(ArgumentMatchers.any());
    verify(mockProcessorLifecycleAware, TIMEOUT).onPaused();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    final var defaultMockedRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    verify(defaultMockedRecordProcessor, never()).process(any(), any());
  }

  @Test
  public void shouldResumeProcessing() {
    // given
    streamPlatform.startStreamProcessor();
    streamPlatform.pauseProcessing();

    final var mockProcessorLifecycleAware = streamPlatform.getMockProcessorLifecycleAware();
    verify(mockProcessorLifecycleAware, TIMEOUT).onRecovered(ArgumentMatchers.any());
    verify(mockProcessorLifecycleAware, TIMEOUT).onPaused();
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    final var defaultMockedRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    verify(defaultMockedRecordProcessor, never()).process(any(), any());

    // when
    streamPlatform.resumeProcessing();

    // then
    verify(defaultMockedRecordProcessor, TIMEOUT.times(2)).process(any(), any());
  }

  @Test
  public void shouldNotUpdateLastWrittenPositionWhenSkipped() {
    // given
    // processor returns empty result, meaning record is marked as skipped
    final var defaultRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    streamPlatform.startStreamProcessor();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    verify(defaultRecordProcessor, TIMEOUT.times(2)).process(any(), any());
    await("Last written position should be updated")
        .untilAsserted(
            () ->
                assertThat(streamPlatform.getStreamProcessor().getLastWrittenPositionAsync().join())
                    .isEqualTo(-1));
  }

  @Test
  public void shouldCallOnErrorWhenProcessingFailsWithExceedingBatchInTransaction() {
    // given
    final var defaultRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();

    final var processingError =
        new ExceededBatchRecordSizeException(mock(RecordBatchEntry.class), 10, 1, 1);

    when(defaultRecordProcessor.process(any(), any()))
        .then(
            (invocationOnMock -> {
              streamPlatform
                  .getZeebeDb()
                  .createContext()
                  .runInTransaction(
                      () -> {
                        throw processingError;
                      });
              return null;
            }));
    streamPlatform.startStreamProcessor();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.event()
            .processInstance(ELEMENT_ACTIVATING, Records.processInstance(1))
            .causedBy(0));

    // then
    final var inOrder = inOrder(defaultRecordProcessor);
    inOrder.verify(defaultRecordProcessor, TIMEOUT).init(any());
    inOrder.verify(defaultRecordProcessor, TIMEOUT).accepts(ValueType.PROCESS_INSTANCE);
    inOrder.verify(defaultRecordProcessor, TIMEOUT).process(any(), any());
    inOrder
        .verify(defaultRecordProcessor, TIMEOUT)
        .onProcessingError(eq(processingError), any(), any());
    inOrder.verifyNoMoreInteractions();
  }

  private static final class TestProcessor implements RecordProcessor {

    ProcessingResult processingResult = EmptyProcessingResult.INSTANCE;
    ProcessingResult processingResultOnError = EmptyProcessingResult.INSTANCE;
    RecordProcessorContext recordProcessorContext;
    private Consumer<RecordProcessorContext> processingAction = (ctx) -> {};
    private Consumer<RecordProcessorContext> onProcessingErrorAction = (ctx) -> {};

    @Override
    public void init(final RecordProcessorContext recordProcessorContext) {
      this.recordProcessorContext = recordProcessorContext;
    }

    @Override
    public boolean accepts(final ValueType valueType) {
      return true;
    }

    @Override
    public void replay(final TypedRecord record) {}

    @Override
    public ProcessingResult process(
        final TypedRecord record, final ProcessingResultBuilder processingResultBuilder) {
      processingAction.accept(recordProcessorContext);
      return processingResult;
    }

    @Override
    public ProcessingResult onProcessingError(
        final Throwable processingException,
        final TypedRecord record,
        final ProcessingResultBuilder processingResultBuilder) {
      onProcessingErrorAction.accept(recordProcessorContext);
      return processingResultOnError;
    }
  }
}
