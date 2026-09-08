/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware.SuspensionAction;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState.State;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess.AdHocSubProcessInstructionRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class SuspensionBehaviorTest {

  private static final long PROCESS_INSTANCE_KEY = 42L;
  private static final long JOB_KEY = 7L;
  private static final long AD_HOC_SUB_PROCESS_ELEMENT_INSTANCE_KEY = 13L;

  private ProcessingState processingState;
  private SuspensionState suspensionState;
  private SuspensionBehavior suspensionBehavior;

  @BeforeEach
  void setUp() {
    processingState = mock(ProcessingState.class);
    suspensionState = mock(SuspensionState.class);
    when(processingState.getSuspensionState()).thenReturn(suspensionState);
    suspensionBehavior = new SuspensionBehavior(processingState);
  }

  @Test
  void shouldProcessWhenProcessorIsNotSuspensionAware() {
    // given - a suspended instance and a processor that does not opt into suspension handling
    markerIs(State.SUSPENDED);

    // when
    final var result = suspensionBehavior.resolve(command(), plainProcessor());

    // then - non-aware processors are never gated, and the key is not even resolved
    assertThat(result.outcome()).isEqualTo(SuspensionAction.PROCESS);
    assertThat(result.processInstanceKey()).isEqualTo(-1);
  }

  @Test
  void shouldProcessWhenNoSuspensionMarker() {
    // given - no marker, but a processor that would otherwise reject
    markerIs(null);

    // when / then
    assertThat(
            suspensionBehavior
                .resolve(command(), overridingProcessor(SuspensionAction.REJECT))
                .outcome())
        .isEqualTo(SuspensionAction.PROCESS);
  }

  @ParameterizedTest
  @EnumSource(
      value = SuspensionAction.class,
      names = {"PROCESS", "REJECT"})
  void shouldApplyProcessorClassificationWhileSuspended(final SuspensionAction behavior) {
    // given
    markerIs(State.SUSPENDED);
    final SuspensionAction expected =
        switch (behavior) {
          case PROCESS -> SuspensionAction.PROCESS;
          case REJECT -> SuspensionAction.REJECT;
          case BUFFER -> throw new IllegalStateException("unreachable");
        };

    // when / then
    assertThat(suspensionBehavior.resolve(command(), overridingProcessor(behavior)).outcome())
        .isEqualTo(expected);
  }

  @Test
  void shouldBufferWhenProcessorClassifiesBufferWhileSuspended() {
    // given
    markerIs(State.SUSPENDED);

    // when
    final var result =
        suspensionBehavior.resolve(command(), overridingProcessor(SuspensionAction.BUFFER));

    // then
    assertThat(result.outcome()).isEqualTo(SuspensionAction.BUFFER);
  }

  @Test
  void shouldProcessWhenAwareProcessorReturnsNull() {
    // given
    markerIs(State.SUSPENDED);

    // when / then - a contract-violating null classification is logged and processed (fail-open)
    assertThat(suspensionBehavior.resolve(command(), overridingProcessor(null)).outcome())
        .isEqualTo(SuspensionAction.PROCESS);
  }

  @Test
  void shouldFlipBufferToProcessWhileResuming() {
    // given
    markerIs(State.RESUMING);

    // when / then - buffered commands drain (pass through) while resuming
    final var result =
        suspensionBehavior.resolve(
            command(), overridingResumingProcessor(SuspensionAction.PROCESS));
    assertThat(result.outcome()).isEqualTo(SuspensionAction.PROCESS);
  }

  @Test
  void shouldStillRejectWhileResuming() {
    // given
    markerIs(State.RESUMING);

    // when / then - rejected commands stay rejected until draining clears the marker
    assertThat(
            suspensionBehavior
                .resolve(command(), overridingResumingProcessor(SuspensionAction.REJECT))
                .outcome())
        .isEqualTo(SuspensionAction.REJECT);
  }

  @Test
  void shouldReturnProcessInstanceKeyResolvedFromCommandValue() {
    // given
    markerIs(State.SUSPENDED);

    // when
    final var result =
        suspensionBehavior.resolve(command(), overridingProcessor(SuspensionAction.REJECT));

    // then - callers reuse the resolved key instead of re-deriving it
    assertThat(result.processInstanceKey()).isEqualTo(PROCESS_INSTANCE_KEY);
  }

  @Test
  void shouldResolveProcessInstanceKeyFromStateForJobCommand() {
    // given - a JOB command whose value carries the entity key, not the process instance key
    final var jobState = mock(JobState.class);
    when(processingState.getJobState()).thenReturn(jobState);
    when(jobState.getJob(JOB_KEY))
        .thenReturn(new JobRecord().setProcessInstanceKey(PROCESS_INSTANCE_KEY));
    markerIs(State.SUSPENDED);
    final var command = mock(TypedRecord.class);
    when(command.getValue()).thenReturn(new JobRecord());
    when(command.getKey()).thenReturn(JOB_KEY);
    when(command.getValueType()).thenReturn(ValueType.JOB);

    // when
    final var result =
        suspensionBehavior.resolve(command, overridingProcessor(SuspensionAction.REJECT));

    // then - the real process instance key is resolved via job state and returned to the caller
    assertThat(result.outcome()).isEqualTo(SuspensionAction.REJECT);
    assertThat(result.processInstanceKey()).isEqualTo(PROCESS_INSTANCE_KEY);
  }

  @Test
  void shouldResolveProcessInstanceKeyFromScopeForVariableDocumentCommand() {
    // given - VARIABLE_DOCUMENT only carries a scope key; resolve via the element instance
    final long scopeKey = 99L;
    final var elementInstanceState = mock(ElementInstanceState.class);
    final var scope = mock(ElementInstance.class);
    when(processingState.getElementInstanceState()).thenReturn(elementInstanceState);
    when(elementInstanceState.getInstance(scopeKey)).thenReturn(scope);
    when(scope.getValue())
        .thenReturn(new ProcessInstanceRecord().setProcessInstanceKey(PROCESS_INSTANCE_KEY));
    markerIs(State.SUSPENDED);
    final var command = mock(TypedRecord.class);
    when(command.getValue()).thenReturn(new VariableDocumentRecord().setScopeKey(scopeKey));
    when(command.getValueType()).thenReturn(ValueType.VARIABLE_DOCUMENT);

    // when
    final var result =
        suspensionBehavior.resolve(command, overridingProcessor(SuspensionAction.PROCESS));

    // then
    assertThat(result.outcome()).isEqualTo(SuspensionAction.PROCESS);
    assertThat(result.processInstanceKey()).isEqualTo(PROCESS_INSTANCE_KEY);
  }

  @Test
  void shouldResolveProcessInstanceKeyFromStateForAdHocSubProcessInstructionCommand() {
    // given - an AD_HOC_SUB_PROCESS_INSTRUCTION command whose value carries the ad-hoc sub-process
    // instance key, not the process instance key
    final var elementInstanceState = mock(ElementInstanceState.class);
    when(processingState.getElementInstanceState()).thenReturn(elementInstanceState);
    final var elementInstance =
        new ElementInstance(
            AD_HOC_SUB_PROCESS_ELEMENT_INSTANCE_KEY,
            ProcessInstanceIntent.ELEMENT_ACTIVATED,
            new ProcessInstanceRecord().setProcessInstanceKey(PROCESS_INSTANCE_KEY));
    when(elementInstanceState.getInstance(AD_HOC_SUB_PROCESS_ELEMENT_INSTANCE_KEY))
        .thenReturn(elementInstance);
    markerIs(State.SUSPENDED);
    final var command = mock(TypedRecord.class);
    when(command.getValue())
        .thenReturn(
            new AdHocSubProcessInstructionRecord()
                .setAdHocSubProcessInstanceKey(AD_HOC_SUB_PROCESS_ELEMENT_INSTANCE_KEY));
    when(command.getValueType()).thenReturn(ValueType.AD_HOC_SUB_PROCESS_INSTRUCTION);

    // when
    final var result =
        suspensionBehavior.resolve(command, overridingProcessor(SuspensionAction.BUFFER));

    // then - the real process instance key is resolved via the element instance state and returned
    // to the caller
    assertThat(result.outcome()).isEqualTo(SuspensionAction.BUFFER);
    assertThat(result.processInstanceKey()).isEqualTo(PROCESS_INSTANCE_KEY);
  }

  @Test
  void shouldProcessAdHocSubProcessInstructionCommandWhenElementInstanceNotFound() {
    // given - the ad-hoc sub-process instance no longer exists (e.g. already completed/terminated)
    final var elementInstanceState = mock(ElementInstanceState.class);
    when(processingState.getElementInstanceState()).thenReturn(elementInstanceState);
    when(elementInstanceState.getInstance(AD_HOC_SUB_PROCESS_ELEMENT_INSTANCE_KEY))
        .thenReturn(null);
    final var command = mock(TypedRecord.class);
    when(command.getValue())
        .thenReturn(
            new AdHocSubProcessInstructionRecord()
                .setAdHocSubProcessInstanceKey(AD_HOC_SUB_PROCESS_ELEMENT_INSTANCE_KEY));
    when(command.getValueType()).thenReturn(ValueType.AD_HOC_SUB_PROCESS_INSTRUCTION);

    // when
    final var result =
        suspensionBehavior.resolve(command, overridingProcessor(SuspensionAction.BUFFER));

    // then - the key can't be resolved (-1), so the command is processed rather than gated
    assertThat(result.outcome()).isEqualTo(SuspensionAction.PROCESS);
    assertThat(result.processInstanceKey()).isEqualTo(-1);
  }

  private void markerIs(final @Nullable State state) {
    when(suspensionState.getSuspensionState(PROCESS_INSTANCE_KEY)).thenReturn(state);
  }

  private static TypedRecord<?> command() {
    final var command = mock(TypedRecord.class);
    when(command.getValue())
        .thenReturn(new ProcessInstanceRecord().setProcessInstanceKey(PROCESS_INSTANCE_KEY));
    return command;
  }

  private static TypedRecordProcessor<?> plainProcessor() {
    return mock(TypedRecordProcessor.class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static TypedRecordProcessor<?> overridingProcessor(
      final SuspensionAware.@Nullable SuspensionAction behavior) {
    final var processor =
        mock(TypedRecordProcessor.class, withSettings().extraInterfaces(SuspensionAware.class));
    when(((SuspensionAware) processor).onSuspended(any())).thenReturn(behavior);
    return processor;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static TypedRecordProcessor<?> overridingResumingProcessor(
      final SuspensionAware.@Nullable SuspensionAction behavior) {
    final var processor =
        mock(TypedRecordProcessor.class, withSettings().extraInterfaces(SuspensionAware.class));
    when(((SuspensionAware) processor).onResuming(any())).thenReturn(behavior);
    return processor;
  }
}
