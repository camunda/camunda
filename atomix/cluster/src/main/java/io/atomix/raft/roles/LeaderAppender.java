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
 * limitations under the License
 */
package io.atomix.raft.roles;

import io.atomix.raft.RaftException;
import io.atomix.raft.RaftServer;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.impl.RaftMemberContext;
import io.atomix.raft.protocol.AppendRequest;
import io.atomix.raft.protocol.AppendResponse;
import io.atomix.raft.protocol.ConfigureRequest;
import io.atomix.raft.protocol.ConfigureResponse;
import io.atomix.raft.protocol.InstallRequest;
import io.atomix.raft.protocol.InstallResponse;
import io.atomix.raft.protocol.RaftRequest;
import io.atomix.raft.storage.snapshot.Snapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The leader appender is responsible for sending {@link AppendRequest}s on behalf of a leader to
 * followers. Append requests are sent by the leader only to other active members of the cluster.
 */
final class LeaderAppender extends AbstractAppender {

  private static final long MAX_HEARTBEAT_WAIT = 60000;
  private static final int MIN_BACKOFF_FAILURE_COUNT = 5;
  private static final int MIN_STEP_DOWN_FAILURE_COUNT = 3;

  private final long leaderTime;
  private final long leaderIndex;
  private final long electionTimeout;
  private final long heartbeatInterval;
  private final Map<Long, CompletableFuture<Long>> appendFutures = new HashMap<>();
  private final List<TimestampedFuture<Long>> heartbeatFutures = new ArrayList<>();
  private final long heartbeatTime;

  LeaderAppender(final LeaderRole leader) {
    super(leader.raft);
    this.leaderTime = System.currentTimeMillis();
    this.leaderIndex = raft.getLogWriter().getNextIndex();
    this.heartbeatTime = leaderTime;
    this.electionTimeout = raft.getElectionTimeout().toMillis();
    this.heartbeatInterval = raft.getHeartbeatInterval().toMillis();
  }

  /**
   * Registers a commit handler for the given commit index.
   *
   * @param index The index for which to register the handler.
   * @return A completable future to be completed once the given log index has been committed.
   */
  public CompletableFuture<Long> appendEntries(final long index) {
    raft.checkThread();

    if (index == 0) {
      return appendEntries();
    }

    if (index <= raft.getCommitIndex()) {
      return CompletableFuture.completedFuture(index);
    }

    // If there are no other stateful servers in the cluster, immediately commit the index OR
    // If there are no other active members in the cluster, update the commit index and complete the
    // commit.
    // The updated commit index will be sent to passive/reserve members on heartbeats.
    if (raft.getCluster().getActiveMemberStates().isEmpty()) {
      final long previousCommitIndex = raft.getCommitIndex();
      raft.setCommitIndex(index);
      completeCommits(previousCommitIndex, index);
      return CompletableFuture.completedFuture(index);
    }

    // Only send entry-specific AppendRequests to active members of the cluster.
    return appendFutures.computeIfAbsent(
        index,
        i -> {
          for (final RaftMemberContext member : raft.getCluster().getActiveMemberStates()) {
            appendEntries(member);
          }
          return new CompletableFuture<>();
        });
  }

  /**
   * Triggers a heartbeat to a majority of the cluster.
   *
   * <p>For followers to which no AppendRequest is currently being sent, a new empty AppendRequest
   * will be created and sent. For followers to which an AppendRequest is already being sent, the
   * appendEntries() call will piggyback on the *next* AppendRequest. Thus, multiple calls to this
   * method will only ever result in a single AppendRequest to each follower at any given time, and
   * the returned future will be shared by all concurrent calls.
   *
   * @return A completable future to be completed the next time a heartbeat is received by a
   *     majority of the cluster.
   */
  public CompletableFuture<Long> appendEntries() {
    raft.checkThread();

    // If there are no other active members in the cluster, simply complete the append operation.
    if (raft.getCluster().getRemoteMemberStates().isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }

    // Create a heartbeat future and add it to the heartbeat futures list.
    final TimestampedFuture<Long> future = new TimestampedFuture<>();
    heartbeatFutures.add(future);

    // Iterate through members and append entries. Futures will be completed on responses from
    // followers.
    for (final RaftMemberContext member : raft.getCluster().getRemoteMemberStates()) {
      appendEntries(member);
    }
    return future;
  }

  /** Completes append entries attempts up to the given index. */
  private void completeCommits(final long previousCommitIndex, final long commitIndex) {
    for (long i = previousCommitIndex + 1; i <= commitIndex; i++) {
      final CompletableFuture<Long> future = appendFutures.remove(i);
      if (future != null) {
        future.complete(i);
      }
    }
  }

