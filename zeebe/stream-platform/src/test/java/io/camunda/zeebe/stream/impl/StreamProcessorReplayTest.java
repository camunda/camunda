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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ErrorIntent;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.RecordProcessor;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.impl.StreamProcessor.Phase;
import io.camunda.zeebe.stream.util.RecordToWrite;
import io.camunda.zeebe.stream.util.Records;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.verification.VerificationWithTimeout;

@ExtendWith(StreamPlatformExtension.class)
final class StreamProcessorReplayTest {

  private static final long TIMEOUT_MILLIS = 2_000L;
  private static final VerificationWithTimeout TIMEOUT = timeout(TIMEOUT_MILLIS);

  @SuppressWarnings("unused") // injected by the extension
  private StreamPlatform streamPlatform;

  @Test
  void shouldReplayEvents() {
    // given
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.event()
            .processInstance(ELEMENT_ACTIVATING, Records.processInstance(1))
            .causedBy(0));

    // when
    streamPlatform.startStreamProcessor();

    // then
    final RecordProcessor recordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final InOrder inOrder = inOrder(recordProcessor);
    inOrder.verify(recordProcessor, TIMEOUT).replay(any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldProcessAfterReplay() {
    // given
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.event()
            .processInstance(ELEMENT_ACTIVATING, Records.processInstance(1))
            .causedBy(0));

    // when
    streamPlatform.startStreamProcessor();

    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.event()
            .processInstance(ELEMENT_ACTIVATING, Records.processInstance(1))
            .causedBy(0));

    // then
    final RecordProcessor recordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final InOrder inOrder = inOrder(recordProcessor);
    inOrder.verify(recordProcessor, TIMEOUT).replay(any());
    inOrder.verify(recordProcessor, TIMEOUT).process(any(), any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldSkipCommands() {
    // given
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.event()
            .processInstance(ELEMENT_ACTIVATING, Records.processInstance(1))
            .causedBy(0));

    // when
    streamPlatform.startStreamProcessor();

    // then
    final RecordProcessor recordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final InOrder inOrder = inOrder(recordProcessor);
    inOrder.verify(recordProcessor, TIMEOUT).replay(any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldSkipRejections() {
    // given
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.rejection()
            .processInstance(ACTIVATE_ELEMENT, Records.processInstance(1))
            .causedBy(0));

    // when
    streamPlatform.startStreamProcessor();

    // then
    final RecordProcessor recordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final InOrder inOrder = inOrder(recordProcessor);
    inOrder.verify(recordProcessor, TIMEOUT).init(any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldRestoreFromLog() {
    // given
    final var eventKeyBeforeSnapshot = Protocol.encodePartitionId(1, 19);

    // on replay the positions and keys should be restored
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.event()
            .processInstance(ELEMENT_ACTIVATING, Records.processInstance(1))
            .key(eventKeyBeforeSnapshot)
            .causedBy(0));

    // when
    // starting the stream processor awaits the opening/replay phase
    final var streamProcessor = streamPlatform.startStreamProcessor();

    // then
    final RecordProcessor recordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final InOrder inOrder = inOrder(recordProcessor);
    inOrder.verify(recordProcessor, TIMEOUT).replay(any());
    inOrder.verifyNoMoreInteractions();

    Awaitility.await("position has to be set on processing start")
        .untilAsserted(
            () -> assertThat(streamProcessor.getLastProcessedPositionAsync().join()).isEqualTo(1L));
    Awaitility.await("position has to be set on processing start")
        .untilAsserted(
            () -> assertThat(streamProcessor.getLastWrittenPositionAsync().join()).isEqualTo(2L));

    // state has to be updated
    assertThat(streamPlatform.getLastSuccessfulProcessedRecordPosition()).isEqualTo(2);
    assertThat(Protocol.decodeKeyInPartition(streamPlatform.getCurrentKey())).isEqualTo(19L);
  }

  @Test
  void shouldRestoreFromSnapshot() throws Exception {
    // given
    final var eventKeyBeforeSnapshot = Protocol.encodePartitionId(1, 19);

    // on replay the positions and keys are restored
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.event()
            .processInstance(ELEMENT_ACTIVATING, Records.processInstance(1))
            .key(eventKeyBeforeSnapshot)
            .causedBy(0));
    // starting the stream processor awaits the opening/replay phase
    streamPlatform.startStreamProcessor();
    // the snapshot will contain the key and the position (in the metadata)
    streamPlatform.snapshot();
    streamPlatform.closeStreamProcessor();
    streamPlatform.resetMockInvocations();

    // when
    final var streamProcessor = streamPlatform.startStreamProcessor();

    // then
    final RecordProcessor recordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final InOrder inOrder = inOrder(recordProcessor);
    inOrder.verify(recordProcessor, TIMEOUT).init(any());
    inOrder.verify(recordProcessor, never()).replay(any());
    inOrder.verifyNoMoreInteractions();

    Awaitility.await("position has to be set on processing start")
        .untilAsserted(
            () -> assertThat(streamProcessor.getLastProcessedPositionAsync().join()).isEqualTo(2L));
    Awaitility.await("position has to be set on processing start")
        .untilAsserted(
            () -> assertThat(streamProcessor.getLastWrittenPositionAsync().join()).isEqualTo(2L));

    // state has to be updated
    assertThat(streamPlatform.getLastSuccessfulProcessedRecordPosition()).isEqualTo(2);
    assertThat(Protocol.decodeKeyInPartition(streamPlatform.getCurrentKey())).isEqualTo(19L);
  }

  @Test
  void shouldNotReplayEventIfAlreadyApplied() throws Exception {
    // given
    final var eventKeyBeforeSnapshot = Protocol.encodePartitionId(1, 19);
    final var eventKeyAfterSnapshot = Protocol.encodePartitionId(1, 21);

    // on replay the positions and keys are restored
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.event()
            .processInstance(ELEMENT_ACTIVATING, Records.processInstance(1))
            .key(eventKeyBeforeSnapshot)
            .causedBy(0));
    // starting the stream processor awaits the opening/replay phase
    streamPlatform.startStreamProcessor();
    // the snapshot will contain the key and the position (in the metadata)
    streamPlatform.snapshot();
    streamPlatform.closeStreamProcessor();
    streamPlatform.resetMockInvocations();

    // when
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.event()
            .key(eventKeyAfterSnapshot)
            .processInstance(ELEMENT_ACTIVATING, Records.processInstance(1))
            .causedBy(0));
    final var streamProcessor = streamPlatform.startStreamProcessor();

