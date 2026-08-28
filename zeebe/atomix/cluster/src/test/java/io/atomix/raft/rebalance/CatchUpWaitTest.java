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
package io.atomix.raft.rebalance;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.raft.LeadershipTransferResult;
import io.atomix.raft.RaftRule;
import io.atomix.raft.RaftServer;
import io.atomix.raft.roles.LeaderRole;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;

public class CatchUpWaitTest {

  /** Ample budget, so a wait ends for the reason under test rather than for lack of time. */
  private static final Duration CATCH_UP_BUDGET = Duration.ofMinutes(1);

  /** Short enough that a test can wait out the deadline, long enough not to fire prematurely. */
  private static final Duration SHORT_CATCH_UP_BUDGET = Duration.ofSeconds(1);

  @Rule public RaftRule raftRule = RaftRule.withBootstrappedNodes(3);

  @Test
  public void shouldCompleteOnceTheDesiredLeaderReachesTheTargetIndex() throws Exception {
    // given
    final long committed = raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var targetId = memberId(raftRule.getFollower().orElseThrow());

    // when
    final var wait = awaitCaughtUp(leader, targetId, committed + 5, CATCH_UP_BUDGET);
    raftRule.appendEntries(5);

    // then
    assertThat(wait.result()).succeedsWithin(Duration.ofSeconds(15)).isEqualTo(Optional.empty());
  }

  @Test
  public void shouldCompleteImmediatelyWhenTheDesiredLeaderIsAlreadyAtTheTargetIndex()
      throws Exception {
    // given
    final long committed = raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    awaitReplicated(leader, memberId(target), committed);

    // when
    final var wait = awaitCaughtUp(leader, memberId(target), committed, CATCH_UP_BUDGET);

    // then
    assertThat(wait.result()).succeedsWithin(Duration.ofSeconds(5)).isEqualTo(Optional.empty());
  }

  @Test
  public void shouldReportNotMemberWhenTheDesiredLeaderIsNotInThePartition() throws Exception {
    // given
    final long committed = raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();

    // when
    final var wait =
        awaitCaughtUp(leader, MemberId.from("not-a-member"), committed + 1, CATCH_UP_BUDGET);

    // then
    assertThat(wait.result())
        .succeedsWithin(Duration.ofSeconds(5))
        .isEqualTo(Optional.of(LeadershipTransferResult.NOT_MEMBER));
  }

  @Test
  public void shouldReportReplicationTimedOutWhenTheBudgetRunsOut() throws Exception {
    // given
    final long committed = raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var targetId = memberId(raftRule.getFollower().orElseThrow());

    // when
    final var wait = awaitCaughtUp(leader, targetId, committed + 1_000, SHORT_CATCH_UP_BUDGET);

    // then
    assertThat(wait.result())
        .succeedsWithin(Duration.ofSeconds(15))
        .isEqualTo(Optional.of(LeadershipTransferResult.REPLICATION_TIMED_OUT));
  }

  @Test
  public void shouldReportLeaderChangedWhenTheLeaderStops() throws Exception {
    // given
    final long committed = raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var targetId = memberId(raftRule.getFollower().orElseThrow());
    final var wait = awaitCaughtUp(leader, targetId, committed + 1_000, CATCH_UP_BUDGET);

    // when
    onRaftThread(leader, wait.phase()::onLeaderStopped);

    // then
    assertThat(wait.result())
        .succeedsWithin(Duration.ofSeconds(5))
        .isEqualTo(Optional.of(LeadershipTransferResult.LEADER_CHANGED));
  }

  @Test
  public void shouldReportPauseFailedWhenThePauseClears() throws Exception {
    // given
    final long committed = raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var targetId = memberId(raftRule.getFollower().orElseThrow());
    final var wait = awaitCaughtUp(leader, targetId, committed + 1_000, CATCH_UP_BUDGET);

    // when
    onRaftThread(leader, wait.phase()::onPauseCleared);

    // then
    assertThat(wait.result())
        .succeedsWithin(Duration.ofSeconds(5))
        .isEqualTo(Optional.of(LeadershipTransferResult.PAUSE_FAILED));
  }

  @Test
  public void shouldKeepTheSuccessfulResultWhenTheDeadlinePasses() throws Exception {
    // given
    final long committed = raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    awaitReplicated(leader, memberId(target), committed);

    // when
    final var wait = awaitCaughtUp(leader, memberId(target), committed, Duration.ofMillis(200));

    // then
    assertThat(wait.result()).succeedsWithin(Duration.ofSeconds(5)).isEqualTo(Optional.empty());
    Awaitility.await("the result stays successful past the deadline")
        .during(Duration.ofMillis(500))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(wait.result().join()).isEqualTo(Optional.empty()));
  }

  private static void awaitReplicated(
      final RaftServer leader, final MemberId memberId, final long index) {
    Awaitility.await("until " + memberId + " has replicated up to " + index)
        .atMost(Duration.ofSeconds(15))
        .until(
            () -> {
              final var context = leader.getContext().getCluster().getMemberContext(memberId);
              return context != null && context.getMatchIndex() >= index;
            });
  }

  private static StartedWait awaitCaughtUp(
      final RaftServer leader,
      final MemberId desiredLeader,
      final long targetIndex,
      final Duration catchUpBudget) {
    final var deadlineMs = System.currentTimeMillis() + catchUpBudget.toMillis();
    final var result = new CompletableFuture<Optional<LeadershipTransferResult>>();
    final var started = new CompletableFuture<CatchUpWait>();
    leader
        .getContext()
        .getThreadContext()
        .execute(
            () -> {
              final var wait =
                  new CatchUpWait(
                      leader.getContext(),
                      leaderRole(leader),
                      desiredLeader,
                      targetIndex,
                      deadlineMs);
              started.complete(wait);
              wait.start()
                  .whenComplete(
                      (outcome, error) -> {
                        if (error != null) {
                          result.completeExceptionally(error);
                        } else {
                          result.complete(outcome);
                        }
                      });
            });
    return new StartedWait(started.join(), result);
  }

  private static void onRaftThread(final RaftServer leader, final Runnable action) {
    CompletableFuture.runAsync(action, leader.getContext().getThreadContext()).join();
  }

  private static LeaderRole leaderRole(final RaftServer leader) {
    return (LeaderRole) leader.getContext().getRaftRole();
  }

  private static MemberId memberId(final RaftServer server) {
    return server.getContext().getCluster().getLocalMember().memberId();
  }

  private record StartedWait(
      CatchUpWait phase, CompletableFuture<Optional<LeadershipTransferResult>> result) {}
}