  @Override
  protected void handleAppendResponseFailure(
      final RaftMemberContext member, final AppendRequest request, final Throwable error) {
    failHeartbeat();
    super.handleAppendResponseFailure(member, request, error);
  }

  @Override
  protected void failAttempt(
      final RaftMemberContext member, final RaftRequest request, final Throwable error) {
    super.failAttempt(member, request, error);

    // Fail heartbeat futures.
    failHeartbeat();

    // Verify that the leader has contacted a majority of the cluster within the last two election
    // timeouts.
    // If the leader is not able to contact a majority of the cluster within two election timeouts,
    // assume
    // that a partition occurred and transition back to the FOLLOWER state.
    final long quorumResponseTime =
        System.currentTimeMillis() - Math.max(computeResponseTime(), leaderTime);
    final long maxQuorumResponseTimeout = electionTimeout * 2;
    if (member.getFailureCount() >= MIN_STEP_DOWN_FAILURE_COUNT
        && quorumResponseTime > maxQuorumResponseTimeout) {
      log.warn(
          "Suspected network partition after {} failures from {} over a period of time {} > {}, stepping down",
          member.getFailureCount(),
          member.getMember().memberId(),
          quorumResponseTime,
          maxQuorumResponseTimeout);
      raft.setLeader(null);
      raft.transition(RaftServer.Role.FOLLOWER);
    }
  }

  @Override
  protected void handleAppendResponse(
      final RaftMemberContext member,
      final AppendRequest request,
      final AppendResponse response,
      final long timestamp) {
    super.handleAppendResponse(member, request, response, timestamp);
    recordHeartbeat(member, timestamp);
  }

  @Override
  protected void handleAppendResponseOk(
      final RaftMemberContext member, final AppendRequest request, final AppendResponse response) {
    // Reset the member failure count and update the member's availability status if necessary.
    succeedAttempt(member);

    // If replication succeeded then trigger commit futures.
    if (response.succeeded()) {
      member.appendSucceeded();
      updateMatchIndex(member, response);

      // If entries were committed to the replica then check commit indexes.
      if (!request.entries().isEmpty()) {
        commitEntries();
      }

      // If there are more entries to send then attempt to send another commit.
      if (hasMoreEntries(member)) {
        appendEntries(member);
      }
    }
    // If we've received a greater term, update the term and transition back to follower.
    else if (response.term() > raft.getTerm()) {
      log.info(
          "Received successful append response higher term ({} > {}) from {}, implying there is a new leader - transitioning to follower",
          response.term(),
          raft.getTerm(),
          member.getMember());
      raft.setTerm(response.term());
      raft.setLeader(null);
      raft.transition(RaftServer.Role.FOLLOWER);
    }
    // If the response failed, the follower should have provided the correct last index in their
    // log. This helps
    // us converge on the matchIndex faster than by simply decrementing nextIndex one index at a
    // time.
    else {
      member.appendFailed();
      resetMatchIndex(member, response);
      resetNextIndex(member, response);
      resetSnapshotIndex(member, response);

      // If there are more entries to send then attempt to send another commit.
      if (hasMoreEntries(member)) {
        appendEntries(member);
      }
    }
  }

  @Override
  protected void appendEntries(final RaftMemberContext member) {
    // Prevent recursive, asynchronous appends from being executed if the appender has been closed.
    if (!open) {
      return;
    }

    // If prior requests to the member have failed, build an empty append request to send to the
    // member
    // to prevent having to read from disk to configure, install, or append to an unavailable
    // member.
    if (member.getFailureCount() >= MIN_BACKOFF_FAILURE_COUNT) {
      // To prevent the leader from unnecessarily attempting to connect to a down follower on every
      // heartbeat,
      // use exponential backoff to back off up to 60 second heartbeat intervals.
      if (System.currentTimeMillis() - member.getFailureTime()
          > Math.min(
              heartbeatInterval * Math.pow(2, member.getFailureCount()), MAX_HEARTBEAT_WAIT)) {
        sendAppendRequest(member, buildAppendEmptyRequest(member));
      }
    }
    // If the member term is less than the current term or the member's configuration index is less
    // than the local configuration index, send a configuration update to the member.
    // Ensure that only one configuration attempt per member is attempted at any given time by
    // storing the
    // member state in a set of configuring members.
    // Once the configuration is complete sendAppendRequest will be called recursively.
    else if (member.getConfigTerm() < raft.getTerm()
        || member.getConfigIndex() < raft.getCluster().getConfiguration().index()) {
      if (member.canConfigure()) {
        sendConfigureRequest(member, buildConfigureRequest(member));
      } else if (member.canHeartbeat()) {
        sendAppendRequest(member, buildAppendEmptyRequest(member));
      }
    }
    // If there's a snapshot at the member's nextIndex, replicate the snapshot.
    else if (member.getMember().getType() == RaftMember.Type.ACTIVE
        || member.getMember().getType() == RaftMember.Type.PROMOTABLE
        || member.getMember().getType() == RaftMember.Type.PASSIVE) {
      final Snapshot snapshot = raft.getSnapshotStore().getCurrentSnapshot();
      if (snapshot != null
          && member.getSnapshotIndex() < snapshot.index()
          && snapshot.index() >= member.getLogReader().getCurrentIndex()) {
        if (!member.canInstall()) {
          return;
        }

        log.debug("Replicating snapshot {} to {}", snapshot.index(), member.getMember().memberId());
        sendInstallRequest(member, buildInstallRequest(member, snapshot));
      } else if (member.canAppend()) {
        sendAppendRequest(member, buildAppendRequest(member, -1));
      }
    }
    // If no AppendRequest is already being sent, send an AppendRequest.
    else if (member.canAppend()) {
      sendAppendRequest(member, buildAppendRequest(member, -1));
    }
  }

