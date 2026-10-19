/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.PartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

final class ExportingStateChangeApplierTest {

  private final PartitionChangeExecutor partitionChangeExecutor =
      mock(PartitionChangeExecutor.class);

  private final MemberId memberId = MemberId.from("1");
  private final ExportingState targetState = ExportingState.PAUSED;

  private final ExportingStateChangeApplier applier =
      new ExportingStateChangeApplier(memberId, targetState, partitionChangeExecutor);

  private final GlobalConfiguration globalConfigurationWithLocalMemberActive =
      globalConfigurationWith(Map.of(memberId, BrokerState.initializeAsActive()));

  private static GlobalConfiguration globalConfigurationWith(
      final Map<MemberId, BrokerState> members) {
    return new GlobalConfiguration(
        GlobalConfiguration.INITIAL_VERSION,
        Optional.empty(),
        members,
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  private static PartitionGroupConfiguration groupWithMembers(
      final Map<MemberId, BrokerPartitionState> members) {
    return new PartitionGroupConfiguration(
        1, 0, members, Optional.empty(), Optional.empty(), Optional.empty());
  }

  private PartitionGroupConfiguration groupWithPartitions(final int... partitionIds) {
    final var partitions = new HashMap<Integer, PartitionState>();
    for (final int partitionId : partitionIds) {
      partitions.put(partitionId, PartitionState.active(1, DynamicPartitionConfig.init()));
    }
    return groupWithMembers(
        Map.of(memberId, new BrokerPartitionState(1, Instant.EPOCH, partitions, Mode.PROCESSING)));
  }

  @Test
  void shouldFailInitIfMemberDoesNotExistInCluster() {
    // given
    final var group = groupWithPartitions(1, 2);

    // when
    final var result = applier.init(globalConfigurationWith(Map.of()), group);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("member '1' does not exist in the cluster");
  }

  @Test
  void shouldFailInitIfMemberIsNotPartOfGroup() {
    // given
    final var group = groupWithMembers(Map.of());

    // when
    final var result = applier.init(globalConfigurationWithLocalMemberActive, group);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not part of this partition group");
  }

  @Test
  void shouldNotChangeStateInInit() {
    // given
    final var group = groupWithPartitions(1, 2);

    // when
    final var result = applier.init(globalConfigurationWithLocalMemberActive, group);

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get().apply(group)).isEqualTo(group);
  }

  @Test
  void shouldSetExporterStateOnAllLocalPartitions() {
    // given
    final var group = groupWithPartitions(1, 2);
    applier.init(globalConfigurationWithLocalMemberActive, group);
    when(partitionChangeExecutor.setExportingState(targetState))
        .thenReturn(CompletableActorFuture.completed(null));

    // when
    applier.apply().join();

    // then
    verify(partitionChangeExecutor).setExportingState(targetState);
  }

  @Test
  void shouldUpdatePartitionConfigStateIfApplySucceeds() {
    // given
    final var group = groupWithPartitions(1, 2);
    applier.init(globalConfigurationWithLocalMemberActive, group);
    when(partitionChangeExecutor.setExportingState(targetState))
        .thenReturn(CompletableActorFuture.completed(null));

    // when
    final var result = applier.apply();

    // then
    assertThat(result).succeedsWithin(Duration.ofMillis(100));
    final var updatedMember = result.join().apply(group).getMember(memberId);
    assertThat(updatedMember.getPartition(1).config().exporting().state()).isEqualTo(targetState);
    assertThat(updatedMember.getPartition(2).config().exporting().state()).isEqualTo(targetState);
  }

  @Test
  void shouldFailFutureIfAnyPartitionFails() {
    // given
    final var group = groupWithPartitions(1, 2);
    applier.init(globalConfigurationWithLocalMemberActive, group);
    when(partitionChangeExecutor.setExportingState(targetState))
        .thenReturn(
            CompletableActorFuture.completedExceptionally(new RuntimeException("force fail")));

    // when
    final var result = applier.apply();

    // then
    assertThat(result)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("force fail");
  }
}
