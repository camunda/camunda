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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A leader may send a snapshot to a follower whose log is further ahead than the leader thinks,
 * e.g. after the follower rejected an append because its flush failed, but kept the entries. If the
 * follower installed the snapshot, it would delete entries after the snapshot index that it had
 * acknowledged, and that the leader may have committed.
 */
final class SnapshotInstallDataLossTest {
  private static final MemberId LEADER = MemberId.from("2");
  // the follower whose flush fails once, and which then receives a snapshot
  private static final MemberId FOLLOWER = MemberId.from("1");
  // the follower which falls behind
  private static final MemberId SLOW_FOLLOWER = MemberId.from("0");

  @TempDir private Path directory;
  private ControllableRaftContexts raft;
  private final AtomicInteger snapshotReplicationsStarted = new AtomicInteger();

  @BeforeEach
  void setup() throws Exception {
    // a threshold of 0 makes the leader send a snapshot to any follower behind its snapshot; with
    // the default of 100, the same happens once the follower is more than 100 entries behind
    raft =
        new ControllableRaftContexts(3, config -> config.setPreferSnapshotReplicationThreshold(0));
    raft.setup(directory, new Random(1));
    context(FOLLOWER)
        .addSnapshotReplicationListener(
            new SnapshotReplicationListener() {
              @Override
              public void onSnapshotReplicationStarted() {
                snapshotReplicationsStarted.incrementAndGet();
              }

              @Override
              public void onSnapshotReplicationCompleted(final long term) {}
            });
  }

  @AfterEach
  void shutdown() throws IOException {
    raft.shutdown();
  }

  @Test
  void shouldKeepAcknowledgedCommittedEntryWhenLeaderSendsSnapshot() {
    // given
    final long base = commitEntryWithFollowerThenSendOlderSnapshotToIt();

    // then - the follower still has the committed entry it acknowledged, and did not start to
    // receive the snapshot
    assertThat(context(FOLLOWER).getLog().getLastIndex())
        .describedAs("last index of the follower after the leader sent the snapshot")
        .isEqualTo(base + 2);
    assertThat(context(FOLLOWER).getCurrentSnapshotIndex()).isZero();
    assertThat(snapshotReplicationsStarted).hasValue(0);
  }

  @Test
  void shouldContinueReplicationAfterFollowerSkippedSnapshot() {
    // given
    final long base = commitEntryWithFollowerThenSendOlderSnapshotToIt();

    // when
    raft.clientAppendOnLeader();
    replicateToAll();

    // then - the leader appends after the snapshot instead of sending it again
    assertThat(context(FOLLOWER).getCommitIndex()).isEqualTo(base + 3);
    assertThat(context(FOLLOWER).getCurrentSnapshotIndex()).isZero();
    assertThat(snapshotReplicationsStarted).hasValue(0);
    raft.assertAllLogsEqual();
  }

  @Test
  void shouldNotOverwriteCommittedEntryAfterLeaderSentSnapshotToFollower() {
    // given
    final long base = commitEntryWithFollowerThenSendOlderSnapshotToIt();

    // when - the old leader is gone, and the two followers elect a new leader, which commits its
    // initial entry
    final var newLeader = electLeaderAmongFollowers();
    final long initialEntryIndex = context(newLeader).getLog().getLastIndex();
    for (int i = 0; i < 100 && context(newLeader).getCommitIndex() < initialEntryIndex; i++) {
      raft.tickHeartbeatTimeout(newLeader);
      deliverAll(FOLLOWER);
      deliverAll(SLOW_FOLLOWER);
    }
    assertThat(context(newLeader).getCommitIndex()).isGreaterThanOrEqualTo(initialEntryIndex);

    // then - the entry the old leader committed at base + 2 is not replaced
    raft.assertAllLogsEqual();
  }

  /**
   * Builds the scenario: entry base + 2 is committed with the leader and FOLLOWER only, then the
   * leader sends its snapshot at base + 1 to FOLLOWER, and crashes before it replicates anything
   * else.
   *
   * @return the last index which all members had in common before the scenario
   */
  private long commitEntryWithFollowerThenSendOlderSnapshotToIt() {
    // everything is committed on all members
    electLeader(LEADER);
    for (int i = 0; i < 5; i++) {
      raft.clientAppendOnLeader();
    }
    replicateToAll();
    final long base = context(LEADER).getLog().getLastIndex();
    assertThat(List.of(LEADER, FOLLOWER, SLOW_FOLLOWER))
        .allSatisfy(member -> assertThat(context(member).getCommitIndex()).isEqualTo(base));

    // entry base + 1 is committed with the slow follower, and the leader takes a snapshot of it
    raft.clientAppendOnLeader();
    raft.runUntilDone(LEADER);
    deliverAll(SLOW_FOLLOWER);
    deliverAll(LEADER);
    assertThat(context(LEADER).getCommitIndex()).isEqualTo(base + 1);
    raft.takeSnapshot(LEADER, base + 1);
    raft.runUntilDone(LEADER);

    // the follower fails to flush entry base + 1: it rejects the append, but keeps the entry
    raft.failNextFlush(FOLLOWER);
    deliverAll(FOLLOWER);
    assertThat(context(FOLLOWER).getLog().getLastIndex()).isEqualTo(base + 1);

    // before the leader sees the rejection, it pipelines entry base + 2 to the follower, which
    // appends and flushes it; the slow follower never gets it
    raft.clientAppendOnLeader();
    raft.runUntilDone(LEADER);
    deliverAll(FOLLOWER);
    assertThat(context(FOLLOWER).getLog().getLastIndex()).isEqualTo(base + 2);
    raft.getServerProtocol(SLOW_FOLLOWER).dropNextMessage();

    // the leader handles the rejection, which makes it send its snapshot to the follower, and
    // then the acknowledgement, which commits entry base + 2 with the follower
    deliverAll(LEADER);
    assertThat(context(LEADER).getCommitIndex()).isEqualTo(base + 2);
    assertThat(context(SLOW_FOLLOWER).getLog().getLastIndex()).isEqualTo(base + 1);

    // the follower handles the snapshot, i.e. it either installs it, or skips it and gets the
    // commit index with the next append; the leader crashes before it replicates anything else
    for (int i = 0; i < 20; i++) {
      deliverAll(FOLLOWER);
      if (context(FOLLOWER).getCurrentSnapshotIndex() >= base + 1
          || context(FOLLOWER).getCommitIndex() >= base + 2) {
        break;
      }
      deliverAll(LEADER);
    }
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
   * Elects a new leader among the two followers, without the old leader, which is gone. Only one
   * member's election timer fires at a time, so they do not split the vote.
   */
  private MemberId electLeaderAmongFollowers() {
    for (int i = 0; i < 20; i++) {
      for (final var candidate : List.of(FOLLOWER, SLOW_FOLLOWER)) {
        raft.tickElectionTimeout(candidate);
        for (int j = 0; j < 10; j++) {
          deliverAll(FOLLOWER);
          deliverAll(SLOW_FOLLOWER);
        }
        if (context(candidate).getRole() == Role.LEADER) {
          raft.assertAtMostOneLeader();
          return candidate;
        }
      }
    }
    throw new AssertionError("No leader elected among " + FOLLOWER + " and " + SLOW_FOLLOWER);
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
  }

  private void deliverAll(final MemberId member) {
    raft.getServerProtocol(member).receiveAll();
    raft.runUntilDone(member);
  }

  private RaftContext context(final MemberId member) {
    return raft.getRaftContext(member);
  }
}
