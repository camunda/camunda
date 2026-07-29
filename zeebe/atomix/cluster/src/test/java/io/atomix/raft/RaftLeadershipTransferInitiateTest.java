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
import io.atomix.raft.protocol.LeadershipTransferInitiateRequest;
import io.atomix.raft.protocol.PollRequest;
import io.atomix.raft.protocol.ReconfigureRequest;
import io.atomix.raft.protocol.TestRaftServerProtocol;
import io.atomix.raft.protocol.TimeoutNowRequest;
import io.atomix.raft.protocol.VersionedAppendRequest;
import io.atomix.raft.protocol.VoteRequest;
import io.atomix.raft.roles.LeaderRole;
import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;

public class RaftLeadershipTransferInitiateTest {

  @Rule public RaftRule raftRule = RaftRule.withBootstrappedNodes(3);

  @Test
  public void shouldRejectTransferToCurrentLeader() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var leaderId = memberId(leader);

    // when
    final var result = initiate(leader, leaderId, leaderId, index(leader));

    // then
    assertThat(result).contains(LeadershipTransferResult.ALREADY_LEADER);
  }

  @Test
  public void shouldRejectTransferToNonMember() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();

    // when
    final var result = initiate(leader, MemberId.from("nonmember"));

    // then
    assertThat(result).contains(LeadershipTransferResult.NOT_MEMBER);
  }

  @Test
  public void shouldRejectTransferToMemberWithUnconvergedLog() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    final var targetId = memberId(target);

    // when
    final var result =
        initiate(
            leader,
            targetId,
            () -> leader.getContext().getCluster().getMemberContext(targetId).appendFailed());

    // then
    assertThat(result).contains(LeadershipTransferResult.NOT_REPLICATING);
  }

  @Test
  public void shouldRejectTransferFromNonCoordinator() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();

    // when
    final var result =
        initiate(leader, memberId(target), MemberId.from("not-the-coordinator"), index(leader));

    // then
    assertThat(result).contains(LeadershipTransferResult.NOT_COORDINATOR);
  }

  @Test
  public void shouldRejectTransferOnAStaleConfiguration() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();

    // when
    final var result = initiate(leader, memberId(target), coordinator(leader), index(leader) - 1);

    // then
    assertThat(result).contains(LeadershipTransferResult.STALE_CONFIGURATION);
  }

  @Test
  public void shouldAcceptTransferToCaughtUpFollower() throws Exception {
    // given
    raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    final var targetId = memberId(target);

    // when
    final var result = initiate(leader, targetId);

    // then
    assertThat(result).as("a caught-up follower is accepted").isEmpty();
  }

  @Test
  public void shouldRejectTransferToUnreachableMember() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    final var targetId = memberId(target);

    // when
    raftRule.partition(target);
    Awaitility.await("the leader stops hearing from the partitioned follower")
        .atMost(Duration.ofSeconds(15))
        .until(() -> outOfContact(leader, targetId));

    // then
    assertThat(initiate(leader, targetId)).contains(LeadershipTransferResult.UNREACHABLE);
  }

  @Test
  public void shouldTolerateASingleMissedAppendToTheDesiredLeader() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    final var targetId = memberId(target);

    // when
    final var result =
        initiate(
            leader,
            targetId,
            () ->
                leader
                    .getContext()
                    .getCluster()
                    .getMemberContext(targetId)
                    .incrementFailureCount());

    // then
    assertThat(result).as("a member that is still answering stays eligible").isEmpty();
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
        initiate(
            leader,
            targetId,
            () ->
                leader
                    .getContext()
                    .getCluster()
                    .getMemberContext(targetId)
                    .setSnapshotReplicationLag(
                        leader.getContext().getRebalanceConfiguration().replicationLagThreshold()
                            + 1));

    // then
    assertThat(result).contains(LeadershipTransferResult.LAG_TOO_HIGH);
  }

  @Test
  public void shouldRejectTransferWhileReconfiguring() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var followers =
        raftRule.getServers().stream()
            .filter(server -> server.getRole() == RaftServer.Role.FOLLOWER)
            .toList();
    final var leavingFollower = followers.get(0);
    final var targetId = memberId(followers.get(1));

    // when
    final var result =
        initiate(
            leader,
            targetId,
            () -> leaderRole(leader).onReconfigure(removeFollowerRequest(leader, leavingFollower)));

    // then
    assertThat(result).contains(LeadershipTransferResult.CONFIGURATION_CHANGE_IN_PROGRESS);
  }

  @Test
  public void shouldRejectTransferWhileAnotherTransferIsPaused() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    final var targetId = memberId(target);

    // when
    final var result =
        initiate(
            leader,
            targetId,
            () ->
                leaderRole(leader)
                    .pauseForTransfer(Duration.ofSeconds(30), System.currentTimeMillis()));

    // then
    assertThat(result).contains(LeadershipTransferResult.TRANSFER_IN_PROGRESS);
  }

  @Test
  public void shouldRejectTransferWhileTheLeaderIsInitializing() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var oldLeader = raftRule.getLeader().orElseThrow();
    final var newLeader = raftRule.getFollower().orElseThrow();

    Awaitility.await("the new leader has the whole log")
        .atMost(Duration.ofSeconds(15))
        .until(
            () ->
                newLeader.getContext().getLog().getLastIndex()
                    >= oldLeader.getContext().getLog().getLastIndex());

    dropSends(newLeader, VersionedAppendRequest.class);
    raftRule.getServers().stream()
        .filter(server -> server != newLeader)
        .forEach(
            server -> {
              dropSends(server, PollRequest.class);
              dropSends(server, VoteRequest.class);
            });
    protocolOf(oldLeader)
        .timeoutNow(
            memberId(newLeader),
            TimeoutNowRequest.builder()
                .withTerm(oldLeader.getContext().getTerm())
                .withLeader(memberId(oldLeader))
                .build());
    Awaitility.await("the new leader takes over")
        .atMost(Duration.ofSeconds(15))
        .until(() -> newLeader.getRole() == RaftServer.Role.LEADER);

    // when
    final var result = initiate(newLeader, memberId(oldLeader));

    // then
    assertThat(result).contains(LeadershipTransferResult.LEADER_INITIALIZING);
  }

  /**
   * Sends an initiate request the leader has no reason to refuse other than the one under test, and
   * returns the reason it refused, or empty if it accepted.
   */
  private static Optional<LeadershipTransferResult> initiate(
      final RaftServer leader, final MemberId desiredLeader) throws Exception {
    return initiate(leader, desiredLeader, () -> {});
  }

  /** As {@link #initiate(RaftServer, MemberId)}, running {@code setUp} on the Raft thread first. */
  private static Optional<LeadershipTransferResult> initiate(
      final RaftServer leader, final MemberId desiredLeader, final Runnable setUp)
      throws Exception {
    return initiate(leader, desiredLeader, coordinator(leader), index(leader), setUp);
  }

  private static Optional<LeadershipTransferResult> initiate(
      final RaftServer leader,
      final MemberId desiredLeader,
      final MemberId coordinator,
      final long coordinatorConfigIndex)
      throws Exception {
    return initiate(leader, desiredLeader, coordinator, coordinatorConfigIndex, () -> {});
  }

  private static Optional<LeadershipTransferResult> initiate(
      final RaftServer leader,
      final MemberId desiredLeader,
      final MemberId coordinator,
      final long coordinatorConfigIndex,
      final Runnable setUp)
      throws Exception {
    final var request =
        LeadershipTransferInitiateRequest.builder()
            .withDesiredLeader(desiredLeader)
            .withCoordinator(coordinator)
            .withCoordinatorConfigIndex(coordinatorConfigIndex)
            .withCorrelationId(1)
            .build();
    return onRaftThread(
        leader,
        () -> {
          setUp.run();
          return Optional.ofNullable(
              leaderRole(leader).onLeadershipTransferInitiate(request).join().rejectionReason());
        });
  }

  /**
   * Whether the leader has heard nothing from {@code member} for longer than an election timeout.
   */
  private static boolean outOfContact(final RaftServer leader, final MemberId member) {
    final var context = leader.getContext().getCluster().getMemberContext(member);
    final var silenceMs = System.currentTimeMillis() - context.getResponseTime();
    return silenceMs > leader.getContext().getElectionTimeout().toMillis();
  }

  private static <T> void dropSends(final RaftServer server, final Class<T> requestType) {
    protocolOf(server)
        .interceptRequest(
            requestType,
            request -> {
              return CompletableFuture.failedFuture(new RuntimeException("dropped in test"));
            });
  }

  private static TestRaftServerProtocol protocolOf(final RaftServer server) {
    return (TestRaftServerProtocol) server.getContext().getProtocol();
  }

  /** Builds a membership change that removes {@code follower}, so it is not a no-op. */
  private static ReconfigureRequest removeFollowerRequest(
      final RaftServer leader, final RaftServer follower) {
    final var leavingId = memberId(follower);
    final var configuration = leader.getContext().getCluster().getConfiguration();
    final var remainingMembers =
        configuration.newMembers().stream()
            .filter(member -> !member.memberId().equals(leavingId))
            .toList();
    return ReconfigureRequest.builder()
        .withIndex(configuration.index())
        .withTerm(configuration.term())
        .withMembers(remainingMembers)
        .from(memberId(leader).id())
        .build();
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
