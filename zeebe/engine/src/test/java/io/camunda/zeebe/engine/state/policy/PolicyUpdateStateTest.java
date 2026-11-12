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
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class PolicyUpdateStateTest {

  private MutableProcessingState processingState;
  private MutablePolicyUpdateState state;

  @BeforeEach
  void setUp() {
    state = processingState.getPolicyUpdateState();
  }

  @Test
  void shouldHaveNoUpdateBeforeItsFirstChunk() {
    // when
    final var openUpdate = state.getUpdate("update-1");

    // then
    assertThat(openUpdate).isEmpty();
  }

  @Test
  void shouldOpenTheUpdateWithItsFirstChunk() {
    // given
    final var chunk = chunk("update-1", 1, 3).setRoles(List.<RoleRecordValue>of(role("role-1")));

    // when
    state.bufferChunk(chunk);

    // then
    final var openUpdate = state.getUpdate("update-1").orElseThrow();
    assertThat(openUpdate.getBufferedChunkCount()).isOne();
    assertThat(openUpdate.hasAllChunksBuffered()).isFalse();
    final var update = openUpdate.getUpdate();
    assertThat(update.getPolicyId()).isEqualTo("policy-1");
    assertThat(update.getPolicyVersion()).isEqualTo(2L);
    assertThat(update.getBasePolicyVersion()).isEqualTo(1L);
    assertThat(update.getSeqNo()).isEqualTo(42L);
    assertThat(update.getUpdateId()).isEqualTo("update-1");
    assertThat(update.getChunkCount()).isEqualTo(3);
    assertThat(update.getRoles()).isEmpty();
  }

  @Test
  void shouldCompleteTheUpdateWhenEveryChunkIsBuffered() {
    // given
    state.bufferChunk(chunk("update-1", 2, 3));
    state.bufferChunk(chunk("update-1", 0, 3));

    // when
    state.bufferChunk(chunk("update-1", 1, 3));

    // then
    final var openUpdate = state.getUpdate("update-1").orElseThrow();
    assertThat(openUpdate.getBufferedChunkCount()).isEqualTo(3);
    assertThat(openUpdate.hasAllChunksBuffered()).isTrue();
  }

  @Test
  void shouldReplaceAChunkThatIsBufferedAgainWithoutCountingItTwice() {
    // given
    state.bufferChunk(chunk("update-1", 0, 2).setRoles(List.<RoleRecordValue>of(role("role-a"))));

    // when
    state.bufferChunk(chunk("update-1", 0, 2).setRoles(List.<RoleRecordValue>of(role("role-b"))));

    // then
    final var openUpdate = state.getUpdate("update-1").orElseThrow();
    assertThat(openUpdate.getBufferedChunkCount()).isOne();
    assertThat(openUpdate.hasAllChunksBuffered()).isFalse();
    assertThat(state.getChunk("update-1", 0).orElseThrow().getRoles())
        .extracting(RoleRecordValue::getRoleId)
        .containsExactly("role-b");
  }

  @Test
  void shouldReturnABufferedChunkWithItsPolicyData() {
    // given
    state.bufferChunk(chunk("update-1", 0, 2).setRoles(List.<RoleRecordValue>of(role("role-0"))));
    state.bufferChunk(chunk("update-1", 1, 2).setRoles(List.<RoleRecordValue>of(role("role-1"))));

    // when
    final var chunk = state.getChunk("update-1", 1);

    // then
    assertThat(chunk).isPresent();
    assertThat(chunk.get().getChunkIndex()).isOne();
    assertThat(chunk.get().getRoles())
        .extracting(RoleRecordValue::getRoleId)
        .containsExactly("role-1");
  }

  @Test
  void shouldNotReturnAChunkThatIsNotBuffered() {
    // given
    state.bufferChunk(chunk("update-1", 0, 2));

    // when
    final var chunk = state.getChunk("update-1", 1);

    // then
    assertThat(chunk).isEmpty();
  }

  @Test
  void shouldRemoveAllUpdatesAndTheirChunks() {
    // given
    state.bufferChunk(chunk("update-1", 0, 2));
    state.bufferChunk(chunk("update-1", 1, 2));
    state.bufferChunk(chunk("update-10", 0, 1));

    // when
    state.removeAll();

    // then
    assertThat(state.getUpdate("update-1")).isEmpty();
    assertThat(state.getUpdate("update-10")).isEmpty();
    assertThat(state.getChunk("update-1", 0)).isEmpty();
    assertThat(state.getChunk("update-1", 1)).isEmpty();
    assertThat(state.getChunk("update-10", 0)).isEmpty();
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

  private static RoleRecord role(final String roleId) {
    return new RoleRecord().setRoleId(roleId).setName(roleId);
  }
}
