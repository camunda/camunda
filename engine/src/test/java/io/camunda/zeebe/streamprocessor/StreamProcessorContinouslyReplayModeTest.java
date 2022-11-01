/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import static io.camunda.zeebe.engine.util.RecordToWrite.command;
import static io.camunda.zeebe.engine.util.RecordToWrite.event;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ACTIVATE_ELEMENT;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.api.RecordProcessor;
import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.streamprocessor.StreamProcessor.Phase;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.mockito.verification.VerificationWithTimeout;

@ExtendWith(StreamPlatformExtension.class)
public final class StreamProcessorContinouslyReplayModeTest {

  private static final long TIMEOUT_MILLIS = 2_000L;
  private static final VerificationWithTimeout TIMEOUT = timeout(TIMEOUT_MILLIS);

  private static final ProcessInstanceRecord RECORD = Records.processInstance(1);

  @SuppressWarnings("unused") // injected by the extension
  private StreamPlatform streamPlatform;

  @BeforeEach
  public void setup() {
    streamPlatform.setStreamProcessorMode(StreamProcessorMode.REPLAY);
  }

  @Test
  public void shouldReplayContinuously() {
    // given
    streamPlatform.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    // when
    final var streamProcessor = streamPlatform.startStreamProcessorNotAwaitOpening();

    streamPlatform.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    // then
    final RecordProcessor recordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final InOrder inOrder = inOrder(recordProcessor);
    inOrder.verify(recordProcessor, TIMEOUT.times(2)).replay(any());
    inOrder.verifyNoMoreInteractions();

    Assertions.assertThat(streamProcessor.getCurrentPhase().join()).isEqualTo(Phase.REPLAY);
  }

