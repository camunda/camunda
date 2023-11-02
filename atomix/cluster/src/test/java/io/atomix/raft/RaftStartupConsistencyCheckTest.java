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

import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftRule.Configurator;
import io.atomix.raft.RaftServer.Builder;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.protocol.InstallRequest;
import io.atomix.raft.protocol.TestRaftServerProtocol;
import io.atomix.raft.snapshot.TestSnapshotStore;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.agrona.LangUtil;
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
    raftRule.takeSnapshot(leader, snapshotIndex, 1);

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

  @Test // regression test for https://github.com/camunda/zeebe/issues/14367
  public void shouldHandleRetriedRequestsAfterSnapshotPersist() throws Exception {
    // given -- force a follower to receive a snapshot on restart
    final var snapshotPersistCalled = new AtomicBoolean();
    final var retryBarrier = new CountDownLatch(1);
    final var leader = raftRule.getLeader().orElseThrow();
    final var follower = raftRule.getFollower().orElseThrow();
    raftRule.shutdownServer(follower);

    final var snapshotIndex = raftRule.appendEntries(500);
    raftRule.takeCompactingSnapshot(leader, snapshotIndex - 1, 3);
    final var lastIndex = raftRule.appendEntries(10);
    leader.getContext().setPreferSnapshotReplicationThreshold(1);

    // when -- persisting the snapshot on the follower is too slow, the leader will retry the
    // `install` requests
    final Runnable interceptor =
        () -> {
          try {
            snapshotPersistCalled.set(true);
            retryBarrier.await();
          } catch (final InterruptedException e) {
            LangUtil.rethrowUnchecked(e);
          }
        };
    raftRule.bootstrapNode(
        follower.name(),
        new Configurator() {
          @Override
          public void configure(final MemberId id, final Builder builder) {
            final var protocol = (TestRaftServerProtocol) builder.protocol;
            protocol.interceptRequest(
                InstallRequest.class,
                r -> {
                  // only allow the persist method to complete if we know this request was received
                  // AFTER we already tried persisting (i.e. when persist is currently blocking)
                  if (snapshotPersistCalled.get()) {
                    retryBarrier.countDown();
                  }
                });
          }

          @Override
          public void configure(final TestSnapshotStore snapshotStore) {
            snapshotStore.interceptOnNewSnapshot(interceptor);
          }
        });

    // then -- rejoined follower should catch up
    raftRule.allNodesHaveSnapshotWithIndex(snapshotIndex);
    raftRule.awaitSameLogSizeOnAllNodes(lastIndex);
  }

  @Test // Regression https://github.com/camunda/zeebe/issues/13790
  public void shouldNotTruncateCommittedEntriesAfterDataLossQuorum() throws Exception {
    // given
    final var nodeWithOldData = raftRule.getLeader().orElseThrow();
    final var commitIndex = raftRule.appendEntries(100);
    raftRule.awaitCommit(commitIndex);
    raftRule.shutdownServer(nodeWithOldData.name());

    // shutdown follower nodes
    final var followerIds =
        raftRule.getServers().stream()
            .filter(s -> s != nodeWithOldData)
            .map(RaftServer::name)
            .toList();

    // when - data loss occurs on the followers and they form quorum
    for (final var id : followerIds) {
      raftRule.shutdownServer(id);
      raftRule.triggerDataLossOnNode(id);
    }

    // followers must start asynchronously, otherwise they will never start
    final var asyncBootstraps = followerIds.stream().map(raftRule::bootstrapNodeAsync).toList();
    for (final var asyncBootstrap : asyncBootstraps) {
      asyncBootstrap.join(Duration.ofSeconds(30));
    }

    // wait until they connect, then append some entries; force an election for now to test truncate
    // TODO: should not need to force an election/term change!
    raftRule.awaitNewLeader();
    raftRule.restartLeader();
    raftRule.awaitNewLeader();
    final var newCommitIndex = raftRule.appendEntries(10);
    raftRule.awaitCommit(newCommitIndex);

    // reconnect the old node
    raftRule.bootstrapNode(nodeWithOldData.name());
    raftRule.appendEntries(5);

    // then
    Awaitility.await("until node is inactive")
        .untilAsserted(() -> assertThat(nodeWithOldData.getRole()).isEqualTo(Role.INACTIVE));
  }
}
