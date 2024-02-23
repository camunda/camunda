/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.raft;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftServer.Role;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameter;

public class RaftServerDisconnectTest {

  @Rule @Parameter public RaftRule raftRule = RaftRule.withBootstrappedNodes(3);

  @Test
  public void shouldLeaderStepDownOnDisconnect() throws Throwable {
    final RaftServer leader = raftRule.getLeader().orElseThrow();

    final CountDownLatch stepDownListener = new CountDownLatch(1);
    leader.addRoleChangeListener(
        (role, term) -> {
          if (role == Role.FOLLOWER) {
            stepDownListener.countDown();
          }
        });

    // when
    raftRule.partition(leader);

    // then
    assertThat(stepDownListener.await(30, TimeUnit.SECONDS)).isTrue();
    assertThat(leader.isLeader()).isFalse();
  }

  @Test
  public void shouldReconnect() throws Throwable {
    // given
    final RaftServer leader = raftRule.getLeader().orElseThrow();
    final AtomicLong commitIndex = new AtomicLong();
    leader.getContext().addCommitListener(commitIndex::set);
    raftRule.appendEntry();
    raftRule.partition(leader);
    Awaitility.await().until(() -> !leader.isLeader());

    // when
    raftRule.awaitNewLeader();
    final var newLeader = raftRule.getLeader().orElseThrow();
    assertThat(leader).isNotEqualTo(newLeader);
    final var secondCommit = raftRule.appendEntry();
    raftRule.reconnect(leader);

    // then - the old leader has received and committed the new entry
    Awaitility.await().until(() -> commitIndex.get() >= secondCommit);
  }

  @Test
  public void shouldFailOverOnLeaderDisconnect() throws Throwable {
    final RaftServer leader = raftRule.getLeader().orElseThrow();
    final MemberId leaderId = leader.getContext().getCluster().getLocalMember().memberId();

    final CountDownLatch newLeaderElected = new CountDownLatch(1);
    final AtomicReference<MemberId> newLeaderId = new AtomicReference<>();
    raftRule
        .getServers()
        .forEach(
            s ->
                s.addRoleChangeListener(
                    (role, term) -> {
                      if (role == Role.LEADER && !s.equals(leader)) {
                        newLeaderId.set(s.getContext().getCluster().getLocalMember().memberId());
                        newLeaderElected.countDown();
                      }
                    }));
    // when
    raftRule.partition(leader);

    // then
    assertThat(newLeaderElected.await(30, TimeUnit.SECONDS)).isTrue();
    assertThat(leaderId).isNotEqualTo(newLeaderId.get());
  }
}
