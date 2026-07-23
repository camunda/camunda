/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.RestoreChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

final class PartitionPreRestoreApplierTest {

  private static final MemberId MEMBER_ID = MemberId.from("1");
  private static final int PARTITION_ID = 1;

  @Test
  void shouldFailInitWhenMemberNotActiveInCluster() {
    // given
    final var applier =
        new PartitionPreRestoreApplier(
            MEMBER_ID, PARTITION_ID, new SucceedingRestoreChangeExecutor());

    // when
    final var result =
        applier.init(GlobalConfiguration.init(), PartitionGroupConfiguration.empty(1));

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldFailInitWhenMemberNotInPartitionGroup() {
    // given
    final var applier =
        new PartitionPreRestoreApplier(
            MEMBER_ID, PARTITION_ID, new SucceedingRestoreChangeExecutor());
    final var globalConfig =
        GlobalConfiguration.init().addMember(MEMBER_ID, BrokerState.initializeAsActive());

    // when
    final var result = applier.init(globalConfig, PartitionGroupConfiguration.empty(1));

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not part of this partition group");
  }

  @Test
  void shouldFailInitWhenMemberIsNotRecovering() {
    // given
    final var applier =
        new PartitionPreRestoreApplier(
            MEMBER_ID, PARTITION_ID, new SucceedingRestoreChangeExecutor());
    final var globalConfig =
        GlobalConfiguration.init().addMember(MEMBER_ID, BrokerState.initializeAsActive());
    final var partitionState =
        Map.of(PARTITION_ID, PartitionState.active(1, DynamicPartitionConfig.init()));
    final var groupConfig =
        PartitionGroupConfiguration.empty(1)
            .addMember(
                MEMBER_ID,
                new BrokerPartitionState(0, Instant.MIN, partitionState, Mode.PROCESSING));

    // when
    final var result = applier.init(globalConfig, groupConfig);

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
    final var globalConfig =
        GlobalConfiguration.init().addMember(MEMBER_ID, BrokerState.initializeAsActive());
    final var groupConfig =
        PartitionGroupConfiguration.empty(1)
            .addMember(
                MEMBER_ID, new BrokerPartitionState(0, Instant.MIN, Map.of(), Mode.RECOVERING));

    // when
    final var result = applier.init(globalConfig, groupConfig);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not replicate that partition");
  }

  @Test
  void shouldDelegateToExecutorAndLeaveGroupConfigurationUnchanged() {
    // given
    final var executor = new SucceedingRestoreChangeExecutor();
    final var applier = new PartitionPreRestoreApplier(MEMBER_ID, PARTITION_ID, executor);

    // when
    final var result = applier.apply();

    // then
    assertThat(result).succeedsWithin(Duration.ofMillis(100));
    assertThat(executor.invokedPartitionIds).containsExactly(PARTITION_ID);
  }

  @Test
  void shouldFailApplyWhenExecutorFails() {
    // given
    final var applier =
        new PartitionPreRestoreApplier(
            MEMBER_ID, PARTITION_ID, new RestoreChangeExecutor.DeniedRestoreChangeExecutor());

    // when
    final var result = applier.apply();

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
