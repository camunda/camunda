/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import static io.camunda.zeebe.dynamic.config.state.MemberState.State.ACTIVE;
import static io.camunda.zeebe.dynamic.config.state.MemberState.State.JOINING;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class MemberJoinApplierTest {

  @Test
  void shouldFailMemberJoinOnInitIfMemberExistsAndActive() {
    // given
    final MemberId memberId = MemberId.from("1");
    final var memberJoinApplier = new MemberJoinApplier(memberId, null);

    final ClusterConfiguration clusterConfigurationWithMember =
        ClusterConfiguration.init().addMember(memberId, MemberState.initializeAsActive(Map.of()));

    // when
    final var result = memberJoinApplier.init(clusterConfigurationWithMember);

    // then
    EitherAssert.assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("but the member is already part of the cluster");
  }

  @Test
  void shouldNotFailMemberJoinOnInitIfMemberExistsInJoiningState() {
    final MemberId memberId = MemberId.from("1");
    final var memberJoinApplier = new MemberJoinApplier(memberId, null);

    final ClusterConfiguration clusterConfigurationWithMember =
        ClusterConfiguration.init().addMember(memberId, MemberState.uninitialized().toJoining());

    // when
    final var result = memberJoinApplier.init(clusterConfigurationWithMember);

    // then
    EitherAssert.assertThat(result).isRight();
    Assertions.assertThat(
            result.get().apply(clusterConfigurationWithMember).getMember(memberId).state())
        .isEqualTo(JOINING);
  }

  @Test
  void shouldSetTheStateToJoiningOnInit() {
    // given
    final MemberId memberId = MemberId.from("1");
    final var memberJoinApplier = new MemberJoinApplier(memberId, null);

    final ClusterConfiguration clusterConfigurationWithMember = ClusterConfiguration.init();

    // when
    final var result = memberJoinApplier.init(clusterConfigurationWithMember);

    // then
    EitherAssert.assertThat(result).isRight();
    Assertions.assertThat(result.get()).isNotNull();
    Assertions.assertThat(
            result.get().apply(clusterConfigurationWithMember).getMember(memberId).state())
        .isEqualTo(JOINING);
  }

  @Test
  void shouldSetTheStateToActiveOnApply() {
    // given
    final MemberId memberId = MemberId.from("1");
    final var memberJoinApplier =
        new MemberJoinApplier(memberId, new NoopClusterMembershipChangeExecutor());

    final ClusterConfiguration clusterConfigurationWithMember = ClusterConfiguration.init();
    final var updater = memberJoinApplier.init(clusterConfigurationWithMember).get();
    final var topologyWithJoining = updater.apply(clusterConfigurationWithMember);

    // when
    final var result = memberJoinApplier.apply();

    // then
    Assertions.assertThat(result).succeedsWithin(Duration.ofMillis(100));
    final var finalTopology = result.join().apply(topologyWithJoining);
    Assertions.assertThat(finalTopology.getMember(memberId).state()).isEqualTo(ACTIVE);
  }

  @Test
  void shouldFailFutureIfJoinFails() {
    // given
    final MemberId memberId = MemberId.from("1");
    final var memberJoinApplier =
        new MemberJoinApplier(memberId, new FailingClusterMembershipChangeExecutor());

    final ClusterConfiguration clusterConfigurationWithMember = ClusterConfiguration.init();
    memberJoinApplier.init(clusterConfigurationWithMember);

    // when
    final var result = memberJoinApplier.apply();

    // then
    Assertions.assertThat(result)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("Force failure");
  }
}
