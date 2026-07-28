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

import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware.SuspensionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionCheck.Decision;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState.State;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class SuspensionCheckTest {

  private static final long PROCESS_INSTANCE_KEY = 42L;
  private static final long JOB_KEY = 7L;

  private ProcessingState processingState;
  private SuspensionState suspensionState;
  private SuspensionCheck suspensionCheck;

  @BeforeEach
  void setUp() {
    processingState = mock(ProcessingState.class);
    suspensionState = mock(SuspensionState.class);
    when(processingState.getSuspensionState()).thenReturn(suspensionState);
    suspensionCheck = new SuspensionCheck(processingState);
  }

  @Test
  void shouldProcessWhenProcessorIsNotSuspensionAware() {
    // given - a suspended instance and a processor that does not opt into suspension handling
    markerIs(State.SUSPENDED);

    // when
    final var result = suspensionCheck.resolve(command(), plainProcessor());

    // then - non-aware processors are never gated, and the key is not even resolved
    assertThat(result.decision()).isEqualTo(Decision.PROCESS);
    assertThat(result.processInstanceKey()).isEqualTo(-1);
  }

  @Test
  void shouldProcessWhenNoSuspensionMarker() {
    // given - no marker, but a processor that would otherwise reject
    markerIs(null);

    // when / then
    assertThat(
            suspensionCheck
                .resolve(command(), overridingProcessor(SuspensionBehavior.REJECT))
                .decision())
        .isEqualTo(Decision.PROCESS);
  }

  @ParameterizedTest
  @EnumSource(
      value = SuspensionBehavior.class,
      names = {"PROCESS", "REJECT"})
  void shouldApplyProcessorClassificationWhileSuspended(final SuspensionBehavior behavior) {
    // given
    markerIs(State.SUSPENDED);
    final Decision expected =
        switch (behavior) {
          case PROCESS -> Decision.PROCESS;
          case REJECT -> Decision.REJECT;
          case BUFFER -> throw new IllegalStateException("unreachable");
        };

    // when / then
    assertThat(suspensionCheck.resolve(command(), overridingProcessor(behavior)).decision())
        .isEqualTo(expected);
  }

  @Test
  void shouldBufferWhenProcessorClassifiesBufferWhileSuspended() {
    // given
    markerIs(State.SUSPENDED);

    // when
    final var result =
        suspensionCheck.resolve(command(), overridingProcessor(SuspensionBehavior.BUFFER));

    // then
    assertThat(result.decision()).isEqualTo(Decision.BUFFER);
  }

  @Test
  void shouldProcessWhenAwareProcessorReturnsNull() {
    // given
    markerIs(State.SUSPENDED);

    // when / then - a contract-violating null classification is logged and processed (fail-open)
    assertThat(suspensionCheck.resolve(command(), overridingProcessor(null)).decision())
        .isEqualTo(Decision.PROCESS);
  }

  @Test
  void shouldFlipBufferToProcessWhileResuming() {
    // given
    markerIs(State.RESUMING);

    // when / then - buffered commands drain (pass through) while resuming
    assertThat(
            suspensionCheck
                .resolve(command(), overridingProcessor(SuspensionBehavior.BUFFER))
                .decision())
        .isEqualTo(Decision.PROCESS);
  }

  @Test
  void shouldStillRejectWhileResuming() {
    // given
    markerIs(State.RESUMING);

    // when / then - rejected commands stay rejected until draining clears the marker
    assertThat(
            suspensionCheck
                .resolve(command(), overridingProcessor(SuspensionBehavior.REJECT))
                .decision())
        .isEqualTo(Decision.REJECT);
  }

  @Test
  void shouldReturnProcessInstanceKeyResolvedFromCommandValue() {
    // given
    markerIs(State.SUSPENDED);

    // when
    final var result =
        suspensionCheck.resolve(command(), overridingProcessor(SuspensionBehavior.REJECT));

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
        suspensionCheck.resolve(command, overridingProcessor(SuspensionBehavior.REJECT));

    // then - the real process instance key is resolved via job state and returned to the caller
    assertThat(result.decision()).isEqualTo(Decision.REJECT);
    assertThat(result.processInstanceKey()).isEqualTo(PROCESS_INSTANCE_KEY);
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
      final @Nullable SuspensionBehavior behavior) {
    final var processor =
        mock(TypedRecordProcessor.class, withSettings().extraInterfaces(SuspensionAware.class));
    when(((SuspensionAware) processor).suspensionBehavior(any())).thenReturn(behavior);
    return processor;
  }
}
