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
import io.camunda.zeebe.dynamic.config.ClusterConfigurationAssert;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

final class PreScalingApplierTest {

  @Test
  void shouldSucceedOnInitIfMemberExists() {
    // given
    final var memberId = MemberId.from("1");
    final var newMemberId = MemberId.from("2");
    final var newClusterMembers = Set.of(memberId, newMemberId);
    final var clusterChangeExecutor = new ClusterChangeExecutor.NoopClusterChangeExecutor();
    final var preScalingApplier =
        new PreScalingApplier(memberId, newClusterMembers, clusterChangeExecutor);

    final var clusterConfigurationWithMember =
        ClusterConfiguration.init().addMember(memberId, MemberState.initializeAsActive(Map.of()));

    // when
    final var result = preScalingApplier.init(clusterConfigurationWithMember);

    // then
    EitherAssert.assertThat(result).isRight();
  }

  @Test
  void shouldFailOnInitIfMemberDoesNotExist() {
    // given
    final var memberId = MemberId.from("1");
    final var otherMemberId = MemberId.from("2");
    final var newClusterMembers = Set.of(memberId, otherMemberId);
    final var clusterChangeExecutor = new ClusterChangeExecutor.NoopClusterChangeExecutor();
    final var preScalingApplier =
        new PreScalingApplier(memberId, newClusterMembers, clusterChangeExecutor);

    final var clusterConfigWithoutMember =
        ClusterConfiguration.init()
            .addMember(otherMemberId, MemberState.initializeAsActive(Map.of()));

    // when
    final var result = preScalingApplier.init(clusterConfigWithoutMember);

    // then
    EitherAssert.assertThat(result).isLeft();
  }

  @Test
  void shouldPreserveSameConfigurationAfterApply() {
    // given
    final var member1Id = MemberId.from("1");
    final var member2Id = MemberId.from("2");
    final var newClusterMembers = Set.of(member1Id, member2Id);
    final var clusterChangeExecutor = new ClusterChangeExecutor.NoopClusterChangeExecutor();

    final var initialConfiguration =
        ClusterConfiguration.init()
            .addMember(member1Id, MemberState.initializeAsActive(Map.of()))
            .addMember(member2Id, MemberState.initializeAsActive(Map.of()));
    final var preScalingApplier =
        new PreScalingApplier(member1Id, newClusterMembers, clusterChangeExecutor);
    final var initializedConfiguration =
        preScalingApplier.init(initialConfiguration).get().apply(initialConfiguration);
    final var updater = preScalingApplier.apply().join();
    final var updatedConfiguration = updater.apply(initializedConfiguration);

    // when
    ClusterConfigurationAssert.assertThatClusterTopology(updatedConfiguration)
        .isEqualTo(initialConfiguration);
  }

  @Test
  void shouldFailFutureIfApplyFails() {
    // given
    final var member1Id = MemberId.from("1");
    final var member2Id = MemberId.from("2");
    final var newClusterMembers = Set.of(member1Id, member2Id);
    final var failingExecutor =
        new ClusterChangeExecutor() {
          @Override
          public ActorFuture<Void> deleteHistory() {
            return null;
          }

          @Override
          public ActorFuture<Void> preScaling(
              final int currentClusterSize, final Set<MemberId> clusterMembers) {
            return CompletableActorFuture.completedExceptionally(
                new RuntimeException("Failed to execute operation"));
          }
        };

    final var initialConfiguration =
        ClusterConfiguration.init()
            .addMember(member1Id, MemberState.initializeAsActive(Map.of()))
            .addMember(member2Id, MemberState.initializeAsActive(Map.of()));
    final var preScalingApplier =
        new PreScalingApplier(member1Id, newClusterMembers, failingExecutor);
    preScalingApplier.init(initialConfiguration);

    // when
    final var applyFuture = preScalingApplier.apply();

    // then
    assertThat(applyFuture)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("Failed to execute operation");
  }
}
