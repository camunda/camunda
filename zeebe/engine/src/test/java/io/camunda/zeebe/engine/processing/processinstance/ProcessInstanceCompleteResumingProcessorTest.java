/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.metrics.SuspensionMetrics;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.timer.DueDateTimerCheckScheduler;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.util.MockTypedRecord;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.api.SideEffectProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public final class ProcessInstanceCompleteResumingProcessorTest {

  private static final long PROCESS_INSTANCE_KEY = 100L;

  private ElementInstanceState elementInstanceState;
  private SuspensionState suspensionState;
  private StateWriter stateWriter;
  private SideEffectWriter sideEffectWriter;
  private TypedRejectionWriter rejectionWriter;
  private DueDateTimerCheckScheduler timerChecker;
  private SuspensionMetrics suspensionMetrics;
  private ProcessInstanceCompleteResumingProcessor processor;

  @BeforeEach
  void setUp() {
    elementInstanceState = mock(ElementInstanceState.class);
    suspensionState = mock(SuspensionState.class);
    stateWriter = mock(StateWriter.class);
    sideEffectWriter = mock(SideEffectWriter.class);
    rejectionWriter = mock(TypedRejectionWriter.class);
    timerChecker = mock(DueDateTimerCheckScheduler.class);
    suspensionMetrics = mock(SuspensionMetrics.class);

    final var writers = mock(Writers.class);
    when(writers.state()).thenReturn(stateWriter);
    when(writers.sideEffect()).thenReturn(sideEffectWriter);
    when(writers.rejection()).thenReturn(rejectionWriter);

    processor =
        new ProcessInstanceCompleteResumingProcessor(
            elementInstanceState, suspensionState, writers, timerChecker, suspensionMetrics);

    // default: the common case of an active instance still marked RESUMING
    when(suspensionState.getSuspensionState(PROCESS_INSTANCE_KEY))
        .thenReturn(SuspensionState.State.RESUMING);
  }

  @Test
  void shouldWriteResumedWhenInstanceExistsAndMarkerIsResuming() {
    // given
    final var record = new ProcessInstanceRecord().setProcessInstanceKey(PROCESS_INSTANCE_KEY);
    seedActiveInstance(record);

    // when
    processor.processRecord(continueResumingCommand());

    // then
    verify(stateWriter)
        .appendFollowUpEvent(
            eq(PROCESS_INSTANCE_KEY), eq(ProcessInstanceIntent.RESUMED), eq(record));
    verify(rejectionWriter, never()).appendRejection(any(), any(), any());
  }

  @Test
  void shouldNudgeDueDateTimerCheckerWhenResumeCompletes() {
    // given - resume must nudge the checker so a timer stranded during suspension fires promptly
    // instead of waiting for the next unrelated timer
    seedActiveInstance(new ProcessInstanceRecord().setProcessInstanceKey(PROCESS_INSTANCE_KEY));

    // when
    processor.processRecord(continueResumingCommand());

    // then
    final var sideEffectCaptor = ArgumentCaptor.forClass(SideEffectProducer.class);
    verify(sideEffectWriter).appendSideEffect(sideEffectCaptor.capture());
    sideEffectCaptor.getValue().flush();

    verify(timerChecker).scheduleTimer(-1);
  }

  @Test
  void shouldRejectWhenInstanceCancelledMidDrain() {
    // given - no element instance seeded; getInstance returns null

    // when
    processor.processRecord(continueResumingCommand());

    // then
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
    verify(rejectionWriter).appendRejection(any(), eq(RejectionType.INVALID_STATE), any());
  }

  @Test
  void shouldRejectWhenMarkerIsNoLongerResuming() {
    // given - a concurrent chain already finalized this instance
    seedActiveInstance(new ProcessInstanceRecord().setProcessInstanceKey(PROCESS_INSTANCE_KEY));
    when(suspensionState.getSuspensionState(PROCESS_INSTANCE_KEY)).thenReturn(null);

    // when
    processor.processRecord(continueResumingCommand());

    // then
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
    final var reasonCaptor = ArgumentCaptor.forClass(String.class);
    verify(rejectionWriter)
        .appendRejection(any(), eq(RejectionType.INVALID_STATE), reasonCaptor.capture());
    assertThat(reasonCaptor.getValue()).contains("no longer RESUMING");
  }

  @Test
  void shouldRejectWhenElementIsTerminating() {
    // given
    seedActiveInstance(
        new ProcessInstanceRecord().setProcessInstanceKey(PROCESS_INSTANCE_KEY),
        ProcessInstanceIntent.ELEMENT_TERMINATING);

    // when
    processor.processRecord(continueResumingCommand());

    // then
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
    verify(rejectionWriter).appendRejection(any(), eq(RejectionType.INVALID_STATE), any());
  }

  @Test
  void shouldRejectWhenElementIsCompleting() {
    // given
    seedActiveInstance(
        new ProcessInstanceRecord().setProcessInstanceKey(PROCESS_INSTANCE_KEY),
        ProcessInstanceIntent.ELEMENT_COMPLETING);

    // when
    processor.processRecord(continueResumingCommand());

    // then
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
    verify(rejectionWriter).appendRejection(any(), eq(RejectionType.INVALID_STATE), any());
  }

  private void seedActiveInstance(final ProcessInstanceRecord record) {
    seedActiveInstance(record, ProcessInstanceIntent.ELEMENT_ACTIVATED);
  }

  private void seedActiveInstance(
      final ProcessInstanceRecord record, final ProcessInstanceIntent state) {
    final var elementInstance = mock(ElementInstance.class);
    when(elementInstance.getValue()).thenReturn(record);
    when(elementInstance.getState()).thenReturn(state);
    when(elementInstanceState.getInstance(PROCESS_INSTANCE_KEY)).thenReturn(elementInstance);
  }

  private MockTypedRecord<ProcessInstanceRecord> continueResumingCommand() {
    return new MockTypedRecord<>(
        PROCESS_INSTANCE_KEY,
        new RecordMetadata(),
        new ProcessInstanceRecord().setProcessInstanceKey(PROCESS_INSTANCE_KEY));
  }
}
