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
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.protocol.TestRaftServerProtocol;
import io.atomix.raft.protocol.TimeoutNowRequest;
import io.atomix.raft.roles.LeaderRole;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;

/**
 * Coverage for the promotion step of a coordinated leadership transfer: the current leader sends
 * TimeoutNow (retrying up to {@code maxTransferAttempts}) to make the caught-up desired leader take
 * over, and reports the terminal result.
 */
public class RaftLeadershipTransferPromoteTest {

  @Rule public RaftRule raftRule = RaftRule.withBootstrappedNodes(3);

  @Test
  public void shouldPromoteDesiredLeaderViaTimeoutNow() throws Exception {
    // given
    raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    final var targetId = target.getContext().getCluster().getLocalMember().memberId();

    // when
    final var transfer = promoteOnRaftThread(leader, targetId);

    // then
    Awaitility.await("target becomes leader")
        .atMost(Duration.ofSeconds(15))
        .until(() -> target.getRole() == Role.LEADER);
    assertThat(transfer)
        .succeedsWithin(Duration.ofSeconds(5))
        .isEqualTo(LeadershipTransferResult.TRANSFERRED);
    assertThat(leader.getRole()).isNotEqualTo(Role.LEADER);
  }

  @Test
  public void shouldSendExactlyMaxTransferAttemptsThenGiveUp() throws Exception {
    // given
    raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    final var targetId = target.getContext().getCluster().getLocalMember().memberId();
    final int maxAttempts = leader.getContext().getRebalanceMaxTransferAttempts();

    final var leaderProtocol = (TestRaftServerProtocol) leader.getContext().getProtocol();
    final LongAdder sends = new LongAdder();
    leaderProtocol.interceptRequest(
        TimeoutNowRequest.class,
        request -> {
          sends.increment();
          return CompletableFuture.failedFuture(new RuntimeException("dropped in test"));
        });

    // when
    final var transfer = promoteOnRaftThread(leader, targetId);

    // then
    assertThat(transfer)
        .succeedsWithin(Duration.ofSeconds(15))
        .isEqualTo(LeadershipTransferResult.TRANSFER_FAILED);
    assertThat(sends.sum()).as("sends are count-bounded").isEqualTo(maxAttempts);
    assertThat(leader.getRole()).isEqualTo(Role.LEADER);
  }

  @Test
  public void shouldReportLeaderChangedWhenAnotherNodeWinsDuringPromotion() throws Exception {
    // given
    raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    final var targetId = target.getContext().getCluster().getLocalMember().memberId();
    raftRule.partition(target);

    // when
    final var transfer = promoteOnRaftThread(leader, targetId);
    leader.stepDown().get();

    // then
    assertThat(transfer)
        .succeedsWithin(Duration.ofSeconds(15))
        .isEqualTo(LeadershipTransferResult.LEADER_CHANGED);
  }

  @Test
  public void shouldFailImmediatelyWhenTargetIsNotAMemberBeforeFirstAttempt() throws Exception {
    // given
    raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    final var targetId = target.getContext().getCluster().getLocalMember().memberId();
    target.leave().get(30, TimeUnit.SECONDS);

    // when
    final var transfer = promoteOnRaftThread(leader, targetId);

    // then
    assertThat(transfer)
        .succeedsWithin(Duration.ofSeconds(5))
        .isEqualTo(LeadershipTransferResult.OFFLINE);
  }

  @Test
  public void shouldAbandonTransferWhenTargetLeavesBetweenRetries() throws Exception {
    // given
    raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    final var targetId = target.getContext().getCluster().getLocalMember().memberId();

    // every TimeoutNow is dropped, so the transfer only ever completes via the membership recheck
    final var leaderProtocol = (TestRaftServerProtocol) leader.getContext().getProtocol();
    final LongAdder sends = new LongAdder();
    final var firstAttemptSent = new CompletableFuture<Void>();
    leaderProtocol.interceptRequest(
        TimeoutNowRequest.class,
        request -> {
          sends.increment();
          firstAttemptSent.complete(null);
          return CompletableFuture.failedFuture(new RuntimeException("dropped in test"));
        });

    // when
    final var transfer = promoteOnRaftThread(leader, targetId);
    firstAttemptSent.get(15, TimeUnit.SECONDS);
    target.leave().get(30, TimeUnit.SECONDS);

    // then
    final int maxAttempts = leader.getContext().getRebalanceMaxTransferAttempts();
    assertThat(transfer)
        .succeedsWithin(Duration.ofSeconds(15))
        .isEqualTo(LeadershipTransferResult.OFFLINE);
    assertThat(sends.sum())
        .as("the membership recheck abandons the transfer before the attempt budget is spent")
        .isLessThan(maxAttempts);
    assertThat(leader.getRole()).isEqualTo(Role.LEADER);
  }

  private CompletableFuture<LeadershipTransferResult> promoteOnRaftThread(
      final RaftServer leader, final MemberId targetId) {
    final var future = new CompletableFuture<LeadershipTransferResult>();
    leader
        .getContext()
        .getThreadContext()
        .execute(
            () ->
                ((LeaderRole) leader.getContext().getRaftRole())
                    .promoteDesiredLeader(targetId)
                    .whenComplete(
                        (result, error) -> {
                          if (error != null) {
                            future.completeExceptionally(error);
                          } else {
                            future.complete(result);
                          }
                        }));
    return future;
  }
}
