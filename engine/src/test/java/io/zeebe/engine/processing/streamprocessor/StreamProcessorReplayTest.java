/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.streamprocessor;

import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_ACTIVATING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;

import io.zeebe.engine.state.EventApplier;
import io.zeebe.engine.util.StreamProcessorRule;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
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

  private static final int EXPECTED_ON_RECOVERED_INVOCATIONS = 2;

  private static final WorkflowInstanceRecord RECORD =
      new WorkflowInstanceRecord().setBpmnElementType(BpmnElementType.TESTING_ONLY);

  @Rule public final StreamProcessorRule streamProcessorRule = new StreamProcessorRule();

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private TypedRecordProcessor<?> typedRecordProcessor;
  @Mock private EventApplier eventApplier;

  @Test
  public void shouldReplayEvents() {
    // given
    final long commandPosition =
        streamProcessorRule.writeCommand(WorkflowInstanceIntent.ACTIVATE_ELEMENT, RECORD);

    streamProcessorRule.writeEvent(
        WorkflowInstanceIntent.ELEMENT_ACTIVATING,
        RECORD,
        writer -> writer.sourceRecordPosition(commandPosition));

    // when
    startStreamProcessor(typedRecordProcessor, eventApplier);

    // then
    final InOrder inOrder = inOrder(typedRecordProcessor, eventApplier);
    inOrder.verify(eventApplier, TIMEOUT).applyState(anyLong(), eq(ELEMENT_ACTIVATING), any());
    inOrder
        .verify(typedRecordProcessor, never())
        .processRecord(anyLong(), any(), any(), any(), any());
    inOrder
        .verify(typedRecordProcessor, TIMEOUT.times(EXPECTED_ON_RECOVERED_INVOCATIONS))
        .onRecovered(any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldSkipCommands() {
    // given
    final var commandPosition =
        streamProcessorRule.writeCommand(WorkflowInstanceIntent.ACTIVATE_ELEMENT, RECORD);

    streamProcessorRule.writeEvent(
        WorkflowInstanceIntent.ELEMENT_ACTIVATING,
        RECORD,
        writer -> writer.sourceRecordPosition(commandPosition));

    // when
    startStreamProcessor(typedRecordProcessor, eventApplier);

    // then
    final InOrder inOrder = inOrder(typedRecordProcessor, eventApplier);
    inOrder
        .verify(typedRecordProcessor, never())
        .processRecord(anyLong(), any(), any(), any(), any());
    inOrder
        .verify(eventApplier, never())
        .applyState(anyLong(), eq(WorkflowInstanceIntent.ACTIVATE_ELEMENT), any());
    inOrder
        .verify(typedRecordProcessor, TIMEOUT.times(EXPECTED_ON_RECOVERED_INVOCATIONS))
        .onRecovered(any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldSkipRejections() {
    // given
    final var commandPosition =
        streamProcessorRule.writeCommand(WorkflowInstanceIntent.ACTIVATE_ELEMENT, RECORD);

    streamProcessorRule.writeCommandRejection(
        WorkflowInstanceIntent.ACTIVATE_ELEMENT,
        RECORD,
        writer -> writer.sourceRecordPosition(commandPosition));

    // when
    startStreamProcessor(typedRecordProcessor, eventApplier);

    // then
    final InOrder inOrder = inOrder(typedRecordProcessor, eventApplier);
    inOrder
        .verify(typedRecordProcessor, never())
        .processRecord(anyLong(), any(), any(), any(), any());
    inOrder
        .verify(eventApplier, never())
        .applyState(anyLong(), eq(WorkflowInstanceIntent.ACTIVATE_ELEMENT), any());
    inOrder
        .verify(typedRecordProcessor, TIMEOUT.times(EXPECTED_ON_RECOVERED_INVOCATIONS))
        .onRecovered(any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldNotReplayEventIfAlreadyApplied() {
    // given
    final var eventKeyBeforeSnapshot = 1L;
    final var eventKeyAfterSnapshot = 2L;

    startStreamProcessor(typedRecordProcessor, eventApplier);

    final long commandPositionBeforeSnapshot =
        streamProcessorRule.writeCommand(WorkflowInstanceIntent.ACTIVATE_ELEMENT, RECORD);

    streamProcessorRule.writeEvent(
        WorkflowInstanceIntent.ELEMENT_ACTIVATING,
        RECORD,
        writer ->
            writer.key(eventKeyBeforeSnapshot).sourceRecordPosition(commandPositionBeforeSnapshot));

    awaitUntilProcessed(commandPositionBeforeSnapshot);

    streamProcessorRule.snapshot();
    streamProcessorRule.closeStreamProcessor();

    // when
    final long commandPositionAfterSnapshot =
        streamProcessorRule.writeCommand(WorkflowInstanceIntent.ACTIVATE_ELEMENT, RECORD);

    streamProcessorRule.writeEvent(
        WorkflowInstanceIntent.ELEMENT_ACTIVATING,
        RECORD,
        writer ->
            writer.key(eventKeyAfterSnapshot).sourceRecordPosition(commandPositionAfterSnapshot));

    startStreamProcessor(typedRecordProcessor, eventApplier);

    // then
    final InOrder inOrder = inOrder(eventApplier);
    inOrder.verify(eventApplier, never()).applyState(eq(eventKeyBeforeSnapshot), any(), any());
    inOrder.verify(eventApplier, TIMEOUT).applyState(eq(eventKeyAfterSnapshot), any(), any());
    inOrder.verifyNoMoreInteractions();
  }

  private void startStreamProcessor(
      final TypedRecordProcessor<?> typedRecordProcessor, final EventApplier eventApplier) {
    streamProcessorRule
        .withEventApplierFactory(zeebeState -> eventApplier)
        .startTypedStreamProcessor(
            (processors, context) ->
                processors
                    .onCommand(
                        ValueType.WORKFLOW_INSTANCE,
                        WorkflowInstanceIntent.ACTIVATE_ELEMENT,
                        typedRecordProcessor)
                    .onEvent(
                        ValueType.WORKFLOW_INSTANCE, ELEMENT_ACTIVATING, typedRecordProcessor));
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