  @Override
  protected boolean hasMoreEntries(final RaftMemberContext member) {
    // If the member's nextIndex is an entry in the local log then more entries can be sent.
    return member.getLogReader().hasNext();
  }

  /** Handles a {@link io.atomix.raft.protocol.RaftResponse.Status#ERROR} response. */
  @Override
  protected void handleAppendResponseError(
      final RaftMemberContext member, final AppendRequest request, final AppendResponse response) {
    // If we've received a greater term, update the term and transition back to follower.
    if (response.term() > raft.getTerm()) {
      log.info(
          "Received error append response with higher term ({} > {}) from {}, implying there is a new leader, transitioning to follower",
          response.term(),
          raft.getTerm(),
          member.getMember());
      raft.setTerm(response.term());
      raft.setLeader(null);
      raft.transition(RaftServer.Role.FOLLOWER);
    } else {
      super.handleAppendResponseError(member, request, response);
    }
  }

  @Override
  protected void handleConfigureResponse(
      final RaftMemberContext member,
      final ConfigureRequest request,
      final ConfigureResponse response,
      final long timestamp) {
    super.handleConfigureResponse(member, request, response, timestamp);
    recordHeartbeat(member, timestamp);
  }

  @Override
  protected void handleInstallResponse(
      final RaftMemberContext member,
      final InstallRequest request,
      final InstallResponse response,
      final long timestamp) {
    super.handleInstallResponse(member, request, response, timestamp);
    recordHeartbeat(member, timestamp);
  }

  @Override
  public void close() {
    super.close();
    appendFutures
        .values()
        .forEach(
            future -> future.completeExceptionally(new IllegalStateException("Inactive state")));
    heartbeatFutures.forEach(
        future ->
            future.completeExceptionally(
                new RaftException.ProtocolException("Failed to reach consensus")));
  }

  /** Records a failed heartbeat. */
  private void failHeartbeat() {
    raft.checkThread();

    // Iterate through pending timestamped heartbeat futures and fail futures that have been pending
    // longer
    // than an election timeout.
    final long currentTimestamp = System.currentTimeMillis();
    final Iterator<TimestampedFuture<Long>> iterator = heartbeatFutures.iterator();
    while (iterator.hasNext()) {
      final TimestampedFuture<Long> future = iterator.next();
      if (currentTimestamp - future.timestamp > electionTimeout) {
        future.completeExceptionally(
            new RaftException.ProtocolException("Failed to reach consensus"));
        iterator.remove();
      }
    }
  }

  /** Records a completed heartbeat to the given member. */
  private void recordHeartbeat(final RaftMemberContext member, final long timestamp) {
    raft.checkThread();

    // Update the member's heartbeat time. This will be used when calculating the quorum heartbeat
    // time.
    member.setHeartbeatTime(timestamp);
    member.setResponseTime(System.currentTimeMillis());

    // Compute the quorum heartbeat time.
    final long heartbeatTime = computeHeartbeatTime();
    final long currentTimestamp = System.currentTimeMillis();

    // Iterate through pending timestamped heartbeat futures and complete all futures where the
    // timestamp
    // is greater than the last timestamp a quorum of the cluster was contacted.
    final Iterator<TimestampedFuture<Long>> iterator = heartbeatFutures.iterator();
    while (iterator.hasNext()) {
      final TimestampedFuture<Long> future = iterator.next();

      // If the future is timestamped prior to the last heartbeat to a majority of the cluster,
      // complete the future.
      if (future.timestamp < heartbeatTime) {
        future.complete(null);
        iterator.remove();
      }
      // If the future is more than an election timeout old, fail it with a protocol exception.
      else if (currentTimestamp - future.timestamp > electionTimeout) {
        future.completeExceptionally(
            new RaftException.ProtocolException("Failed to reach consensus"));
        iterator.remove();
      }
      // Otherwise, we've reached recent heartbeat futures. Break out of the loop.
      else {
        break;
      }
    }

    // If heartbeat futures are still pending, attempt to send heartbeats.
    if (!heartbeatFutures.isEmpty()) {
      sendHeartbeats();
    }
  }

