/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.impl.RaftContext;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * After a failed flush, the kernel marks the pages clean and reports the error only once, so a
 * retried flush succeeds without writing anything. A member which fails to flush must therefore
 * fail, instead of retrying the flush and later treating the entries as persisted.
 */
final class FlushFailureTest {
  private static final MemberId LEADER = MemberId.from("2");
  private static final MemberId FOLLOWER = MemberId.from("1");
  private static final MemberId OTHER_FOLLOWER = MemberId.from("0");

  @TempDir private Path directory;
  private ControllableRaftContexts raft;

  @BeforeEach
  void setup() throws Exception {
    raft = new ControllableRaftContexts(3);
    raft.setup(directory, new Random(1));
    electLeader();
  }

  @AfterEach
  void shutdown() throws IOException {
    raft.shutdown();
  }

  @Test
  void shouldFailFollowerWhenItFailsToFlushAppendedEntries() {
    // given
    final long lastIndex = context(LEADER).getLog().getLastIndex();
    raft.failFlushes(FOLLOWER);

    // when
    raft.clientAppendOnLeader();
    replicate();

    // then - the follower fails instead of rejecting the append and flushing again on a resend
    assertFailed(FOLLOWER);

    // then - it never acknowledges the entry, which is committed with the other follower
    assertThat(context(LEADER).getCommitIndex()).isEqualTo(lastIndex + 1);
    assertThat(matchIndexOnLeader(FOLLOWER)).isEqualTo(lastIndex);
    assertThat(context(FOLLOWER).getCommitIndex()).isLessThanOrEqualTo(lastIndex);
  }

  @Test
  void shouldNotRecoverFollowerAfterFailedFlush() {
    // given
    final long lastIndex = context(LEADER).getLog().getLastIndex();
    raft.failFlushes(FOLLOWER);
    raft.clientAppendOnLeader();
    replicate();

    // when - more entries are replicated
    raft.clientAppendOnLeader();
    replicate();

    // then - the follower stays failed, and never acknowledges them
    assertFailed(FOLLOWER);
    assertThat(context(LEADER).getCommitIndex()).isEqualTo(lastIndex + 2);
    assertThat(matchIndexOnLeader(FOLLOWER)).isEqualTo(lastIndex);
  }

  @Test
  void shouldFailLeaderWhenItFailsToFlushBeforeCommitting() {
    // given
    final long lastIndex = context(LEADER).getLog().getLastIndex();
    raft.failFlushes(LEADER);

    // when
    raft.clientAppendOnLeader();
    replicate();

    // then - the leader fails instead of stepping down and keeping the entry
    assertFailed(LEADER);
    assertThat(context(LEADER).getCommitIndex()).isEqualTo(lastIndex);
  }

  @Test
  void shouldElectNewLeaderAfterLeaderFailedToFlush() {
    // given
    raft.failFlushes(LEADER);
    raft.clientAppendOnLeader();
    replicate();
    assertFailed(LEADER);

    // when
    final var newLeader = electLeaderAmongFollowers();
    final long initialEntryIndex = context(newLeader).getLog().getLastIndex();
    for (int i = 0; i < 100 && context(newLeader).getCommitIndex() < initialEntryIndex; i++) {
      raft.tickHeartbeatTimeout(newLeader);
      raft.processAllMessage();
      raft.runUntilDone();
    }

    // then - the remaining members keep committing, and the failed leader stays inactive
    assertThat(context(newLeader).getCommitIndex()).isGreaterThanOrEqualTo(initialEntryIndex);
    assertThat(context(LEADER).getRole()).isEqualTo(Role.INACTIVE);
  }

  @Test
  void shouldNotCommitAnUnflushedEntryWhenTheAppendTimesOut() {
    // given - drain the election heartbeats, then withhold all traffic to the other follower
    for (int i = 0; i < 5; i++) {
      raft.processAllMessage();
      raft.runUntilDone();
    }
    final var leader = context(LEADER);
    final var next = leader.getLog().getLastIndex() + 1;
    raft.failFlushes(FOLLOWER);
    raft.clientAppendOnLeader();
    raft.runUntilDone(LEADER);

    // when - the append reaches the follower but its Raft thread is stalled. The leader times
    // out the RPC and sends an empty probe before the follower gets to run either request.
    raft.getServerProtocol(FOLLOWER).receiveAll();
    raft.getServerProtocol(LEADER).tick(5_001L);
    raft.runUntilDone(LEADER);
    raft.tickHeartbeatTimeout(LEADER);
    raft.getServerProtocol(FOLLOWER).receiveAll();
    raft.runUntilDone(FOLLOWER);
    raft.getServerProtocol(LEADER).receiveAll();
    raft.runUntilDone(LEADER);

    // then - the leader is the only durable copy, so it cannot commit the entry
    assertThat(context(OTHER_FOLLOWER).getLog().getLastIndex()).isLessThan(next);
    assertThat(context(FOLLOWER).getMetaStore().loadLastFlushedIndex()).isLessThan(next);
    assertThat(context(FOLLOWER).getHealthReport().isDead()).isTrue();
    assertThat(leader.getCommitIndex()).isLessThan(next);
    assertThat(matchIndexOnLeader(FOLLOWER)).isLessThan(next);
  }

  private void assertFailed(final MemberId member) {
    final var context = context(member);
    assertThat(context.getRole()).isEqualTo(Role.INACTIVE);
    final var health = context.getHealthReport();
    assertThat(health.isDead()).isTrue();
    assertThat(health.getIssue()).isNotNull();
    assertThat(health.getIssue().throwable())
        .hasMessageContaining("Failed to persist data")
        .hasRootCauseMessage("Injected flush failure");
  }

  private long matchIndexOnLeader(final MemberId member) {
    return context(LEADER).getCluster().getMemberContext(member).getMatchIndex();
  }

  private void electLeader() {
    raft.tickElectionTimeout(LEADER);
    for (int i = 0; i < 100 && context(LEADER).getRole() != Role.LEADER; i++) {
      raft.tickHeartbeatTimeout();
      raft.processAllMessage();
      raft.runUntilDone();
    }
    assertThat(context(LEADER).getRole()).isEqualTo(Role.LEADER);
    // records the leader, which clientAppendOnLeader appends on
    raft.assertAtMostOneLeader();
    replicate();
  }

  /**
   * Elects a new leader among the two followers. Only one member's election timer fires at a time,
   * so they do not split the vote.
   */
  private MemberId electLeaderAmongFollowers() {
    for (int i = 0; i < 20; i++) {
      for (final var candidate : List.of(FOLLOWER, OTHER_FOLLOWER)) {
        raft.tickElectionTimeout(candidate);
        for (int j = 0; j < 10; j++) {
          raft.processAllMessage();
          raft.runUntilDone();
        }
        if (context(candidate).getRole() == Role.LEADER) {
          return candidate;
        }
      }
    }
    throw new AssertionError("No leader elected among " + FOLLOWER + " and " + OTHER_FOLLOWER);
  }

  private void replicate() {
    for (int i = 0; i < 10; i++) {
      raft.runUntilDone();
      raft.processAllMessage();
      raft.runUntilDone();
      raft.tickHeartbeatTimeout(LEADER);
    }
  }

  private RaftContext context(final MemberId member) {
    return raft.getRaftContext(member);
  }
}
