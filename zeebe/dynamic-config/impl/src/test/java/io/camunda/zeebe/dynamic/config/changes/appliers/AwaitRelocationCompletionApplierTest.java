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

import com.google.common.collect.ImmutableSortedSet;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.AwaitRelocationCompletion;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation.HashMod;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.AllPartitions;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class AwaitRelocationCompletionApplierTest {

  private final PartitionScalingChangeExecutor executor =
      mock(PartitionScalingChangeExecutor.class);
  private final GlobalConfiguration globalConfiguration = GlobalConfiguration.init();

  private static PartitionGroupConfiguration groupWith(final Optional<RoutingState> routing) {
    return new PartitionGroupConfiguration(
        1, 0, Map.of(), routing, Optional.empty(), Optional.empty());
  }

  private AwaitRelocationCompletion operationFor(final Set<Integer> partitionsToRelocate) {
    return new AwaitRelocationCompletion(
        MemberId.from("1"), 3, ImmutableSortedSet.copyOf(partitionsToRelocate));
  }

  @Test
  void shouldRejectIfPartitionsToRelocateIsEmpty() {
    // when
    final var result =
        new AwaitRelocationCompletionApplier(executor, operationFor(Set.of()))
            .init(globalConfiguration, groupWith(Optional.empty()));

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectIfRoutingStateIsNotInitialized() {
    // when
    final var result =
        new AwaitRelocationCompletionApplier(executor, operationFor(Set.of(2, 3)))
            .init(globalConfiguration, groupWith(Optional.empty()));

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Routing state is not initialized");
  }

  @Test
  void shouldAcceptAndApplyAsNoop() {
    // given
    final var group =
        groupWith(Optional.of(new RoutingState(1, new AllPartitions(3), new HashMod(3))));
    final var applier = new AwaitRelocationCompletionApplier(executor, operationFor(Set.of(2, 3)));

    // when
    final var initResult = applier.init(globalConfiguration, group);
    final var resultingGroup = applier.apply().join().apply(group);

    // then
    assertThat(initResult).isRight();
    Assertions.assertThat(resultingGroup).isEqualTo(group);
  }
}
