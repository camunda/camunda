/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationAssert;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeAppliers.ClusterOperationApplier;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation.HashMod;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.ActivePartitions;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.AllPartitions;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

final class StartPartitionScaleUpApplierTest {
  private final PartitionScalingChangeExecutor executor =
      mock(PartitionScalingChangeExecutor.class);
  private final ClusterConfiguration initialConfiguration =
      ClusterConfiguration.init()
          .addMember(
              MemberId.from("1"),
              MemberState.initializeAsActive(
                  Map.of(
                      1,
                      PartitionState.active(1, DynamicPartitionConfig.init()),
                      2,
                      PartitionState.active(1, DynamicPartitionConfig.init()),
                      3,
                      PartitionState.active(1, DynamicPartitionConfig.init()))));

  @Test
  void shouldFailOnPartitionCountTooLow() {
    EitherAssert.assertThat(
            new StartPartitionScaleUpApplier(executor, 0).init(initialConfiguration))
        .left()
        .asInstanceOf(InstanceOfAssertFactories.throwable(IllegalArgumentException.class))
        .hasMessageContaining("Desired partition count must be greater than 1");
  }

  @Test
  void shouldFailOnPartitionCountTooHigh() {
    EitherAssert.assertThat(
            new StartPartitionScaleUpApplier(executor, 100000).init(initialConfiguration))
        .left()
        .asInstanceOf(InstanceOfAssertFactories.throwable(IllegalArgumentException.class))
        .hasMessageContaining("Desired partition count must not exceed 8192");
  }

  @Test
  void shouldFailWhenRoutingStateIsMissing() {
    // given - routing state not initialized
    assertThat(initialConfiguration).returns(Optional.empty(), ClusterConfiguration::routingState);

    // when - trying to init the operation
    final var result = new StartPartitionScaleUpApplier(executor, 4).init(initialConfiguration);

    // then - init fails with a useful exception
    EitherAssert.assertThat(result)
        .left()
        .asInstanceOf(InstanceOfAssertFactories.throwable(IllegalStateException.class))
        .hasMessageContaining("Routing state is not initialized yet");
  }

  @Test
  void shouldFailWhenDesiredPartitionCountIsLowerThanCurrentPartitionCount() {
    // given - cluster does not contain partition 4
    assertThat(initialConfiguration.partitionCount()).isEqualTo(3);

    // when - trying to init the operation to scale up to 4 partitions
    final var result = new StartPartitionScaleUpApplier(executor, 2).init(initialConfiguration);

    // then - init fails with a useful exception
    EitherAssert.assertThat(result)
        .left()
        .asInstanceOf(InstanceOfAssertFactories.throwable(IllegalStateException.class))
        .hasMessageContaining(
            "Desired partition count (2) must be greater than current partition count(3)");
  }

  @Test
  void shouldFailWhenRequestHandlingIsUnexpected() {
    // given - request handling is not AllPartitions
    final var routingState =
        new RoutingState(1, new ActivePartitions(1, Set.of(), Set.of(2, 3)), new HashMod(1));
    final var configuration =
        new ClusterConfiguration(
            initialConfiguration.version(),
            initialConfiguration.members(),
            initialConfiguration.lastChange(),
            initialConfiguration.pendingChanges(),
            Optional.of(routingState),
            initialConfiguration.clusterId());

    // when - trying to init the operation to scale up
    final var result = new StartPartitionScaleUpApplier(executor, 4).init(configuration);

    // then - init fails with a useful exception
    EitherAssert.assertThat(result)
        .left()
        .asInstanceOf(InstanceOfAssertFactories.throwable(IllegalStateException.class))
        .hasMessageContaining(
            "Cannot start scaling up because request handling strategy is not stable");
  }

