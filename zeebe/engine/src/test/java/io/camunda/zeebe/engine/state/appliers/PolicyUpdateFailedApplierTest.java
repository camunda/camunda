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
import io.camunda.zeebe.engine.state.mutable.MutablePolicyUpdateState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class PolicyUpdateFailedApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutablePolicyUpdateState policyUpdateState;
  private PolicyUpdateFailedApplier applier;

  @BeforeEach
  void setup() {
    policyUpdateState = processingState.getPolicyUpdateState();
    applier = new PolicyUpdateFailedApplier(policyUpdateState, processingState.getHubEventState());
  }

  @Test
  void shouldRemoveTheFailedUpdateAndItsChunks() {
    // given
    policyUpdateState.bufferChunk(1L, chunk(0, 2));

    // when
    applier.applyState(2L, chunk(1, 2).setErrorMessage("chunk 1 skipped"));

    // then
    assertThat(policyUpdateState.getOpenUpdate()).isEmpty();
    assertThat(policyUpdateState.getChunk("update-1", 0)).isEmpty();
  }

  @Test
  void shouldMarkTheHubEventOfTheUpdateAsRejectedWithTheReason() {
    // given
    policyUpdateState.bufferChunk(1L, chunk(0, 2));

    // when
    applier.applyState(2L, chunk(1, 2).setErrorMessage("chunk 1 skipped"));

    // then
    assertThat(processingState.getHubEventState().getLastFinished())
        .contains(
            new HubEvent(
                42L, HubEventType.POLICY_UPDATE, HubEventStatus.REJECTED, "chunk 1 skipped"));
    assertThat(processingState.getHubEventState().getLastAppliedSeqNo()).isEmpty();
  }

  private static PolicyUpdateRecord chunk(final int chunkIndex, final int chunkCount) {
    return new PolicyUpdateRecord()
        .setPolicyId("policy-1")
        .setPolicyVersion(2L)
        .setBasePolicyVersion(1L)
        .setSeqNo(42L)
        .setUpdateId("update-1")
        .setChunkIndex(chunkIndex)
        .setChunkCount(chunkCount);
  }
}
