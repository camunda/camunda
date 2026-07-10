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
import io.atomix.raft.roles.LeaderRole;
import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;

/**
 * Coverage for the leader-side transfer pre-checks and the catch-up wait (Coordinated Leadership
 * Transfer). The promotion step and the coordinator wire transport are covered elsewhere.
 */
public class RaftLeadershipTransferInitiateTest {

  @Rule public RaftRule raftRule = RaftRule.withBootstrappedNodes(3);

  @Test
  public void shouldRejectTransferToCurrentLeader() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var leaderId = memberId(leader);

    // when
    final var result =
        onRaftThread(
            leader, () -> leaderRole(leader).precheckTransfer(leaderId, leaderId, index(leader)));

    // then
    assertThat(result).contains(LeadershipTransferResult.ALREADY_LEADER);
  }

  @Test
  public void shouldRejectTransferToNonMember() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();

    // when
    final var result =
        onRaftThread(
            leader,
            () ->
                leaderRole(leader)
                    .precheckTransfer(
                        MemberId.from("nonmember"), coordinator(leader), index(leader)));

    // then
    assertThat(result).contains(LeadershipTransferResult.OFFLINE);
  }

  @Test
  public void shouldRejectTransferFromNonCoordinator() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();

    // when
    final var result =
        onRaftThread(
            leader,
            () ->
                leaderRole(leader)
                    .precheckTransfer(
                        memberId(target), MemberId.from("not-the-coordinator"), index(leader)));

    // then
    assertThat(result).contains(LeadershipTransferResult.INVALID_COORDINATOR);
  }

  @Test
  public void shouldPassPrecheckAndCatchUpCaughtUpFollower() throws Exception {
    // given
    raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    final var targetId = memberId(target);

    // when
    final var precheck =
        onRaftThread(
            leader,
            () ->
                leaderRole(leader).precheckTransfer(targetId, coordinator(leader), index(leader)));
    assertThat(precheck).as("pre-checks pass for a caught-up follower").isEmpty();

    final CompletableFuture<Optional<LeadershipTransferResult>> caughtUp =
        onRaftThread(
            leader,
            () ->
                leaderRole(leader)
                    .awaitDesiredLeaderCaughtUp(
                        targetId,
                        leader.getContext().getLog().getLastIndex(),
                        Duration.ofSeconds(10)));

    // then
    assertThat(caughtUp).succeedsWithin(Duration.ofSeconds(10)).isEqualTo(Optional.empty());
  }

  @Test
  public void shouldRejectTransferToUnreachableMember() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    final var targetId = memberId(target);
    raftRule.partition(target);

    // the leader accumulates contact failures to the partitioned follower
    Awaitility.await("leader records contact failures to the partitioned follower")
        .atMost(Duration.ofSeconds(15))
        .until(
            () ->
                onRaftThread(
                        leader,
                        () ->
                            leader
                                .getContext()
                                .getCluster()
                                .getMemberContext(targetId)
                                .getFailureCount())
                    > 0);

    // when
    final var result =
        onRaftThread(
            leader,
            () ->
                leaderRole(leader).precheckTransfer(targetId, coordinator(leader), index(leader)));

    // then
    assertThat(result).contains(LeadershipTransferResult.OFFLINE);
  }

  @Test
  public void shouldRejectTransferWhenLagTooHigh() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    final var targetId = memberId(target);

    // when
    final var result =
        onRaftThread(
            leader,
            () -> {
              // push the desired leader's replication lag past the rebalance threshold
              leader
                  .getContext()
                  .getCluster()
                  .getMemberContext(targetId)
                  .setSnapshotReplicationLag(
                      leader.getContext().getRebalanceReplicationLagThreshold() + 1);
              return leaderRole(leader)
                  .precheckTransfer(targetId, coordinator(leader), index(leader));
            });

    // then
    assertThat(result).contains(LeadershipTransferResult.LAG_TOO_HIGH);
  }

  @Test
  public void shouldRejectTransferWhenSnapshotSizeIsUnknown() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    final var targetId = memberId(target);

    // when — an unknown snapshot size must fail closed, not read as zero (caught up)
    final var result =
        onRaftThread(
            leader,
            () -> {
              leader
                  .getContext()
                  .getCluster()
                  .getMemberContext(targetId)
                  .markSnapshotReplicationLagUnknown();
              return leaderRole(leader)
                  .precheckTransfer(targetId, coordinator(leader), index(leader));
            });

    // then
    assertThat(result).contains(LeadershipTransferResult.LAG_TOO_HIGH);
  }

  @Test
  public void shouldCompleteCatchUpWithTimedOutWhenPauseWatchdogExpires() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    final var targetId = memberId(target);

    // when
    final CompletableFuture<Optional<LeadershipTransferResult>> caughtUp =
        onRaftThread(
            leader,
            () -> {
              leaderRole(leader).pauseForTransfer(Duration.ofMillis(500));
              return leaderRole(leader)
                  .awaitDesiredLeaderCaughtUp(
                      targetId,
                      leader.getContext().getLog().getLastIndex() + 1_000,
                      Duration.ofSeconds(30));
            });

    // then
    assertThat(caughtUp)
        .succeedsWithin(Duration.ofSeconds(10))
        .isEqualTo(Optional.of(LeadershipTransferResult.REPLICATION_TIMED_OUT));
  }

  private static LeaderRole leaderRole(final RaftServer leader) {
    return (LeaderRole) leader.getContext().getRaftRole();
  }

  private static MemberId memberId(final RaftServer server) {
    return server.getContext().getCluster().getLocalMember().memberId();
  }

  private static long index(final RaftServer leader) {
    return leader.getContext().getCluster().getConfiguration().index();
  }

  private static MemberId coordinator(final RaftServer leader) {
    return leader.getContext().getCluster().getConfiguration().newMembers().stream()
        .map(io.atomix.raft.cluster.RaftMember::memberId)
        .min(Comparator.comparing(MemberId::id))
        .orElseThrow();
  }

  /** Runs {@code action} on the leader's Raft thread and returns its result to the test thread. */
  private static <T> T onRaftThread(final RaftServer leader, final Supplier<T> action)
      throws Exception {
    final var future = new CompletableFuture<T>();
    leader
        .getContext()
        .getThreadContext()
        .execute(
            () -> {
              try {
                future.complete(action.get());
              } catch (final Exception e) {
                future.completeExceptionally(e);
              }
            });
    return future.get();
  }
}
