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

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.impl.RaftMemberContext;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;

public class RaftReplicationLagTest {

  @Rule public final RaftRule raftRule = RaftRule.withBootstrappedNodes(3);

  @Test
  public void shouldHaveZeroLagAfterReplicationCatchesUp() throws Exception {
    // given - normal cluster, all followers reachable
    raftRule.appendEntries(20);

    // when - all entries committed
    final var leader = raftRule.getLeader().orElseThrow();

    // then - every replication target sees zero lag once acks have caught up
    Awaitility.await("all replication targets have zero lag")
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              for (final RaftMemberContext member :
                  leader.getContext().getCluster().getReplicationTargets()) {
                assertThat(member.getLogReplicationLagBytes())
                    .describedAs("member %s log lag", member.getMember().memberId())
                    .isLessThanOrEqualTo(0);
                assertThat(member.getSnapshotInstallRemainingBytes())
                    .describedAs("member %s snapshot install bytes", member.getMember().memberId())
                    .isZero();
              }
            });
  }

  @Test
  public void shouldTrackLagWhilePartitionedFollowerCannotAck() throws Exception {
    // given
    final var leader = raftRule.getLeader().orElseThrow();
    final var partitioned = raftRule.getFollower().orElseThrow();
    final var partitionedId = partitioned.cluster().getLocalMember().memberId();

    raftRule.appendEntries(5); // baseline, all caught up
    awaitLagFor(leader, partitionedId, 0L);

    // when - partition the follower then append more entries
    raftRule.partition(partitioned);
    raftRule.appendEntries(10);

    // then - the partitioned follower's lag grows; the other follower stays at zero
    Awaitility.await("partitioned follower lag > 0")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              final var partitionedMember =
                  leader.getContext().getCluster().getMemberContext(partitionedId);
              assertThat(partitionedMember.getLogReplicationLagBytes()).isPositive();
            });

    for (final RaftMemberContext member :
        leader.getContext().getCluster().getReplicationTargets()) {
      if (!member.getMember().memberId().equals(partitionedId)) {
        assertThat(member.getLogReplicationLagBytes())
            .describedAs("non-partitioned follower stays caught up")
            .isLessThanOrEqualTo(0);
      }
    }
  }

  @Test
  public void shouldRecalibrateLagWhenPartitionedFollowerReconnects() throws Exception {
    // given - follower partitioned, lag accumulated
    final var leader = raftRule.getLeader().orElseThrow();
    final var partitioned = raftRule.getFollower().orElseThrow();
    final var partitionedId = partitioned.cluster().getLocalMember().memberId();

    raftRule.partition(partitioned);
    raftRule.appendEntries(10);

    Awaitility.await("partitioned follower lag > 0")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(
                        leader
                            .getContext()
                            .getCluster()
                            .getMemberContext(partitionedId)
                            .getLogReplicationLagBytes())
                    .isPositive());

    // when - heal the partition and let the follower catch up
    raftRule.reconnect(partitioned);

    // then - reset() recalibrates and subsequent acks drop lag to zero
    awaitLagFor(leader, partitionedId, 0L);
  }

  @Test
  public void shouldZeroSnapshotInstallRemainingBytesAfterInstallCompletes() throws Exception {
    // given - partition a follower so it falls behind a compacting snapshot
    final var leader = raftRule.getLeader().orElseThrow();
    final var follower = raftRule.getFollower().orElseThrow();
    final var followerId = follower.cluster().getLocalMember().memberId();

    raftRule.partition(follower);
    leader.getContext().setPreferSnapshotReplicationThreshold(1);
    final var commitIndex = raftRule.appendEntries(2);
    raftRule.takeSnapshot(leader, commitIndex, 3); // 3-chunk snapshot
    raftRule.appendEntry();

    // when - reconnect and wait for the install to land on the follower
    final var snapshotReceived = new CountDownLatch(1);
    raftRule
        .getPersistedSnapshotStore(follower.name())
        .addSnapshotListener(s -> snapshotReceived.countDown());
    raftRule.reconnect(follower);
    assertThat(snapshotReceived.await(30, TimeUnit.SECONDS)).isTrue();

    // then - install bookkeeping clears and log lag drops to zero
    Awaitility.await("follower snapshot install bytes back to zero")
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              final var member = leader.getContext().getCluster().getMemberContext(followerId);
              assertThat(member.getSnapshotInstallRemainingBytes())
                  .describedAs("snapshotInstallRemainingBytes should be cleared after install")
                  .isZero();
              assertThat(member.getLogReplicationLagBytes())
                  .describedAs("log lag should be zero once follower catches up post-install")
                  .isLessThanOrEqualTo(0);
            });
  }

  private void awaitLagFor(
      final RaftServer leader, final MemberId memberId, final long expectedLag) {
    Awaitility.await("member " + memberId + " lag == " + expectedLag)
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              final var member = leader.getContext().getCluster().getMemberContext(memberId);
              assertThat(member.getLogReplicationLagBytes()).isEqualTo(expectedLag);
            });
  }
}
