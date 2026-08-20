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

import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.impl.RaftNamespaces;
import io.atomix.raft.protocol.LeadershipTransferInitiateRequest;
import io.atomix.raft.protocol.LeadershipTransferInitiateResponse;
import io.atomix.raft.protocol.LeadershipTransferResultRequest;
import io.atomix.raft.protocol.LeadershipTransferResultResponse;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.raft.protocol.TestRaftServerProtocol;
import io.atomix.raft.roles.LeaderRole;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;

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
                memberId(leader),
                initiate(targetId, coordinatorId, configIndex(leader), 0x5eed_000aL))
            .get(5, TimeUnit.SECONDS);

    // then
    assertThat(ack.accepted()).as("leader accepts the transfer").isTrue();
    Awaitility.await("target becomes leader")
        .atMost(Duration.ofSeconds(15))
        .until(() -> target.getRole() == Role.LEADER);
    Awaitility.await("the previous leader gives up leadership")
        .atMost(Duration.ofSeconds(15))
        .until(() -> leader.getRole() != Role.LEADER);
    final var notification = reported.get(10, TimeUnit.SECONDS);
    assertThat(notification.result()).isEqualTo(LeadershipTransferResult.TRANSFERRED);
    assertThat(notification.correlationId())
        .as("the success notification echoes the initiate request's correlation id")
        .isEqualTo(0x5eed_000aL);
  }

  @Test
  public void shouldAcceptInitiateForCaughtUpFollower() throws Exception {
    // given
    raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();
    final var targetId = memberId(raftRule.getFollower().orElseThrow());

    // when
    final var ack =
        leader
            .getContext()
            .getProtocol()
            .leadershipTransferInitiate(
                memberId(leader),
                initiate(targetId, coordinator(leader), configIndex(leader), 0x5eed_0001L))
            .get(5, TimeUnit.SECONDS);

    // then
    assertThat(ack.accepted()).isTrue();
  }

  @Test
  public void shouldAcceptAnotherTransferAfterOneFailed() throws Exception {
    // given
    raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();
    final var targetId = memberId(raftRule.getFollower().orElseThrow());
    final var coordinatorId = coordinator(leader);
    final var reported = new ArrayBlockingQueue<LeadershipTransferResultRequest>(2);
    protocolOf(coordinatorId)
        .registerLeadershipTransferResultHandler(
            request -> {
              reported.add(request);
              return CompletableFuture.completedFuture(
                  LeadershipTransferResultResponse.builder().withStatus(Status.OK).build());
            });
    leader
        .getContext()
        .setLeadershipTransferWriteBarrier(
            new LeadershipTransferWriteBarrier() {
              @Override
              public CompletableFuture<Long> freeze(final Duration timeout) {
                return CompletableFuture.failedFuture(
                    new TimeoutException(
                        "Timed out establishing the leadership-transfer write freeze"));
              }

              @Override
              public CompletableFuture<Void> unfreeze() {
                return CompletableFuture.completedFuture(null);
              }
            });

    // when
    final var first =
        leader
            .getContext()
            .getProtocol()
            .leadershipTransferInitiate(
                memberId(leader),
                initiate(targetId, coordinatorId, configIndex(leader), 0x5eed_000cL))
            .get(5, TimeUnit.SECONDS);
    assertThat(reported.poll(10, TimeUnit.SECONDS))
        .extracting(LeadershipTransferResultRequest::result)
        .isEqualTo(LeadershipTransferResult.PAUSE_FAILED);
    final var second =
        leader
            .getContext()
            .getProtocol()
            .leadershipTransferInitiate(
                memberId(leader),
                initiate(targetId, coordinatorId, configIndex(leader), 0x5eed_000dL))
            .get(5, TimeUnit.SECONDS);

    // then
    assertThat(first.accepted()).isTrue();
    assertThat(second.accepted())
        .as("a leader that kept leadership is free to run another transfer")
        .isTrue();
    assertThat(reported.poll(10, TimeUnit.SECONDS))
        .extracting(LeadershipTransferResultRequest::result)
        .isEqualTo(LeadershipTransferResult.PAUSE_FAILED);
  }

  @Test
  public void shouldRejectInitiateOnFollower() throws Exception {
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
                initiate(memberId(leader), coordinatorId, configIndex(leader), 0x5eed_0002L))
            .get(5, TimeUnit.SECONDS);

    // then
    assertThat(ack.accepted()).isFalse();
    assertThat(ack.error().type()).isEqualTo(RaftError.Type.ILLEGAL_MEMBER_STATE);
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
                memberId(leader),
                initiate(memberId(leader), coordinatorId, configIndex(leader), 0x5eed_0003L))
            .get(5, TimeUnit.SECONDS);

    // then
    assertThat(ack.accepted()).isFalse();
    assertThat(ack.rejectionReason()).isEqualTo(LeadershipTransferResult.ALREADY_LEADER);
  }

  @Test
  public void shouldReportConfigurationChangeWhenTheFreezeIsRefused() throws Exception {
    // given
    raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();
    final var targetId = memberId(raftRule.getFollower().orElseThrow());
    final var coordinatorId = coordinator(leader);
    final CompletableFuture<LeadershipTransferResultRequest> reported = new CompletableFuture<>();
    protocolOf(coordinatorId)
        .registerLeadershipTransferResultHandler(
            request -> {
              reported.complete(request);
              return CompletableFuture.completedFuture(
                  LeadershipTransferResultResponse.builder().withStatus(Status.OK).build());
            });

    leader
        .getContext()
        .setLeadershipTransferWriteBarrier(
            new LeadershipTransferWriteBarrier() {
              @Override
              public CompletableFuture<Long> freeze(final Duration timeout) {
                return CompletableFuture.failedFuture(
                    new CompletionException(
                        new LeaderRole.ConfigurationChangeInProgressException(
                            "Cannot pause for leadership transfer: configuration change in"
                                + " progress")));
              }

              @Override
              public CompletableFuture<Void> unfreeze() {
                return CompletableFuture.completedFuture(null);
              }
            });

    // when
    final var ack =
        leader
            .getContext()
            .getProtocol()
            .leadershipTransferInitiate(
                memberId(leader),
                initiate(targetId, coordinatorId, configIndex(leader), 0x5eed_0004L))
            .get(5, TimeUnit.SECONDS);

    // then
    assertThat(ack.accepted()).isTrue();
    final var notification = reported.get(10, TimeUnit.SECONDS);
    assertThat(notification.result())
        .isEqualTo(LeadershipTransferResult.CONFIGURATION_CHANGE_IN_PROGRESS);
    assertThat(notification.correlationId())
        .as("the failure notification echoes the initiate request's correlation id")
        .isEqualTo(0x5eed_0004L);
  }

  @Test
  public void shouldReportPauseFailureWhenTheFreezeFails() throws Exception {
    // given
    raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();
    final var targetId = memberId(raftRule.getFollower().orElseThrow());
    final var coordinatorId = coordinator(leader);
    final CompletableFuture<LeadershipTransferResultRequest> reported = new CompletableFuture<>();
    protocolOf(coordinatorId)
        .registerLeadershipTransferResultHandler(
            request -> {
              reported.complete(request);
              return CompletableFuture.completedFuture(
                  LeadershipTransferResultResponse.builder().withStatus(Status.OK).build());
            });

    leader
        .getContext()
        .setLeadershipTransferWriteBarrier(
            new LeadershipTransferWriteBarrier() {
              @Override
              public CompletableFuture<Long> freeze(final Duration timeout) {
                return CompletableFuture.failedFuture(
                    new TimeoutException("Timed out arming the leadership-transfer pause barrier"));
              }

              @Override
              public CompletableFuture<Void> unfreeze() {
                return CompletableFuture.completedFuture(null);
              }
            });

    // when
    final var ack =
        leader
            .getContext()
            .getProtocol()
            .leadershipTransferInitiate(
                memberId(leader),
                initiate(targetId, coordinatorId, configIndex(leader), 0x5eed_000bL))
            .get(5, TimeUnit.SECONDS);

    // then
    assertThat(ack.accepted()).isTrue();
    assertThat(reported.get(10, TimeUnit.SECONDS).result())
        .isEqualTo(LeadershipTransferResult.PAUSE_FAILED);
  }

  @Test
  public void shouldRoundTripTransferMessagesThroughRaftNamespace() {
    // given
    final var initiateRequest = initiate(MemberId.from("2"), MemberId.from("1"), 7, 0x5eed_0005L);
    final var overridingRequest =
        LeadershipTransferInitiateRequest.builder()
            .withDesiredLeader(MemberId.from("2"))
            .withCoordinator(MemberId.from("1"))
            .withCoordinatorConfigVersion(7)
            .withCorrelationId(0x5eed_0005L)
            .withReplicationLagThreshold(4096)
            .withReplicationTimeout(Duration.ofSeconds(30))
            .withMaxTransferAttempts(7)
            .build();
    final var acceptedResponse =
        LeadershipTransferInitiateResponse.builder().withStatus(Status.OK).build();
    final var rejectedResponse =
        LeadershipTransferInitiateResponse.builder()
            .withStatus(Status.OK)
            .withRejectionReason(LeadershipTransferResult.LAG_TOO_HIGH)
            .build();
    final var notLeaderResponse =
        LeadershipTransferInitiateResponse.builder()
            .withStatus(Status.ERROR)
            .withError(RaftError.Type.ILLEGAL_MEMBER_STATE)
            .build();
    final var resultRequest =
        result(
            MemberId.from("3"),
            MemberId.from("2"),
            LeadershipTransferResult.TRANSFERRED,
            0x5eed_0005L);

    // when / then
    assertThat(roundTrip(initiateRequest)).isEqualTo(initiateRequest);
    assertThat(roundTrip(initiateRequest).correlationId()).isEqualTo(0x5eed_0005L);
    assertThat(roundTrip(overridingRequest)).isEqualTo(overridingRequest);
    assertThat(roundTrip(overridingRequest).replicationTimeout()).isEqualTo(Duration.ofSeconds(30));
    assertThat(roundTrip(acceptedResponse).accepted()).isTrue();
    final LeadershipTransferInitiateResponse deserializedRejection = roundTrip(rejectedResponse);
    assertThat(deserializedRejection.accepted()).isFalse();
    assertThat(deserializedRejection.rejectionReason())
        .isEqualTo(LeadershipTransferResult.LAG_TOO_HIGH);
    final LeadershipTransferInitiateResponse deserializedNotLeader = roundTrip(notLeaderResponse);
    assertThat(deserializedNotLeader.accepted()).isFalse();
    assertThat(deserializedNotLeader.error().type()).isEqualTo(RaftError.Type.ILLEGAL_MEMBER_STATE);
    assertThat(roundTrip(resultRequest)).isEqualTo(resultRequest);
    assertThat(roundTrip(resultRequest).correlationId()).isEqualTo(0x5eed_0005L);
  }

  @Test
  public void shouldTellRequestsApartByCorrelationId() {
    // given
    final var initiateRequest = initiate(MemberId.from("2"), MemberId.from("1"), 7, 0x5eed_0006L);
    final var sameInitiateRequest =
        initiate(MemberId.from("2"), MemberId.from("1"), 7, 0x5eed_0006L);
    final var otherInitiateRequest =
        initiate(MemberId.from("2"), MemberId.from("1"), 7, 0x5eed_0007L);
    final var resultRequest =
        result(
            MemberId.from("3"),
            MemberId.from("2"),
            LeadershipTransferResult.TRANSFERRED,
            0x5eed_0006L);
    final var sameResultRequest =
        result(
            MemberId.from("3"),
            MemberId.from("2"),
            LeadershipTransferResult.TRANSFERRED,
            0x5eed_0006L);
    final var otherResultRequest =
        result(
            MemberId.from("3"),
            MemberId.from("2"),
            LeadershipTransferResult.TRANSFERRED,
            0x5eed_0007L);

    // then
    assertThat(initiateRequest.correlationId()).isEqualTo(0x5eed_0006L);
    assertThat(initiateRequest)
        .isEqualTo(sameInitiateRequest)
        .hasSameHashCodeAs(sameInitiateRequest)
        .isNotEqualTo(otherInitiateRequest);
    assertThat(resultRequest.correlationId()).isEqualTo(0x5eed_0006L);
    assertThat(resultRequest)
        .isEqualTo(sameResultRequest)
        .hasSameHashCodeAs(sameResultRequest)
        .isNotEqualTo(otherResultRequest);
  }

  @Test
  public void shouldRejectRequestsBuiltWithoutACorrelationId() {
    // given
    final var initiateBuilder =
        LeadershipTransferInitiateRequest.builder()
            .withDesiredLeader(MemberId.from("2"))
            .withCoordinator(MemberId.from("1"))
            .withCoordinatorConfigVersion(7);
    final var resultBuilder =
        LeadershipTransferResultRequest.builder()
            .withLeader(MemberId.from("3"))
            .withDesiredLeader(MemberId.from("2"))
            .withResult(LeadershipTransferResult.TRANSFERRED);

    // when / then
    assertThatThrownBy(initiateBuilder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("correlationId");
    assertThatThrownBy(resultBuilder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("correlationId");
  }

  @Test
  public void shouldKeepAcceptedCorrelationIdWhenAnotherInitiateIsRejected() throws Exception {
    // given
    raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();
    final var targetId = memberId(raftRule.getFollower().orElseThrow());
    final var coordinatorId = coordinator(leader);
    final CompletableFuture<LeadershipTransferResultRequest> reported = new CompletableFuture<>();
    protocolOf(coordinatorId)
        .registerLeadershipTransferResultHandler(
            request -> {
              reported.complete(request);
              return CompletableFuture.completedFuture(
                  LeadershipTransferResultResponse.builder().withStatus(Status.OK).build());
            });

    final var freeze = new CompletableFuture<Long>();
    leader
        .getContext()
        .setLeadershipTransferWriteBarrier(
            new LeadershipTransferWriteBarrier() {
              @Override
              public CompletableFuture<Long> freeze(final Duration timeout) {
                return freeze;
              }

              @Override
              public CompletableFuture<Void> unfreeze() {
                return CompletableFuture.completedFuture(null);
              }
            });

    // when
    final var accepted =
        leader
            .getContext()
            .getProtocol()
            .leadershipTransferInitiate(
                memberId(leader),
                initiate(targetId, coordinatorId, configIndex(leader), 0x5eed_0008L))
            .get(5, TimeUnit.SECONDS);
    final var rejected =
        leader
            .getContext()
            .getProtocol()
            .leadershipTransferInitiate(
                memberId(leader),
                initiate(targetId, coordinatorId, configIndex(leader), 0x5eed_0009L))
            .get(5, TimeUnit.SECONDS);
    freeze.completeExceptionally(new RuntimeException("freeze abandoned in test"));

    // then
    assertThat(accepted.accepted()).isTrue();
    assertThat(rejected.accepted()).isFalse();
    assertThat(rejected.rejectionReason()).isEqualTo(LeadershipTransferResult.TRANSFER_IN_PROGRESS);
    assertThat(reported.get(10, TimeUnit.SECONDS).correlationId())
        .as("the rejected request does not replace the in-flight attempt's correlation id")
        .isEqualTo(0x5eed_0008L);
  }

  private TestRaftServerProtocol protocolOf(final MemberId memberId) {
    return raftRule.getServers().stream()
        .filter(server -> memberId(server).equals(memberId))
        .map(server -> (TestRaftServerProtocol) server.getContext().getProtocol())
        .findFirst()
        .orElseThrow();
  }

  private static <T> T roundTrip(final T message) {
    return RaftNamespaces.RAFT_PROTOCOL.deserialize(
        RaftNamespaces.RAFT_PROTOCOL.serialize(message));
  }

  private LeadershipTransferInitiateRequest initiate(
      final MemberId desiredLeader,
      final MemberId coordinator,
      final long configVersion,
      final long correlationId) {
    return LeadershipTransferInitiateRequest.builder()
        .withDesiredLeader(desiredLeader)
        .withCoordinator(coordinator)
        .withCoordinatorConfigVersion(configVersion)
        .withCorrelationId(correlationId)
        .build();
  }

  private static LeadershipTransferResultRequest result(
      final MemberId leader,
      final MemberId desiredLeader,
      final LeadershipTransferResult result,
      final long correlationId) {
    return LeadershipTransferResultRequest.builder()
        .withLeader(leader)
        .withDesiredLeader(desiredLeader)
        .withResult(result)
        .withCorrelationId(correlationId)
        .build();
  }

  private static MemberId memberId(final RaftServer server) {
    return server.getContext().getCluster().getLocalMember().memberId();
  }

  private static long configIndex(final RaftServer leader) {
    return leader.getContext().getCluster().getConfiguration().index();
  }

  private static MemberId coordinator(final RaftServer leader) {
    return leader.getContext().getCluster().getConfiguration().newMembers().stream()
        .map(io.atomix.raft.cluster.RaftMember::memberId)
        .min(Comparator.comparing(MemberId::id))
        .orElseThrow();
  }
}
