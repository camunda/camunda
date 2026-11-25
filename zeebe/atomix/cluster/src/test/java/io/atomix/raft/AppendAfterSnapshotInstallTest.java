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
import io.atomix.raft.protocol.AppendResponse;
import io.atomix.raft.protocol.ProtocolVersionHandler;
import io.atomix.raft.protocol.VersionedAppendRequest;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A follower which installed a snapshot has a log that starts right after the snapshot. When the
 * leader appends with the snapshot as the previous entry, the follower must check the previous
 * entry against its snapshot, not against the first entry of its log, which can have a different
 * term. Otherwise, it rejects every append, and the leader never catches it up.
 */
final class AppendAfterSnapshotInstallTest {
  private static final MemberId OLD_LEADER = MemberId.from("0");
  // the leader which sends its snapshot to the follower
  private static final MemberId LEADER = MemberId.from("1");
  // the follower which falls behind, and then receives the snapshot
  private static final MemberId FOLLOWER = MemberId.from("2");

  @TempDir private Path directory;
  private ControllableRaftContexts raft;

  @BeforeEach
  void setup() throws Exception {
    // a threshold of 0 makes the leader send a snapshot to any follower behind its snapshot; with
    // the default of 100, the same happens once the follower is more than 100 entries behind
    raft =
        new ControllableRaftContexts(3, config -> config.setPreferSnapshotReplicationThreshold(0));
    raft.setup(directory, new Random(1));
  }

  @AfterEach
  void shutdown() throws IOException {
    raft.shutdown();
  }

  @Test
  void shouldCatchUpFollowerWhoseLogStartsWithNewerTermThanItsSnapshot() {
    // given - the follower installed the leader's snapshot, whose entry has an older term than the
    // entry after it
    final long snapshotIndex = installSnapshotOfOlderTermOnFollower();

    // when - the follower appends the entries after the snapshot, but fails to flush them, so the
    // leader appends them again, with the snapshot as the previous entry
    raft.failNextFlush(FOLLOWER);
    raft.deliverAll(FOLLOWER);
    assertThat(raft.getRaftContext(FOLLOWER).getLog().getFirstIndex()).isEqualTo(snapshotIndex + 1);
    assertThat(raft.getRaftContext(FOLLOWER).getLog().getLastEntry().term())
        .isGreaterThan(raft.getRaftContext(FOLLOWER).getCurrentSnapshot().getTerm());
    raft.clientAppendOnLeader();
    replicateTo(FOLLOWER);

    // then
    assertThat(raft.getRaftContext(FOLLOWER).getCommitIndex())
        .isEqualTo(raft.getRaftContext(LEADER).getLog().getLastIndex());
    raft.assertAllLogsEqual();
  }

  @Test
  void shouldRejectPreviousEntryBeforeSnapshotWithSnapshotIndexAsLastLogIndex() {
    // given - the follower's log starts right after its snapshot
    final long snapshotIndex = followerWithLogAfterSnapshot();
    final long snapshotTerm = raft.getRaftContext(FOLLOWER).getCurrentSnapshot().getTerm();

    // when - the previous entry is covered by the snapshot, but is not the snapshot itself
    final var response = appendOnFollower(snapshotIndex - 1, snapshotTerm);

    // then - the leader continues after the snapshot, and the log is kept
    assertThat(response.succeeded()).isFalse();
    assertThat(response.lastLogIndex()).isEqualTo(snapshotIndex);
    assertThat(raft.getRaftContext(FOLLOWER).getLog().getFirstIndex()).isEqualTo(snapshotIndex + 1);
    assertThat(raft.getRaftContext(FOLLOWER).getLog().getLastIndex()).isEqualTo(snapshotIndex + 1);
  }

  @Test
  void shouldRejectPreviousEntryAtSnapshotIndexWithAnotherTerm() {
    // given - the follower's log starts right after its snapshot
    final long snapshotIndex = followerWithLogAfterSnapshot();
    final long snapshotTerm = raft.getRaftContext(FOLLOWER).getCurrentSnapshot().getTerm();

    // when
    final var response = appendOnFollower(snapshotIndex, snapshotTerm + 1);

    // then - the leader probes the entry before, and the log is kept
    assertThat(response.succeeded()).isFalse();
    assertThat(response.lastLogIndex()).isEqualTo(snapshotIndex - 1);
    assertThat(raft.getRaftContext(FOLLOWER).getLog().getFirstIndex()).isEqualTo(snapshotIndex + 1);
    assertThat(raft.getRaftContext(FOLLOWER).getLog().getLastIndex()).isEqualTo(snapshotIndex + 1);
  }

  /**
   * Builds the scenario of {@link #installSnapshotOfOlderTermOnFollower()}, and lets the follower
   * append the entry after the snapshot.
   *
   * @return the index of the snapshot
   */
  private long followerWithLogAfterSnapshot() {
    final long snapshotIndex = installSnapshotOfOlderTermOnFollower();
    raft.deliverAll(FOLLOWER);
    assertThat(raft.getRaftContext(FOLLOWER).getLog().getFirstIndex()).isEqualTo(snapshotIndex + 1);
    assertThat(raft.getRaftContext(FOLLOWER).getLog().getLastIndex()).isEqualTo(snapshotIndex + 1);
    return snapshotIndex;
  }

