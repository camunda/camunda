/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor.NoopPartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ScaleUpOperation.*;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation.HashMod;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.ActivePartitions;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.AllPartitions;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

public class AwaitRedistributionCompletionTest extends AbstractApplierTest {

  MemberId memberId = new MemberId("1");

  ClusterConfiguration empty = ClusterConfiguration.init();
  ClusterConfiguration validAllPartitionConfig =
      new ClusterConfiguration(
          1,
          Map.of(),
          Optional.empty(),
          Optional.empty(),
          Optional.of(RoutingState.initializeWithPartitionCount(3)),
          Optional.empty());

  ClusterConfiguration valid3OutOf6Partitions =
      new ClusterConfiguration(
          1,
          Map.of(),
          Optional.empty(),
          Optional.empty(),
          Optional.of(
              new RoutingState(
                  1, new ActivePartitions(3, Set.of(), Set.of(4, 5, 6)), new HashMod(3))),
          Optional.empty());

  PartitionScalingChangeExecutor executor = new NoopPartitionScalingChangeExecutor();

  @Test
  public void shouldFailWhenNoRoutingStateIsPresent() {
    final var changeOperation =
        new AwaitRedistributionCompletion(memberId, 6, new TreeSet<>(List.of(2, 3)));
    final var applier = new AwaitRedistributionCompletionApplier(executor, changeOperation);
    EitherAssert.assertThat(applier.init(empty)).isLeft();
  }

  @Test
  public void shouldFailWhenListOfPartitionIsEmpty() {
    final var changeOperation = new AwaitRedistributionCompletion(memberId, 6, new TreeSet<>());
    final var applier = new AwaitRedistributionCompletionApplier(executor, changeOperation);
    EitherAssert.assertThat(applier.init(validAllPartitionConfig))
        .isLeft()
        .satisfies(left -> assertThat(left.getLeft()).isInstanceOf(IllegalArgumentException.class));
  }

  @Test
  public void shouldAddNewPartitionToRequestHandling() {
    // given
    final var operation =
        new AwaitRedistributionCompletion(memberId, 6, new TreeSet<>(List.of(4, 5)));
    final var applier = new AwaitRedistributionCompletionApplier(executor, operation);
    // when
    final var modified = runApplier(applier, valid3OutOf6Partitions);
    // then
    assertThat(modified.routingState())
        .isNotEmpty()
        .satisfies(
            routingState ->
                assertThat(routingState.get().requestHandling())
                    .isEqualTo(new ActivePartitions(3, Set.of(4, 5), Set.of(6))));
  }

  @Test
  public void shouldSetRequestHandlingAsAllPartition() {
    // given
    final var operation =
        new AwaitRedistributionCompletion(memberId, 6, new TreeSet<>(List.of(4, 5, 6)));
    final var applier = new AwaitRedistributionCompletionApplier(executor, operation);
    // when
    final var modified = runApplier(applier, valid3OutOf6Partitions);

    // then
    assertThat(modified.routingState())
        .isNotEmpty()
        .satisfies(
            routingState ->
                assertThat(routingState.get().requestHandling()).isEqualTo(new AllPartitions(6)));
  }
}
