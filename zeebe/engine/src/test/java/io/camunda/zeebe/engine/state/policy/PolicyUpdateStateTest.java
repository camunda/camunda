/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.policy;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutablePolicyUpdateState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class PolicyUpdateStateTest {

  private MutableProcessingState processingState;
  private MutablePolicyUpdateState policyUpdateState;

  @BeforeEach
  void setup() {
    policyUpdateState = processingState.getPolicyUpdateState();
  }

  @Test
  void shouldHaveNoCurrentVersionByDefault() {
    // then
    assertThat(policyUpdateState.getCurrentVersion()).isZero();
  }

  @Test
  void shouldStoreCurrentVersion() {
    // when
    policyUpdateState.setCurrentVersion(3);

    // then
    assertThat(policyUpdateState.getCurrentVersion()).isEqualTo(3);
  }

  @Test
  void shouldOpenAssemblyOnFirstChunk() {
    // given
    final var envelopeId = UUID.randomUUID().toString();

    // when
    policyUpdateState.appendChunk(chunk(envelopeId, 1, 2, 0));

    // then
    final var assembly = policyUpdateState.getAssembly(envelopeId).orElseThrow();
    assertThat(assembly.getPolicyVersion()).isEqualTo(1);
    assertThat(assembly.getChunkCount()).isEqualTo(2);
    assertThat(assembly.getReceivedCount()).isEqualTo(1);
    assertThat(policyUpdateState.hasChunk(envelopeId, 0)).isTrue();
    assertThat(policyUpdateState.hasChunk(envelopeId, 1)).isFalse();
  }

  @Test
  void shouldCountEveryAppendedChunk() {
    // given
    final var envelopeId = UUID.randomUUID().toString();
    policyUpdateState.appendChunk(chunk(envelopeId, 1, 2, 0));

    // when
    policyUpdateState.appendChunk(chunk(envelopeId, 1, 2, 1));

    // then
    assertThat(policyUpdateState.getAssembly(envelopeId).orElseThrow().getReceivedCount())
        .isEqualTo(2);
  }

  @Test
  void shouldFindTheOpenAssembly() {
    // given
    final var envelopeId = UUID.randomUUID().toString();
    policyUpdateState.appendChunk(chunk(envelopeId, 4, 3, 0));

    // when
    final var open = policyUpdateState.findOpenAssembly();

    // then
    assertThat(open).isPresent();
    assertThat(open.get().getEnvelopeId()).isEqualTo(envelopeId);
  }

  @Test
  void shouldRemoveAssemblyAndItsChunks() {
    // given
    final var envelopeId = UUID.randomUUID().toString();
    policyUpdateState.appendChunk(chunk(envelopeId, 1, 2, 0));
    policyUpdateState.appendChunk(chunk(envelopeId, 1, 2, 1));

    // when
    policyUpdateState.removeAssembly(envelopeId);

    // then
    assertThat(policyUpdateState.getAssembly(envelopeId)).isEmpty();
    assertThat(policyUpdateState.hasChunk(envelopeId, 0)).isFalse();
    assertThat(policyUpdateState.hasChunk(envelopeId, 1)).isFalse();
  }

  private static PolicyUpdateRecord chunk(
      final String envelopeId, final long version, final int count, final int index) {
    return new PolicyUpdateRecord()
        .setEnvelopeId(envelopeId)
        .setPolicyVersion(version)
        .setChunkCount(count)
        .setChunkIndex(index);
  }
}
