/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ErrorIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.util.RecordToWrite;
import io.camunda.zeebe.stream.util.Records;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.verification.VerificationWithTimeout;

@ExtendWith(StreamPlatformExtension.class)
class StreamProcessorErrorHandlingTest {

  private static final long TIMEOUT_MILLIS = 2_000L;
  private static final VerificationWithTimeout TIMEOUT = timeout(TIMEOUT_MILLIS);

  @SuppressWarnings("unused") // injected by the extension
  private StreamPlatform streamPlatform;

  @Test
  void shouldRejectUserCommandIfProcessingErrorHandlingFailed() {
    // given
    final var defaultMockedRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    when(defaultMockedRecordProcessor.process(any(), any())).thenThrow(new RuntimeException());
    when(defaultMockedRecordProcessor.onProcessingError(any(), any(), any()))
        .thenThrow(new RuntimeException());

    streamPlatform.startStreamProcessor();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.userCommand()
            .processInstance(ProcessInstanceIntent.ACTIVATE_ELEMENT, Records.processInstance(1)));

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
    assertThat(recordMetadata.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(recordMetadata.getIntent()).isEqualTo(ErrorIntent.CREATED);
    assertThat(record.getSourceEventPosition()).isEqualTo(1);

    final var commandResponseWriter = streamPlatform.getMockCommandResponseWriter();

    verify(commandResponseWriter, TIMEOUT.times(1)).recordType(RecordType.COMMAND_REJECTION);
    verify(commandResponseWriter, TIMEOUT.times(1)).valueType(ValueType.ERROR);
    verify(commandResponseWriter, TIMEOUT.times(1)).tryWriteResponse(anyInt(), anyLong());
  }

  @Test
  void shouldContinueProcessingEvenIfErrorHandlingFailedForUserCommand() {
    // given
    final var defaultMockedRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final var successResult = new BufferedProcessingResultBuilder((c, s) -> true);
    successResult.appendRecordReturnEither(
        1,
        Records.processInstance(1),
        new RecordMetadata().recordType(RecordType.EVENT).intent(ELEMENT_ACTIVATED));

    when(defaultMockedRecordProcessor.process(any(), any()))
        .thenThrow(new RuntimeException())
        .thenReturn(successResult.build());
    when(defaultMockedRecordProcessor.onProcessingError(any(), any(), any()))
        .thenThrow(new RuntimeException());

    streamPlatform.startStreamProcessor();

    // Command that should fail
    streamPlatform.writeBatch(
        RecordToWrite.userCommand()
            .processInstance(ProcessInstanceIntent.ACTIVATE_ELEMENT, Records.processInstance(1)));
    verify(defaultMockedRecordProcessor, TIMEOUT.times(1)).onProcessingError(any(), any(), any());

    // when
    // Command that should succeed
    final var secondCommand =
        streamPlatform.writeBatch(
            RecordToWrite.userCommand()
                .processInstance(
                    ProcessInstanceIntent.ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    verify(defaultMockedRecordProcessor, TIMEOUT.times(2)).process(any(), any());
    final var logStreamReader = streamPlatform.getLogStream().newLogStreamReader();
    logStreamReader.seekToFirstEvent();
    logStreamReader.next(); // command
    logStreamReader.next(); // rejection
    logStreamReader.next(); // command

    await("should write follow up event of second command to log")
        .untilAsserted(() -> assertThat(logStreamReader.hasNext()).isTrue());
    final var record = logStreamReader.next();
    final var recordMetadata = new RecordMetadata();
    record.readMetadata(recordMetadata);
    assertThat(recordMetadata.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(recordMetadata.getIntent()).isEqualTo(ELEMENT_ACTIVATED);
    assertThat(record.getSourceEventPosition()).isEqualTo(secondCommand);
  }
}
