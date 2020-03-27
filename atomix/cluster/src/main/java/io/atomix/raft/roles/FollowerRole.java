/*
 * Copyright 2015-present Open Networking Foundation
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
import io.atomix.raft.RaftServer;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.cluster.impl.RaftMemberContext;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.protocol.AppendRequest;
import io.atomix.raft.protocol.AppendResponse;
import io.atomix.raft.protocol.ConfigureRequest;
import io.atomix.raft.protocol.ConfigureResponse;
import io.atomix.raft.protocol.InstallRequest;
import io.atomix.raft.protocol.InstallResponse;
import io.atomix.raft.protocol.LeaderHeartbeatRequest;
import io.atomix.raft.protocol.PollRequest;
import io.atomix.raft.protocol.PollResponse;
import io.atomix.raft.protocol.VoteRequest;
import io.atomix.raft.protocol.VoteResponse;
import io.atomix.raft.storage.log.entry.RaftLogEntry;
import io.atomix.raft.utils.Quorum;
import io.atomix.storage.journal.Indexed;
import io.atomix.utils.concurrent.Scheduled;
import java.time.Duration;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/** Follower state. */
public final class FollowerRole extends ActiveRole {

  private final Random random = new Random();
  private Scheduled heartbeatTimer;
  private final ClusterMembershipEventListener clusterListener = this::handleClusterEvent;
  private volatile long lastHeartbeat;

  public FollowerRole(final RaftContext context) {
    super(context);
  }

  @Override
  public synchronized CompletableFuture<RaftRole> start() {

    if (raft.getCluster().getActiveMemberStates().isEmpty()) {
      log.info("Single member cluster. Transitioning directly to candidate.");
      raft.transition(RaftServer.Role.CANDIDATE);
      return CompletableFuture.completedFuture(this);
    }

    raft.getMembershipService().addListener(clusterListener);
    return super.start()
        .thenRun(this::resetHeartbeatTimeoutFromDifferentThread)
        .thenApply(v -> this);
  }

  @Override
  public synchronized CompletableFuture<Void> stop() {
    raft.getMembershipService().removeListener(clusterListener);
    raft.getHeartbeatThread().execute(this::cancelHeartbeatTimer);

    return super.stop();
  }

  @Override
  public RaftServer.Role role() {
    return RaftServer.Role.FOLLOWER;
  }

  @Override
  public CompletableFuture<InstallResponse> onInstall(final InstallRequest request) {
    final CompletableFuture<InstallResponse> future = super.onInstall(request);
    resetHeartbeatTimeoutFromDifferentThread();
    return future;
  }

