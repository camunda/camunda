/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.raft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.atomix.raft.snapshot.TestSnapshotStore;
import java.util.concurrent.CompletableFuture;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;

public class RaftStartupConsistencyCheckTest {

  @Rule public RaftRule raftRule = RaftRule.withBootstrappedNodes(3);

  @Test // Regression test for https://github.com/camunda/zeebe/issues/10451
  public void shouldNotFailRestartIfFollowerCrashedBeforeCommittingSnapshot() throws Exception {
    // given
    final var followerToRestart = raftRule.getFollower().orElseThrow();
    raftRule.partition(followerToRestart);

    final var commitIndex = raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();

    // lower the threshold so that the follower receives snapshot instead of log events
    leader.getContext().setPreferSnapshotReplicationThreshold(1);
    final long snapshotIndex = commitIndex - 1;
    raftRule.doSnapshotOnMember(leader, snapshotIndex, 1);

    final TestSnapshotStore testSnapshotStore =
        (TestSnapshotStore) followerToRestart.getContext().getPersistedSnapshotStore();
    final CompletableFuture<Void> followerCrashed = new CompletableFuture<>();
    // Throw exception before committing the snapshot and trigger shutdown to simulate "crash"
    testSnapshotStore.interceptOnNewSnapshot(
        () -> {
          try {
            followerToRestart
                .shutdown()
                .thenApply(r -> followerCrashed.complete(null))
                .exceptionally(followerCrashed::completeExceptionally);

            throw new RuntimeException("Error");
          } catch (final Exception e) {
            throw new RuntimeException(e);
          }
        });

    // when
    // reconnect follower so that it receives the snapshot, and trigger "crash"
    raftRule.reconnect(followerToRestart);
    followerCrashed.join(); // wait until follower is crashed

    // then
    // restart the follower
    final String followerId = followerToRestart.cluster().getLocalMember().memberId().id();
    assertThatNoException()
        .describedAs("Consistency check on startup does not fail.")
        .isThrownBy(
            () ->
                // recreate the server and rejoin the cluster
                raftRule.joinCluster(followerId));

    Awaitility.await("Restarted follower has received new snapshot")
        .untilAsserted(
            () ->
                assertThat(raftRule.getPersistedSnapshotStore(followerId).getCurrentSnapshotIndex())
                    .isEqualTo(snapshotIndex));
  }
}
