/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationAssert;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PartitionState.State;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class PartitionDemoteApplierTest {

  private final PartitionChangeExecutor partitionChangeExecutor =
      mock(PartitionChangeExecutor.class);
  private final MemberId localMemberId = MemberId.from("1");
  private final MemberId otherMemberId = MemberId.from("2");
  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();
  private final PartitionDemoteApplier applier =
      new PartitionDemoteApplier(1, localMemberId, partitionChangeExecutor);

  private ClusterConfiguration configurationWith(
      final PartitionState localPartition, final PartitionState otherPartition) {
    return ClusterConfiguration.init()
        .addMember(localMemberId, MemberState.initializeAsActive(Map.of(1, localPartition)))
        .addMember(otherMemberId, MemberState.initializeAsActive(Map.of(1, otherPartition)));
  }

  @Test
  void shouldRejectDemoteIfLocalMemberIsNotInCluster() {
    // given
    final var configuration =
        ClusterConfiguration.init()
            .addMember(
                otherMemberId,
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, partitionConfig))));

    // when
    final var result = applier.init(configuration);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("the local member does not exist in the cluster");
  }

  @Test
  void shouldRejectDemoteIfLocalMemberDoesNotHavePartition() {
    // given
    final var configuration =
        ClusterConfiguration.init()
            .addMember(localMemberId, MemberState.initializeAsActive(Map.of()));

    // when
    final var result = applier.init(configuration);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("the local member does not have the partition");
  }

  @Test
  void shouldRejectDemoteIfNoOtherReplicaExists() {
    // given - demoting the only replica would leave a non-empty replication group without any
    // voting member, which can neither elect a leader nor commit
    final var configuration =
        ClusterConfiguration.init()
            .addMember(
                localMemberId,
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, partitionConfig))));

    // when
    final var result = applier.init(configuration);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no other member has the partition in active state");
  }

  @Test
  void shouldRejectDemoteIfOtherReplicasAreNotActive() {
    // given - the other replica is still a learner, so it cannot vote either
    final var configuration =
        configurationWith(
            PartitionState.active(1, partitionConfig),
            PartitionState.joining(1, partitionConfig).toLearner());

    // when
    final var result = applier.init(configuration);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no other member has the partition in active state");
  }

  @Test
  void shouldNotFailOnInitIfPartitionIsAlreadyLeaving() {
    // given - restart-safe retry
    final var configuration =
        ClusterConfiguration.init()
            .addMember(
                localMemberId,
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, partitionConfig).toLeaving())));

    // when
    final var result = applier.init(configuration);

    // then
    assertThat(result).isRight();
  }

  @Test
  void shouldMarkPartitionAsLeaving() {
    // given
    final var configuration =
        configurationWith(
            PartitionState.active(1, partitionConfig), PartitionState.active(1, partitionConfig));

    // when
    final var resultingConfiguration = applier.init(configuration).get().apply(configuration);

    // then
    ClusterConfigurationAssert.assertThatClusterTopology(resultingConfiguration)
        .member(localMemberId)
        .hasPartitionWithState(1, State.LEAVING);
  }

  @Test
  void shouldExecuteDemoteCallbackAndKeepPartitionLeaving() {
    // given - the demotion does not remove the partition; the subsequent leave operation does
    final var configuration =
        configurationWith(
            PartitionState.active(1, partitionConfig), PartitionState.active(1, partitionConfig));
    final var configurationAfterInit = applier.init(configuration).get().apply(configuration);
    when(partitionChangeExecutor.demote(anyInt()))
        .thenReturn(CompletableActorFuture.completed(null));

    // when
    final var resultingConfiguration = applier.apply().join().apply(configurationAfterInit);

    // then
    verify(partitionChangeExecutor, times(1)).demote(1);
    ClusterConfigurationAssert.assertThatClusterTopology(resultingConfiguration)
        .member(localMemberId)
        .hasPartitionWithState(1, State.LEAVING);
  }
}
