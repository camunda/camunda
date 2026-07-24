/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ACTIVATE_ELEMENT;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.stream.api.ProcessingResult;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.util.RecordToWrite;
import io.camunda.zeebe.stream.util.Records;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.verification.VerificationWithTimeout;

/**
 * Tests the maximum batch processing time: a batch stops growing once processing it took longer
 * than the configured limit, but only if a client is waiting on a response from the batch or
 * further records are waiting on the log.
 */
@ExtendWith(StreamPlatformExtension.class)
final class StreamProcessorMaxBatchProcessingTimeTest {

  private static final long TIMEOUT_MILLIS = 2_000L;
  private static final VerificationWithTimeout TIMEOUT = timeout(TIMEOUT_MILLIS);

  @SuppressWarnings("unused") // injected by the extension
  private StreamPlatform streamPlatform;

  @Test
  void shouldProcessFollowUpCommandInOwnBatchWhenMaxBatchProcessingTimeIsExhausted() {
    // given -- processing takes longer than the maximum batch processing time, a client is waiting
    // on a response and another command is waiting on the log
    final var processor = streamPlatform.getDefaultMockedRecordProcessor();
    when(processor.process(any(), any()))
        .thenAnswer(
            invocation -> {
              simulateProcessingFor(Duration.ofMillis(25));
              return followUpCommandAndResponseResult(invocation.getArgument(1));
            })
        .thenAnswer(invocation -> followUpEventResult(invocation.getArgument(1)));
    streamPlatform.buildStreamProcessor(
        streamPlatform.getLogStream(),
        true,
        cfg -> cfg.maxBatchProcessingTime(Duration.ofMillis(1)));

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then -- the follow-up command is not batched but processed on its own, resulting in three
    // batches: the initial command, the waiting command and the deferred follow-up command
    verify(processor, TIMEOUT.times(3)).process(any(), any());
    final var listener = streamPlatform.getMockStreamProcessorListener();
    verify(listener, TIMEOUT.times(3)).onProcessed(any());
  }

  @Test
  void shouldProcessFollowUpCommandInSameBatchWhenMaxBatchProcessingTimeIsNotExhausted() {
    // given -- processing is much faster than the maximum batch processing time
    final var processor = streamPlatform.getDefaultMockedRecordProcessor();
    when(processor.process(any(), any()))
        .thenAnswer(invocation -> followUpCommandAndResponseResult(invocation.getArgument(1)))
        .thenAnswer(invocation -> followUpEventResult(invocation.getArgument(1)));
    streamPlatform.buildStreamProcessor(
        streamPlatform.getLogStream(),
        true,
        cfg -> cfg.maxBatchProcessingTime(Duration.ofMinutes(10)));

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then -- the follow-up command is processed in the same batch, so it is skipped when read
    // from the log, along with the follow-up events of both batches
    final var listener = streamPlatform.getMockStreamProcessorListener();
    verify(listener, TIMEOUT.times(3)).onSkipped(any());
    verify(processor, times(3)).process(any(), any());
    verify(listener, times(2)).onProcessed(any());
  }

  @Test
  void shouldProcessFollowUpCommandInOwnBatchWhenOnlyRecordsAreWaiting() {
    // given -- processing takes longer than the maximum batch processing time and another command
    // is waiting on the log, but no client is waiting on a response from this batch
    final var processor = streamPlatform.getDefaultMockedRecordProcessor();
    when(processor.process(any(), any()))
        .thenAnswer(
            invocation -> {
              simulateProcessingFor(Duration.ofMillis(25));
              return followUpCommandResult(invocation.getArgument(1));
            })
        .thenAnswer(invocation -> followUpEventResult(invocation.getArgument(1)));
    streamPlatform.buildStreamProcessor(
        streamPlatform.getLogStream(),
        true,
        cfg -> cfg.maxBatchProcessingTime(Duration.ofMillis(1)));

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then -- the follow-up command is deferred so that the waiting command is processed first
    verify(processor, TIMEOUT.times(3)).process(any(), any());
    final var listener = streamPlatform.getMockStreamProcessorListener();
    verify(listener, TIMEOUT.times(3)).onProcessed(any());
  }

