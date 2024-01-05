/*
 * Copyright 2015-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft.roles;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.MemberId;
import io.atomix.raft.ElectionTimer;
import io.atomix.raft.ElectionTimerFactory;
import io.atomix.raft.RaftServer;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.protocol.AppendResponse;
import io.atomix.raft.protocol.ConfigureRequest;
import io.atomix.raft.protocol.ConfigureResponse;
import io.atomix.raft.protocol.InstallRequest;
import io.atomix.raft.protocol.InstallResponse;
import io.atomix.raft.protocol.InternalAppendRequest;
import io.atomix.raft.protocol.PollRequest;
import io.atomix.raft.protocol.PollResponse;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.raft.protocol.TransferRequest;
import io.atomix.raft.protocol.TransferResponse;
import io.atomix.raft.protocol.VoteRequest;
import io.atomix.raft.protocol.VoteResponse;
import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.utils.VoteQuorum;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Follower state. */
public final class FollowerRole extends ActiveRole {

  private final ElectionTimer electionTimer;
  private final ClusterMembershipEventListener clusterListener = this::handleClusterEvent;

  public FollowerRole(final RaftContext context, final ElectionTimerFactory electionTimerFactory) {
    super(context);
    electionTimer = electionTimerFactory.create(this::schedulePollRequests, log);
  }

  public ElectionTimer getElectionTimer() {
    return electionTimer;
  }

  @Override
  public synchronized CompletableFuture<RaftRole> start() {
    raft.getMembershipService().addListener(clusterListener);

    if (raft.getCluster().isSingleMemberCluster()) {
      log.info("Single member cluster. Transitioning directly to candidate.");
      raft.transition(RaftServer.Role.CANDIDATE);
      return CompletableFuture.completedFuture(this);
    }

    return super.start().thenRun(electionTimer::reset).thenApply(v -> this);
  }

  @Override
  public synchronized CompletableFuture<Void> stop() {
    raft.getMembershipService().removeListener(clusterListener);
    electionTimer.cancel();

    return super.stop();
  }

  @Override
  public RaftServer.Role role() {
    return RaftServer.Role.FOLLOWER;
  }

  @Override
  public CompletableFuture<InstallResponse> onInstall(final InstallRequest request) {
    final CompletableFuture<InstallResponse> future = super.onInstall(request);
    if (isRequestFromCurrentLeader(request.currentTerm(), request.leader())) {
      onHeartbeatFromLeader();
    }
    return future;
  }

  /** Handles a cluster event. */
  private void handleClusterEvent(final ClusterMembershipEvent event) {
    raft.getThreadContext()
        .execute(
            () -> {
              final RaftMember leader = raft.getLeader();
              if (leader != null
                  && event.type() == ClusterMembershipEvent.Type.MEMBER_REMOVED
                  && event.subject().id().equals(leader.memberId())) {
                log.info(
                    "Known leader {} was removed from cluster, sending poll requests",
                    leader.memberId());
                raft.setLeader(null);
                sendPollRequests();
              }
            });
  }

  /**
   * Polls all members of the cluster to determine whether this member should transition to the
   * CANDIDATE state.
   */
  private void sendPollRequests() {
    // Create a quorum that will track the number of nodes that have responded to the poll request.
    final AtomicBoolean complete = new AtomicBoolean();
    final var votingMembers = raft.getCluster().getVotingMembers();

    // If there are no other members in the cluster, immediately transition to leader.
    if (votingMembers.isEmpty()) {
      log.info("Transitioning to candidate as there are no known other active members");
      raft.transition(RaftServer.Role.CANDIDATE);
      return;
    }

    log.info("Sending poll requests to all active members: {}", votingMembers);

    final var quorum =
        raft.getCluster()
            .getVoteQuorum(
                elected -> {
                  // If a majority of the cluster indicated they would vote for us then transition
                  // to
                  // candidate.
                  complete.set(true);
                  if (raft.getLeader() == null && elected) {
                    raft.transition(RaftServer.Role.CANDIDATE);
                  } else {
                    electionTimer.reset();
                  }
                });

    // First, load the last log entry to get its term. We load the entry
    // by its index since the index is required by the protocol.
    final IndexedRaftLogEntry lastEntry = raft.getLog().getLastEntry();

    final long lastTerm;
    if (lastEntry != null) {
      lastTerm = lastEntry.term();
    } else {
      lastTerm = 0;
    }

    // Once we got the last log term, iterate through each current member
    // of the cluster and vote each member for a vote.
    for (final RaftMember member : votingMembers) {
      log.debug("Polling {} for next term {}", member, raft.getTerm() + 1);
      final PollRequest request =
          PollRequest.builder()
              .withTerm(raft.getTerm())
              .withCandidate(raft.getCluster().getLocalMember().memberId())
              .withLastLogIndex(lastEntry != null ? lastEntry.index() : 0)
              .withLastLogTerm(lastTerm)
              .build();
      raft.getProtocol()
          .poll(member.memberId(), request)
          .whenCompleteAsync(
              (response, error) -> handlePollResponse(complete, quorum, member, response, error),
              raft.getThreadContext());
    }
  }

