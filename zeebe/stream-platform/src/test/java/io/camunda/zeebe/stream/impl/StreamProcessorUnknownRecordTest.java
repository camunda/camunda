/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ACTIVATE_ELEMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.stream.util.RecordToWrite;
import io.camunda.zeebe.stream.util.Records;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthReport;
import java.util.concurrent.CopyOnWriteArrayList;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * A record whose value type this broker does not know was written by a newer broker. It cannot be
 * read, so it must stop the stream processor instead of being skipped or handled as if it were the
 * previous record.
 */
@ExtendWith(StreamPlatformExtension.class)
final class StreamProcessorUnknownRecordTest {

  @SuppressWarnings("unused") // injected by the extension
  private StreamPlatform streamPlatform;

  @Test
  void shouldFailInsteadOfSkippingCommandOfUnknownValueType() {
    // given
    final var recordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final var streamProcessor = streamPlatform.startStreamProcessor();
    final long knownPosition =
        streamPlatform.writeBatch(
            RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));
    Awaitility.await("known command is processed")
        .until(() -> streamPlatform.getLastSuccessfulProcessedRecordPosition() == knownPosition);

    // when
    streamPlatform.writeBatch(RecordToWrite.command().unknownValueType());

    // then
    Awaitility.await("processor fails on the unknown command").until(streamProcessor::isFailed);
    verify(recordProcessor, never()).onProcessingError(any(), any(), any());
    assertThat(streamPlatform.getLastSuccessfulProcessedRecordPosition())
        .as("the unknown command is not marked as processed")
        .isEqualTo(knownPosition);
  }

  @Test
  void shouldFailReplayWithNoSuchProcessorExceptionForEventOfUnknownValueType() {
    // given
    final var reports = new CopyOnWriteArrayList<HealthReport>();
    final var streamProcessor = streamPlatform.startStreamProcessorInReplayOnlyMode();
    streamProcessor.addFailureListener(
        new FailureListener() {
          @Override
          public void onFailure(final HealthReport report) {
            reports.add(report);
          }

          @Override
          public void onRecovered(final HealthReport report) {}

          @Override
          public void onUnrecoverableFailure(final HealthReport report) {
            reports.add(report);
          }
        });

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.event().unknownValueType().causedBy(0));

    // then
    Awaitility.await("replay fails").until(() -> !reports.isEmpty());
    final var issue = reports.get(0).getIssue();
    assertThat(issue).isNotNull();
    assertThat(issue.throwable()).hasRootCauseInstanceOf(NoSuchProcessorException.class);
  }
}
