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
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateRoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation.HashMod;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.AllPartitions;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class UpdateRoutingStateApplierTest {

  private final PartitionScalingChangeExecutor executor =
      mock(PartitionScalingChangeExecutor.class);
  private final GlobalConfiguration globalConfiguration = GlobalConfiguration.init();

  private static PartitionGroupConfiguration groupWith(final Optional<RoutingState> routing) {
    return new PartitionGroupConfiguration(
        1, 0, Map.of(), routing, Optional.empty(), Optional.empty());
  }

  @Test
  void shouldUseProvidedRoutingStateAndBumpVersion() {
    // given
    final var group =
        groupWith(Optional.of(new RoutingState(1, new AllPartitions(1), new HashMod(1))));
    final var newState = new RoutingState(5, new AllPartitions(2), new HashMod(2));
    final var operation = new UpdateRoutingState(MemberId.from("1"), Optional.of(newState));
    final var applier = new UpdateRoutingStateApplier(operation, executor);

    // when
    final var resultingGroup = applier.apply().join().apply(group);

    // then
    Assertions.assertThat(resultingGroup.routingState().orElseThrow().version()).isEqualTo(2);
    Assertions.assertThat(resultingGroup.routingState().orElseThrow().requestHandling())
        .isEqualTo(new AllPartitions(2));
  }

  @Test
  void shouldFetchRoutingStateFromExecutorWhenNotProvided() {
    // given
    final var group = groupWith(Optional.empty());
    final var fetchedState = new RoutingState(9, new AllPartitions(3), new HashMod(3));
    when(executor.getRoutingState()).thenReturn(CompletableActorFuture.completed(fetchedState));
    final var operation = new UpdateRoutingState(MemberId.from("1"), Optional.empty());
    final var applier = new UpdateRoutingStateApplier(operation, executor);

    // when
    final var initResult = applier.init(globalConfiguration, group);
    final var resultingGroup = applier.apply().join().apply(group);

    // then
    assertThat(initResult).isRight();
    Assertions.assertThat(resultingGroup.routingState().orElseThrow().version()).isEqualTo(1);
    Assertions.assertThat(resultingGroup.routingState().orElseThrow().requestHandling())
        .isEqualTo(new AllPartitions(3));
  }
}