  /**
   * Returns the last time a majority of the cluster was contacted.
   *
   * <p>This is calculated by sorting the list of active members and getting the last time the
   * majority of the cluster was contacted based on the index of a majority of the members. So, in a
   * list of 3 ACTIVE members, index 1 (the second member) will be used to determine the commit time
   * in a sorted members list.
   */
  private long computeHeartbeatTime() {
    final int quorumIndex = getQuorumIndex();
    if (quorumIndex >= 0) {
      return raft.getCluster()
          .getActiveMemberStates(
              (m1, m2) -> Long.compare(m2.getHeartbeatTime(), m1.getHeartbeatTime()))
          .get(quorumIndex)
          .getHeartbeatTime();
    }
    return System.currentTimeMillis();
  }

  /** Attempts to send heartbeats to all followers. */
  private void sendHeartbeats() {
    for (final RaftMemberContext member : raft.getCluster().getRemoteMemberStates()) {
      appendEntries(member);
    }
  }

  /** Checks whether any futures can be completed. */
  private void commitEntries() {
    raft.checkThread();

    // Sort the list of replicas, order by the last index that was replicated
    // to the replica. This will allow us to determine the median index
    // for all known replicated entries across all cluster members.
    final List<RaftMemberContext> members =
        raft.getCluster()
            .getActiveMemberStates(
                (m1, m2) ->
                    Long.compare(
                        m2.getMatchIndex() != 0 ? m2.getMatchIndex() : 0L,
                        m1.getMatchIndex() != 0 ? m1.getMatchIndex() : 0L));

    // If the active members list is empty (a configuration change occurred between an append
    // request/response)
    // ensure all commit futures are completed and cleared.
    if (members.isEmpty()) {
      final long commitIndex = raft.getLogWriter().getLastIndex();
      final long previousCommitIndex = raft.setCommitIndex(commitIndex);
      if (commitIndex > previousCommitIndex) {
        log.trace("Committed entries up to {}", commitIndex);
        completeCommits(previousCommitIndex, commitIndex);
      }
      return;
    }

    // Calculate the current commit index as the median matchIndex.
    final long commitIndex = members.get(getQuorumIndex()).getMatchIndex();

    // If the commit index has increased then update the commit index. Note that in order to ensure
    // the leader completeness property holds, we verify that the commit index is greater than or
    // equal to
    // the index of the leader's no-op entry. Update the commit index and trigger commit futures.
    final long previousCommitIndex = raft.getCommitIndex();
    if (commitIndex > 0
        && commitIndex > previousCommitIndex
        && (leaderIndex > 0 && commitIndex >= leaderIndex)) {
      log.trace("Committed entries up to {}", commitIndex);
      raft.setCommitIndex(commitIndex);
      completeCommits(previousCommitIndex, commitIndex);
    }
  }

  private long computeResponseTime() {
    final int quorumIndex = getQuorumIndex();
    if (quorumIndex >= 0) {
      return raft.getCluster()
          .getActiveMemberStates(
              (m1, m2) -> Long.compare(m2.getResponseTime(), m1.getResponseTime()))
          .get(quorumIndex)
          .getResponseTime();
    }
    return System.currentTimeMillis();
  }

  /**
   * Returns the current quorum index.
   *
   * @return The current quorum index.
   */
  private int getQuorumIndex() {
    return raft.getCluster().getQuorum() - 2;
  }

  /**
   * Returns the leader index.
   *
   * @return The leader index.
   */
  public long getIndex() {
    return leaderIndex;
  }

  /**
   * Returns the current commit time.
   *
   * @return The current commit time.
   */
  public long getTime() {
    return heartbeatTime;
  }

  /** Timestamped completable future. */
  private static class TimestampedFuture<T> extends CompletableFuture<T> {

    private final long timestamp;

    TimestampedFuture() {
      this(System.currentTimeMillis());
    }

    TimestampedFuture(final long timestamp) {
      this.timestamp = timestamp;
    }
  }
}
