/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ACTIVATE_ELEMENT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.stream.api.EmptyProcessingResult;
import io.camunda.zeebe.stream.util.RecordToWrite;
import io.camunda.zeebe.stream.util.Records;
import java.util.concurrent.atomic.AtomicBoolean;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(StreamPlatformExtension.class)
public class StreamProcessorHealthTest {

  @SuppressWarnings("unused") // injected by the extension
  private StreamPlatform streamPlatform;

  private StreamProcessor streamProcessor;

  @Test
  void shouldBeHealthyOnStart() {
    // when
    streamProcessor = streamPlatform.startStreamProcessor();

    // then
    Awaitility.await("wait to become healthy again")
        .until(() -> streamProcessor.getHealthReport().isHealthy());
  }

  @Test
  void shouldMarkUnhealthyWhenLoopInErrorHandling() {
    // given
    streamProcessor = streamPlatform.startStreamProcessor();

    final var mockProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    when(mockProcessor.process(any(), any())).thenThrow(new RuntimeException("expected"));
    when(mockProcessor.onProcessingError(any(), any(), any()))
        .thenThrow(new RuntimeException("expected"));

    // when
    // since processing fails we will write error event
    // we want to fail error even transaction
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    Awaitility.await("wait to become unhealthy")
        .until(() -> streamProcessor.getHealthReport().isUnhealthy());
  }

  @Test
  void shouldBecomeHealthyWhenErrorIsResolved() {
    // given
    streamProcessor = streamPlatform.startStreamProcessor();
    final var shouldFail = new AtomicBoolean(true);

    final var mockProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    when(mockProcessor.process(any(), any())).thenThrow(new RuntimeException("expected"));
    when(mockProcessor.onProcessingError(any(), any(), any()))
        .thenAnswer(
            invocationOnMock -> {
              if (shouldFail.get()) {
                throw new RuntimeException("expected");
              }
              return EmptyProcessingResult.INSTANCE;
            });
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));
    Awaitility.await("wait to become unhealthy")
        .until(() -> streamProcessor.getHealthReport().isUnhealthy());

    // when
    shouldFail.set(false);

    // then
    Awaitility.await("wait to become healthy again")
        .until(() -> streamProcessor.getHealthReport().isHealthy());
  }
}