  /** Cancels the heartbeat timer. */
  private void cancelHeartbeatTimer() {
    raft.checkHeartbeatThread();

    if (heartbeatTimer != null) {
      log.trace("Cancelling heartbeat timer");
      heartbeatTimer.cancel();
      heartbeatTimer = null;
    }
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
    final Set<DefaultRaftMember> votingMembers =
        raft.getCluster().getActiveMemberStates().stream()
            .map(RaftMemberContext::getMember)
            .collect(Collectors.toSet());

    // If there are no other members in the cluster, immediately transition to leader.
    if (votingMembers.isEmpty()) {
      raft.transition(RaftServer.Role.CANDIDATE);
      return;
    }

    final Quorum quorum =
        new Quorum(
            raft.getCluster().getQuorum(),
            elected -> {
              // If a majority of the cluster indicated they would vote for us then transition to
              // candidate.
              complete.set(true);
              if (raft.getLeader() == null && elected) {
                raft.transition(RaftServer.Role.CANDIDATE);
              } else {
                resetHeartbeatTimeoutFromDifferentThread();
              }
            });

    // First, load the last log entry to get its term. We load the entry
    // by its index since the index is required by the protocol.
    final Indexed<RaftLogEntry> lastEntry = raft.getLogWriter().getLastEntry();

    final long lastTerm;
    if (lastEntry != null) {
      lastTerm = lastEntry.entry().term();
    } else {
      lastTerm = 0;
    }

    log.debug("Polling members {}", votingMembers);

    // Once we got the last log term, iterate through each current member
    // of the cluster and vote each member for a vote.
    for (final DefaultRaftMember member : votingMembers) {
      log.debug("Polling {} for next term {}", member, raft.getTerm() + 1);
      final PollRequest request =
          PollRequest.builder()
              .withTerm(raft.getTerm())
              .withCandidate(raft.getCluster().getMember().memberId())
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
    resetHeartbeatTimeoutFromDifferentThread();
    return future;
  }

  @Override
  public CompletableFuture<AppendResponse> onAppend(final AppendRequest request) {
    // Reset the heartbeat timeout.
    resetHeartbeatTimeoutFromDifferentThread();

    return super.onAppend(request);
  }

  @Override
  protected VoteResponse handleVote(final VoteRequest request) {
    // Reset the heartbeat timeout if we voted for another candidate.
    final VoteResponse response = super.handleVote(request);
    if (response.voted()) {
      resetHeartbeatTimeoutFromDifferentThread();
    }
    return response;
  }

  @Override
  public void onLeaderHeartbeat(final LeaderHeartbeatRequest request) {
    raft.checkHeartbeatThread();
    logRequest(request);

    final long currentTerm = raft.getTerm();
    final RaftMember currentLeader = raft.getLeader();
    if (request.term() < currentTerm
        || (request.term() == currentTerm
            && (currentLeader == null || !request.leader().equals(currentLeader.memberId())))) {
      log.debug(
          "Expected heartbeat from {} in term {}, but received one from {} in term {}, ignoring it",
          currentLeader,
          currentTerm,
          request.leader(),
          request.term());
      return;
    }

    // If the request indicates a term that is greater than the current term then
    // assign that term and leader to the current context
    raft.getThreadContext().execute(() -> updateTermAndLeader(request.term(), request.leader()));

    // Reset the heartbeat timeout.
    resetHeartbeatTimeoutFromHeartbeatThread();
  }

  private void resetHeartbeatTimeoutFromDifferentThread() {
    raft.runOnHeartbeatContext(this::resetHeartbeatTimeoutFromHeartbeatThread);
  }

  private void resetHeartbeatTimeoutFromHeartbeatThread() {
    raft.checkHeartbeatThread();
    if (!isRunning()) {
      return;
    }

    cancelHeartbeatTimer();
    updateHeartbeat(System.currentTimeMillis());

    // Set the election timeout in a semi-random fashion with the random range
    // being election timeout and 2 * election timeout.
    final Duration delay =
        raft.getElectionTimeout()
            .plus(Duration.ofMillis(random.nextInt((int) raft.getElectionTimeout().toMillis())));
    heartbeatTimer = raft.getHeartbeatThread().schedule(delay, () -> onHeartbeatTimeout(delay));
  }

  private void updateHeartbeat(final long currentTimestamp) {
    raft.checkHeartbeatThread();

    if (lastHeartbeat > 0) {
      raft.getRaftRoleMetrics().observeHeartbeatInterval(currentTimestamp - lastHeartbeat);
    }

    lastHeartbeat = currentTimestamp;
  }

  private void schedulePollRequests(final Duration delay) {
    raft.checkThread();

    if (!isRunning()) {
      return;
    }

    if (raft.getFirstCommitIndex() == 0 || raft.getState() == RaftContext.State.READY) {
      final long missTime = System.currentTimeMillis() - lastHeartbeat;
      log.info(
          "No heartbeat from {} in the last {} (calculated from last {} ms), sending poll requests",
          raft.getLeader(),
          delay,
          missTime);
      raft.getRaftRoleMetrics().countHeartbeatMiss();

      raft.setLeader(null);
      sendPollRequests();
    }
  }

  private void onHeartbeatTimeout(final Duration delay) {
    raft.checkHeartbeatThread();

    if (!isRunning()) {
      return;
    }

    final Duration pollTimeout = raft.getElectionTimeout();

    if (heartbeatTimer != null && !heartbeatTimer.isDone()) {
      log.debug(
          "Cancelling pre-existing heartbeatTimer, most likely the node is polling for an election...");
      heartbeatTimer.cancel();
    }

    heartbeatTimer =
        raft.getHeartbeatThread()
            .schedule(
                pollTimeout,
                () -> {
                  log.debug("Failed to poll a majority of the cluster in {}", pollTimeout);
                  resetHeartbeatTimeoutFromHeartbeatThread();
                });

    raft.getThreadContext().execute(() -> schedulePollRequests(delay));
  }

  private void handlePollResponse(
      final AtomicBoolean complete,
      final Quorum quorum,
      final DefaultRaftMember member,
      final PollResponse response,
      final Throwable error) {
    raft.checkThread();

    if (isRunning() && !complete.get()) {
      if (error != null) {
        log.warn("{}", error.getMessage());
        quorum.fail();
      } else {
        if (response.term() > raft.getTerm()) {
          raft.setTerm(response.term());
        }

        if (!response.accepted()) {
          log.debug("Received rejected poll from {}", member);
          quorum.fail();
        } else if (response.term() != raft.getTerm()) {
          log.debug("Received accepted poll for a different term from {}", member);
          quorum.fail();
        } else {
          log.debug("Received accepted poll from {}", member);
          quorum.succeed();
        }
      }
    }
  }
}
