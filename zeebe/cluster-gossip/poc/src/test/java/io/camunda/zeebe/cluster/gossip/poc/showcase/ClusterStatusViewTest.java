/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip.poc.showcase;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.cluster.gossip.poc.showcase.ClusterStatusView.Mode;
import io.camunda.zeebe.cluster.gossip.testkit.InMemoryGossipCluster;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * Runs the showcase consumer on the in-memory test kit: the way a consumer tests itself without any
 * transport. The proof-of-concept tests run the same consumer on real clusters.
 */
final class ClusterStatusViewTest {

  private static final MemberId A = MemberId.from(0);
  private static final MemberId B = MemberId.from(1);

  private final AtomicLong clock = new AtomicLong(1);
  private final InMemoryGossipCluster cluster = new InMemoryGossipCluster(clock::get);
  private final ClusterStatusView viewA =
      new ClusterStatusView(cluster.addMember(A), Runnable::run);
  private final ClusterStatusView viewB =
      new ClusterStatusView(cluster.addMember(B), Runnable::run);

  @Test
  void shouldShowLeaderOfOtherBroker() {
    // given
    viewA.becameLeader(1);

    // when
    cluster.sync();

    // then
    assertThat(viewB.leadersOf(1)).containsExactly(A);
  }

  @Test
  void shouldShowBothClaimsWhileLeadershipMoves() {
    // given
    viewA.becameLeader(1);
    cluster.sync();

    // when - B takes over before A's step-down arrived
    viewB.becameLeader(1);
    cluster.sync();

    // then
    assertThat(viewB.leadersOf(1)).containsExactlyInAnyOrder(A, B);
  }

  @Test
  void shouldForgetLeaderThatLeft() {
    // given
    viewA.becameLeader(1);
    cluster.sync();

    // when
    cluster.removeMember(A);

    // then
    assertThat(viewB.leadersOf(1)).isEmpty();
  }

  @Test
  void shouldSwitchModeClusterWide() {
    // when
    viewA.setMode(Mode.MAINTENANCE);
    cluster.sync();

    // then
    assertThat(viewB.mode()).isEqualTo(Mode.MAINTENANCE);
  }

  @Test
  void shouldKeepModeAcrossRestartOfBroker() {
    // given
    viewA.setMode(Mode.MAINTENANCE);
    cluster.sync();

    // when
    final var restartedB = new ClusterStatusView(cluster.restart(B), Runnable::run);
    cluster.sync();

    // then
    assertThat(restartedB.mode()).isEqualTo(Mode.MAINTENANCE);
  }
}