    // then
    final ArgumentCaptor<TypedRecord> recordCaptor = ArgumentCaptor.forClass(TypedRecord.class);
    final RecordProcessor recordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final InOrder inOrder = inOrder(recordProcessor);
    inOrder.verify(recordProcessor, TIMEOUT).replay(recordCaptor.capture());
    inOrder.verifyNoMoreInteractions();

    assertThat(recordCaptor.getValue().getKey()).isEqualTo(eventKeyAfterSnapshot);

    Awaitility.await("position has to be set on processing start")
        .untilAsserted(
            () -> assertThat(streamProcessor.getLastProcessedPositionAsync().join()).isEqualTo(3L));
    Awaitility.await("position has to be set on processing start")
        .untilAsserted(
            () -> assertThat(streamProcessor.getLastWrittenPositionAsync().join()).isEqualTo(4L));
    assertThat(Protocol.decodeKeyInPartition(streamPlatform.getCurrentKey())).isEqualTo(21L);
  }

  @RegressionTest("https://github.com/camunda/camunda/issues/13101")
  void shouldNotReplayErrorEventAppliedInSnapshot() throws Exception {
    // given
    final var processorWhichFails = setupProcessorWhichFailsOnProcessing();
    setupOnErrorReactionForProcessor(processorWhichFails);

    streamPlatform.startStreamProcessor();
    // commands to process -> which should fail
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(2)));

    // await that two commands are failed and processed, to make sure error event has been committed
    verify(processorWhichFails, TIMEOUT.times(2)).onProcessingError(any(), any(), any());
    Awaitility.await("last processed position is updated")
        .until(() -> streamPlatform.getLastSuccessfulProcessedRecordPosition(), pos -> pos >= 2);
    // the snapshot should contain last processed position
    streamPlatform.snapshot();
    streamPlatform.closeStreamProcessor();
    streamPlatform.resetMockInvocations();

    // when
    streamPlatform.startStreamProcessor();
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    verifyProcessingErrorLifecycle(processorWhichFails);
    // we shouldn't replay any events - due to snapshot
    verify(processorWhichFails, never()).replay(any());
  }

  @Test
  void shouldReturnLastProcessedPositionInSnapshotWhenPausedDuringReplay() throws Exception {
    // given
    final var positionInSnapshot =
        streamPlatform.writeBatch(
            RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));
    streamPlatform.writeBatch(
        RecordToWrite.event()
            .processInstance(ELEMENT_ACTIVATING, Records.processInstance(1))
            .key(1)
            .causedBy((int) positionInSnapshot));

    streamPlatform.startStreamProcessor();
    // Ensure that the last processed position is updated before taking a snapshot. This is
    // necessary to ensure that after restarting the processor, it can recover from a non-zero
    // processed position.
    Awaitility.await("Last written position has to be updated before taking snapshot")
        .untilAsserted(
            () ->
                assertThat(
                        streamPlatform.getStreamProcessor().getLastProcessedPositionAsync().join())
                    .isEqualTo(positionInSnapshot));
    streamPlatform.snapshot();

    // write more events to make sure that the processor is paused in replay phase after recovery
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.event()
            .processInstance(ELEMENT_ACTIVATING, Records.processInstance(1))
            .key(2)
            .causedBy(0));

    streamPlatform.closeStreamProcessor();
    streamPlatform.resetMockInvocations();

    // when
    final var streamProcessor = streamPlatform.startStreamProcessorPaused();

    // then
    assertThat(streamProcessor.getCurrentPhase().join()).isEqualTo(Phase.PAUSED);
    Awaitility.await("position has to be set when replay is paused")
        .untilAsserted(
            () ->
                assertThat(streamProcessor.getLastProcessedPositionAsync().join())
                    .isEqualTo(positionInSnapshot));
  }

  private static void verifyProcessingErrorLifecycle(final RecordProcessor processorWhichFails) {
    final var inOrder = inOrder(processorWhichFails);
    inOrder.verify(processorWhichFails, TIMEOUT).init(any());
    inOrder.verify(processorWhichFails, TIMEOUT).accepts(ValueType.PROCESS_INSTANCE);
    inOrder.verify(processorWhichFails, TIMEOUT).process(any(), any());
    inOrder.verify(processorWhichFails, TIMEOUT).onProcessingError(any(), any(), any());
    inOrder.verifyNoMoreInteractions();
  }

  private RecordProcessor setupProcessorWhichFailsOnProcessing() {
    final var processorWhichFails = streamPlatform.getDefaultMockedRecordProcessor();
    doThrow(new RuntimeException("processing error"))
        .when(processorWhichFails)
        .process(any(), any());
    return processorWhichFails;
  }

  private static void setupOnErrorReactionForProcessor(final RecordProcessor processorWhichFails) {
    doAnswer(
            invocationOnMock -> {
              // writing error event on failure
              final var builder = (ProcessingResultBuilder) invocationOnMock.getArgument(2);
              final RecordMetadata recordMetadata = new RecordMetadata();
              recordMetadata
                  .valueType(ValueType.ERROR)
                  .intent(ErrorIntent.CREATED)
                  .recordType(RecordType.EVENT);
              builder.appendRecord(6, Records.processInstance(6), recordMetadata);
              return builder.build();
            })
        .when(processorWhichFails)
        .onProcessingError(
            any(Throwable.class), any(TypedRecord.class), any(ProcessingResultBuilder.class));
  }

  @Test
  void shouldIgnoreKeysFromDifferentPartition() {
    // given
    final var keyOfThisPartition = Protocol.encodePartitionId(1, 19L);
    final var keyOfOtherPartition = Protocol.encodePartitionId(2, 21L);

    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.event()
            .key(keyOfThisPartition)
            .processInstance(ELEMENT_ACTIVATING, Records.processInstance(1))
            .causedBy(0),
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
        RecordToWrite.event()
            .key(keyOfOtherPartition)
            .processInstance(ELEMENT_ACTIVATING, Records.processInstance(1))
            .causedBy(2));

    // when
    streamPlatform.startStreamProcessor();

    // then
    assertThat(Protocol.decodeKeyInPartition(streamPlatform.getCurrentKey())).isEqualTo(19L);
  }
}