  /** Sends an empty append with the given previous entry to the follower, as the leader. */
  private AppendResponse appendOnFollower(final long prevLogIndex, final long prevLogTerm) {
    final var follower = raft.getRaftContext(FOLLOWER);
    final var request =
        VersionedAppendRequest.builder()
            .withTerm(follower.getTerm())
            .withLeader(LEADER)
            .withPrevLogIndex(prevLogIndex)
            .withPrevLogTerm(prevLogTerm)
            .withEntries(List.of())
            .withCommitIndex(follower.getCommitIndex())
            .build();
    final var response = new AtomicReference<CompletableFuture<AppendResponse>>();
    follower
        .getThreadContext()
        .execute(
            () ->
                response.set(
                    follower.getRaftRole().onAppend(ProtocolVersionHandler.transform(request))));
    raft.runUntilDone(FOLLOWER);
    return response.get().join();
  }

  /**
   * Builds the scenario: entries up to the returned index are committed in the old leader's term
   * without the follower. A new leader appends its initial entry in a newer term, takes a snapshot
   * at the returned index, and sends it to the follower. The follower installs it, and the append
   * with the entries after the snapshot is on its way to the follower.
   *
   * @return the index of the snapshot
   */
  private long installSnapshotOfOlderTermOnFollower() {
    electLeader(OLD_LEADER);

    // entries are committed without the follower, which does not get them
    for (int i = 0; i < 5; i++) {
      raft.clientAppendOnLeader();
    }
    for (int i = 0; i < 10; i++) {
      raft.tickHeartbeatTimeout(OLD_LEADER);
      raft.deliverAll(LEADER);
      raft.deliverAll(OLD_LEADER);
      dropAll(FOLLOWER);
    }
    final long snapshotIndex = raft.getRaftContext(OLD_LEADER).getLog().getLastIndex();
    assertThat(raft.getRaftContext(OLD_LEADER).getCommitIndex()).isEqualTo(snapshotIndex);
    assertThat(raft.getRaftContext(LEADER).getCommitIndex()).isEqualTo(snapshotIndex);
    assertThat(raft.getRaftContext(FOLLOWER).getLog().getLastIndex()).isLessThan(snapshotIndex);

    // a new leader is elected without the follower, and commits its initial entry
    raft.transferLeadershipTo(LEADER);
    for (int i = 0; i < 100 && raft.getRaftContext(LEADER).getRole() != Role.LEADER; i++) {
      raft.deliverAll(LEADER);
      raft.deliverAll(OLD_LEADER);
      dropAll(FOLLOWER);
    }
    assertThat(raft.getRaftContext(LEADER).getRole()).isEqualTo(Role.LEADER);
    raft.assertAtMostOneLeader();
    for (int i = 0; i < 10; i++) {
      raft.tickHeartbeatTimeout(LEADER);
      raft.deliverAll(OLD_LEADER);
      raft.deliverAll(LEADER);
      dropAll(FOLLOWER);
    }
    assertThat(raft.getRaftContext(LEADER).getCommitIndex()).isEqualTo(snapshotIndex + 1);
    assertThat(raft.getRaftContext(LEADER).getTerm()).isGreaterThan(termAt(LEADER, snapshotIndex));

    // the leader takes a snapshot of the last entry of the old leader's term, and sends it to the
    // follower
    raft.takeSnapshot(LEADER, snapshotIndex);
    raft.runUntilDone(LEADER);
    for (int i = 0;
        i < 100 && raft.getRaftContext(FOLLOWER).getCurrentSnapshotIndex() < snapshotIndex;
        i++) {
      raft.tickHeartbeatTimeout(LEADER);
      raft.deliverAll(FOLLOWER);
      raft.deliverAll(LEADER);
    }
    assertThat(raft.getRaftContext(FOLLOWER).getCurrentSnapshotIndex()).isEqualTo(snapshotIndex);
    assertThat(raft.getRaftContext(FOLLOWER).getLog().isEmpty()).isTrue();
    return snapshotIndex;
  }

  private void electLeader(final MemberId leader) {
    raft.tickElectionTimeout(leader);
    for (int i = 0; i < 100 && raft.getRaftContext(leader).getRole() != Role.LEADER; i++) {
      raft.tickHeartbeatTimeout();
      raft.processAllMessage();
      raft.runUntilDone();
    }
    assertThat(raft.getRaftContext(leader).getRole()).isEqualTo(Role.LEADER);
    // records the leader, which clientAppendOnLeader appends on
    raft.assertAtMostOneLeader();
    for (int i = 0; i < 10; i++) {
      raft.tickHeartbeatTimeout(leader);
      raft.processAllMessage();
      raft.runUntilDone();
    }
    assertThat(List.of(OLD_LEADER, LEADER, FOLLOWER))
        .allSatisfy(
            member ->
                assertThat(raft.getRaftContext(member).getCommitIndex())
                    .isEqualTo(raft.getRaftContext(leader).getLog().getLastIndex()));
  }

  private void replicateTo(final MemberId follower) {
    final var leader = raft.getRaftContext(LEADER);
    for (int i = 0;
        i < 100 && raft.getRaftContext(follower).getCommitIndex() < leader.getLog().getLastIndex();
        i++) {
      raft.tickHeartbeatTimeout(LEADER);
      raft.deliverAll(OLD_LEADER);
      raft.deliverAll(follower);
      raft.deliverAll(LEADER);
    }
  }

  private long termAt(final MemberId member, final long index) {
    try (final var reader = raft.getRaftContext(member).getLog().openCommittedReader()) {
      reader.seek(index);
      return reader.next().term();
    }
  }

  private void dropAll(final MemberId member) {
    raft.runUntilDone();
    raft.getServerProtocol(member).dropAllMessages();
  }
}
