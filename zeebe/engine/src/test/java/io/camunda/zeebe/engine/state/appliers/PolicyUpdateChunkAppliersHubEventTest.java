/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.immutable.HubEventState.HubEvent;
import io.camunda.zeebe.engine.state.immutable.HubEventState.HubEventStatus;
import io.camunda.zeebe.engine.state.immutable.HubEventState.HubEventType;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The chunk and cancel appliers track how far the Hub event of an update is handled, so that a new
 * leader of partition 1 can tell an update in progress from one that is finished.
 */
@ExtendWith(ProcessingStateExtension.class)
final class PolicyUpdateChunkAppliersHubEventTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  @Test
  void shouldMarkTheHubEventAsBufferingOnceTheFirstChunkIsAppended() {
    // given
    final var applier =
        new PolicyUpdateChunkAppendedApplier(
            processingState.getPolicyUpdateState(), processingState.getHubEventState());

    // when
    applier.applyState(1L, chunk(0));

    // then
    assertThat(processingState.getHubEventState().getLast())
        .contains(new HubEvent(42L, HubEventType.POLICY_UPDATE, HubEventStatus.BUFFERING, null));
    assertThat(processingState.getHubEventState().getLastFinished()).isEmpty();
  }

  @Test
  void shouldMarkTheHubEventAsApplyingOnceTheFirstChunkIsApplied() {
    // given
    new PolicyUpdateChunkAppendedApplier(
            processingState.getPolicyUpdateState(), processingState.getHubEventState())
        .applyState(1L, chunk(0));
    final var applier = new PolicyUpdateChunkAppliedApplier(processingState.getHubEventState());

    // when
    applier.applyState(2L, chunk(0));

    // then
    assertThat(processingState.getHubEventState().getLast())
        .contains(new HubEvent(42L, HubEventType.POLICY_UPDATE, HubEventStatus.APPLYING, null));
    assertThat(processingState.getHubEventState().getLastFinished()).isEmpty();
  }

  @Test
  void shouldMarkTheHubEventAsCanceledWithTheReasonOnceTheUpdateIsCanceled() {
    // given
    new PolicyUpdateChunkAppendedApplier(
            processingState.getPolicyUpdateState(), processingState.getHubEventState())
        .applyState(1L, chunk(0));
    final var applier =
        new PolicyUpdateCanceledApplier(
            processingState.getPolicyUpdateState(), processingState.getHubEventState());

    // when
    applier.applyState(2L, chunk(0).setErrorMessage("hangs after a leader change"));

    // then
    assertThat(processingState.getHubEventState().getLast())
        .contains(
            new HubEvent(
                42L,
                HubEventType.POLICY_UPDATE,
                HubEventStatus.CANCELED,
                "hangs after a leader change"));
    assertThat(processingState.getHubEventState().getLastFinished()).isEmpty();
    assertThat(processingState.getPolicyUpdateState().getOpenUpdate()).isEmpty();
  }

  private static PolicyUpdateRecord chunk(final int chunkIndex) {
    return new PolicyUpdateRecord()
        .setPolicyId("policy-1")
        .setPolicyVersion(2L)
        .setBasePolicyVersion(1L)
        .setSeqNo(42L)
        .setUpdateId("update-1")
        .setChunkIndex(chunkIndex)
        .setChunkCount(1);
  }
}
