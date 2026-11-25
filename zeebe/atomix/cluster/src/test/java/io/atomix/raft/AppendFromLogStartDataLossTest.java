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
 * A leader whose log was never compacted sends the entries from the start of its log with a
 * previous term of zero. A follower may already have some of them, e.g. because it rejected an
 * earlier append whose flush failed, but kept the entries, and then acknowledged a pipelined append
 * after them. If the follower reset its log for such a request, it would delete entries that it
 * acknowledged, and that the leader may have committed.
 */
final class AppendFromLogStartDataLossTest {
  private static final MemberId LEADER = MemberId.from("2");
  // the follower which loses its data, and whose flush then fails once
  private static final MemberId FOLLOWER = MemberId.from("1");
  // the follower which does not get the last entry
  private static final MemberId SLOW_FOLLOWER = MemberId.from("0");

  @TempDir private Path directory;
  private ControllableRaftContexts raft;

  @BeforeEach
  void setup() throws Exception {
    raft = new ControllableRaftContexts(3);
    raft.setup(directory, new Random(1));
  }

  @AfterEach
  void shutdown() throws IOException {
    raft.shutdown();
  }

  @Test
  void shouldKeepAcknowledgedCommittedEntryWhenLeaderAppendsFromLogStart() {
    // given
    final long base = commitEntryWithFollowerThenAppendFromLogStartToIt();

    // then - the follower still has the committed entry it acknowledged
    assertThat(context(FOLLOWER).getLog().getLastIndex())
        .describedAs("last index of the follower after the leader appended from the log start")
        .isEqualTo(base + 1);
    assertThat(context(FOLLOWER).getRole()).isEqualTo(Role.FOLLOWER);
  }

  @Test
  void shouldContinueReplicationAfterLeaderAppendedFromLogStart() {
    // given
    final long base = commitEntryWithFollowerThenAppendFromLogStartToIt();

    // when
    raft.clientAppendOnLeader();
    replicateToAll();

    // then
    assertThat(context(FOLLOWER).getCommitIndex()).isEqualTo(base + 2);
    raft.assertAllLogsEqual();
    raft.assertNoDataLoss();
  }

  /**
   * Builds the scenario: FOLLOWER loses its data, gets the leader's log from its start but fails to
   * flush it, and then acknowledges entry base + 1, which is committed with the leader and FOLLOWER
   * only. Finally, the leader handles the rejection and sends its log from the start again.
   *
   * @return the leader's last index before the scenario
   */
  private long commitEntryWithFollowerThenAppendFromLogStartToIt() {
    // everything is committed on all members, and no member has a snapshot
    electLeader(LEADER);
    for (int i = 0; i < 5; i++) {
      raft.clientAppendOnLeader();
    }
    replicateToAll();

    // the follower loses its data, and is unreachable while the leader is elected in a new term
    raft.restartWithDataLossWithoutCatchingUp(FOLLOWER);
    assertThat(context(FOLLOWER).getLog().isEmpty()).isTrue();
    reelectLeaderWithoutFollower();
    final var followerOnLeader = context(LEADER).getCluster().getMemberContext(FOLLOWER);

    // the new leader has to configure the follower; while the configuration is in flight, it sends
    // a heartbeat, which the follower rejects as its log is empty
    raft.tickHeartbeatTimeout(LEADER);
    raft.runUntilDone(LEADER);
    raft.tickHeartbeatTimeout(LEADER);
    raft.runUntilDone(LEADER);
    raft.getServerProtocol(FOLLOWER).dropNextMessage();
    deliverAll(FOLLOWER);
    deliverAll(LEADER);
    assertThat(followerOnLeader.getNextIndex()).isOne();

    // while the next configuration is in flight, the leader sends a heartbeat from the log start,
    // which the follower acknowledges, so that the leader sends further appends without waiting
    raft.tickHeartbeatTimeout(LEADER);
    raft.runUntilDone(LEADER);
    deliverAll(FOLLOWER);
    deliverAll(SLOW_FOLLOWER);
    assertThat(context(FOLLOWER).getLog().isEmpty()).isTrue();

    // the leader sends its log from the start, and right after it the next entry, base + 1, which
    // the slow follower never gets
    final long base = context(LEADER).getLog().getLastIndex();
    deliverAll(LEADER);
    deliverAll(SLOW_FOLLOWER);
    raft.clientAppendOnLeader();
    raft.runUntilDone(LEADER);
    raft.getServerProtocol(SLOW_FOLLOWER).dropNextMessage();

    // the follower fails to flush the entries from the log start: it rejects the append, but keeps
    // the entries; then it appends, flushes and acknowledges entry base + 1
    raft.failNextFlush(FOLLOWER);
    deliverAll(FOLLOWER);
    assertThat(context(FOLLOWER).getLog().getLastIndex()).isEqualTo(base + 1);

    // the leader handles the rejection, which makes it send its log from the start again, and the
    // acknowledgement, which commits entry base + 1 with the follower
    deliverAll(LEADER);
    assertThat(context(LEADER).getCommitIndex()).isEqualTo(base + 1);
    assertThat(context(SLOW_FOLLOWER).getLog().getLastIndex()).isEqualTo(base);

    // the follower handles the append from the log start
    deliverAll(FOLLOWER);
    return base;
  }