  @Test
  void shouldProcessFollowUpCommandInOwnBatchWhenOnlyClientIsWaitingForResponse() {
    // given -- processing takes longer than the maximum batch processing time and a client is
    // waiting on a response, but nothing else is waiting on the log
    final var processor = streamPlatform.getDefaultMockedRecordProcessor();
    when(processor.process(any(), any()))
        .thenAnswer(
            invocation -> {
              simulateProcessingFor(Duration.ofMillis(25));
              return followUpCommandAndResponseResult(invocation.getArgument(1));
            })
        .thenAnswer(invocation -> followUpEventResult(invocation.getArgument(1)));
    streamPlatform.buildStreamProcessor(
        streamPlatform.getLogStream(),
        true,
        cfg -> cfg.maxBatchProcessingTime(Duration.ofMillis(1)));

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then -- the follow-up command is deferred so that the response is sent out earlier
    verify(processor, TIMEOUT.times(2)).process(any(), any());
    final var listener = streamPlatform.getMockStreamProcessorListener();
    verify(listener, TIMEOUT.times(2)).onProcessed(any());
  }

  @Test
  void shouldContinueBatchWhenNoOneIsWaiting() {
    // given -- processing takes longer than the maximum batch processing time, but no client is
    // waiting on a response and nothing else is waiting on the log
    final var processor = streamPlatform.getDefaultMockedRecordProcessor();
    when(processor.process(any(), any()))
        .thenAnswer(
            invocation -> {
              simulateProcessingFor(Duration.ofMillis(25));
              return followUpCommandResult(invocation.getArgument(1));
            })
        .thenAnswer(invocation -> followUpEventResult(invocation.getArgument(1)));
    streamPlatform.buildStreamProcessor(
        streamPlatform.getLogStream(),
        true,
        cfg -> cfg.maxBatchProcessingTime(Duration.ofMillis(1)));

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then -- the follow-up command is still processed in the same batch, so it is skipped when
    // read from the log, along with the follow-up event
    final var listener = streamPlatform.getMockStreamProcessorListener();
    verify(listener, TIMEOUT.times(2)).onSkipped(any());
    verify(processor, times(2)).process(any(), any());
    verify(listener, times(1)).onProcessed(any());
  }

  private static ProcessingResult followUpCommandAndResponseResult(
      final ProcessingResultBuilder builder) {
    return appendFollowUpCommand(builder)
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
        .build();
  }

  private static ProcessingResult followUpCommandResult(final ProcessingResultBuilder builder) {
    return appendFollowUpCommand(builder).build();
  }

  private static ProcessingResultBuilder appendFollowUpCommand(
      final ProcessingResultBuilder builder) {
    return builder.appendRecord(
        4,
        Records.processInstance(1),
        new RecordMetadata()
            .recordType(RecordType.COMMAND)
            .intent(ELEMENT_ACTIVATING)
            .rejectionType(RejectionType.NULL_VAL)
            .rejectionReason(""));
  }

  private static ProcessingResult followUpEventResult(final ProcessingResultBuilder builder) {
    return builder
        .appendRecord(
            5,
            Records.processInstance(1),
            new RecordMetadata()
                .recordType(RecordType.EVENT)
                .intent(ELEMENT_ACTIVATING)
                .rejectionType(RejectionType.NULL_VAL)
                .rejectionReason(""))
        .build();
  }

  /** Simulates a slow processor by busy-waiting for the given duration. */
  private static void simulateProcessingFor(final Duration duration) {
    final var end = System.nanoTime() + duration.toNanos();
    while (System.nanoTime() - end < 0) {
      Thread.onSpinWait();
    }
  }
}