  @Override
  public CompletableFuture<ConfigureResponse> onConfigure(final ConfigureRequest request) {
    final CompletableFuture<ConfigureResponse> future = super.onConfigure(request);
    if (isRequestFromCurrentLeader(request.term(), request.leader())) {
      onHeartbeatFromLeader();
    }
    return future;
  }

  @Override
  public CompletableFuture<TransferResponse> onTransfer(final TransferRequest request) {
    log.info(
        "Current leader {} tries to transfer leadership, transitioning to candidate now.",
        request.member());
    raft.transition(Role.CANDIDATE);
    return CompletableFuture.completedFuture(
        logResponse(TransferResponse.builder().withStatus(Status.OK).build()));
  }

  @Override
  public CompletableFuture<AppendResponse> onAppend(final InternalAppendRequest request) {
    final CompletableFuture<AppendResponse> future = super.onAppend(request);
    if (isRequestFromCurrentLeader(request.term(), request.leader())) {
      onHeartbeatFromLeader();
    }
    return future;
  }

  @Override
  protected VoteResponse handleVote(final VoteRequest request) {
    // Reset the heartbeat timeout if we voted for another candidate.
    final VoteResponse response = super.handleVote(request);
    if (response.voted()) {
      onHeartbeatFromLeader();
    }
    return response;
  }

  private boolean isRequestFromCurrentLeader(final long term, final MemberId leader) {
    final long currentTerm = raft.getTerm();
    final RaftMember currentLeader = raft.getLeader();
    if (term < currentTerm
        || (term == currentTerm
            && (currentLeader == null || !leader.equals(currentLeader.memberId())))) {
      log.debug(
          "Expected heartbeat from {} in term {}, but received one from {} in term {}, ignoring it",
          currentLeader,
          currentTerm,
          leader,
          leader);
      return false;
    }
    return true;
  }

  private void onHeartbeatFromLeader() {
    raft.checkThread();
    if (!isRunning()) {
      return;
    }

    updateHeartbeat(System.currentTimeMillis());
    electionTimer.reset();
  }

  private void updateHeartbeat(final long currentTimestamp) {
    if (raft.getLastHeartbeat() > 0) {
      raft.getRaftRoleMetrics()
          .observeHeartbeatInterval(currentTimestamp - raft.getLastHeartbeat());
    }

    raft.setLastHeartbeat(currentTimestamp);
  }

  private void schedulePollRequests() {
    raft.checkThread();

    if (!isRunning()) {
      return;
    }

    if (raft.getFirstCommitIndex() == 0 || raft.getState() == RaftContext.State.READY) {
      final var timeSinceLastHeartbeatMs = System.currentTimeMillis() - raft.getLastHeartbeat();
      final var leader =
          Optional.ofNullable(raft.getLeader())
              .map(DefaultRaftMember::memberId)
              .map(MemberId::id)
              .orElse("a known leader");

      log.info("No heartbeat from {} since {}ms", leader, timeSinceLastHeartbeatMs);
      raft.getRaftRoleMetrics().countHeartbeatMiss();

      raft.setLeader(null);
      sendPollRequests();
    }
  }

  private void handlePollResponse(
      final AtomicBoolean complete,
      final VoteQuorum quorum,
      final RaftMember member,
      final PollResponse response,
      final Throwable error) {
    raft.checkThread();

    if (isRunning() && !complete.get()) {
      if (error != null) {
        log.warn("Poll request to {} failed: {}", member.memberId(), error.getMessage());
        quorum.fail(member.memberId());
      } else {
        if (response.term() > raft.getTerm()) {
          raft.setTerm(response.term());
        }

        if (!response.accepted()) {
          log.debug("Received rejected poll from {}", member);
          quorum.fail(member.memberId());
        } else if (response.term() != raft.getTerm()) {
          log.debug("Received accepted poll for a different term from {}", member);
          quorum.fail(member.memberId());
        } else {
          log.debug("Received accepted poll from {}", member);
          quorum.succeed(member.memberId());
        }
      }
    }
  }
}
