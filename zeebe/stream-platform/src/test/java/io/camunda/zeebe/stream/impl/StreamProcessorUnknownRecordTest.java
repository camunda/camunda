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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.protocol.Protocol;
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
 * previous record, and the failure must be logged exactly once. When the record comes from a newer
 * broker version that is expected during a rolling update and is logged as such, otherwise it is a
 * genuine error.
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
    assertThat(issue.throwable()).isInstanceOf(NoSuchProcessorException.class);
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
    assertThat(reports).allMatch(HealthReport::isDead);
    assertThat(failureErrors())
        .singleElement()
        .satisfies(e -> assertThat(e.getThrown()).as("stack trace is logged").isNotNull());
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
    assertThat(reports).allMatch(HealthReport::isUnhealthy);
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
    assertThat(reports).allMatch(HealthReport::isDead);
    assertThat(failureErrors())
        .singleElement()
        .satisfies(e -> assertThat(e.getThrown()).as("stack trace is logged").isNotNull());
  }

  @Test
  void shouldNotCommitAnyEventOfBatchWhenLaterEventHasUnknownValueType() {
    // given
    final var streamProcessor = streamPlatform.startStreamProcessorInReplayOnlyMode();
    final var reports = captureReports(streamProcessor);
    final var knownEventKey = Protocol.encodePartitionId(1, 19);

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.event()
            .processInstance(ELEMENT_ACTIVATING, Records.processInstance(1))
            .key(knownEventKey)
            .causedBy(0),
        RecordToWrite.event().unknownValueType().causedBy(0));

    // then
    Awaitility.await("replay fails").until(() -> !reports.isEmpty());
    verify(streamPlatform.getDefaultMockedRecordProcessor(), times(1)).replay(any());
    assertThat(Protocol.decodeKeyInPartition(streamPlatform.getCurrentKey()))
        .as("the key of the known event is not committed")
        .isZero();
    assertThat(streamPlatform.getLastSuccessfulProcessedRecordPosition())
        .as("the batch is not marked as processed")
        .isNotPositive();
  }

  @Test
  void shouldKeepReplayingWhenReplaySkipsCommandOfUnknownValueType() {
    // given
    final var streamProcessor = streamPlatform.startStreamProcessorInReplayOnlyMode();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.event()
            .processInstance(ELEMENT_ACTIVATING, Records.processInstance(1))
            .causedBy(0),
        RecordToWrite.command().unknownValueType().causedBy(0));

    // then
    Awaitility.await("the whole batch is replayed")
        .untilAsserted(
            () -> assertThat(streamProcessor.getLastProcessedPositionAsync().join()).isEqualTo(1L));
    assertThat(streamProcessor.isFailed()).isFalse();
  }

  @Test
  void shouldOnlyWarnWhenReplayReachesRecordFromNewerVersion() {
    // given
    final var streamProcessor = streamPlatform.startStreamProcessorInReplayOnlyMode();
    final var reports = captureReports(streamProcessor);

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.event().fromNewerBrokerVersion().causedBy(0));

    // then
    Awaitility.await("replay fails").until(() -> !reports.isEmpty());
    assertThat(reports).allMatch(HealthReport::isUnhealthy);
    assertOnlyRollingUpgradeWarning();
  }

  @Test
  void shouldOnlyWarnWhenProcessingCommandFromNewerVersion() {
    // given
    final var streamProcessor = streamPlatform.startStreamProcessor();
    final var reports = captureReports(streamProcessor);

    // when
    streamPlatform.writeBatch(RecordToWrite.command().fromNewerBrokerVersion());

    // then
    Awaitility.await("processing fails").until(() -> !reports.isEmpty());
    assertThat(reports).allMatch(HealthReport::isDead);
    assertOnlyRollingUpgradeWarning();
  }

  private void assertOnlyRollingUpgradeWarning() {
    assertThat(recorder.getAppendedEvents())
        .as("a single warning explains that this is expected during a rolling upgrade")
        .filteredOn(e -> e.getMessage().getFormattedMessage().contains("rolling upgrade"))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getLevel()).isEqualTo(Level.WARN);
              assertThat(e.getThrown()).as("no stack trace").isNull();
              assertThat(e.getMessage().getFormattedMessage())
                  .contains("valueType: unknown")
                  .contains("newer than this broker's version")
                  .contains("must be upgraded to at least version");
            });
    assertThat(failureErrors()).as("the failure is not also logged as an error").isEmpty();
  }

  /**
   * The ERROR events that report the failure of the unknown record. The loggers are shared with
   * every other test in the same JVM, which may fail stream processors for other reasons, so only
   * events that carry this failure are considered.
   */
  private List<LogEvent> failureErrors() {
    return recorder.getAppendedEvents().stream()
        .filter(e -> e.getLevel() == Level.ERROR)
        .filter(
            e ->
                e.getThrown() instanceof final NoSuchProcessorException exception
                    && String.valueOf(exception.getMessage()).contains("unknown value type"))
        .toList();
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
