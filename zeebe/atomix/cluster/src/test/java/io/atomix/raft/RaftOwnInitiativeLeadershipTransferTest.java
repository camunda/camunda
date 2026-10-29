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
import io.atomix.raft.protocol.LeadershipTransferInitiateRequest;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;

public class RaftOwnInitiativeLeadershipTransferTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Rule public RaftRule raftRule = RaftRule.withBootstrappedNodes(3);

  @Test
  public void shouldHandLeadershipToTheDesiredFollower() throws Exception {
    // given
    raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();

    // when
    final var result = leader.getContext().transferLeadership(memberId(target));

    // then
    assertThat(result).succeedsWithin(TIMEOUT).isEqualTo(LeadershipTransferResult.TRANSFERRED);
    Awaitility.await("the target leads").atMost(TIMEOUT).until(() -> target.isLeader());
    assertThat(leader.getRole()).isNotEqualTo(Role.LEADER);
  }

  @Test
  public void shouldNotAskTheRebalanceCoordinator() throws Exception {
    // given
    raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    leader
        .getContext()
        .setLeadershipTransferCoordinatorCheck(
            (coordinator, version) -> Optional.of(LeadershipTransferResult.NOT_COORDINATOR))
        .join();

    // when
    final var result = leader.getContext().transferLeadership(memberId(target));

    // then
    assertThat(result).succeedsWithin(TIMEOUT).isEqualTo(LeadershipTransferResult.TRANSFERRED);
  }

  @Test
  public void shouldNotTransferFromAFollower() throws Exception {
    // given
    raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();
    final var follower = raftRule.getFollower().orElseThrow();

    // when
    final var result = follower.getContext().transferLeadership(memberId(leader));

    // then
    assertThat(result).succeedsWithin(TIMEOUT).isEqualTo(LeadershipTransferResult.LEADER_CHANGED);
    assertThat(leader.isLeader()).isTrue();
  }

  @Test
  public void shouldKeepLeadershipWhenTheDesiredLeaderIsUnreachable() throws Exception {
    // given
    raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    raftRule.partition(target);
    final var electionTimeout = leader.getContext().getElectionTimeout().toMillis();
    Awaitility.await("the target is silent for longer than an election timeout")
        .atMost(TIMEOUT)
        .until(
            () ->
                System.currentTimeMillis()
                        - leader
                            .getContext()
                            .getCluster()
                            .getMemberContext(memberId(target))
                            .getResponseTime()
                    > 2 * electionTimeout);

    // when
    final var result = leader.getContext().transferLeadership(memberId(target));

    // then
    assertThat(result).succeedsWithin(TIMEOUT).isEqualTo(LeadershipTransferResult.UNREACHABLE);
    assertThat(leader.isLeader()).isTrue();
  }

  @Test
  public void shouldHandLeadershipToAReplicaThatAsksForItself() throws Exception {
    // given
    raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    leader
        .getContext()
        .setLeadershipTransferCoordinatorCheck(
            (coordinator, version) -> Optional.of(LeadershipTransferResult.NOT_COORDINATOR))
        .join();

    // when
    final var response =
        target
            .getContext()
            .getProtocol()
            .leadershipTransferInitiate(
                memberId(leader),
                LeadershipTransferInitiateRequest.builder()
                    .withDesiredLeader(memberId(target))
                    .withCoordinator(memberId(target))
                    .withCoordinatorConfigVersion(0)
                    .withCorrelationId(-1)
                    .build())
            .get(10, TimeUnit.SECONDS);

    // then
    assertThat(response.accepted()).isTrue();
    Awaitility.await("the target leads").atMost(TIMEOUT).until(() -> target.isLeader());
  }

  @Test
  public void shouldLeadOnceTheLeaderGrantsTheRequest() throws Exception {
    // given
    raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();

    // when
    final var result = target.getContext().requestLeadership(TIMEOUT);

    // then
    assertThat(result).succeedsWithin(TIMEOUT).isEqualTo(LeadershipTransferResult.TRANSFERRED);
    assertThat(target.isLeader()).isTrue();
    Awaitility.await("the old leader steps down")
        .atMost(TIMEOUT)
        .until(() -> leader.getRole() != Role.LEADER);
  }

  @Test
  public void shouldAnswerAlreadyLeaderWhenTheLeaderRequestsItsOwnLeadership() throws Exception {
    // given
    raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();

    // when
    final var result = leader.getContext().requestLeadership(TIMEOUT);

    // then
    assertThat(result).succeedsWithin(TIMEOUT).isEqualTo(LeadershipTransferResult.ALREADY_LEADER);
  }

  @Test
  public void shouldStillAskTheCoordinatorForAnotherReplica() throws Exception {
    // given
    raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    leader
        .getContext()
        .setLeadershipTransferCoordinatorCheck(
            (coordinator, version) -> Optional.of(LeadershipTransferResult.NOT_COORDINATOR))
        .join();
    final var requester =
        raftRule.getServers().stream()
            .filter(server -> server != leader && server != target)
            .findFirst()
            .orElseThrow();

    // when
    final var response =
        requester
            .getContext()
            .getProtocol()
            .leadershipTransferInitiate(
                memberId(leader),
                LeadershipTransferInitiateRequest.builder()
                    .withDesiredLeader(memberId(target))
                    .withCoordinator(memberId(requester))
                    .withCoordinatorConfigVersion(0)
                    .withCorrelationId(-1)
                    .build())
            .get(10, TimeUnit.SECONDS);

    // then
    assertThat(response.accepted()).isFalse();
    assertThat(response.rejectionReason()).isEqualTo(LeadershipTransferResult.NOT_COORDINATOR);
  }

  private static MemberId memberId(final RaftServer server) {
    return server.getContext().getCluster().getLocalMember().memberId();
  }
}
