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

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.protocol.LeadershipTransferInitiateRequest;
import io.atomix.raft.protocol.LeadershipTransferInitiateResponse;
import io.atomix.raft.protocol.LeadershipTransferResultRequest;
import io.atomix.raft.protocol.LeadershipTransferResultResponse;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.raft.protocol.TestRaftServerProtocol;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Drives a coordinated leadership transfer the way a coordinator would. */
final class CoordinatedTransferDriver {

  private final RaftRule raftRule;
  private final RaftServer leader;
  private final MemberId coordinatorId;
  private final CompletableFuture<LeadershipTransferResultRequest> reported =
      new CompletableFuture<>();
  private long nextCorrelationId = 0x5eed_0100L;

  CoordinatedTransferDriver(final RaftRule raftRule, final RaftServer leader) {
    this.raftRule = raftRule;
    this.leader = leader;
    coordinatorId =
        leader.getContext().getCluster().getConfiguration().newMembers().stream()
            .map(RaftMember::memberId)
            .min(Comparator.comparing(MemberId::id))
            .orElseThrow();
    protocolOf(coordinatorId)
        .registerLeadershipTransferResultHandler(
            request -> {
              reported.complete(request);
              return CompletableFuture.completedFuture(
                  LeadershipTransferResultResponse.builder().withStatus(Status.OK).build());
            });
  }

  /**
   * A follower other than the coordinator, so a test that cuts the desired leader off does not also
   * cut off the coordinator the result is reported to.
   */
  RaftServer followerOutsideCoordinator() {
    return raftRule.getServers().stream()
        .filter(server -> server.getRole() == RaftServer.Role.FOLLOWER)
        .filter(server -> !memberId(server).equals(coordinatorId))
        .findFirst()
        .orElseThrow();
  }

  /** Requests a transfer to {@code desiredLeader} on the coordinator's behalf. */
  LeadershipTransferInitiateResponse initiate(final RaftServer desiredLeader) throws Exception {
    final var request =
        LeadershipTransferInitiateRequest.builder()
            .withDesiredLeader(memberId(desiredLeader))
            .withCoordinator(coordinatorId)
            .withCoordinatorConfigIndex(leader.getContext().getCluster().getConfiguration().index())
            .withCorrelationId(nextCorrelationId++)
            .build();
    return leader
        .getContext()
        .getProtocol()
        .leadershipTransferInitiate(memberId(leader), request)
        .get(5, TimeUnit.SECONDS);
  }

  /** Completes with the terminal result the leader reports to the coordinator. */
  CompletableFuture<LeadershipTransferResultRequest> reportedResult() {
    return reported;
  }

  static MemberId memberId(final RaftServer server) {
    return server.getContext().getCluster().getLocalMember().memberId();
  }

  private TestRaftServerProtocol protocolOf(final MemberId memberId) {
    return raftRule.getServers().stream()
        .filter(server -> memberId(server).equals(memberId))
        .map(server -> (TestRaftServerProtocol) server.getContext().getProtocol())
        .findFirst()
        .orElseThrow();
  }
}
