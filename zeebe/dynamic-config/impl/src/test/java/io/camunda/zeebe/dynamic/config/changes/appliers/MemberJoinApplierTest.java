/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.ClusterMembershipChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.NoopClusterMembershipChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.BrokerState.State;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class MemberJoinApplierTest {

  private final MemberId memberId = MemberId.from("1");
  private final ClusterMembershipChangeExecutor clusterMembershipChangeExecutor =
      new NoopClusterMembershipChangeExecutor();

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

  private static CurrentClusterConfiguration currentClusterConfigurationWith(
      final GlobalConfiguration globalConfiguration) {
    return new CurrentClusterConfiguration(
        CurrentClusterConfiguration.INITIAL_VERSION,
        globalConfiguration,
        Map.of(),
        PhasedChangeState.empty());
  }

  @Test
  void shouldAddNewMemberAsJoining() {
    // given
    final var initialConfig = globalConfigurationWith(Map.of());
    final var applier = new MemberJoinApplier(memberId, clusterMembershipChangeExecutor);

    // when
    final var result = applier.init(currentClusterConfigurationWith(initialConfig));

    // then
    assertThat(result).isRight();
    final var updatedConfig = result.get().apply(initialConfig);
    Assertions.assertThat(updatedConfig.hasMember(memberId)).isTrue();
    Assertions.assertThat(updatedConfig.getMember(memberId).state()).isEqualTo(State.JOINING);
  }

  @Test
  void shouldRejectJoinIfMemberIsAlreadyActive() {
    // given
    final var initialConfig =
        globalConfigurationWith(Map.of(memberId, BrokerState.initializeAsActive()));
    final var applier = new MemberJoinApplier(memberId, clusterMembershipChangeExecutor);

    // when
    final var result = applier.init(currentClusterConfigurationWith(initialConfig));

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already part of the cluster");
  }

  @Test
  void shouldNotFailOnInitIfMemberIsAlreadyJoining() {
    // given — restart-safe retry: the member was already marked JOINING before a restart
    final var initialConfig =
        globalConfigurationWith(
            Map.of(memberId, BrokerState.uninitialized().setState(State.JOINING)));
    final var applier = new MemberJoinApplier(memberId, clusterMembershipChangeExecutor);

    // when
    final var result = applier.init(currentClusterConfigurationWith(initialConfig));

    // then
    assertThat(result).isRight();
    final var updatedConfig = result.get().apply(initialConfig);
    Assertions.assertThat(updatedConfig.getMember(memberId).state()).isEqualTo(State.JOINING);
  }

  @Test
  void shouldExecuteJoinCallback() {
    // given
    final var initialConfig = globalConfigurationWith(Map.of());
    final var applier = new MemberJoinApplier(memberId, clusterMembershipChangeExecutor);
    final var configWithJoining =
        applier.init(currentClusterConfigurationWith(initialConfig)).get().apply(initialConfig);

    // when
    final var resultingConfig = applier.apply().join().apply(configWithJoining);

    // then
    Assertions.assertThat(resultingConfig.getMember(memberId).state()).isEqualTo(State.ACTIVE);
  }

  @Test
  void shouldReturnExceptionWhenJoinFailed() {
    // given
    final ClusterMembershipChangeExecutor failingExecutor =
        new ClusterMembershipChangeExecutor() {
          @Override
          public ActorFuture<Void> addBroker(final MemberId memberId) {
            return CompletableActorFuture.completedExceptionally(new RuntimeException("Expected"));
          }

          @Override
          public ActorFuture<Void> removeBroker(final MemberId memberId) {
            return CompletableActorFuture.completed(null);
          }
        };
    final var applier = new MemberJoinApplier(memberId, failingExecutor);

    // when
    final var joinFuture = applier.apply();

    // then
    Assertions.assertThat(joinFuture)
        .failsWithin(java.time.Duration.ofMillis(100))
        .withThrowableOfType(java.util.concurrent.ExecutionException.class)
        .withCauseInstanceOf(RuntimeException.class)
        .withMessageContaining("Expected");
  }
}
