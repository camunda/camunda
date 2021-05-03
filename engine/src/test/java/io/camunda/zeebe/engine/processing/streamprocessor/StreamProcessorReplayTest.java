/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.streamprocessor;

import static io.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.EventApplier;
import io.zeebe.engine.util.StreamProcessorRule;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import java.util.concurrent.atomic.AtomicLong;
import org.assertj.core.api.Assumptions;
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

  private static final ProcessInstanceRecord RECORD =
      new ProcessInstanceRecord().setBpmnElementType(BpmnElementType.TESTING_ONLY);

  @Rule public final StreamProcessorRule streamProcessorRule = new StreamProcessorRule();

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private TypedRecordProcessor<?> typedRecordProcessor;
  @Mock private EventApplier eventApplier;

  @Test
  public void shouldReplayEvents() {
    // given
    final long commandPosition =
        streamProcessorRule.writeCommand(ProcessInstanceIntent.ACTIVATE_ELEMENT, RECORD);

    streamProcessorRule.writeEvent(
        ProcessInstanceIntent.ELEMENT_ACTIVATING,
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
        streamProcessorRule.writeCommand(ProcessInstanceIntent.ACTIVATE_ELEMENT, RECORD);

    streamProcessorRule.writeEvent(
        ProcessInstanceIntent.ELEMENT_ACTIVATING,
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
        .applyState(anyLong(), eq(ProcessInstanceIntent.ACTIVATE_ELEMENT), any());
    inOrder
        .verify(typedRecordProcessor, TIMEOUT.times(EXPECTED_ON_RECOVERED_INVOCATIONS))
        .onRecovered(any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldSkipRejections() {
    // given
    final var commandPosition =
        streamProcessorRule.writeCommand(ProcessInstanceIntent.ACTIVATE_ELEMENT, RECORD);

    streamProcessorRule.writeCommandRejection(
        ProcessInstanceIntent.ACTIVATE_ELEMENT,
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
        .applyState(anyLong(), eq(ProcessInstanceIntent.ACTIVATE_ELEMENT), any());
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
        streamProcessorRule.writeCommand(ProcessInstanceIntent.ACTIVATE_ELEMENT, RECORD);

    streamProcessorRule.writeEvent(
        ProcessInstanceIntent.ELEMENT_ACTIVATING,
        RECORD,
        writer ->
            writer.key(eventKeyBeforeSnapshot).sourceRecordPosition(commandPositionBeforeSnapshot));

    awaitUntilProcessed(commandPositionBeforeSnapshot);

    streamProcessorRule.snapshot();
    streamProcessorRule.closeStreamProcessor();

    // when
    final long commandPositionAfterSnapshot =
        streamProcessorRule.writeCommand(ProcessInstanceIntent.ACTIVATE_ELEMENT, RECORD);

    streamProcessorRule.writeEvent(
        ProcessInstanceIntent.ELEMENT_ACTIVATING,
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

  @Test
  public void shouldRestoreKeyGenerator() {
    // given
    final var lastGeneratedKey = 2L;
    final var previousGeneratedKey = 1L;

    final long firstCommandPosition =
        streamProcessorRule.writeCommand(ProcessInstanceIntent.ACTIVATE_ELEMENT, RECORD);

    streamProcessorRule.writeEvent(
        ProcessInstanceIntent.ELEMENT_ACTIVATING,
        RECORD,
        writer -> writer.key(lastGeneratedKey).sourceRecordPosition(firstCommandPosition));

    final long secondCommandPosition =
        streamProcessorRule.writeCommand(
            previousGeneratedKey, ProcessInstanceIntent.ACTIVATE_ELEMENT, RECORD);

    streamProcessorRule.writeEvent(
        ProcessInstanceIntent.ELEMENT_ACTIVATING,
        RECORD,
        writer -> writer.key(previousGeneratedKey).sourceRecordPosition(secondCommandPosition));

    // when
    startStreamProcessor(typedRecordProcessor, eventApplier);

    // then
    verify(typedRecordProcessor, TIMEOUT.times(EXPECTED_ON_RECOVERED_INVOCATIONS))
        .onRecovered(any());

    final var keyGenerator = streamProcessorRule.getZeebeState().getKeyGenerator();
    assertThat(keyGenerator.nextKey()).isEqualTo(lastGeneratedKey + 1);
  }

  @Test
  public void shouldRestoreKeyGeneratorAfterSkippingCommand() {
    Assumptions.assumeThat(MigratedStreamProcessors.isMigrated(ValueType.INCIDENT))
        .describedAs("Expected a not yet migrated value type")
        .isFalse();

    final var incidentRecord = new IncidentRecord();

    // given
    final var lastGeneratedKey = 3L;
    final var previousGeneratedKey = 1L;
    final var firstGeneratedKey = 2L;

    final long firstCommandPosition =
        streamProcessorRule.writeCommand(ProcessInstanceIntent.ACTIVATE_ELEMENT, RECORD);

    streamProcessorRule.writeEvent(
        ProcessInstanceIntent.ELEMENT_ACTIVATING,
        RECORD,
        writer -> writer.key(firstGeneratedKey).sourceRecordPosition(firstCommandPosition));

    final long secondCommandPosition =
        streamProcessorRule.writeCommand(
            previousGeneratedKey, ProcessInstanceIntent.ACTIVATE_ELEMENT, RECORD);

    streamProcessorRule.writeEvent(
        ProcessInstanceIntent.ELEMENT_ACTIVATING,
        RECORD,
        writer -> writer.key(previousGeneratedKey).sourceRecordPosition(secondCommandPosition));

    final long reprocessingCommandPosition =
        streamProcessorRule.writeCommand(IncidentIntent.RESOLVE, incidentRecord);

    streamProcessorRule.writeEvent(
        IncidentIntent.RESOLVED,
        incidentRecord,
        writer -> writer.key(lastGeneratedKey).sourceRecordPosition(reprocessingCommandPosition));

    // when
    final var generatedKeyOnReprocessing = new AtomicLong(-1);

    streamProcessorRule
        .withEventApplierFactory(zeebeState -> eventApplier)
        .startTypedStreamProcessor(
            (processors, context) ->
                processors
                    .onCommand(
                        ValueType.PROCESS_INSTANCE,
                        ProcessInstanceIntent.ACTIVATE_ELEMENT,
                        typedRecordProcessor)
                    .onEvent(ValueType.PROCESS_INSTANCE, ELEMENT_ACTIVATING, typedRecordProcessor)
                    .onCommand(
                        ValueType.INCIDENT,
                        IncidentIntent.RESOLVE,
                        new TypedRecordProcessor<>() {
                          @Override
                          public void processRecord(
                              final TypedRecord<UnifiedRecordValue> record,
                              final TypedResponseWriter responseWriter,
                              final TypedStreamWriter streamWriter) {
                            final var keyGenerator = context.getZeebeState().getKeyGenerator();
                            generatedKeyOnReprocessing.set(keyGenerator.nextKey());
                          }
                        }));

    awaitUntilProcessed(reprocessingCommandPosition);

    // then
    assertThat(generatedKeyOnReprocessing.get())
        .describedAs(
            "Expected the generated key on reprocessing to be equal to the written key on the stream")
        .isEqualTo(lastGeneratedKey);
  }

  @Test
  public void shouldIgnoreKeysFromDifferentPartition() {
    // given
    final var keyOfThisPartition = Protocol.encodePartitionId(0, 1L);
    final var keyOfOtherPartition = Protocol.encodePartitionId(1, 2L);

    final long firstCommandPosition =
        streamProcessorRule.writeCommand(ProcessInstanceIntent.ACTIVATE_ELEMENT, RECORD);

    streamProcessorRule.writeEvent(
        ProcessInstanceIntent.ELEMENT_ACTIVATING,
        RECORD,
        writer -> writer.key(keyOfThisPartition).sourceRecordPosition(firstCommandPosition));

    final long secondCommandPosition =
        streamProcessorRule.writeCommand(
            keyOfOtherPartition, ProcessInstanceIntent.ACTIVATE_ELEMENT, RECORD);

    streamProcessorRule.writeEvent(
        ProcessInstanceIntent.ELEMENT_ACTIVATING,
        RECORD,
        writer -> writer.key(keyOfOtherPartition).sourceRecordPosition(secondCommandPosition));

    // when
    startStreamProcessor(typedRecordProcessor, eventApplier);

    // then
    verify(typedRecordProcessor, TIMEOUT.times(EXPECTED_ON_RECOVERED_INVOCATIONS))
        .onRecovered(any());

    final var keyGenerator = streamProcessorRule.getZeebeState().getKeyGenerator();
    assertThat(keyGenerator.nextKey()).isEqualTo(keyOfThisPartition + 1);
  }

  private void startStreamProcessor(
      final TypedRecordProcessor<?> typedRecordProcessor, final EventApplier eventApplier) {
    streamProcessorRule
        .withEventApplierFactory(zeebeState -> eventApplier)
        .startTypedStreamProcessor(
            (processors, context) ->
                processors
                    .onCommand(
                        ValueType.PROCESS_INSTANCE,
                        ProcessInstanceIntent.ACTIVATE_ELEMENT,
                        typedRecordProcessor)
                    .onEvent(ValueType.PROCESS_INSTANCE, ELEMENT_ACTIVATING, typedRecordProcessor));
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
