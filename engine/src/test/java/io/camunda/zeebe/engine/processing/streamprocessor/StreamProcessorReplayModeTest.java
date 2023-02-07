/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import static io.camunda.zeebe.engine.util.RecordToWrite.command;
import static io.camunda.zeebe.engine.util.RecordToWrite.event;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ACTIVATE_ELEMENT;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static java.util.function.Predicate.isEqual;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.zeebe.engine.state.EventApplier;
import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.engine.util.StreamProcessorRule;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.streamprocessor.StreamProcessor;
import io.camunda.zeebe.streamprocessor.StreamProcessor.Phase;
import io.camunda.zeebe.streamprocessor.StreamProcessorMode;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.verification.VerificationWithTimeout;

public final class StreamProcessorReplayModeTest {

  private static final long TIMEOUT_MILLIS = 2_000L;
  private static final VerificationWithTimeout TIMEOUT = timeout(TIMEOUT_MILLIS);

  private static final int PARTITION_ID = 1;

  private static final ProcessInstanceRecord RECORD = Records.processInstance(1);

  @Rule
  public final StreamProcessorRule replayUntilEnd =
      new StreamProcessorRule(PARTITION_ID).withStreamProcessorMode(StreamProcessorMode.PROCESSING);

  @Rule
  public final StreamProcessorRule replayContinuously =
      new StreamProcessorRule(PARTITION_ID).withStreamProcessorMode(StreamProcessorMode.REPLAY);

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private TypedRecordProcessor<?> typedRecordProcessor;
  @Mock private EventApplier eventApplier;

