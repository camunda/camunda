/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationAssert;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class MemberLeaveApplierTest {

  @Test
  void shouldFailInitIfMemberDoesNotExist() {
    final MemberId memberId = MemberId.from("1");
    final var memberLeaveApplier = new MemberLeaveApplier(memberId, null);

    final ClusterConfiguration clusterConfigurationWithOutMember = ClusterConfiguration.init();

    // when
    final var result = memberLeaveApplier.init(clusterConfigurationWithOutMember);

    // then
    EitherAssert.assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("but the member is not part of the cluster");
  }

  @Test
  void shouldFailInitIfMemberHasPartitions() {
    final MemberId memberId = MemberId.from("1");
    final var memberLeaveApplier = new MemberLeaveApplier(memberId, null);

    final ClusterConfiguration clusterConfigurationWithMember =
        ClusterConfiguration.init()
            .addMember(
                memberId,
                MemberState.initializeAsActive(Map.of())
                    .addPartition(1, PartitionState.active(1, DynamicPartitionConfig.init())));

    // when
    final var result = memberLeaveApplier.init(clusterConfigurationWithMember);

    // then
    EitherAssert.assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("but the member still has partitions assigned");
  }

  @Test
  void shouldSetStateToLeavingOnInit() {
    final MemberId memberId = MemberId.from("1");
    final var memberLeaveApplier = new MemberLeaveApplier(memberId, null);

    final ClusterConfiguration clusterConfigurationWithMember =
        ClusterConfiguration.init().addMember(memberId, MemberState.initializeAsActive(Map.of()));

    // when
    final var result = memberLeaveApplier.init(clusterConfigurationWithMember);
    final var updateTopologyAfterInit = result.get().apply(clusterConfigurationWithMember);

    // then
    ClusterConfigurationAssert.assertThatClusterTopology(updateTopologyAfterInit)
        .hasMemberWithState(1, MemberState.State.LEAVING);
  }

  @Test
  void shouldSetStateToLeftOnApply() {
    // given
    final MemberId memberId = MemberId.from("1");
    final var memberLeaveApplier =
        new MemberLeaveApplier(memberId, new NoopClusterMembershipChangeExecutor());

    final ClusterConfiguration clusterConfigurationWithMember =
        ClusterConfiguration.init().addMember(memberId, MemberState.initializeAsActive(Map.of()));
    final var updater = memberLeaveApplier.init(clusterConfigurationWithMember).get();
    final var topologyWithLeaving = updater.apply(clusterConfigurationWithMember);

    // when
    final var updateTopologyAfterApply =
        memberLeaveApplier.apply().join().apply(topologyWithLeaving);

    // then
    ClusterConfigurationAssert.assertThatClusterTopology(updateTopologyAfterApply)
        .hasMemberWithState(1, MemberState.State.LEFT);
  }

  @Test
  void shouldFailFutureIfLeaveFails() {
    // given
    final MemberId memberId = MemberId.from("1");
    final var memberLeaveApplier =
        new MemberLeaveApplier(memberId, new FailingClusterMembershipChangeExecutor());

    final ClusterConfiguration clusterConfigurationWithMember =
        ClusterConfiguration.init().addMember(memberId, MemberState.initializeAsActive(Map.of()));
    memberLeaveApplier.init(clusterConfigurationWithMember);

    // when
    final var result = memberLeaveApplier.apply();

    // then
    Assertions.assertThat(result)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("Force failure");
  }
}
