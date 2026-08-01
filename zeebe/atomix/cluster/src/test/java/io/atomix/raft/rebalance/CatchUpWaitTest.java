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
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;

public class CatchUpWaitTest {

  /** Ample budget, so a wait ends for the reason under test rather than for lack of time. */
  private static final Duration CATCH_UP_BUDGET = Duration.ofMinutes(1);

  @Rule public RaftRule raftRule = RaftRule.withBootstrappedNodes(3);

  @Test
  public void shouldCompleteOnceTheDesiredLeaderReachesTheTargetIndex() throws Exception {
    // given
    final long committed = raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var targetId = memberId(raftRule.getFollower().orElseThrow());

    // when
    final var caughtUp = awaitCaughtUp(leader, targetId, committed + 5, CATCH_UP_BUDGET);
    raftRule.appendEntries(5);

    // then
    assertThat(caughtUp).succeedsWithin(Duration.ofSeconds(15)).isEqualTo(Optional.empty());
  }

  @Test
  public void shouldReportNotMemberWhenTheDesiredLeaderLeavesThePartition() throws Exception {
    // given
    final long committed = raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();

    // when
    final var caughtUp =
        awaitCaughtUp(leader, memberId(target), committed + 1_000, CATCH_UP_BUDGET);
    target.leave().get(30, TimeUnit.SECONDS);

    // then
    assertThat(caughtUp)
        .succeedsWithin(Duration.ofSeconds(15))
        .isEqualTo(Optional.of(LeadershipTransferResult.NOT_MEMBER));
  }

  @Test
  public void shouldReportReplicationTimedOutWhenTheBudgetRunsOut() throws Exception {
    // given
    final long committed = raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var targetId = memberId(raftRule.getFollower().orElseThrow());

    // when
    final var caughtUp = awaitCaughtUp(leader, targetId, committed + 1_000, Duration.ofSeconds(1));

    // then
    assertThat(caughtUp)
        .succeedsWithin(Duration.ofSeconds(15))
        .isEqualTo(Optional.of(LeadershipTransferResult.REPLICATION_TIMED_OUT));
  }

  private static CompletableFuture<Optional<LeadershipTransferResult>> awaitCaughtUp(
      final RaftServer leader,
      final MemberId desiredLeader,
      final long targetIndex,
      final Duration catchUpBudget) {
    final var deadlineMs = System.currentTimeMillis() + catchUpBudget.toMillis();
    final var caughtUp = new CompletableFuture<Optional<LeadershipTransferResult>>();
    leader
        .getContext()
        .getThreadContext()
        .execute(
            () ->
                new CatchUpWait(
                        leader.getContext(),
                        leaderRole(leader)::isRunning,
                        desiredLeader,
                        targetIndex,
                        deadlineMs)
                    .start()
                    .whenComplete(
                        (result, error) -> {
                          if (error != null) {
                            caughtUp.completeExceptionally(error);
                          } else {
                            caughtUp.complete(result);
                          }
                        }));
    return caughtUp;
  }

  private static LeaderRole leaderRole(final RaftServer leader) {
    return (LeaderRole) leader.getContext().getRaftRole();
  }

  private static MemberId memberId(final RaftServer server) {
    return server.getContext().getCluster().getLocalMember().memberId();
  }
}
