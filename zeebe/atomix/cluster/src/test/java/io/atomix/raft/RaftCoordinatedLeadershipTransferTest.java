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
import io.atomix.raft.partition.impl.RaftNamespaces;
import io.atomix.raft.protocol.LeadershipTransferInitiateRequest;
import io.atomix.raft.protocol.LeadershipTransferInitiateResponse;
import io.atomix.raft.protocol.LeadershipTransferResultRequest;
import io.atomix.raft.protocol.LeadershipTransferResultResponse;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.raft.protocol.TestRaftServerProtocol;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;

/**
 * Coverage for the leader-side composing handler that drives a coordinated leadership transfer
 * end-to-end from an initiate message and reports the result to the coordinator.
 */
public class RaftCoordinatedLeadershipTransferTest {

  @Rule public RaftRule raftRule = RaftRule.withBootstrappedNodes(3);

  @Test
  public void shouldDriveTransferOnInitiateAndNotifyCoordinator() throws Exception {
    // given
    raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    final var targetId = memberId(target);
    final var coordinatorId = coordinator(leader);

    final CompletableFuture<LeadershipTransferResultRequest> reported = new CompletableFuture<>();
    protocolOf(coordinatorId)
        .registerLeadershipTransferResultHandler(
            request -> {
              reported.complete(request);
              return CompletableFuture.completedFuture(
                  LeadershipTransferResultResponse.builder().withStatus(Status.OK).build());
            });

    // when
    final var ack =
        leader
            .getContext()
            .getProtocol()
            .leadershipTransferInitiate(
                memberId(leader), initiate(targetId, coordinatorId, configVersion(leader)))
            .get(5, TimeUnit.SECONDS);

    // then
    assertThat(ack.accepted()).as("leader accepts the transfer").isTrue();
    Awaitility.await("target becomes leader")
        .atMost(Duration.ofSeconds(15))
        .until(() -> target.getRole() == Role.LEADER);
    assertThat(reported.get(10, TimeUnit.SECONDS).result())
        .isEqualTo(LeadershipTransferResult.TRANSFERRED);
  }

  @Test
  public void shouldRejectInitiateOnFollowerWithRedirect() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var follower = raftRule.getFollower().orElseThrow();
    final var coordinatorId = coordinator(leader);

    // when
    final var ack =
        follower
            .getContext()
            .getProtocol()
            .leadershipTransferInitiate(
                memberId(follower),
                initiate(memberId(leader), coordinatorId, configVersion(leader)))
            .get(5, TimeUnit.SECONDS);

    // then
    assertThat(ack.accepted()).isFalse();
    assertThat(ack.leader()).isEqualTo(memberId(leader));
  }

  @Test
  public void shouldAckImmediateSkipWhenDesiredLeaderIsAlreadyLeader() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var coordinatorId = coordinator(leader);

    // when
    final var ack =
        leader
            .getContext()
            .getProtocol()
            .leadershipTransferInitiate(
                memberId(leader), initiate(memberId(leader), coordinatorId, configVersion(leader)))
            .get(5, TimeUnit.SECONDS);

    // then
    assertThat(ack.accepted()).isFalse();
    assertThat(ack.result()).isEqualTo(LeadershipTransferResult.ALREADY_LEADER);
  }

  @Test
  public void shouldRoundTripTransferMessagesThroughRaftNamespace() {
    // given
    final var initiateRequest = initiate(MemberId.from("2"), MemberId.from("1"), 7);
    final var initiateResponse =
        LeadershipTransferInitiateResponse.builder()
            .withStatus(Status.OK)
            .withAccepted(false)
            .withResult(LeadershipTransferResult.LAG_TOO_HIGH)
            .withLeader(MemberId.from("3"))
            .build();
    final var resultRequest =
        LeadershipTransferResultRequest.builder()
            .withLeader(MemberId.from("3"))
            .withDesiredLeader(MemberId.from("2"))
            .withResult(LeadershipTransferResult.TRANSFERRED)
            .build();

    // when / then
    assertThat(roundTrip(initiateRequest)).isEqualTo(initiateRequest);
    final LeadershipTransferInitiateResponse deserializedResponse = roundTrip(initiateResponse);
    assertThat(deserializedResponse.accepted()).isFalse();
    assertThat(deserializedResponse.result()).isEqualTo(LeadershipTransferResult.LAG_TOO_HIGH);
    assertThat(deserializedResponse.leader()).isEqualTo(MemberId.from("3"));
    assertThat(roundTrip(resultRequest)).isEqualTo(resultRequest);
  }

  private static <T> T roundTrip(final T message) {
    return RaftNamespaces.RAFT_PROTOCOL.deserialize(
        RaftNamespaces.RAFT_PROTOCOL.serialize(message));
  }

  private LeadershipTransferInitiateRequest initiate(
      final MemberId desiredLeader, final MemberId coordinator, final long configVersion) {
    return LeadershipTransferInitiateRequest.builder()
        .withDesiredLeader(desiredLeader)
        .withCoordinator(coordinator)
        .withCoordinatorConfigVersion(configVersion)
        .build();
  }

  private TestRaftServerProtocol protocolOf(final MemberId memberId) {
    return raftRule.getServers().stream()
        .filter(server -> memberId(server).equals(memberId))
        .map(server -> (TestRaftServerProtocol) server.getContext().getProtocol())
        .findFirst()
        .orElseThrow();
  }

  private static MemberId memberId(final RaftServer server) {
    return server.getContext().getCluster().getLocalMember().memberId();
  }

  private static long configVersion(final RaftServer leader) {
    return leader.getContext().getCluster().getConfiguration().index();
  }

  private static MemberId coordinator(final RaftServer leader) {
    return leader.getContext().getCluster().getConfiguration().newMembers().stream()
        .map(io.atomix.raft.cluster.RaftMember::memberId)
        .min(Comparator.comparing(MemberId::id))
        .orElseThrow();
  }
}
