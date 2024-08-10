/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationAssert;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.scheduler.testing.TestActorFuture;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class PartitionForceReconfigureApplierTest {
  private final PartitionChangeExecutor partitionChangeExecutor =
      mock(PartitionChangeExecutor.class);
  private final MemberId localMemberId = MemberId.from("1");
  private final MemberId otherMember = MemberId.from("2");

  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();
  private final ClusterConfiguration validTopology =
      ClusterConfiguration.init()
          .addMember(localMemberId, MemberState.initializeAsActive(Map.of()))
          .updateMember(
              localMemberId, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
          .addMember(otherMember, MemberState.initializeAsActive(Map.of()))
          .updateMember(
              otherMember, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)));

  @Test
  void shouldRejectIfLocalMemberNotPartOfNewConfiguration() {
    // when
    final var result =
        new PartitionForceReconfigureApplier(
                1, localMemberId, Set.of(otherMember), partitionChangeExecutor)
            .init(validTopology);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("member is not part of the new configuration");
  }

  @Test
  void shouldRejectIfNewConfigurationIsEmpty() {
    // when
    final var result =
        new PartitionForceReconfigureApplier(1, localMemberId, Set.of(), partitionChangeExecutor)
            .init(validTopology);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("the new configuration is empty");
  }

  @Test
  void shouldRejectIfMembersDoNotHavePartitionsAlready() {
    // given
    final var topologyWithMembersWithoutPartition =
        validTopology.updateMember(localMemberId, m -> m.removePartition(1));

    // when
    final var result =
        new PartitionForceReconfigureApplier(
                1, localMemberId, Set.of(localMemberId), partitionChangeExecutor)
            .init(topologyWithMembersWithoutPartition);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("member '1' does not have the partition");
  }

  @Test
  void shouldRejectIfMembersAreNotActive() {
    // given
    final var topologyWithMembersNotActive =
        validTopology.updateMember(localMemberId, MemberState::toLeaving);

    // when
    final var result =
        new PartitionForceReconfigureApplier(
                1, localMemberId, Set.of(localMemberId), partitionChangeExecutor)
            .init(topologyWithMembersNotActive);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("member '1' is not active");
  }

  @Test
  void shouldInitSuccessfullyWithoutChangingTopology() {
    // when
    final var result =
        new PartitionForceReconfigureApplier(
                1, localMemberId, Set.of(localMemberId), partitionChangeExecutor)
            .init(validTopology);

    // then
    assertThat(result).isRight();
    Assertions.assertThat(result.get().apply(validTopology)).isEqualTo(validTopology);
  }

  @Test
  void shouldFailFutureIfApplyFailed() {
    // given
    when(partitionChangeExecutor.forceReconfigure(anyInt(), any()))
        .thenReturn(TestActorFuture.failedFuture(new RuntimeException("force fail")));

    // when
    final PartitionForceReconfigureApplier partitionForceReconfigureApplier =
        new PartitionForceReconfigureApplier(
            1, localMemberId, Set.of(localMemberId), partitionChangeExecutor);
    partitionForceReconfigureApplier.init(validTopology);
    final var applyFuture = partitionForceReconfigureApplier.apply();

    // then
    Assertions.assertThat(applyFuture)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("force fail");
  }

  @Test
  void shouldRemovePartitionFromNonMembersIfApplySucceeded() {
    // given
    when(partitionChangeExecutor.forceReconfigure(anyInt(), any()))
        .thenReturn(TestActorFuture.completedFuture(null));

    final PartitionForceReconfigureApplier partitionForceReconfigureApplier =
        new PartitionForceReconfigureApplier(
            1, localMemberId, Set.of(localMemberId), partitionChangeExecutor);
    partitionForceReconfigureApplier.init(validTopology);

    // when
    final var updater = partitionForceReconfigureApplier.apply().join();

    // then
    final var resultingTopology = updater.apply(validTopology);
    ClusterConfigurationAssert.assertThatClusterTopology(resultingTopology)
        .member(otherMember)
        .doesNotContainPartition(1);
  }
}
