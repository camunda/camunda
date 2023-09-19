/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.changes;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import io.camunda.zeebe.topology.ClusterTopologyAssert;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.state.PartitionState;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

final class MemberLeaveApplierTest {

  @Test
  void shouldFailInitIfMemberDoesNotExist() {
    final MemberId memberId = MemberId.from("1");
    final var memberLeaveApplier = new MemberLeaveApplier(memberId, null);

    final ClusterTopology clusterTopologyWithOutMember = ClusterTopology.init();

    // when
    final var result = memberLeaveApplier.init(clusterTopologyWithOutMember);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("but the member is not part of the topology");
  }

  @Test
  void shouldFailInitIfMemberHasPartitions() {
    final MemberId memberId = MemberId.from("1");
    final var memberLeaveApplier = new MemberLeaveApplier(memberId, null);

    final ClusterTopology clusterTopologyWithMember =
        ClusterTopology.init()
            .addMember(
                memberId,
                MemberState.initializeAsActive(Map.of()).addPartition(1, PartitionState.active(1)));

    // when
    final var result = memberLeaveApplier.init(clusterTopologyWithMember);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("but the member still has partitions assigned");
  }

  @Test
  void shouldSetStateToLeavingOnInit() {
    final MemberId memberId = MemberId.from("1");
    final var memberLeaveApplier = new MemberLeaveApplier(memberId, null);

    final ClusterTopology clusterTopologyWithMember =
        ClusterTopology.init().addMember(memberId, MemberState.initializeAsActive(Map.of()));

    // when
    final var result = memberLeaveApplier.init(clusterTopologyWithMember);
    final var updateTopologyAfterInit =
        clusterTopologyWithMember.updateMember(memberId, result.get());

    // then
    ClusterTopologyAssert.assertThatClusterTopology(updateTopologyAfterInit)
        .hasMemberWithState(1, MemberState.State.LEAVING);
  }

  @Test
  void shouldSetStateToLeftOnApply() {
    // given
    final MemberId memberId = MemberId.from("1");
    final var memberLeaveApplier =
        new MemberLeaveApplier(memberId, new NoopTopologyMembershipChangeExecutor());

    final ClusterTopology clusterTopologyWithMember =
        ClusterTopology.init().addMember(memberId, MemberState.initializeAsActive(Map.of()));
    final var updater = memberLeaveApplier.init(clusterTopologyWithMember).get();
    final var topologyWithLeaving = clusterTopologyWithMember.updateMember(memberId, updater);

    // when
    final var result = memberLeaveApplier.apply().join();
    final var updateTopologyAfterApply = topologyWithLeaving.updateMember(memberId, result);

    // then
    ClusterTopologyAssert.assertThatClusterTopology(updateTopologyAfterApply)
        .hasMemberWithState(1, MemberState.State.LEFT);
  }

  @Test
  void shouldFailFutureIfLeaveFails() {
    // given
    final MemberId memberId = MemberId.from("1");
    final var memberLeaveApplier =
        new MemberLeaveApplier(memberId, new FailingTopologyMembershipChangeExecutor());

    final ClusterTopology clusterTopologyWithMember =
        ClusterTopology.init().addMember(memberId, MemberState.initializeAsActive(Map.of()));
    memberLeaveApplier.init(clusterTopologyWithMember);

    // when
    final var result = memberLeaveApplier.apply();

    // then
    assertThat(result)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("Force failure");
  }
}
