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
import io.camunda.zeebe.stream.api.ProcessingResult;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.util.RecordToWrite;
import io.camunda.zeebe.stream.util.Records;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.verification.VerificationWithTimeout;

@ExtendWith(StreamPlatformExtension.class)
final class StreamProcessorMaxBatchProcessingTimeTest {

  private static final long TIMEOUT_MILLIS = 2_000L;
  private static final VerificationWithTimeout TIMEOUT = timeout(TIMEOUT_MILLIS);

  @SuppressWarnings("unused") // injected by the extension
  private StreamPlatform streamPlatform;

  @Test
  void shouldProcessFollowUpCommandInOwnBatchWhenMaxBatchProcessingTimeIsExhausted() {
    // given -- processing a command takes longer than the maximum batch processing time
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

    // then -- the follow-up command is not batched but processed on its own
    verify(processor, TIMEOUT.times(2)).process(any(), any());
    final var listener = streamPlatform.getMockStreamProcessorListener();
    verify(listener, TIMEOUT.times(2)).onProcessed(any());
  }

  @Test
  void shouldProcessFollowUpCommandInSameBatchWhenMaxBatchProcessingTimeIsNotExhausted() {
    // given -- processing is much faster than the maximum batch processing time
    final var processor = streamPlatform.getDefaultMockedRecordProcessor();
    when(processor.process(any(), any()))
        .thenAnswer(invocation -> followUpCommandResult(invocation.getArgument(1)))
        .thenAnswer(invocation -> followUpEventResult(invocation.getArgument(1)));
    streamPlatform.buildStreamProcessor(
        streamPlatform.getLogStream(),
        true,
        cfg -> cfg.maxBatchProcessingTime(Duration.ofMinutes(10)));

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then -- the follow-up command is processed in the same batch, so both the command and the
    // follow-up event are skipped when they are read from the log
    verify(processor, TIMEOUT.times(2)).process(any(), any());
    final var listener = streamPlatform.getMockStreamProcessorListener();
    verify(listener, TIMEOUT.times(2)).onSkipped(any());
    verify(listener, times(1)).onProcessed(any());
  }

  private static ProcessingResult followUpCommandResult(final ProcessingResultBuilder builder) {
    return builder
        .appendRecord(
            4,
            Records.processInstance(1),
            new RecordMetadata()
                .recordType(RecordType.COMMAND)
                .intent(ELEMENT_ACTIVATING)
                .rejectionType(RejectionType.NULL_VAL)
                .rejectionReason(""))
        .build();
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
