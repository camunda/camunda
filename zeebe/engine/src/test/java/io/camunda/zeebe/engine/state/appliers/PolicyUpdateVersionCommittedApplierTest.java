/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.immutable.PolicyState.CommittedPolicy;
import io.camunda.zeebe.engine.state.mutable.MutablePolicyState;
import io.camunda.zeebe.engine.state.mutable.MutablePolicyUpdateState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class PolicyUpdateVersionCommittedApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutablePolicyState policyState;
  private MutablePolicyUpdateState policyUpdateState;
  private PolicyUpdateVersionCommittedApplier applier;

  @BeforeEach
  void setup() {
    policyState = processingState.getPolicyState();
    policyUpdateState = processingState.getPolicyUpdateState();
    applier = new PolicyUpdateVersionCommittedApplier(policyState, policyUpdateState);
  }

  @Test
  void shouldCommitThePolicyVersion() {
    // given
    final var lastChunk = chunk("update-1", 0, 1);

    // when
    applier.applyState(1L, lastChunk);

    // then
    assertThat(policyState.getCurrentPolicy()).contains(new CommittedPolicy("policy-1", 2L, 42L));
  }

  @Test
  void shouldRemoveEveryBufferedUpdateAndItsChunks() {
    // given the applied chunks of the committed update are still buffered, and so is a chunk of an
    // update that never completed
    policyUpdateState.bufferChunk(chunk("update-1", 0, 2));
    policyUpdateState.bufferChunk(chunk("update-1", 1, 2));
    policyUpdateState.bufferChunk(chunk("update-0", 0, 3));

    // when
    applier.applyState(1L, chunk("update-1", 1, 2));

    // then
    assertThat(policyUpdateState.getUpdate("update-1")).isEmpty();
    assertThat(policyUpdateState.getChunk("update-1", 0)).isEmpty();
    assertThat(policyUpdateState.getChunk("update-1", 1)).isEmpty();
    assertThat(policyUpdateState.getUpdate("update-0")).isEmpty();
    assertThat(policyUpdateState.getChunk("update-0", 0)).isEmpty();
  }

  private static PolicyUpdateRecord chunk(
      final String updateId, final int chunkIndex, final int chunkCount) {
    return new PolicyUpdateRecord()
        .setPolicyId("policy-1")
        .setPolicyVersion(2L)
        .setBasePolicyVersion(1L)
        .setSeqNo(42L)
        .setUpdateId(updateId)
        .setChunkIndex(chunkIndex)
        .setChunkCount(chunkCount);
  }
}
