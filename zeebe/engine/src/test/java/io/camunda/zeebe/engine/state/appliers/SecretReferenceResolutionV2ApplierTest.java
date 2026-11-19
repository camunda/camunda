/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.immutable.ProbeState.State;
import io.camunda.zeebe.engine.state.mutable.MutableProbeState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableSecretReferenceState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The parking of probes by the version 2 appliers of {@code RESOLUTION_REQUESTED} and {@code
 * RESOLUTION_COMPLETED}. Version 1 handles jobs only and is covered by its own tests.
 */
@ExtendWith(ProcessingStateExtension.class)
final class SecretReferenceResolutionV2ApplierTest {

  private static final String STORE = "default";

  private MutableProcessingState processingState;
  private MutableProbeState probeState;
  private MutableSecretReferenceState secretReferenceState;
  private SecretReferenceResolutionRequestedV2Applier requestedApplier;
  private SecretReferenceResolutionCompletedV2Applier completedApplier;

  @BeforeEach
  void setUp() {
    probeState = processingState.getProbeState();
    secretReferenceState = processingState.getSecretReferenceState();
    requestedApplier =
        new SecretReferenceResolutionRequestedV2Applier(
            secretReferenceState, processingState.getJobState(), probeState);
    completedApplier =
        new SecretReferenceResolutionCompletedV2Applier(secretReferenceState, probeState);
    probeState.create(1L, new ProbeRecord().setProbeId("probe-a").setDeadline(1_000));
  }

  @Test
  void shouldMarkTheReferencePendingAndParkTheProbes() {
    // when
    requestedApplier.applyState(10L, reference("token").addProbeKey(1L));

    // then
    assertThat(secretReferenceState.isPending(STORE, "token")).isTrue();
    assertThat(probeState.getState(1L)).isEqualTo(State.WAITING_FOR_SECRET_RESOLUTION);
  }

  @Test
  void shouldMakeTheProbeActivatableWhenItsLastReferenceResolves() {
    // given
    requestedApplier.applyState(10L, reference("token").addProbeKey(1L));

    // when
    completedApplier.applyState(10L, reference("token"));

    // then
    assertThat(secretReferenceState.isPending(STORE, "token")).isFalse();
    assertThat(probeState.getState(1L)).isEqualTo(State.ACTIVATABLE);
  }

  @Test
  void shouldKeepTheProbeParkedWhileAnotherReferenceIsPending() {
    // given
    requestedApplier.applyState(10L, reference("token").addProbeKey(1L));
    requestedApplier.applyState(11L, reference("key").addProbeKey(1L));

    // when
    completedApplier.applyState(10L, reference("token"));

    // then
    assertThat(probeState.getState(1L)).isEqualTo(State.WAITING_FOR_SECRET_RESOLUTION);

    // when - the other one resolves too
    completedApplier.applyState(11L, reference("key"));

    // then
    assertThat(probeState.getState(1L)).isEqualTo(State.ACTIVATABLE);
  }

  private static SecretReferenceRecord reference(final String secretReference) {
    return new SecretReferenceRecord().setStoreId(STORE).setSecretReference(secretReference);
  }
}
