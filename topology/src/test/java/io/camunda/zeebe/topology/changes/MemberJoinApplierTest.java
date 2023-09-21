/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.changes;

import static io.camunda.zeebe.topology.state.MemberState.State.ACTIVE;
import static io.camunda.zeebe.topology.state.MemberState.State.JOINING;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

final class MemberJoinApplierTest {

  @Test
  void shouldFailMemberJoinOnInitIfMemberExistsAndActive() {
    // given
    final MemberId memberId = MemberId.from("1");
    final var memberJoinApplier = new MemberJoinApplier(memberId, null);

    final ClusterTopology clusterTopologyWithMember =
        ClusterTopology.init().addMember(memberId, MemberState.initializeAsActive(Map.of()));

    // when
    final var result = memberJoinApplier.init(clusterTopologyWithMember);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("but the member is already part of the topology");
  }

  @Test
  void shouldFailMemberJoinOnInitIfMemberExistsInJoiningState() {
    final MemberId memberId = MemberId.from("1");
    final var memberJoinApplier = new MemberJoinApplier(memberId, null);

    final ClusterTopology clusterTopologyWithMember =
        ClusterTopology.init().addMember(memberId, MemberState.uninitialized().toJoining());

    // when
    final var result = memberJoinApplier.init(clusterTopologyWithMember);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("but the member is already part of the topology");
  }

  @Test
  void shouldSetTheStateToJoiningOnInit() {
    // given
    final MemberId memberId = MemberId.from("1");
    final var memberJoinApplier = new MemberJoinApplier(memberId, null);

    final ClusterTopology clusterTopologyWithMember = ClusterTopology.init();

    // when
    final var result = memberJoinApplier.init(clusterTopologyWithMember);

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get()).isNotNull();
    assertThat(result.get().apply(null).state()).isEqualTo(JOINING);
  }

  @Test
  void shouldSetTheStateToActiveOnApply() {
    // given
    final MemberId memberId = MemberId.from("1");
    final var memberJoinApplier =
        new MemberJoinApplier(memberId, new NoopTopologyMembershipChangeExecutor());

    final ClusterTopology clusterTopologyWithMember = ClusterTopology.init();
    final var updater = memberJoinApplier.init(clusterTopologyWithMember).get();
    final var topologyWithJoining = clusterTopologyWithMember.updateMember(memberId, updater);

    // when
    final var result = memberJoinApplier.apply();

    // then
    assertThat(result).succeedsWithin(Duration.ofMillis(100));
    final var finalTopology = topologyWithJoining.updateMember(memberId, result.join());
    assertThat(finalTopology.getMember(memberId).state()).isEqualTo(ACTIVE);
  }

  @Test
  void shouldFailFutureIfJoinFails() {
    // given
    final MemberId memberId = MemberId.from("1");
    final var memberJoinApplier =
        new MemberJoinApplier(memberId, new FailingTopologyMembershipChangeExecutor());

    final ClusterTopology clusterTopologyWithMember = ClusterTopology.init();
    memberJoinApplier.init(clusterTopologyWithMember);

    // when
    final var result = memberJoinApplier.apply();

    // then
    assertThat(result)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("Force failure");
  }
}
