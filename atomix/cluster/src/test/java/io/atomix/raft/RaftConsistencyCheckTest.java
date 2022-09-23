/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.raft;

import static org.assertj.core.api.Assertions.assertThatNoException;

import io.atomix.raft.snapshot.TestSnapshotStore;
import java.util.concurrent.CompletableFuture;
import org.junit.Rule;
import org.junit.Test;

public class RaftConsistencyCheckTest {

  @Rule public RaftRule raftRule = RaftRule.withBootstrappedNodes(3);

  @Test
  public void shouldNotFailRestartIfFollowerCrashedBeforeCommittingSnapshot() throws Exception {
    // given
    final var followerToRestart = raftRule.getFollower().orElseThrow();
    raftRule.partition(followerToRestart);

    final var commitIndex = raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();

    // lower the threshold so that the follower recieves snapshot instead of log events
    leader.getContext().setPreferSnapshotReplicationThreshold(1);
    raftRule.doSnapshotOnMember(leader, commitIndex - 1, 1);

    final TestSnapshotStore testSnapshotStore =
        (TestSnapshotStore) followerToRestart.getContext().getPersistedSnapshotStore();
    final CompletableFuture<Void> followerCrashed = new CompletableFuture<>();
    // Throw exception before committing the snapshot and trigger shutdown to simulat "crash"
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

    // reconnect follower so that it receives the snapshot, and trigger "crash"
    raftRule.reconnect(followerToRestart);
    followerCrashed.join(); // wait until follower is crashed

    // restart the follower
    assertThatNoException()
        .describedAs("Consistency check on startup does not fail.")
        .isThrownBy(
            () ->
                raftRule.joinCluster(followerToRestart.cluster().getLocalMember().memberId().id()));
  }
}
