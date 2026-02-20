/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.atomix.raft.protocol.InstallRequest;
import io.atomix.raft.protocol.TestRaftServerProtocol;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.Rule;
import org.junit.Test;

/**
 * Integration test verifying that an in-progress snapshot transfer is NOT aborted when the follower
 * receives empty heartbeat AppendRequests from the leader during the transfer.
 *
 * <p>This test validates the fix for SUPPORT-31571: during network flapping, the leader enters a
 * failure backoff path and sends empty AppendRequests (heartbeats) instead of snapshot chunks. The
 * old code unconditionally aborted the pending snapshot on any AppendRequest, causing wasteful
 * rollback cycles. The fix only aborts the snapshot when log entries are actually appended.
 */
public class SnapshotSurvivesHeartbeatDuringTransferTest {

  @Rule public RaftRule raftRule = RaftRule.withBootstrappedNodes(3);

  @Test
  public void shouldCompleteSnapshotDespiteHeartbeatsDuringTransfer() throws Throwable {
    // given -- a follower that is behind and needs a snapshot
    final var leader = raftRule.getLeader().orElseThrow();
    final var follower = raftRule.getFollower().orElseThrow();
    final var leaderProtocol = (TestRaftServerProtocol) leader.getContext().getProtocol();

    // Register a SnapshotReplicationListener on the follower to track lifecycle
    final var snapshotReplicationListener = mock(SnapshotReplicationListener.class);
    follower.getContext().addSnapshotReplicationListener(snapshotReplicationListener);

    // Partition the follower and create conditions requiring snapshot replication
    raftRule.partition(follower);
    leader.getContext().setPreferSnapshotReplicationThreshold(1);
    final var commitIndex = raftRule.appendEntries(2);

    // Use multiple chunks so we can pause mid-transfer
    final int numberOfChunks = 5;
    raftRule.takeSnapshot(leader, commitIndex, numberOfChunks);
    raftRule.appendEntry();

    // Set up an interceptor that pauses snapshot transfer after the first chunk.
    // This creates a window where the leader's heartbeat timer will send empty AppendRequests
    // to the follower while the snapshot transfer is "in flight" -- exactly the scenario that
    // caused the rollback storm in SUPPORT-31571.
    final var firstChunkSent = new CountDownLatch(1);
    final var resumeTransfer = new CountDownLatch(1);
    final var chunkCount = new AtomicInteger(0);

    leaderProtocol.interceptRequest(
        InstallRequest.class,
        (Function<InstallRequest, CompletableFuture<Void>>)
            request -> {
              final int chunk = chunkCount.incrementAndGet();
              if (chunk == 1) {
                // Let the first chunk through, then signal that it was sent
                firstChunkSent.countDown();
                return CompletableFuture.completedFuture(null);
              } else {
                // Block subsequent chunks until we release the latch
                try {
                  if (!resumeTransfer.await(30, TimeUnit.SECONDS)) {
                    return CompletableFuture.failedFuture(
                        new RuntimeException("Timed out waiting for resume signal"));
                  }
                } catch (final InterruptedException e) {
                  Thread.currentThread().interrupt();
                  return CompletableFuture.failedFuture(e);
                }
                return CompletableFuture.completedFuture(null);
              }
            });

    // when -- reconnect the follower; the first snapshot chunk will be sent, then heartbeats
    // will flow while the remaining chunks are paused
    final var snapshotReceived = new CountDownLatch(1);
    raftRule
        .getPersistedSnapshotStore(follower.name())
        .addSnapshotListener(s -> snapshotReceived.countDown());
    raftRule.reconnect(follower);

    // Wait for the first chunk to be sent and processed
    assertThat(firstChunkSent.await(30, TimeUnit.SECONDS))
        .describedAs("First snapshot chunk should be sent")
        .isTrue();

    // Verify that snapshot replication has started on the follower
    verify(snapshotReplicationListener, timeout(5_000).times(1)).onSnapshotReplicationStarted();

    // Let heartbeats flow for a while. The leader's heartbeat interval is 100ms, so 500ms
    // gives at least 4-5 heartbeats (empty AppendRequests) to the follower while the snapshot
    // is in-flight. Before the fix, any of these would have aborted the pending snapshot.
    Thread.sleep(500);

    // Resume the snapshot transfer -- release remaining chunks
    resumeTransfer.countDown();

    // then -- the snapshot should complete successfully despite the heartbeats
    assertThat(snapshotReceived.await(30, TimeUnit.SECONDS))
        .describedAs("Snapshot should be received successfully despite heartbeats during transfer")
        .isTrue();

    // Verify the full snapshot replication lifecycle completed
    verify(snapshotReplicationListener, timeout(5_000).times(1))
        .onSnapshotReplicationCompleted(follower.getTerm());
  }
}
