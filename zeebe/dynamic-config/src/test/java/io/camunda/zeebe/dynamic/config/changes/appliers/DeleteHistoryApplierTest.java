/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.ClusterChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class DeleteHistoryApplierTest {

  private final ClusterChangeExecutor clusterChangeExecutor = mock(ClusterChangeExecutor.class);
  private final GlobalConfiguration globalConfiguration = GlobalConfiguration.init();
  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  private static PartitionGroupConfiguration groupWithMembers(
      final Map<MemberId, BrokerPartitionState> members) {
    return new PartitionGroupConfiguration(
        1, 0, members, Optional.empty(), Optional.empty(), Optional.empty());
  }

  private static BrokerPartitionState brokerWith(final Map<Integer, PartitionState> partitions) {
    return new BrokerPartitionState(1, Instant.EPOCH, partitions, Mode.PROCESSING);
  }

  @Test
  void shouldRejectIfPartitionsStillExistInGroup() {
    // given
    final var group =
        groupWithMembers(
            Map.of(
                MemberId.from("1"),
                brokerWith(Map.of(1, PartitionState.active(1, partitionConfig)))));

    // when
    final var result =
        new DeleteHistoryApplier(clusterChangeExecutor).init(globalConfiguration, group);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("partitions still exist");
  }

  @Test
  void shouldExecuteDeleteHistoryCallback() {
    // given
    final var group = groupWithMembers(Map.of());
    final var applier = new DeleteHistoryApplier(clusterChangeExecutor);
    when(clusterChangeExecutor.deleteHistory()).thenReturn(CompletableActorFuture.completed(null));

    // when
    final var initResult = applier.init(globalConfiguration, group);
    assertThat(initResult).isRight();
    applier.apply().join();

    // then
    verify(clusterChangeExecutor, times(1)).deleteHistory();
  }
}