  @Test
  void shouldFailWhenActuallyScalingDown() {
    // given - request handling is set up for three partitions
    final var routingState = new RoutingState(1, new AllPartitions(3), new HashMod(3));
    final var configuration =
        new ClusterConfiguration(
            initialConfiguration.version(),
            initialConfiguration.members(),
            initialConfiguration.lastChange(),
            initialConfiguration.pendingChanges(),
            Optional.of(routingState),
            initialConfiguration.clusterId());

    // when - trying to init the operation to scale "up" to 2 partitions
    final var result = new StartPartitionScaleUpApplier(executor, 2).init(configuration);

    // then - init fails with a useful exception
    EitherAssert.assertThat(result)
        .left()
        .asInstanceOf(InstanceOfAssertFactories.throwable(IllegalStateException.class))
        .hasMessageContaining(
            "Desired partition count (2) must be greater than current partition count(3)");
  }

  @Test
  void shouldForwardExecutorFailure() {
    // given - routing state is stable, pointing at just one partition
    final var routingState = new RoutingState(1, new AllPartitions(1), new HashMod(1));
    final var configuration =
        new ClusterConfiguration(
            initialConfiguration.version(),
            initialConfiguration.members(),
            initialConfiguration.lastChange(),
            initialConfiguration.pendingChanges(),
            Optional.of(routingState),
            initialConfiguration.clusterId());

    // when - applying the operation to scale up to three once
    final var expectedFailure = new RuntimeException("Expected failure");
    when(executor.initiateScaleUp(anyInt()))
        .thenReturn(CompletableActorFuture.completedExceptionally(expectedFailure));
    final var applier = new StartPartitionScaleUpApplier(executor, 2);

    // then - applier fails during apply
    assertThat(applier.apply())
        .failsWithin(Duration.ofSeconds(1))
        .withThrowableThat()
        .withCause(expectedFailure);
  }

  @Test
  void shouldCallExecutorWithDesiredPartitionCount() {
    // given - routing state is stable, pointing at just one partition
    final var routingState = new RoutingState(1, new AllPartitions(3), new HashMod(1));
    final var configuration =
        new ClusterConfiguration(
            initialConfiguration.version(),
            initialConfiguration.members(),
            initialConfiguration.lastChange(),
            initialConfiguration.pendingChanges(),
            Optional.of(routingState),
            initialConfiguration.clusterId());

    // when - applying the operation to scale up to three once
    when(executor.initiateScaleUp(anyInt())).thenReturn(CompletableActorFuture.completed(null));
    final var updatedConfiguration =
        runApplier(new StartPartitionScaleUpApplier(executor, 4), configuration);

    // then - executor is called
    verify(executor).initiateScaleUp(4);
  }

  @Test
  void shouldUpdateRoutingState() {
    // given - routing state is stable, pointing at just one partition
    final var routingState = new RoutingState(1, new AllPartitions(3), new HashMod(3));
    final var configuration =
        new ClusterConfiguration(
            initialConfiguration.version(),
            initialConfiguration.members(),
            initialConfiguration.lastChange(),
            initialConfiguration.pendingChanges(),
            Optional.of(routingState),
            initialConfiguration.clusterId());

    // when - applying the operation to scale up to three once
    when(executor.initiateScaleUp(anyInt())).thenReturn(CompletableActorFuture.completed(null));
    final var updatedConfiguration =
        runApplier(new StartPartitionScaleUpApplier(executor, 4), configuration);

    // then - routing state is updated
    ClusterConfigurationAssert.assertThatClusterTopology(updatedConfiguration)
        .routingState()
        .hasVersion(2)
        .hasActivatedPartitions(3)
        .hasRequestHandling(new ActivePartitions(3, Set.of(), Set.of(4)));
  }

  private static ClusterConfiguration runApplier(
      final ClusterOperationApplier applier, final ClusterConfiguration initialConfiguration) {
    final var initResult = applier.init(initialConfiguration);
    EitherAssert.assertThat(initResult).isRight();
    final var initializedConfiguration = initResult.get().apply(initialConfiguration);
    final var updater = applier.apply().join();
    return updater.apply(initializedConfiguration);
  }
}