  @Test
  @RegressionTest("https://github.com/camunda/zeebe/issues/7662")
  public void shouldReplayIfNoEventsAfterSnapshot() throws Exception {
    // given
    streamPlatform.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));
    streamPlatform.setStreamProcessorMode(StreamProcessorMode.PROCESSING);
    streamPlatform.startStreamProcessor();
    streamPlatform.snapshot();
    streamPlatform.closeStreamProcessor();
    Mockito.clearInvocations(streamPlatform.getDefaultMockedRecordProcessor());

    // when
    // clear log
    streamPlatform.resetLogContext();
    // restart with snapshot, but the events in the snapshot are not available at startup
    streamPlatform.setStreamProcessorMode(StreamProcessorMode.REPLAY);
    streamPlatform.startStreamProcessorNotAwaitOpening();

    streamPlatform.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    // new events to replay
    streamPlatform.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    // then
    final RecordProcessor recordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final InOrder inOrder = inOrder(recordProcessor);
    inOrder.verify(recordProcessor, TIMEOUT.times(1)).replay(any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldNotReplayWhenPaused() {
    // given
    streamPlatform.startStreamProcessorNotAwaitOpening();
    streamPlatform.pauseProcessing();

    // when
    streamPlatform.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    // then
    final RecordProcessor recordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final InOrder inOrder = inOrder(recordProcessor);
    inOrder.verify(recordProcessor, TIMEOUT).init(any());
    inOrder.verify(recordProcessor, never()).replay(any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldPauseReplay() {
    // given
    final var streamProcessor = streamPlatform.startStreamProcessorNotAwaitOpening();
    streamPlatform.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    await("should have replayed first events")
        .until(() -> streamProcessor.getLastProcessedPositionAsync().join(), (pos) -> pos > 0L);

    // when
    streamPlatform.pauseProcessing();
    streamPlatform.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    // then
    final RecordProcessor recordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final InOrder inOrder = inOrder(recordProcessor);
    inOrder.verify(recordProcessor, TIMEOUT).init(any());
    inOrder.verify(recordProcessor, TIMEOUT.times(1)).replay(any());
    inOrder.verifyNoMoreInteractions();

    Assertions.assertThat(streamProcessor.getCurrentPhase().join()).isEqualTo(Phase.PAUSED);
  }

  @Test
  public void shouldReplayAfterResumed() {
    // given
    final var streamProcessor = streamPlatform.startStreamProcessorNotAwaitOpening();
    streamPlatform.pauseProcessing();
    streamPlatform.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    // when
    streamPlatform.resumeProcessing();

    // then
    final RecordProcessor recordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final InOrder inOrder = inOrder(recordProcessor);
    inOrder.verify(recordProcessor, TIMEOUT).init(any());
    inOrder.verify(recordProcessor, TIMEOUT.times(1)).replay(any());
    inOrder.verifyNoMoreInteractions();

    Assertions.assertThat(streamProcessor.getCurrentPhase().join()).isEqualTo(Phase.REPLAY);
  }

  @Test
  public void shouldReplayMoreAfterResumed() {
    // given
    final var streamProcessor = streamPlatform.startStreamProcessorNotAwaitOpening();
    streamPlatform.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    await("should have replayed first events")
        .until(() -> streamProcessor.getLastProcessedPositionAsync().join(), (pos) -> pos > 0L);

    streamPlatform.pauseProcessing();
    streamPlatform.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    // when
    streamPlatform.resumeProcessing();

    // then
    final RecordProcessor recordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final InOrder inOrder = inOrder(recordProcessor);
    inOrder.verify(recordProcessor, TIMEOUT).init(any());
    inOrder.verify(recordProcessor, TIMEOUT.times(2)).replay(any());
    inOrder.verifyNoMoreInteractions();

    Assertions.assertThat(streamProcessor.getCurrentPhase().join()).isEqualTo(Phase.REPLAY);
  }

  @Test
  public void shouldUpdateLastProcessedAndWrittenPositionOnReplay() {
    // given
    final var streamProcessor = streamPlatform.startStreamProcessorNotAwaitOpening();

    // when
    streamPlatform.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    // then
    final RecordProcessor recordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final InOrder inOrder = inOrder(recordProcessor);
    inOrder.verify(recordProcessor, TIMEOUT).init(any());
    inOrder.verify(recordProcessor, TIMEOUT).replay(any());
    inOrder.verifyNoMoreInteractions();

    Awaitility.await("position has to be updated during replay")
        .untilAsserted(
            () ->
                Assertions.assertThat(streamProcessor.getLastProcessedPositionAsync().join())
                    .isEqualTo(1L));
    Awaitility.await("position has to be updated during replay")
        .untilAsserted(
            () ->
                Assertions.assertThat(streamProcessor.getLastWrittenPositionAsync().join())
                    .isEqualTo(2L));
  }

  @Test
  public void shouldSetLastProcessedPositionOnStateToSourcePosition() {
    // given
    final var streamProcessor = streamPlatform.startStreamProcessorNotAwaitOpening();

    // when
    streamPlatform.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    Awaitility.await("position has to be updated during replay")
        .untilAsserted(
            () ->
                Assertions.assertThat(streamProcessor.getLastProcessedPositionAsync().join())
                    .isEqualTo(1L));

    // then
    Assertions.assertThat(streamPlatform.getLastSuccessfulProcessedRecordPosition())
        .describedAs("Last processed position in the state must be the last source position")
        .isEqualTo(1);
  }

  @Test
  public void shouldRestoreFromSnapshot() throws Exception {
    // given
    final var eventKeyBeforeSnapshot = Protocol.encodePartitionId(1, 19);

    // on replay the positions and keys are restored
    streamPlatform.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event()
            .processInstance(ELEMENT_ACTIVATING, RECORD)
            .key(eventKeyBeforeSnapshot)
            .causedBy(0),
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event()
            .processInstance(ELEMENT_ACTIVATING, RECORD)
            .key(eventKeyBeforeSnapshot)
            .causedBy(2));

    streamPlatform.startStreamProcessorNotAwaitOpening();
    verify(streamPlatform.getDefaultMockedRecordProcessor(), TIMEOUT.times(2)).replay(any());

    // the snapshot will contain the key and the position (in the metadata)
    streamPlatform.snapshot();
    streamPlatform.closeStreamProcessor();
    Mockito.clearInvocations(streamPlatform.getDefaultMockedRecordProcessor());

    // when - restoring from snapshot
    final var streamProcessor = streamPlatform.startStreamProcessorNotAwaitOpening();

    // then - we expect that we DON'T replay events which are part of the snapshot
    final RecordProcessor recordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final InOrder inOrder = inOrder(recordProcessor);
    inOrder.verify(recordProcessor, TIMEOUT).init(any());
    inOrder.verify(recordProcessor, never()).replay(any());
    inOrder.verifyNoMoreInteractions();

    // This also means that the last processed position will be restored from the snapshot
    // With that in mind it will never or should never happen that the last processed position
    // is smaller than the snapshot position
    Awaitility.await("position has to be set during replay")
        .untilAsserted(
            () ->
                Assertions.assertThat(streamProcessor.getLastProcessedPositionAsync().join())
                    .isEqualTo(3L));

    Assertions.assertThat(streamPlatform.getLastSuccessfulProcessedRecordPosition())
        .isEqualTo(3);
    Assertions.assertThat(Protocol.decodeKeyInPartition(streamPlatform.getCurrentKey()))
        .isEqualTo(19L);
  }
}
