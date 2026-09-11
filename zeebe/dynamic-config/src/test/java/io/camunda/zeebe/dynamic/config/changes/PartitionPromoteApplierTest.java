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

final class PartitionPromoteApplierTest {

  private final PartitionChangeExecutor partitionChangeExecutor =
      mock(PartitionChangeExecutor.class);
  private final MemberId localMemberId = MemberId.from("1");
  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();
  private final PartitionPromoteApplier applier =
      new PartitionPromoteApplier(1, localMemberId, partitionChangeExecutor);

  private PartitionState learner() {
    return PartitionState.joining(1, partitionConfig).toLearner();
  }

  private ClusterConfiguration configurationWithLocalPartition(final PartitionState partition) {
    return ClusterConfiguration.init()
        .addMember(localMemberId, MemberState.initializeAsActive(Map.of(1, partition)));
  }

  @Test
  void shouldRejectPromoteIfLocalMemberIsNotActiveInCluster() {
    // given
    final var configuration =
        ClusterConfiguration.init()
            .addMember(localMemberId, MemberState.uninitialized().toJoining());

    // when
    final var result = applier.init(configuration);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not an active member of the cluster");
  }

  @Test
  void shouldRejectPromoteIfLocalMemberDoesNotHavePartition() {
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
  void shouldRejectPromoteIfPartitionIsNotALearner() {
    // given - the partition is still JOINING, so the join operation has not completed yet
    final var configuration =
        configurationWithLocalPartition(PartitionState.joining(1, partitionConfig));

    // when
    final var result = applier.init(configuration);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("the partition is in state JOINING");
  }

  @Test
  void shouldNotFailOnInitIfPartitionIsAlreadyActive() {
    // given - the node restarted after the promotion completed but before recording the operation
    final var configuration =
        configurationWithLocalPartition(PartitionState.active(1, partitionConfig));

    // when
    final var result = applier.init(configuration);

    // then
    assertThat(result).isRight();
  }

  @Test
  void shouldExecutePromoteCallbackAndMarkPartitionActive() {
    // given
    final var configuration = configurationWithLocalPartition(learner());
    final var configurationAfterInit = applier.init(configuration).get().apply(configuration);
    when(partitionChangeExecutor.promote(anyInt()))
        .thenReturn(CompletableActorFuture.completed(null));

    // when
    final var resultingConfiguration = applier.apply().join().apply(configurationAfterInit);

    // then
    verify(partitionChangeExecutor, times(1)).promote(1);
    ClusterConfigurationAssert.assertThatClusterTopology(resultingConfiguration)
        .member(localMemberId)
        .hasPartitionWithState(1, State.ACTIVE);
  }

  @Test
  void shouldFailApplyWhilePromotionIsRejected() {
    // given - the member is not caught up yet, so the leader rejects the promotion; the
    // configuration manager retries apply() with backoff until the catch-up gate accepts
    final var configuration = configurationWithLocalPartition(learner());
    applier.init(configuration);
    when(partitionChangeExecutor.promote(anyInt()))
        .thenReturn(
            CompletableActorFuture.completedExceptionally(
                new RuntimeException("not caught up yet")));

    // when
    final var result = applier.apply();

    // then
    Assertions.assertThat(result.isCompletedExceptionally()).isTrue();
  }
}
