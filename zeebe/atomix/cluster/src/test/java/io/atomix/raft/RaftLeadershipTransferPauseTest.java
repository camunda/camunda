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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.raft.RaftError.Type;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.raft.protocol.ReconfigureRequest;
import io.atomix.raft.roles.LeaderRole;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;

/**
 * Coverage for the paused-mode watchdog on the Raft thread (Coordinated Leadership Transfer). The
 * watchdog guarantees a partition is never left paused and unavailable: if it is not resumed in
 * time, the leader steps down so services restart and a new leader can be elected.
 */
public class RaftLeadershipTransferPauseTest {

  @Rule public RaftRule raftRule = RaftRule.withBootstrappedNodes(3);

  @Test
  public void shouldStepDownWhenPauseWatchdogExpires() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var leftLeadership = new CountDownLatch(1);
    leader.addRoleChangeListener(
        (role, term) -> {
          if (role != Role.LEADER) {
            leftLeadership.countDown();
          }
        });

    // when
    pauseOnRaftThread(leader, Duration.ofMillis(500));

    // then
    assertThat(leftLeadership.await(15, TimeUnit.SECONDS))
        .as("leader steps down after the pause watchdog expires")
        .isTrue();
  }

  @Test
  public void shouldMeasureWatchdogDeadlineFromPausedSince() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var leftLeadership = new CountDownLatch(1);
    leader.addRoleChangeListener(
        (role, term) -> {
          if (role != Role.LEADER) {
            leftLeadership.countDown();
          }
        });

    // when
    final var resumeTimeout = Duration.ofSeconds(2);
    leader
        .getContext()
        .getThreadContext()
        .execute(
            () ->
                ((LeaderRole) leader.getContext().getRaftRole())
                    .pauseForTransfer(
                        resumeTimeout,
                        System.currentTimeMillis() - resumeTimeout.toMillis() + 500));

    // then
    assertThat(leftLeadership.await(1500, TimeUnit.MILLISECONDS))
        .as("leader steps down on the remaining budget when arming consumed most of it")
        .isTrue();
  }

  @Test
  public void shouldRejectPauseWhenResumeBudgetAlreadyExhausted() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var resumeTimeout = Duration.ofSeconds(30);

    // when / then
    final Role roleAfterRejectedPause =
        onRaftThread(
            leader,
            () -> {
              final var leaderRole = (LeaderRole) leader.getContext().getRaftRole();
              assertThatThrownBy(
                      () ->
                          leaderRole.pauseForTransfer(
                              resumeTimeout, System.currentTimeMillis() - resumeTimeout.toMillis()))
                  .isInstanceOf(IllegalStateException.class)
                  .hasMessageContaining("already passed");
              return leaderRole.role();
            });
    assertThat(roleAfterRejectedPause)
        .as("leader keeps leadership when the pause is declined")
        .isEqualTo(Role.LEADER);
  }

  @Test
  public void shouldNotStepDownWhenResumedBeforeWatchdogExpires() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();

    // when
    leader
        .getContext()
        .getThreadContext()
        .execute(
            () -> {
              final var leaderRole = (LeaderRole) leader.getContext().getRaftRole();
              leaderRole.pauseForTransfer(Duration.ofMillis(200), System.currentTimeMillis());
              leaderRole.resumeFromTransfer();
            });

    // then
    Awaitility.await("leader keeps leadership after resume")
        .during(Duration.ofMillis(500))
        .atMost(Duration.ofSeconds(1))
        .until(() -> leader.getRole() == Role.LEADER);
  }

  @Test
  public void shouldKeepRebalanceMetricsRegisteredAfterLeaderStepsDown() throws Exception {
    // given
    final var leader = raftRule.getLeader().orElseThrow();
    final var registry = leader.getContext().getMeterRegistry();
    leader.getContext().getRebalanceMetrics().observePauseDuration(Duration.ofMillis(1));
    assertThat(registry.find("zeebe.cluster.rebalance.partition.pause.duration").timer())
        .isNotNull();

    // when
    leader.stepDown().get();
    Awaitility.await("leader steps down")
        .atMost(Duration.ofSeconds(15))
        .until(() -> leader.getRole() != Role.LEADER);

    // then
    assertThat(registry.find("zeebe.cluster.rebalance.partition.pause.duration").timer())
        .as("rebalance metrics are partition-lifetime and are not removed when the role stops")
        .isNotNull();
  }

  @Test
  public void shouldCaptureFrozenLastIndexWhenPausing() throws Exception {
    // given
    raftRule.appendEntries(7);
    final var leader = raftRule.getLeader().orElseThrow();
    final long lastIndex = onRaftThread(leader, () -> leader.getContext().getLog().getLastIndex());

    // when
    final long captured =
        onRaftThread(
            leader,
            () ->
                ((LeaderRole) leader.getContext().getRaftRole())
                    .pauseForTransfer(Duration.ofSeconds(30), System.currentTimeMillis()));

    // then
    assertThat(captured).isEqualTo(lastIndex);
  }

  @Test
  public void shouldRejectDuplicatePause() throws Exception {
    // given
    raftRule.appendEntries(6);
    final var leader = raftRule.getLeader().orElseThrow();

    // when
    onRaftThread(
        leader,
        () -> {
          final var leaderRole = (LeaderRole) leader.getContext().getRaftRole();
          leaderRole.pauseForTransfer(Duration.ofSeconds(30), System.currentTimeMillis());

          // then
          assertThatThrownBy(
                  () ->
                      leaderRole.pauseForTransfer(
                          Duration.ofSeconds(30), System.currentTimeMillis()))
              .isInstanceOf(IllegalStateException.class)
              .hasMessageContaining("already paused");
          return null;
        });
  }

  @Test
  public void shouldResumeIdempotently() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();

    // when
    final long frozenIndex =
        onRaftThread(
            leader,
            () -> {
              final var leaderRole = (LeaderRole) leader.getContext().getRaftRole();
              final long index =
                  leaderRole.pauseForTransfer(Duration.ofSeconds(30), System.currentTimeMillis());
              leaderRole.resumeFromTransfer();
              leaderRole.resumeFromTransfer();
              return index;
            });

    // then
    assertThat(raftRule.appendEntry())
        .as("a repeated resume still fully lifts the pause and re-admits writes")
        .isGreaterThan(frozenIndex);
  }

  @Test
  public void shouldStepDownWhenApplicationAppendIsAttemptedWhilePaused() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var leftLeadership = new CountDownLatch(1);
    leader.addRoleChangeListener(
        (role, term) -> {
          if (role != Role.LEADER) {
            leftLeadership.countDown();
          }
        });

    final long frozenIndex =
        onRaftThread(
            leader,
            () ->
                ((LeaderRole) leader.getContext().getRaftRole())
                    .pauseForTransfer(Duration.ofSeconds(30), System.currentTimeMillis()));

    // when
    final var rejected = raftRule.appendEntryAsync();

    // then
    assertThatThrownBy(rejected::awaitCommit)
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasMessageContaining("paused for a leadership transfer");

    assertThat(onRaftThread(leader, () -> leader.getContext().getLog().getLastIndex()))
        .as("the rejected entry is not appended")
        .isEqualTo(frozenIndex);

    assertThat(leftLeadership.await(15, TimeUnit.SECONDS))
        .as("the leader steps down after an append is attempted while paused")
        .isTrue();
  }

  @Test
  public void shouldRejectReconfigurationWhilePaused() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    onRaftThread(
        leader,
        () -> {
          ((LeaderRole) leader.getContext().getRaftRole())
              .pauseForTransfer(Duration.ofSeconds(30), System.currentTimeMillis());
          return null;
        });

    // when
    final var response =
        onRaftThread(
            leader,
            () ->
                ((LeaderRole) leader.getContext().getRaftRole())
                    .onReconfigure(removeAnyFollowerRequest(leader))
                    .getNow(null));

    // then
    assertThat(response).isNotNull();
    assertThat(response.status()).isEqualTo(Status.ERROR);
    assertThat(response.error().type()).isEqualTo(Type.CONFIGURATION_ERROR);
  }

  @Test
  public void shouldRejectPauseWhileReconfiguring() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();

    // when / then
    onRaftThread(
        leader,
        () -> {
          final var leaderRole = (LeaderRole) leader.getContext().getRaftRole();
          leaderRole.onReconfigure(removeAnyFollowerRequest(leader));
          assertThatThrownBy(
                  () ->
                      leaderRole.pauseForTransfer(
                          Duration.ofSeconds(30), System.currentTimeMillis()))
              .isInstanceOf(LeaderRole.ConfigurationChangeInProgressException.class)
              .hasMessageContaining("configuration change in progress");
          return null;
        });
  }

  /** Builds a membership change that removes an arbitrary follower, so it is not a no-op. */
  private ReconfigureRequest removeAnyFollowerRequest(final RaftServer leader) {
    final var follower = raftRule.getFollower().orElseThrow();
    final var followerId = follower.getContext().getCluster().getLocalMember().memberId();
    final var configuration = leader.getContext().getCluster().getConfiguration();
    final var updatedMembers =
        configuration.newMembers().stream()
            .filter(member -> !member.memberId().equals(followerId))
            .toList();
    return ReconfigureRequest.builder()
        .withIndex(configuration.index())
        .withTerm(configuration.term())
        .withMembers(updatedMembers)
        .from(leader.getContext().getCluster().getLocalMember().memberId().id())
        .build();
  }

  private void pauseOnRaftThread(final RaftServer leader, final Duration resumeTimeout) {
    leader
        .getContext()
        .getThreadContext()
        .execute(
            () ->
                ((LeaderRole) leader.getContext().getRaftRole())
                    .pauseForTransfer(resumeTimeout, System.currentTimeMillis()));
  }

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
              } catch (final Throwable t) {
                future.completeExceptionally(t);
              }
            });
    return future.get(30, TimeUnit.SECONDS);
  }
}
