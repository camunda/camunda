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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.util.MockTypedRecord;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public final class ProcessInstanceCompleteSuspendingProcessorTest {

  private static final long PROCESS_INSTANCE_KEY = 100L;

  private ElementInstanceState elementInstanceState;
  private SuspensionState suspensionState;
  private StateWriter stateWriter;
  private TypedRejectionWriter rejectionWriter;
  private SuspensionMetrics suspensionMetrics;
  private ProcessInstanceCompleteSuspendingProcessor processor;

  @BeforeEach
  void setUp() {
    elementInstanceState = mock(ElementInstanceState.class);
    suspensionState = mock(SuspensionState.class);
    stateWriter = mock(StateWriter.class);
    rejectionWriter = mock(TypedRejectionWriter.class);
    suspensionMetrics = mock(SuspensionMetrics.class);

    final var writers = mock(Writers.class);
    when(writers.state()).thenReturn(stateWriter);
    when(writers.rejection()).thenReturn(rejectionWriter);

    processor =
        new ProcessInstanceCompleteSuspendingProcessor(
            elementInstanceState, suspensionState, writers, suspensionMetrics);

    // default: the common case of an active instance still marked SUSPENDING
    when(suspensionState.getSuspensionState(PROCESS_INSTANCE_KEY))
        .thenReturn(SuspensionState.State.SUSPENDING);
  }

  @Test
  void shouldWriteSuspendedWhenInstanceExistsAndMarkerIsSuspending() {
    // given
    final var record = new ProcessInstanceRecord().setProcessInstanceKey(PROCESS_INSTANCE_KEY);
    seedActiveInstance(record);

    // when
    processor.processRecord(completeSuspendingCommand());

    // then
    verify(stateWriter)
        .appendFollowUpEvent(
            eq(PROCESS_INSTANCE_KEY), eq(ProcessInstanceIntent.SUSPENDED), eq(record));
    verify(rejectionWriter, never()).appendRejection(any(), any(), any());
    verify(suspensionMetrics).instanceSuspended();
  }

  @Test
  void shouldRejectWhenInstanceCancelledWhileSuspending() {
    // given - no element instance seeded; getInstance returns null

    // when
    processor.processRecord(completeSuspendingCommand());

    // then
    verifyNotSuspended();
    final var reasonCaptor = ArgumentCaptor.forClass(String.class);
    verify(rejectionWriter)
        .appendRejection(any(), eq(RejectionType.INVALID_STATE), reasonCaptor.capture());
    assertThat(reasonCaptor.getValue()).contains("no longer exists");
  }

  @Test
  void shouldRejectWhenMarkerIsNoLongerSuspending() {
    // given
    seedActiveInstance(new ProcessInstanceRecord().setProcessInstanceKey(PROCESS_INSTANCE_KEY));
    when(suspensionState.getSuspensionState(PROCESS_INSTANCE_KEY)).thenReturn(null);

    // when
    processor.processRecord(completeSuspendingCommand());

    // then
    verifyNotSuspended();
    final var reasonCaptor = ArgumentCaptor.forClass(String.class);
    verify(rejectionWriter)
        .appendRejection(any(), eq(RejectionType.INVALID_STATE), reasonCaptor.capture());
    assertThat(reasonCaptor.getValue()).contains("no longer SUSPENDING");
  }

  @Test
  void shouldRejectWhenElementIsTerminating() {
    // given - a cancel was processed while the instance was suspending
    seedActiveInstance(
        new ProcessInstanceRecord().setProcessInstanceKey(PROCESS_INSTANCE_KEY),
        ProcessInstanceIntent.ELEMENT_TERMINATING);

    // when
    processor.processRecord(completeSuspendingCommand());

    // then
    verifyNotSuspended();
    verify(rejectionWriter).appendRejection(any(), eq(RejectionType.INVALID_STATE), any());
  }

  @Test
  void shouldRejectWhenElementIsCompleting() {
    // given
    seedActiveInstance(
        new ProcessInstanceRecord().setProcessInstanceKey(PROCESS_INSTANCE_KEY),
        ProcessInstanceIntent.ELEMENT_COMPLETING);

    // when
    processor.processRecord(completeSuspendingCommand());

    // then
    verifyNotSuspended();
    verify(rejectionWriter).appendRejection(any(), eq(RejectionType.INVALID_STATE), any());
  }

  private void verifyNotSuspended() {
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
    verify(suspensionMetrics, never()).instanceSuspended();
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

  private MockTypedRecord<ProcessInstanceRecord> completeSuspendingCommand() {
    return new MockTypedRecord<>(
        PROCESS_INSTANCE_KEY,
        new RecordMetadata(),
        new ProcessInstanceRecord().setProcessInstanceKey(PROCESS_INSTANCE_KEY));
  }
}
