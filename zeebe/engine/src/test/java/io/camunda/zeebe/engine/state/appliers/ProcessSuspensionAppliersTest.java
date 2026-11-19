/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.deployment.ProcessStateTest;
import io.camunda.zeebe.engine.state.mutable.MutableProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class ProcessSuspensionAppliersTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableProcessState processState;
  private ProcessSuspendedApplier suspendedApplier;
  private ProcessResumedApplier resumedApplier;

  @BeforeEach
  void setUp() {
    processState = processingState.getProcessState();
    suspendedApplier = new ProcessSuspendedApplier(processState);
    resumedApplier = new ProcessResumedApplier(processState);
  }

  @Test
  void shouldMarkOnlyTheVersionOfTheEventAsSuspended() {
    // given
    final var v1 = ProcessStateTest.creatingProcessRecord(processingState, "processId", 1);
    final var v2 = ProcessStateTest.creatingProcessRecord(processingState, "processId", 2);
    processState.putProcess(v1.getKey(), v1);
    processState.putProcess(v2.getKey(), v2);

    // when
    suspendedApplier.applyState(1L, v1);

    // then
    assertThat(processState.isSuspended(v1.getKey())).isTrue();
    assertThat(processState.isSuspended(v2.getKey())).isFalse();
  }

  @Test
  void shouldRemoveMarkerOnlyForTheVersionOfTheEvent() {
    // given
    final var v1 = ProcessStateTest.creatingProcessRecord(processingState, "processId", 1);
    final var v2 = ProcessStateTest.creatingProcessRecord(processingState, "processId", 2);
    processState.putProcess(v1.getKey(), v1);
    processState.putProcess(v2.getKey(), v2);
    processState.markSuspended(v1);
    processState.markSuspended(v2);

    // when
    resumedApplier.applyState(1L, v1);

    // then
    assertThat(processState.isSuspended(v1.getKey())).isFalse();
    assertThat(processState.isSuspended(v2.getKey())).isTrue();
  }

  @Test
  void shouldBeIdempotentWhenTheEventIsAppliedTwice() {
    // given
    final var v1 = ProcessStateTest.creatingProcessRecord(processingState, "processId", 1);
    processState.putProcess(v1.getKey(), v1);

    // when
    suspendedApplier.applyState(1L, v1);
    suspendedApplier.applyState(2L, v1);
    final var suspended = processState.isSuspended(v1.getKey());
    resumedApplier.applyState(3L, v1);
    resumedApplier.applyState(4L, v1);

    // then
    assertThat(suspended).isTrue();
    assertThat(processState.isSuspended(v1.getKey())).isFalse();
  }
}
