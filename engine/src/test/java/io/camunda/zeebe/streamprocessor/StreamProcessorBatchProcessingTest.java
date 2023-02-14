/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import static io.camunda.zeebe.engine.util.RecordToWrite.command;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ACTIVATE_ELEMENT;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.api.ProcessingResultBuilder;
import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.engine.util.StreamPlatform;
import io.camunda.zeebe.engine.util.StreamPlatformExtension;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.verification.VerificationWithTimeout;

@ExtendWith(StreamPlatformExtension.class)
public class StreamProcessorBatchProcessingTest {

  private static final long TIMEOUT_MILLIS = 2_000L;
  private static final VerificationWithTimeout TIMEOUT = timeout(TIMEOUT_MILLIS);

  private static final ProcessInstanceRecord RECORD = Records.processInstance(1);

  @SuppressWarnings("unused") // injected by the extension
  private StreamPlatform streamPlatform;

  @Test
  public void shouldProcessFollowUpEventsAndCommands() {
    // given
    streamPlatform.setMaxCommandsInBatch(100); // enable batch processing
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
    streamPlatform.writeBatch(command().processInstance(ACTIVATE_ELEMENT, RECORD));

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
}
