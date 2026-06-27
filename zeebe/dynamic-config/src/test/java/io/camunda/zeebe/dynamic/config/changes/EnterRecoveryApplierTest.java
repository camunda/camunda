/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.ModeChangeExecutor.NoopModeChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.MemberState.State;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

final class EnterRecoveryApplierTest {

  private static final MemberId MEMBER_ID = MemberId.from("1");

  private final ModeChangeExecutor noopExecutor = new NoopModeChangeExecutor();
  private final ModeChangeExecutor failingExecutor =
      new ModeChangeExecutor() {
        @Override
        public CompletableActorFuture<Void> enterRecovery() {
          return CompletableActorFuture.completedExceptionally(
              new RuntimeException("Force failure"));
        }

        @Override
        public CompletableActorFuture<Void> exitRecovery() {
          return CompletableActorFuture.completedExceptionally(
              new RuntimeException("Force failure"));
        }
      };

  @Test
  void shouldFailInitWhenMemberNotInCluster() {
    // given
    final var applier = new EnterRecoveryApplier(MEMBER_ID, noopExecutor);
    final var config = ClusterConfiguration.init();

    // when
    final var result = applier.initMemberState(config);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("member is not part of the cluster");
  }

  @Test
  void shouldFailInitWhenMemberIsNotActive() {
    // given
    final var applier = new EnterRecoveryApplier(MEMBER_ID, noopExecutor);
    final var config =
        ClusterConfiguration.init().addMember(MEMBER_ID, MemberState.uninitialized().toJoining());

    // when
    final var result = applier.initMemberState(config);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("the member is not part of the cluster");
  }

  @Test
  void shouldSucceedAsNoOpWhenMemberIsAlreadyRecovering() {
    // given — member is already in recovery mode
    final var applier = new EnterRecoveryApplier(MEMBER_ID, noopExecutor);
    final var recoveringMember = MemberState.initializeAsActive(Map.of()).toRecovering();
    final var config = ClusterConfiguration.init().addMember(MEMBER_ID, recoveringMember);

    // when
    final var result = applier.initMemberState(config);

    // then — transition is a no-op: it succeeds but leaves the member untouched
    EitherAssert.assertThat(result).isRight();
    final var updatedMember = result.get().apply(recoveringMember);
    assertThat(updatedMember).isEqualTo(recoveringMember);
    assertThat(updatedMember.state()).isEqualTo(State.RECOVERING);
  }

  @Test
  void shouldLeaveMemberStateUnchangedOnInitWhenMemberIsActive() {
    // given
    final var applier = new EnterRecoveryApplier(MEMBER_ID, noopExecutor);
    final var activeMember = MemberState.initializeAsActive(Map.of());
    final var config = ClusterConfiguration.init().addMember(MEMBER_ID, activeMember);

    // when
    final var result = applier.initMemberState(config);

    // then — init does not transition the state; the flip to RECOVERING happens on apply
    EitherAssert.assertThat(result).isRight();
    final var updatedMember = result.get().apply(activeMember);
    assertThat(updatedMember.state()).isEqualTo(State.ACTIVE);
  }

  @Test
  void shouldCompleteApplySuccessfully() {
    // given
    final var applier = new EnterRecoveryApplier(MEMBER_ID, noopExecutor);

    // when
    final var result = applier.applyOperation();

    // then
    assertThat(result).succeedsWithin(Duration.ofMillis(100));
  }

  @Test
  void shouldSetStateToRecoveringOnApply() {
    // given
    final var applier = new EnterRecoveryApplier(MEMBER_ID, noopExecutor);
    final var activeMember = MemberState.initializeAsActive(Map.of());

    // when
    final var transformer = applier.applyOperation().join();
    final var updatedMember = transformer.apply(activeMember);

    // then
    assertThat(updatedMember.state()).isEqualTo(State.RECOVERING);
  }

  @Test
  void shouldFailApplyWhenExecutorFails() {
    // given
    final var applier = new EnterRecoveryApplier(MEMBER_ID, failingExecutor);

    // when
    final var result = applier.applyOperation();

    // then
    assertThat(result)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("Force failure");
  }
}
