/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSortedSet;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.AwaitRedistributionCompletion;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation.HashMod;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.ActivePartitions;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.AllPartitions;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class AwaitRedistributionCompletionApplierTest {

  private final PartitionScalingChangeExecutor executor =
      mock(PartitionScalingChangeExecutor.class);
  private final GlobalConfiguration globalConfiguration = GlobalConfiguration.init();

  private static PartitionGroupConfiguration groupWith(final Optional<RoutingState> routing) {
    return new PartitionGroupConfiguration(
        1, 0, Map.of(), routing, Optional.empty(), Optional.empty());
  }

  private AwaitRedistributionCompletion operationFor(final Set<Integer> partitionsToRedistribute) {
    final SortedSet<Integer> sorted = ImmutableSortedSet.copyOf(partitionsToRedistribute);
    return new AwaitRedistributionCompletion(MemberId.from("1"), 3, sorted);
  }

  @Test
  void shouldRejectIfPartitionsToRedistributeIsEmpty() {
    // when
    final var result =
        new AwaitRedistributionCompletionApplier(executor, operationFor(Set.of()))
            .init(globalConfiguration, groupWith(Optional.empty()));

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectIfRoutingStateIsNotInitialized() {
    // when
    final var result =
        new AwaitRedistributionCompletionApplier(executor, operationFor(Set.of(2, 3)))
            .init(globalConfiguration, groupWith(Optional.empty()));

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Routing state is not initialized");
  }

  @Test
  void shouldRejectIfAllPartitionsAreAlreadyActive() {
    // given
    final var group =
        groupWith(Optional.of(new RoutingState(1, new AllPartitions(3), new HashMod(3))));

    // when
    final var result =
        new AwaitRedistributionCompletionApplier(executor, operationFor(Set.of(2, 3)))
            .init(globalConfiguration, group);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("All partitions are active");
  }

  @Test
  void shouldActivatePartitionsOnCompletion() {
    // given
    final var group =
        groupWith(
            Optional.of(
                new RoutingState(
                    1, new ActivePartitions(1, Set.of(), Set.of(2, 3)), new HashMod(1))));
    final var operation = operationFor(Set.of(2, 3));
    final var applier = new AwaitRedistributionCompletionApplier(executor, operation);
    when(executor.awaitRedistributionCompletion(eq(3), eq(Set.of(2, 3)), isNull()))
        .thenReturn(CompletableActorFuture.completed(null));

    // when
    final var initResult = applier.init(globalConfiguration, group);
    assertThat(initResult).isRight();
    final var resultingGroup = applier.apply().join().apply(group);

    // then
    verify(executor, times(1)).awaitRedistributionCompletion(eq(3), eq(Set.of(2, 3)), any());
    final var requestHandling = resultingGroup.routingState().orElseThrow().requestHandling();
    Assertions.assertThat(requestHandling).isEqualTo(new AllPartitions(3));
  }
}
