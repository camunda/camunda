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
import static io.camunda.zeebe.engine.util.RecordToWrite.rejection;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ACTIVATE_ELEMENT;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.engine.state.EventApplier;
import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.engine.util.StreamProcessingComposite.StreamProcessorTestFactory;
import io.camunda.zeebe.engine.util.StreamProcessorRule;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.verification.VerificationWithTimeout;

public final class StreamProcessorReplayTest {

  private static final long TIMEOUT_MILLIS = 2_000L;
  private static final VerificationWithTimeout TIMEOUT = timeout(TIMEOUT_MILLIS);

  private static final ProcessInstanceRecord RECORD = Records.processInstance(1);

  @Rule public final StreamProcessorRule streamProcessorRule = new StreamProcessorRule();

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private TypedRecordProcessor<?> typedRecordProcessor;
  @Mock private EventApplier eventApplier;

  @Test
  public void shouldReplayEvents() {
    // given
    streamProcessorRule.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    // when
    startStreamProcessor(typedRecordProcessor, eventApplier);

    // then
    verify(eventApplier, TIMEOUT).applyState(anyLong(), eq(ELEMENT_ACTIVATING), any());
    verifyNoMoreInteractions(eventApplier);
    verifyNoInteractions(typedRecordProcessor);
  }

  @Test
  public void shouldSkipCommands() {
    // given
    streamProcessorRule.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    // when
    startStreamProcessor(typedRecordProcessor, eventApplier);

    // then
    verify(eventApplier, never()).applyState(anyLong(), eq(ACTIVATE_ELEMENT), any());
    verifyNoInteractions(typedRecordProcessor);
  }

  @Test
  public void shouldSkipRejections() {
    // given
    streamProcessorRule.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        rejection().processInstance(ACTIVATE_ELEMENT, RECORD).causedBy(0));

    // when
    startStreamProcessor(typedRecordProcessor, eventApplier);

    // then
    verifyNoInteractions(typedRecordProcessor, eventApplier);
  }

  @Test
  public void shouldNotReplayEventIfAlreadyApplied() {
    // given
    final var eventKeyBeforeSnapshot = 1L;
    final var eventKeyAfterSnapshot = 2L;

    final StreamProcessorTestFactory processorTestFactory =
        (processors, context) ->
            processors.onCommand(
                ValueType.PROCESS_INSTANCE,
                ACTIVATE_ELEMENT,
                new TypedRecordProcessor<>() {
                  @Override
                  public void processRecord(final TypedRecord<UnifiedRecordValue> record) {
                    // we need to produce a result otherwise the command will marked as skipped
                    context.getWriters().sideEffect().appendSideEffect(() -> true);
                  }
                });
    streamProcessorRule
        .withEventApplierFactory(zeebeState -> eventApplier)
        .startTypedStreamProcessor(processorTestFactory);

    final long commandPositionBeforeSnapshot =
        streamProcessorRule.writeCommand(ACTIVATE_ELEMENT, RECORD);

    streamProcessorRule.writeEvent(
        ProcessInstanceIntent.ELEMENT_ACTIVATING,
        RECORD,
        writer ->
            writer.key(eventKeyBeforeSnapshot).sourceRecordPosition(commandPositionBeforeSnapshot));

    awaitUntilProcessed(commandPositionBeforeSnapshot);

    streamProcessorRule.snapshot();
    streamProcessorRule.closeStreamProcessor();

    // when
    streamProcessorRule.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().key(eventKeyAfterSnapshot).processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    startStreamProcessor(typedRecordProcessor, eventApplier);

    // then
    final InOrder inOrder = inOrder(eventApplier);
    inOrder.verify(eventApplier, never()).applyState(eq(eventKeyBeforeSnapshot), any(), any());
    inOrder.verify(eventApplier, TIMEOUT).applyState(eq(eventKeyAfterSnapshot), any(), any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldRestoreKeyGenerator() {
    // given
    final var lastGeneratedKey = 2L;
    final var previousGeneratedKey = 1L;

    streamProcessorRule.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().key(lastGeneratedKey).processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0),
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().key(previousGeneratedKey).processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(2));

    // when
    startStreamProcessor(typedRecordProcessor, eventApplier);

    // then
    final var keyGenerator = streamProcessorRule.getProcessingState().getKeyGenerator();
    assertThat(keyGenerator.nextKey()).isEqualTo(lastGeneratedKey + 1);
  }

  @Test
  public void shouldIgnoreKeysFromDifferentPartition() {
    // given
    final var keyOfThisPartition = Protocol.encodePartitionId(0, 1L);
    final var keyOfOtherPartition = Protocol.encodePartitionId(1, 2L);

    streamProcessorRule.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().key(keyOfThisPartition).processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0),
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().key(keyOfOtherPartition).processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(2));

    // when
    startStreamProcessor(typedRecordProcessor, eventApplier);

    // then
    final var keyGenerator = streamProcessorRule.getProcessingState().getKeyGenerator();
    assertThat(keyGenerator.nextKey()).isEqualTo(keyOfThisPartition + 1);
  }

  private void startStreamProcessor(
      final TypedRecordProcessor<?> typedRecordProcessor, final EventApplier eventApplier) {
    streamProcessorRule
        .withEventApplierFactory(zeebeState -> eventApplier)
        .startTypedStreamProcessor(
            (processors, context) ->
                processors.onCommand(
                    ValueType.PROCESS_INSTANCE, ACTIVATE_ELEMENT, typedRecordProcessor));
  }

  private void awaitUntilProcessed(final long position) {
    Awaitility.await()
        .untilAsserted(
            () -> {
              final var processedPosition =
                  streamProcessorRule.getStreamProcessor(0).getLastProcessedPositionAsync().join();
              assertThat(processedPosition).isEqualTo(position);
            });
  }
}
