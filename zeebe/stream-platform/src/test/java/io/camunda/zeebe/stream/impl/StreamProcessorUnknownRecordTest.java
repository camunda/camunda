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
import static org.mockito.Mockito.when;

import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.stream.api.RecordProcessor;
import io.camunda.zeebe.stream.util.RecordToWrite;
import io.camunda.zeebe.stream.util.Records;
import io.camunda.zeebe.test.util.logging.RecordingAppender;
import java.util.List;
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
 * Verifies how records that no processor accepts are logged. When such a record was written by a
 * newer broker version (expected during a rolling upgrade) the misleading {@code
 * NoSuchProcessorException} error with a stack trace is replaced by a clear warning. When the
 * record did not come from a newer broker it is a genuine bug and is still logged loudly. In all
 * cases the stream processor fails as before.
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
    final RecordProcessor recordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    // no processor accepts a value type unknown to this version
    when(recordProcessor.accepts(ValueType.NULL_VAL)).thenReturn(false);
    when(recordProcessor.accepts(ValueType.SBE_UNKNOWN)).thenReturn(false);

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
  void shouldWarnWhenReplayReachesRecordFromNewerVersion() {
    // given
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.event().fromNewerBrokerVersion().causedBy(0));

    // when - a follower replays (replay-only mode)
    final var streamProcessor = streamPlatform.startStreamProcessorInReplayOnlyMode();

    // then - replay fails, but with a friendly warning instead of the scary error
    Awaitility.await("replay fails on a record from a newer version")
        .until(() -> streamProcessor.getHealthReport().isUnhealthy());
    assertFriendlyVersionSkewWarning();
  }

  @Test
  void shouldStepDownWhenReplayReachesRecordFromNewerVersion() {
    // given
    final RecordProcessor recordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.event().fromNewerBrokerVersion().causedBy(0));

    // when - a leader replays (processing mode); recovery never completes, so do not await opening
    final var streamProcessor = streamPlatform.startStreamProcessorNotAwaitOpening();

    // then - the leader steps down and never starts processing, with a friendly warning
    Awaitility.await("leader steps down when replay reaches a record from a newer version")
        .until(() -> streamProcessor.getHealthReport().isUnhealthy());
    verify(recordProcessor, never()).process(any(), any());
    assertFriendlyVersionSkewWarning();
  }

  @Test
  void shouldWarnWhenProcessingCommandFromNewerVersion() {
    // given
    final RecordProcessor recordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    streamPlatform.writeBatch(RecordToWrite.command().fromNewerBrokerVersion());

    // when - the leader replays (the command is skipped) and then tries to process it
    final var streamProcessor = streamPlatform.startStreamProcessor();

    // then - the leader steps down without processing the command, with a friendly warning
    Awaitility.await("leader steps down when processing a command from a newer version")
        .until(() -> streamProcessor.getHealthReport().isUnhealthy());
    verify(recordProcessor, never()).process(any(), any());
    assertFriendlyVersionSkewWarning();
  }

  @Test
  void shouldLogErrorWhenRecordIsNotFromNewerVersion() {
    // given - an unprocessable record that did NOT come from a newer broker (a genuine bug)
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.event().unknownValueType().causedBy(0));

    // when
    final var streamProcessor = streamPlatform.startStreamProcessorNotAwaitOpening();

    // then - it is still reported loudly as a NoSuchProcessorException, not a rolling-upgrade
    // warning
    Awaitility.await("replay fails on a genuinely unprocessable record")
        .until(() -> streamProcessor.getHealthReport().isUnhealthy());
    final List<LogEvent> events = recorder.getAppendedEvents();
    assertThat(events)
        .as("a genuine missing processor is still logged loudly with the exception")
        .anyMatch(e -> e.getLevel() == Level.ERROR && hasCause(e, NoSuchProcessorException.class));
    assertThat(events)
        .as("no rolling-upgrade warning for a record that is not from a newer broker")
        .noneMatch(e -> e.getMessage().getFormattedMessage().contains("rolling upgrade"));
  }

  /**
   * Asserts the behaviour the issue is actually about: the misleading {@code
   * NoSuchProcessorException} error with a stack trace is gone, replaced by a clear warning about
   * the rolling upgrade.
   */
  private void assertFriendlyVersionSkewWarning() {
    final List<LogEvent> events = recorder.getAppendedEvents();
    assertThat(events)
        .as("the misleading NoSuchProcessorException is not logged as an error with a stack trace")
        .noneMatch(e -> e.getLevel() == Level.ERROR && hasCause(e, NoSuchProcessorException.class))
        .noneMatch(e -> e.getMessage().getFormattedMessage().contains("No processor registered"));
    assertThat(events)
        .as("a friendly warning explains that this is expected during a rolling upgrade")
        .anyMatch(
            e ->
                e.getLevel() == Level.WARN
                    && e.getMessage().getFormattedMessage().contains("rolling upgrade"));
  }

  private static boolean hasCause(final LogEvent event, final Class<?> type) {
    for (Throwable cause = event.getThrown(); cause != null; cause = cause.getCause()) {
      if (type.isInstance(cause)) {
        return true;
      }
    }
    return false;
  }
}
