/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.topology.changes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import io.camunda.zeebe.topology.state.ClusterConfiguration;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.state.PartitionState;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PartitionReconfigurePriorityApplierTest {

  private static final PartitionChangeExecutor FAILING_EXECUTOR =
      mock(PartitionChangeExecutor.class);

  @BeforeAll
  static void init() {
    when(FAILING_EXECUTOR.reconfigurePriority(anyInt(), anyInt()))
        .thenReturn(
            CompletableActorFuture.completedExceptionally(new RuntimeException("force failed")));
  }

  @Test
  void shouldFailPartitionReconfigurePriorityIfPartitionDoesNotExist() {
    // given
    final int partitionId = 5;
    final MemberId memberId = MemberId.from("1");
    final var partitionReconfigurePriorityApplier =
        new PartitionReconfigurePriorityApplier(partitionId, 1, memberId, null);
    final ClusterConfiguration clusterConfigurationWithMember =
        ClusterConfiguration.init()
            .addMember(
                memberId, MemberState.initializeAsActive(Map.of(1, PartitionState.active(1))));

    // when
    final var result = partitionReconfigurePriorityApplier.init(clusterConfigurationWithMember);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not have the partition");
  }

  @Test
  void shouldFailPartitionReconfigurePriorityIfPartitionIsNotActive() {
    // given
    final int partitionId = 5;
    final MemberId memberId = MemberId.from("1");
    final var partitionReconfigurePriorityApplier =
        new PartitionReconfigurePriorityApplier(partitionId, 1, memberId, null);
    final ClusterConfiguration clusterConfigurationWithMember =
        ClusterConfiguration.init()
            .addMember(
                memberId,
                MemberState.initializeAsActive(Map.of(partitionId, PartitionState.joining(1))));

    // when
    final var result = partitionReconfigurePriorityApplier.init(clusterConfigurationWithMember);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("has partition in state JOINING");
  }

  @Test
  void shouldNotChangeStateOnInit() {
    // given
    final int partitionId = 5;
    final MemberId memberId = MemberId.from("1");
    final var partitionReconfigurePriorityApplier =
        new PartitionReconfigurePriorityApplier(partitionId, 1, memberId, null);
    final ClusterConfiguration initialClusterConfiguration =
        ClusterConfiguration.init()
            .addMember(
                memberId,
                MemberState.initializeAsActive(Map.of(partitionId, PartitionState.active(1))));

    // when
    final var result = partitionReconfigurePriorityApplier.init(initialClusterConfiguration);

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get().apply(initialClusterConfiguration).getMember(memberId))
        .isEqualTo(initialClusterConfiguration.getMember(memberId));
  }

  @Test
  void shouldUpdatePriorityAfterApply() {
    // given
    final int partitionId = 5;
    final MemberId memberId = MemberId.from("1");
    final var partitionReconfigurePriorityApplier =
        new PartitionReconfigurePriorityApplier(
            partitionId, 3, memberId, new NoopPartitionChangeExecutor());
    final ClusterConfiguration initialClusterConfiguration =
        ClusterConfiguration.init()
            .addMember(
                memberId,
                MemberState.initializeAsActive(Map.of(partitionId, PartitionState.active(1))));

    // when
    final var newMemberState =
        partitionReconfigurePriorityApplier
            .apply()
            .join()
            .apply(initialClusterConfiguration)
            .getMember(memberId);

    // then
    assertThat(newMemberState.getPartition(partitionId).priority()).isEqualTo(3);
  }

  @Test
  void shouldFailApplyIfPartitionChangeExecutorFails() {
    // given
    final int partitionId = 5;
    final MemberId memberId = MemberId.from("1");
    final var partitionReconfigurePriorityApplier =
        new PartitionReconfigurePriorityApplier(partitionId, 3, memberId, FAILING_EXECUTOR);

    // when
    final var resultFuture = partitionReconfigurePriorityApplier.apply();

    // then
    assertThat(resultFuture)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("force failed");
  }
}
