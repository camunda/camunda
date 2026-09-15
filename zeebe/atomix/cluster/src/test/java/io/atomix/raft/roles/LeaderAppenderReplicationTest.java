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
package io.atomix.raft.roles;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftRule;
import io.atomix.raft.RaftServer;
import io.atomix.raft.partition.RaftPartitionConfig;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.Rule;
import org.junit.Test;

/** Coverage for the per-follower replication observer. */
public class LeaderAppenderReplicationTest {
  private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(3);

  @Rule
  public RaftRule raftRule =
      RaftRule.withBootstrappedNodes(
          3,
          new RaftRule.Configurator() {
            @Override
            public void configure(final MemberId id, final RaftServer.Builder builder) {
              builder.withPartitionConfig(
                  new RaftPartitionConfig()
                      .setElectionTimeout(Duration.ofSeconds(6))
                      .setHeartbeatInterval(HEARTBEAT_INTERVAL));
            }
          });

  @Test
  public void shouldCompleteImmediatelyWhenTheFollowerIsAlreadyCaughtUp() throws Exception {
    // given
    final long committed = raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();

    // when
    final var replicated = awaitReplication(leader, memberId(target), committed);

    // then
    assertThat(replicated).succeedsWithin(Duration.ofSeconds(5));
  }

  @Test
  public void shouldCompleteOnceTheFollowerReachesTheTargetIndex() throws Exception {
    // given
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    raftRule.partition(target);
    final long committed = raftRule.appendEntries(5);
    raftRule.reconnect(target);

    // when
    final var replicated = awaitReplication(leader, memberId(target), committed);

    // then
    assertThat(replicated).succeedsWithin(HEARTBEAT_INTERVAL.dividedBy(3));
  }

  @Test
  public void shouldOnlyCompleteForTheObservedFollower() throws Exception {
    // given
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    final var other = otherFollower(target);
    raftRule.partition(target);

    // when
    final long committed = raftRule.appendEntries(5);
    final var replicated = awaitReplication(leader, memberId(target), committed);
    final long later = raftRule.appendEntries(1);

    // then
    assertThat(awaitReplication(leader, memberId(other), later))
        .succeedsWithin(HEARTBEAT_INTERVAL.dividedBy(3));
    assertThat(replicated).isNotDone();
  }

  @Test
  public void shouldIgnoreLaterResponsesForACancelledWait() throws Exception {
    // given
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    raftRule.partition(target);
    final long committed = raftRule.appendEntries(5);
    final var cancelled = awaitReplication(leader, memberId(target), committed);

    // when
    onRaftThread(leader, () -> cancelled.cancel(false));
    raftRule.reconnect(target);

    // then
    assertThat(awaitReplication(leader, memberId(target), committed))
        .succeedsWithin(HEARTBEAT_INTERVAL.dividedBy(3));
    assertThat(cancelled).isCancelled();
  }

  @Test
  public void shouldFailPendingWaitsWhenTheLeaderStepsDown() throws Exception {
    // given
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    raftRule.partition(target);
    final long committed = raftRule.appendEntries(5);
    final var replicated = awaitReplication(leader, memberId(target), committed);

    // when
    leader.stepDown().get();

    // then
    assertThat(replicated).failsWithin(Duration.ofSeconds(15));
  }

  private static CompletableFuture<Void> awaitReplication(
      final RaftServer leader, final MemberId memberId, final long targetIndex) {
    final var started = new CompletableFuture<CompletableFuture<Void>>();
    leader
        .getContext()
        .getThreadContext()
        .execute(
            () -> started.complete(leaderRole(leader).awaitReplication(memberId, targetIndex)));
    return started.join();
  }

  private static void onRaftThread(final RaftServer leader, final Runnable action) {
    CompletableFuture.runAsync(action, leader.getContext().getThreadContext()).join();
  }

  private RaftServer otherFollower(final RaftServer follower) {
    return raftRule.getServers().stream()
        .filter(server -> server.getRole() == RaftServer.Role.FOLLOWER)
        .filter(server -> !memberId(server).equals(memberId(follower)))
        .findFirst()
        .orElseThrow();
  }

  private static LeaderRole leaderRole(final RaftServer leader) {
    return (LeaderRole) leader.getContext().getRaftRole();
  }

  private static MemberId memberId(final RaftServer server) {
    return server.getContext().getCluster().getLocalMember().memberId();
  }
}
