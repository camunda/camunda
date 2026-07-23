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
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

final class PartitionPreRestoreApplierTest {

  private static final MemberId MEMBER_ID = MemberId.from("1");
  private static final int PARTITION_ID = 1;

  @Test
  void shouldFailInitWhenMemberNotInCluster() {
    // given
    final var applier =
        new PartitionPreRestoreApplier(
            MEMBER_ID, PARTITION_ID, new SucceedingRestoreChangeExecutor());

    // when
    final var result = applier.initMemberState(ClusterConfiguration.init());

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldFailInitWhenMemberIsNotRecovering() {
    // given
    final var applier =
        new PartitionPreRestoreApplier(
            MEMBER_ID, PARTITION_ID, new SucceedingRestoreChangeExecutor());
    final var config =
        ClusterConfiguration.init()
            .addMember(
                MEMBER_ID,
                MemberState.initializeAsActive(
                    Map.of(PARTITION_ID, PartitionState.active(1, DynamicPartitionConfig.init()))));

    // when
    final var result = applier.initMemberState(config);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not in recovery");
  }

  @Test
  void shouldFailInitWhenMemberDoesNotReplicatePartition() {
    // given
    final var applier =
        new PartitionPreRestoreApplier(
            MEMBER_ID, PARTITION_ID, new SucceedingRestoreChangeExecutor());
    final var recoveringMember = MemberState.initializeAsActive(Map.of()).toRecovering();
    final var config = ClusterConfiguration.init().addMember(MEMBER_ID, recoveringMember);

    // when
    final var result = applier.initMemberState(config);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not replicate that partition");
  }

  @Test
  void shouldDelegateToExecutorAndLeaveMemberStateUnchanged() {
    // given
    final var executor = new SucceedingRestoreChangeExecutor();
    final var applier = new PartitionPreRestoreApplier(MEMBER_ID, PARTITION_ID, executor);
    final var recoveringMember =
        MemberState.initializeAsActive(
                Map.of(PARTITION_ID, PartitionState.active(1, DynamicPartitionConfig.init())))
            .toRecovering();

    // when
    final var result = applier.applyOperation();

    // then
    assertThat(result).succeedsWithin(Duration.ofMillis(100));
    assertThat(result.join().apply(recoveringMember)).isEqualTo(recoveringMember);
    assertThat(executor.invokedPartitionIds).containsExactly(PARTITION_ID);
  }

  @Test
  void shouldFailApplyWhenExecutorFails() {
    // given
    final var applier =
        new PartitionPreRestoreApplier(
            MEMBER_ID, PARTITION_ID, new RestoreChangeExecutor.DeniedRestoreChangeExecutor());

    // when
    final var result = applier.applyOperation();

    // then
    assertThat(result)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("only supported while the broker is in recovery mode");
  }

  private static final class SucceedingRestoreChangeExecutor implements RestoreChangeExecutor {
    private final List<Integer> invokedPartitionIds = new ArrayList<>();

    @Override
    public ActorFuture<Void> preRestore(final int partitionId) {
      invokedPartitionIds.add(partitionId);
      return CompletableActorFuture.completed(null);
    }
  }
}