  private void electLeader(final MemberId leader) {
    raft.tickElectionTimeout(leader);
    for (int i = 0; i < 100 && context(leader).getRole() != Role.LEADER; i++) {
      raft.tickHeartbeatTimeout();
      raft.processAllMessage();
      raft.runUntilDone();
    }
    assertThat(context(leader).getRole()).isEqualTo(Role.LEADER);
    // records the leader, which clientAppendOnLeader appends on
    raft.assertAtMostOneLeader();
    replicateToAll();
  }

  /**
   * Elects LEADER again in a new term, while FOLLOWER is unreachable: LEADER steps down, and wins
   * the next election with the vote of SLOW_FOLLOWER.
   */
  private void reelectLeaderWithoutFollower() {
    final var leader = context(LEADER);
    final long term = leader.getTerm();
    leader.getThreadContext().execute(() -> leader.transition(Role.FOLLOWER));
    raft.runUntilDone(LEADER);
    raft.tickElectionTimeout(LEADER);
    final var others = List.of(LEADER, SLOW_FOLLOWER);
    for (int i = 0;
        i < 100
            && !(leader.isLeader()
                && leader.getTerm() > term
                && others.stream()
                    .allMatch(m -> context(m).getCommitIndex() == leader.getLog().getLastIndex()));
        i++) {
      dropAll(FOLLOWER);
      others.forEach(this::deliverAll);
      raft.tickHeartbeatTimeout(LEADER);
      raft.runUntilDone(LEADER);
    }
    dropAll(FOLLOWER);
    others.forEach(this::deliverAll);
    assertThat(leader.getRole()).isEqualTo(Role.LEADER);
    assertThat(leader.getTerm()).isGreaterThan(term);
    // records the leader, which clientAppendOnLeader appends on
    raft.assertAtMostOneLeader();
  }

  private void replicateToAll() {
    final var leader = context(LEADER);
    for (int i = 0; i < 100; i++) {
      raft.processAllMessage();
      raft.runUntilDone();
      if (List.of(LEADER, FOLLOWER, SLOW_FOLLOWER).stream()
          .allMatch(m -> context(m).getCommitIndex() == leader.getLog().getLastIndex())) {
        return;
      }
      raft.tickHeartbeatTimeout(LEADER);
    }
    assertThat(List.of(LEADER, FOLLOWER, SLOW_FOLLOWER))
        .allSatisfy(
            m -> assertThat(context(m).getCommitIndex()).isEqualTo(leader.getLog().getLastIndex()));
  }

  private void deliverAll(final MemberId member) {
    raft.getServerProtocol(member).receiveAll();
    raft.runUntilDone(member);
  }

  private void dropAll(final MemberId member) {
    final var protocol = raft.getServerProtocol(member);
    for (int i = 0; i < 100; i++) {
      protocol.dropNextMessage();
    }
  }

  private RaftContext context(final MemberId member) {
    return raft.getRaftContext(member);
  }
}
