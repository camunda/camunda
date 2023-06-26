/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ACTIVATE_ELEMENT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.api.ProcessingResultBuilder;
import io.camunda.zeebe.engine.api.RecordProcessor;
import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.engine.util.StreamPlatform;
import io.camunda.zeebe.engine.util.StreamPlatformExtension;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ErrorIntent;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.verification.VerificationWithTimeout;

@ExtendWith(StreamPlatformExtension.class)
final class MigratedStreamProcessorReplayTest {

  private static final long TIMEOUT_MILLIS = 2_000L;
  private static final VerificationWithTimeout TIMEOUT = timeout(TIMEOUT_MILLIS);

  @SuppressWarnings("unused") // injected by the extension
  private StreamPlatform streamPlatform;

  @RegressionTest("https://github.com/camunda/zeebe/issues/13101")
  public void shouldNotApplyErrorEventOnReplay() throws Exception {
    // given
    final var processorWhichFails = setupProcessorWhichFailsOnProcessing();
    setupOnErrorReactionForProcessor(processorWhichFails);

    streamPlatform.startStreamProcessor();
    // commands to process -> which should fail
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(2)));

    // await that two commands are failed and processed, to make sure error event has been committed
    verify(processorWhichFails, TIMEOUT.times(2)).onProcessingError(any(), any(), any());
    Awaitility.await("last processed position is updated")
        .until(() -> streamPlatform.getLastSuccessfulProcessedRecordPosition(), pos -> pos >= 2);
    // the snapshot should contain last processed position
    streamPlatform.snapshot();
    streamPlatform.closeStreamProcessor();
    streamPlatform.resetMockInvocations();

    // when
    streamPlatform.startStreamProcessor();
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    verifyProcessingErrorLifecycle(processorWhichFails);
    // we shouldn't replay any events - due to snapshot
    // we shouldn't replay any events - due to snapshot
    verify(processorWhichFails, never()).replay(any());
  }

  private static void verifyProcessingErrorLifecycle(final RecordProcessor processorWhichFails) {
    final var inOrder = inOrder(processorWhichFails);
    inOrder.verify(processorWhichFails, TIMEOUT).init(any());
    inOrder.verify(processorWhichFails, TIMEOUT).accepts(ValueType.PROCESS_INSTANCE);
    inOrder.verify(processorWhichFails, TIMEOUT).process(any(), any());
    inOrder.verify(processorWhichFails, TIMEOUT).onProcessingError(any(), any(), any());
    inOrder.verifyNoMoreInteractions();
  }

  private RecordProcessor setupProcessorWhichFailsOnProcessing() {
    final var processorWhichFails = streamPlatform.getDefaultMockedRecordProcessor();
    doThrow(new RuntimeException("processing error"))
        .when(processorWhichFails)
        .process(any(), any());
    return processorWhichFails;
  }

  private static void setupOnErrorReactionForProcessor(final RecordProcessor processorWhichFails) {
    doAnswer(
            invocationOnMock -> {
              // writing error event on failure
              final var builder = (ProcessingResultBuilder) invocationOnMock.getArgument(2);
              final RecordMetadata recordMetadata = new RecordMetadata();
              recordMetadata
                  .valueType(ValueType.ERROR)
                  .intent(ErrorIntent.CREATED)
                  .recordType(RecordType.EVENT);
              builder.appendRecord(
                  6,
                  RecordType.EVENT,
                  ErrorIntent.CREATED,
                  RejectionType.NULL_VAL,
                  "",
                  Records.processInstance(6));
              return builder.build();
            })
        .when(processorWhichFails)
        .onProcessingError(
            any(Throwable.class), any(TypedRecord.class), any(ProcessingResultBuilder.class));
  }
}