  @Test
  public void shouldReplayUntilEnd() {
    // given
    replayUntilEnd.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    // when
    startStreamProcessor(replayUntilEnd);

    await()
        .untilAsserted(
            () -> assertThat(getCurrentPhase(replayUntilEnd)).isEqualTo(Phase.PROCESSING));

    replayUntilEnd.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    // then
    final InOrder inOrder = inOrder(typedRecordProcessor, eventApplier);
    inOrder.verify(eventApplier, TIMEOUT).applyState(anyLong(), eq(ELEMENT_ACTIVATING), any());
    inOrder.verify(typedRecordProcessor, TIMEOUT).processRecord(any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldReplayContinuously() {
    // given
    replayContinuously.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    // when
    startStreamProcessor(replayContinuously);

    replayContinuously.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    // then
    verify(eventApplier, TIMEOUT.times(2)).applyState(anyLong(), eq(ELEMENT_ACTIVATING), any());

    verifyNoMoreInteractions(eventApplier);

    assertThat(getCurrentPhase(replayContinuously)).isEqualTo(Phase.REPLAY);
  }

  @Test
  public void shouldReplayIfNoEventsAfterSnapshot() {
    // given
    startStreamProcessor(replayContinuously);
    final var snapshotPosition = 1L;
    replayContinuously.getLastProcessedPositionState().markAsProcessed(snapshotPosition);
    replayContinuously.snapshot();
    replayContinuously.closeStreamProcessor();

    // when - restart with snapshot, but the events in the snapshot are not available at startup
    startStreamProcessor(replayContinuously);

    // event already applied in snapshot
    replayContinuously.writeEvent(
        ProcessInstanceIntent.ELEMENT_ACTIVATING,
        RECORD,
        writer -> writer.key(1L).sourceRecordPosition(snapshotPosition));

    // new events to replay
    replayContinuously.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    // then
    final InOrder inOrder = inOrder(typedRecordProcessor, eventApplier);
    inOrder
        .verify(eventApplier, TIMEOUT.times(1))
        .applyState(anyLong(), eq(ELEMENT_ACTIVATING), any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldNotReplayWhenPaused() {
    // given
    startWithPausedStreamProcessor(replayContinuously);

    // when
    replayContinuously.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    // then
    final var inOrder = inOrder(typedRecordProcessor, eventApplier);
    inOrder.verifyNoMoreInteractions();
    assertThat(getCurrentPhase(replayContinuously)).isEqualTo(Phase.PAUSED);
  }

  @Test
  public void shouldPauseReplay() {
    // given
    final var streamProcessor = startStreamProcessor(replayContinuously);
    replayContinuously.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    await("should have replayed first events")
        .until(replayContinuously::getLastSuccessfulProcessedRecordPosition, (pos) -> pos > 0);

    // when
    streamProcessor.pauseProcessing().join();
    replayContinuously.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    // then
    final var inOrder = inOrder(typedRecordProcessor, eventApplier);
    inOrder
        .verify(eventApplier, TIMEOUT.times(1))
        .applyState(anyLong(), eq(ELEMENT_ACTIVATING), any());
    inOrder.verifyNoMoreInteractions();

    assertThat(getCurrentPhase(replayContinuously)).isEqualTo(Phase.PAUSED);
  }

  @Test
  public void shouldReplayAfterResumed() {
    // given
    startWithPausedStreamProcessor(replayContinuously);

    replayContinuously.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    // when
    replayContinuously.resumeProcessing(1);

    // then
    verify(eventApplier, TIMEOUT.times(1)).applyState(anyLong(), eq(ELEMENT_ACTIVATING), any());

    verifyNoMoreInteractions(eventApplier);

    assertThat(getCurrentPhase(replayContinuously)).isEqualTo(Phase.REPLAY);
  }

  @Test
  public void shouldReplayMoreAfterResumed() {
    // given
    final var streamProcessor = startStreamProcessor(replayContinuously);
    replayContinuously.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    await("should have replayed first events")
        .until(replayContinuously::getLastSuccessfulProcessedRecordPosition, (pos) -> pos > 0);
    streamProcessor.pauseProcessing().join();
    replayContinuously.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    // when
    streamProcessor.resumeProcessing();

    // then
    final var inOrder = inOrder(typedRecordProcessor, eventApplier);
    inOrder
        .verify(eventApplier, TIMEOUT.times(2))
        .applyState(anyLong(), eq(ELEMENT_ACTIVATING), any());
    inOrder.verifyNoMoreInteractions();

    assertThat(getCurrentPhase(replayContinuously)).isEqualTo(Phase.REPLAY);
  }

  @Test
  public void shouldUpdateLastProcessedAndWrittenPositionOnReplay() {
    // given
    startStreamProcessor(replayContinuously);

    // when
    final var commandPosition = replayContinuously.writeCommand(ACTIVATE_ELEMENT, RECORD);
    final var eventPosition =
        replayContinuously.writeEvent(
            ELEMENT_ACTIVATING, RECORD, event -> event.sourceRecordPosition(commandPosition));

    // then
    verify(eventApplier, TIMEOUT).applyState(anyLong(), eq(ELEMENT_ACTIVATING), any());

    await()
        .untilAsserted(
            () -> {
              final var lastProcessedPosition = getLastProcessedPosition(replayContinuously);
              final var lastWrittenPosition = getLastWrittenPosition(replayContinuously);

              assertThat(lastProcessedPosition)
                  .describedAs(
                      "Expected the position of the command to be the last processed position")
                  .isEqualTo(commandPosition);

              assertThat(lastWrittenPosition)
                  .describedAs("Expected the position of the event to be the last written position")
                  .isEqualTo(eventPosition);
            });
  }

  @Test
  public void shouldSetLastProcessedPositionOnStateToSourcePosition() {
    // given
    startStreamProcessor(replayContinuously);

    // when
    final var commandPosition = replayContinuously.writeCommand(ACTIVATE_ELEMENT, RECORD);
    replayContinuously.writeEvent(
        ELEMENT_ACTIVATING, RECORD, event -> event.sourceRecordPosition(commandPosition));

    verify(eventApplier, TIMEOUT).applyState(anyLong(), eq(ELEMENT_ACTIVATING), any());

    await().until(() -> getLastProcessedPosition(replayContinuously), isEqual(commandPosition));

    // then
    assertThat(replayContinuously.getLastSuccessfulProcessedRecordPosition())
        .describedAs("Last processed position in the state must be the last source position")
        .isEqualTo(commandPosition);
  }

  @Test
  public void shouldNotSetLastProcessedPositionIfLessThanSnapshotPosition() {
    // given
    final var snapshotPosition = 2L;

    startStreamProcessor(replayContinuously);

    replayContinuously.getLastProcessedPositionState().markAsProcessed(snapshotPosition);

    replayContinuously.snapshot();
    replayContinuously.closeStreamProcessor();

    // when
    startStreamProcessor(replayContinuously);

    await()
        .untilAsserted(
            () -> assertThat(getCurrentPhase(replayContinuously)).isEqualTo(Phase.REPLAY));

    // then
    final var lastProcessedPositionState = replayContinuously.getLastProcessedPositionState();

    await()
        .untilAsserted(
            () ->
                assertThat(lastProcessedPositionState.getLastSuccessfulProcessedRecordPosition())
                    .describedAs(
                        "Expected that the last processed position is not less than the snapshot position")
                    .isEqualTo(snapshotPosition));
  }

  private StreamProcessor startStreamProcessor(final StreamProcessorRule streamProcessorRule) {
    return streamProcessorRule
        .withEventApplierFactory(zeebeState -> eventApplier)
        .startTypedStreamProcessorNotAwaitOpening(
            (processors, context) ->
                processors.onCommand(
                    ValueType.PROCESS_INSTANCE, ACTIVATE_ELEMENT, typedRecordProcessor));
  }

  private void startWithPausedStreamProcessor(final StreamProcessorRule streamProcessorRule) {
    startStreamProcessor(streamProcessorRule).pauseProcessing().join();
  }

  private Phase getCurrentPhase(final StreamProcessorRule streamProcessorRule) {
    return getStreamProcessor(streamProcessorRule).getCurrentPhase().join();
  }

  private Long getLastProcessedPosition(final StreamProcessorRule streamProcessorRule) {
    return getStreamProcessor(streamProcessorRule).getLastProcessedPositionAsync().join();
  }

  private Long getLastWrittenPosition(final StreamProcessorRule streamProcessorRule) {
    return getStreamProcessor(streamProcessorRule).getLastWrittenPositionAsync().join();
  }

  private StreamProcessor getStreamProcessor(final StreamProcessorRule streamProcessorRule) {
    return streamProcessorRule.getStreamProcessor(PARTITION_ID);
  }
}
