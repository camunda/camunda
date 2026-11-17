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
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A leader which received its snapshot by an install has a log that starts right after the
 * snapshot. A follower whose log ends at the snapshot's index does not need the snapshot: the next
 * append can use the snapshot as its previous entry.
 */
final class SnapshotReplicationAfterSnapshotInstallTest {
  private static final MemberId OLD_LEADER = MemberId.from("0");
  // its log ends at the snapshot index when the new leader takes over
  private static final MemberId FOLLOWER = MemberId.from("1");
  // installs the snapshot, and then becomes leader
  private static final MemberId NEW_LEADER = MemberId.from("2");

  @TempDir private Path directory;
  private ControllableRaftContexts raft;
  private final AtomicInteger snapshotReplicationsStarted = new AtomicInteger();

  @BeforeEach
  void setup() throws Exception {
    // a threshold of 0 makes the leader send a snapshot to any follower behind its snapshot
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
  void shouldAppendAfterSnapshotWhenLeadersLogStartsAfterIt() {
    // given - the new leader installed the old leader's snapshot, and its log starts after it
    final long snapshotIndex = replicateSnapshotToNewLeaderOnly();
    assertThat(context(NEW_LEADER).getLog().getFirstIndex()).isEqualTo(snapshotIndex + 1);
    assertThat(context(FOLLOWER).getLog().getLastIndex()).isEqualTo(snapshotIndex);

    // when
    electNewLeader();
    final long lastIndex = context(NEW_LEADER).getLog().getLastIndex();
    for (int i = 0; i < 20 && context(FOLLOWER).getCommitIndex() < lastIndex; i++) {
      raft.tickHeartbeatTimeout(NEW_LEADER);
      deliverAll(FOLLOWER);
      deliverAll(NEW_LEADER);
    }

    // then - the follower catches up with appends, without receiving the snapshot
    assertThat(context(FOLLOWER).getCommitIndex()).isEqualTo(lastIndex);
    assertThat(context(FOLLOWER).getLog().getFirstIndex()).isOne();
    assertThat(snapshotReplicationsStarted).hasValue(0);
  }

  /**
   * @return the index of the old leader's snapshot, which the new leader installed
   */
  private long replicateSnapshotToNewLeaderOnly() {
    raft.tickElectionTimeout(OLD_LEADER);
    for (int i = 0; i < 100 && context(OLD_LEADER).getRole() != Role.LEADER; i++) {
      raft.tickHeartbeatTimeout();
      raft.processAllMessage();
      raft.runUntilDone();
    }
    assertThat(context(OLD_LEADER).getRole()).isEqualTo(Role.LEADER);
    // records the leader, which clientAppendOnLeader appends on
    raft.assertAtMostOneLeader();

    // the new leader falls behind, while the others commit up to the snapshot index
    for (int i = 0; i < 5; i++) {
      raft.clientAppendOnLeader();
    }
    raft.runUntilDone(OLD_LEADER);
    final long snapshotIndex = context(OLD_LEADER).getLog().getLastIndex();
    replicateOnlyTo(FOLLOWER, NEW_LEADER, snapshotIndex);
    raft.takeSnapshot(OLD_LEADER, snapshotIndex);
    raft.runUntilDone(OLD_LEADER);

    // the new leader installs the snapshot, and then commits one more entry without the follower
    for (int i = 0; i < 20 && context(NEW_LEADER).getCurrentSnapshotIndex() < snapshotIndex; i++) {
      raft.tickHeartbeatTimeout(OLD_LEADER);
      dropAll(FOLLOWER);
      deliverAll(NEW_LEADER);
      deliverAll(OLD_LEADER);
    }
    assertThat(context(NEW_LEADER).getCurrentSnapshotIndex()).isEqualTo(snapshotIndex);
    raft.clientAppendOnLeader();
    replicateOnlyTo(NEW_LEADER, FOLLOWER, snapshotIndex + 1);

    // the old leader crashes
    dropAll(FOLLOWER);
    dropAll(NEW_LEADER);
    return snapshotIndex;
  }

  private void replicateOnlyTo(final MemberId member, final MemberId other, final long index) {
    for (int i = 0; i < 20 && context(OLD_LEADER).getCommitIndex() < index; i++) {
      raft.runUntilDone(OLD_LEADER);
      dropAll(other);
      deliverAll(member);
      deliverAll(OLD_LEADER);
      raft.tickHeartbeatTimeout(OLD_LEADER);
    }
    assertThat(context(OLD_LEADER).getCommitIndex()).isEqualTo(index);
  }

  private void electNewLeader() {
    for (int i = 0; i < 20 && context(NEW_LEADER).getRole() != Role.LEADER; i++) {
      raft.tickElectionTimeout(NEW_LEADER);
      for (int j = 0; j < 10; j++) {
        deliverAll(FOLLOWER);
        deliverAll(NEW_LEADER);
      }
    }
    assertThat(context(NEW_LEADER).getRole()).isEqualTo(Role.LEADER);
  }

  private void dropAll(final MemberId member) {
    for (int i = 0; i < 100; i++) {
      raft.getServerProtocol(member).dropNextMessage();
    }
    raft.runUntilDone(member);
  }

  private void deliverAll(final MemberId member) {
    raft.getServerProtocol(member).receiveAll();
    raft.runUntilDone(member);
  }

  private RaftContext context(final MemberId member) {
    return raft.getRaftContext(member);
  }
}
