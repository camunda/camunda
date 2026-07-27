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
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation.HashMod;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.ActivePartitions;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.AllPartitions;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class StartPartitionScaleUpApplierTest {

  private final PartitionScalingChangeExecutor executor =
      mock(PartitionScalingChangeExecutor.class);
  private final MemberId memberId = MemberId.from("1");
  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();
  private final GlobalConfiguration globalConfiguration = GlobalConfiguration.init();

  private static PartitionGroupConfiguration groupWith(
      final Map<MemberId, BrokerPartitionState> members, final Optional<RoutingState> routing) {
    return new PartitionGroupConfiguration(
        1, 0, members, routing, Optional.empty(), Optional.empty());
  }

  private static BrokerPartitionState brokerWith(final Map<Integer, PartitionState> partitions) {
    return new BrokerPartitionState(1, Instant.EPOCH, partitions, Mode.PROCESSING);
  }

  @Test
  void shouldRejectIfDesiredPartitionCountBelowMinimum() {
    // when
    final var result =
        new StartPartitionScaleUpApplier(executor, 0)
            .init(globalConfiguration, groupWith(Map.of(), Optional.empty()));

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectIfDesiredPartitionCountNotGreaterThanCurrent() {
    // given — the group already has partition 1
    final var group =
        groupWith(
            Map.of(memberId, brokerWith(Map.of(1, PartitionState.active(1, partitionConfig)))),
            Optional.of(new RoutingState(1, new AllPartitions(1), new HashMod(1))));

    // when
    final var result =
        new StartPartitionScaleUpApplier(executor, 1).init(globalConfiguration, group);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must be greater than current partition count");
  }

  @Test
  void shouldRejectIfRoutingStateIsNotInitialized() {
    // given
    final var group = groupWith(Map.of(), Optional.empty());

    // when
    final var result =
        new StartPartitionScaleUpApplier(executor, 2).init(globalConfiguration, group);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Routing state is not initialized");
  }

  @Test
  void shouldRejectIfRequestHandlingIsNotStable() {
    // given — already mid-scale-up (ActivePartitions, not AllPartitions)
    final var group =
        groupWith(
            Map.of(),
            Optional.of(
                new RoutingState(1, new ActivePartitions(1, Set.of(), Set.of(2)), new HashMod(1))));

    // when
    final var result =
        new StartPartitionScaleUpApplier(executor, 3).init(globalConfiguration, group);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not stable");
  }

  @Test
  void shouldExecuteScaleUpAndUpdateRoutingState() {
    // given
    final var group =
        groupWith(
            Map.of(memberId, brokerWith(Map.of(1, PartitionState.active(1, partitionConfig)))),
            Optional.of(new RoutingState(1, new AllPartitions(1), new HashMod(1))));
    final var applier = new StartPartitionScaleUpApplier(executor, 3);
    when(executor.initiateScaleUp(3)).thenReturn(CompletableActorFuture.completed(null));

    // when
    final var initResult = applier.init(globalConfiguration, group);
    assertThat(initResult).isRight();
    final var resultingGroup = applier.apply().join().apply(group);

    // then
    verify(executor, times(1)).initiateScaleUp(3);
    final var newRoutingState = resultingGroup.routingState().orElseThrow();
    Assertions.assertThat(newRoutingState.version()).isEqualTo(2);
    Assertions.assertThat(newRoutingState.requestHandling())
        .isEqualTo(new ActivePartitions(1, Set.of(), Set.of(2, 3)));
  }
}
