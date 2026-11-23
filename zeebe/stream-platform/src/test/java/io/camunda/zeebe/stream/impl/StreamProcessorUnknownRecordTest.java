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
import io.camunda.zeebe.test.util.logging.RecordingAppender;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthReport;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * A record whose value type this broker does not know was written by a newer broker. It cannot be
 * read, so it must stop the stream processor instead of being skipped or handled as if it were the
 * previous record, and the failure must be logged exactly once.
 */
@ExtendWith(StreamPlatformExtension.class)
final class StreamProcessorUnknownRecordTest {

  @SuppressWarnings("unused") // injected by the extension
  private StreamPlatform streamPlatform;

  private final RecordingAppender recorder = new RecordingAppender();
  private final Logger processorLog = (Logger) LogManager.getLogger("io.camunda.zeebe.processor");
  private final Logger logStreamLog = (Logger) LogManager.getLogger("io.camunda.zeebe.logstreams");
  private Level previousProcessorLevel;
  private Level previousLogStreamLevel;

  @BeforeEach
  void beforeEach() {
    previousProcessorLevel = processorLog.getLevel();
    previousLogStreamLevel = logStreamLog.getLevel();
    processorLog.setLevel(Level.WARN);
    logStreamLog.setLevel(Level.WARN);
    recorder.start();
    processorLog.addAppender(recorder);
    logStreamLog.addAppender(recorder);
  }

  @AfterEach
  void afterEach() {
    processorLog.removeAppender(recorder);
    logStreamLog.removeAppender(recorder);
    recorder.stop();
    processorLog.setLevel(previousProcessorLevel);
    logStreamLog.setLevel(previousLogStreamLevel);
  }

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
    final var streamProcessor = streamPlatform.startStreamProcessorInReplayOnlyMode();
    final var reports = captureReports(streamProcessor);

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

  @Test
  void shouldLogProcessingFailureOnceWhenCommandHasUnknownValueType() {
    // given
    final var streamProcessor = streamPlatform.startStreamProcessor();
    final var reports = captureReports(streamProcessor);

    // when
    streamPlatform.writeBatch(RecordToWrite.command().unknownValueType());

    // then
    Awaitility.await("processing fails").until(() -> !reports.isEmpty());
    assertThat(failureErrors())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getThrown()).as("stack trace is logged").isNotNull();
              assertThat(e.getThrown().getMessage())
                  .as("the failed record is reported")
                  .contains("Expected to process record");
            });
  }

  @Test
  void shouldLogReplayFailureOnceWhenEventHasUnknownValueType() {
    // given
    final var streamProcessor = streamPlatform.startStreamProcessorInReplayOnlyMode();
    final var reports = captureReports(streamProcessor);

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.event().unknownValueType().causedBy(0));

    // then
    Awaitility.await("replay fails").until(() -> !reports.isEmpty());
    assertThat(failureErrors())
        .singleElement()
        .satisfies(e -> assertThat(e.getThrown()).as("stack trace is logged").isNotNull());
  }

  @Test
  void shouldLogReplayFailureOnceWhenLeaderReplaysEventOfUnknownValueType() {
    // given
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.event().unknownValueType().causedBy(0));

    // when
    final var streamProcessor = streamPlatform.startStreamProcessorNotAwaitOpening();
    final var reports = captureReports(streamProcessor);

    // then
    Awaitility.await("replay fails").until(() -> !reports.isEmpty());
    assertThat(failureErrors())
        .singleElement()
        .satisfies(e -> assertThat(e.getThrown()).as("stack trace is logged").isNotNull());
  }

  /**
   * The ERROR events that report the failure of the unknown record. The loggers are shared with
   * every other test in the same JVM, which may fail stream processors for other reasons, so only
   * events that carry this failure are considered: a {@link NoSuchProcessorException} for an
   * unknown value type, or a message that names the record's unknown value type.
   */
  private List<LogEvent> failureErrors() {
    return recorder.getAppendedEvents().stream()
        .filter(e -> e.getLevel() == Level.ERROR)
        .filter(
            e ->
                isUnknownValueTypeFailure(e.getThrown())
                    || e.getMessage().getFormattedMessage().contains("NULL_VAL"))
        .toList();
  }

  private static boolean isUnknownValueTypeFailure(final Throwable thrown) {
    for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
      if (cause instanceof NoSuchProcessorException
          && String.valueOf(cause.getMessage()).contains("unknown value type")) {
        return true;
      }
    }
    return false;
  }

  private static List<HealthReport> captureReports(final StreamProcessor streamProcessor) {
    final var reports = new CopyOnWriteArrayList<HealthReport>();
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
    return reports;
  }
}
