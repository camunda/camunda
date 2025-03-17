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

import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.raft.RaftException;
import io.atomix.raft.RaftException.CommitFailedException;
import io.atomix.raft.RaftException.NoLeader;
import io.atomix.raft.RaftServer;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.cluster.impl.RaftMemberContext;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.metrics.LeaderAppenderMetrics;
import io.atomix.raft.protocol.AppendRequest;
import io.atomix.raft.protocol.AppendResponse;
import io.atomix.raft.protocol.ConfigureRequest;
import io.atomix.raft.protocol.ConfigureResponse;
import io.atomix.raft.protocol.InstallRequest;
import io.atomix.raft.protocol.InstallResponse;
import io.atomix.raft.protocol.RaftRequest;
import io.atomix.raft.protocol.RaftResponse;
import io.atomix.raft.protocol.ReplicatableJournalRecord;
import io.atomix.raft.protocol.VersionedAppendRequest;
import io.atomix.raft.snapshot.impl.SnapshotChunkImpl;
import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.utils.logging.ContextualLoggerFactory;
import io.atomix.utils.logging.LoggerContext;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.SnapshotChunk;
import io.camunda.zeebe.snapshots.SnapshotChunkReader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;

/**
 * The leader appender is responsible for sending {@link AppendRequest}s on behalf of a leader to
 * followers. Append requests are sent by the leader only to other active members of the cluster.
 */
final class LeaderAppender {

  private static final int MIN_BACKOFF_FAILURE_COUNT = 5;

  private final int maxBatchSizePerAppend;
  private final Logger log;
  private final RaftContext raft;
  private boolean open = true;

  private final LeaderAppenderMetrics metrics;
  private final long leaderTime;
  private final long leaderIndex;
  private final long electionTimeout;
  private final NavigableMap<Long, CompletableFuture<Long>> appendFutures = new TreeMap<>();
  private final List<TimestampedFuture<Long>> heartbeatFutures = new ArrayList<>();
  private final long heartbeatTime;
  private final int minStepDownFailureCount;
  private final long maxQuorumResponseTimeout;

  LeaderAppender(final LeaderRole leader) {
    raft = checkNotNull(leader.raft, "context cannot be null");
    log =
        ContextualLoggerFactory.getLogger(
            getClass(), LoggerContext.builder(RaftServer.class).addValue(raft.getName()).build());
    metrics = new LeaderAppenderMetrics(raft.getName(), raft.getMeterRegistry());
    maxBatchSizePerAppend = raft.getMaxAppendBatchSize();
    leaderTime = System.currentTimeMillis();
    leaderIndex =
        raft.getLog().isEmpty() ? raft.getLog().getFirstIndex() : raft.getLog().getLastIndex() + 1;
    heartbeatTime = leaderTime;
    electionTimeout = raft.getElectionTimeout().toMillis();
    minStepDownFailureCount = raft.getMinStepDownFailureCount();
    maxQuorumResponseTimeout =
        raft.getMaxQuorumResponseTimeout().isZero()
            ? electionTimeout * 2
            : raft.getMaxQuorumResponseTimeout().toMillis();
  }

  /**
   * Builds an append request.
   *
   * @param member The member to which to send the request.
   * @return The append request.
   */
  private VersionedAppendRequest buildAppendRequest(
      final RaftMemberContext member, final long lastIndex) {
    // If the log is empty then send an empty commit.
    // If the next index hasn't yet been set then we send an empty commit first.
    // If the next index is greater than the last index then send an empty commit.
    // If the member failed to respond to recent communication send an empty commit. This
    // helps avoid doing expensive work until we can ascertain the member is back up.
    if (!hasMoreEntries(member)) {
      return buildAppendEmptyRequest(member);
    } else if (member.getFailureCount() > 0) {
      return buildAppendEmptyRequest(member);
    } else {
      return buildAppendEntriesRequest(member, lastIndex);
    }
  }

  /**
   * Builds an empty AppendEntries request.
   *
   * <p>Empty append requests are used as heartbeats to followers.
   */
  private VersionedAppendRequest buildAppendEmptyRequest(final RaftMemberContext member) {
    // Read the previous entry from the reader.
    // The reader can be null for RESERVE members.
    final IndexedRaftLogEntry prevEntry = member.getCurrentEntry();

    final DefaultRaftMember leader = raft.getLeader();
    return builderWithPreviousEntry(prevEntry)
        .withTerm(raft.getTerm())
        .withLeader(leader.memberId())
        .withEntries(Collections.emptyList())
        .withCommitIndex(raft.getCommitIndex())
        .build();
  }

  private VersionedAppendRequest.Builder builderWithPreviousEntry(
      final IndexedRaftLogEntry prevEntry) {
    long prevIndex = 0;
    long prevTerm = 0;

    if (prevEntry != null) {
      prevIndex = prevEntry.index();
      prevTerm = prevEntry.term();
    } else {
      final var currentSnapshot = raft.getCurrentSnapshot();
      if (currentSnapshot != null) {
        prevIndex = currentSnapshot.getIndex();
        prevTerm = currentSnapshot.getTerm();
      }
    }
    return VersionedAppendRequest.builder().withPrevLogTerm(prevTerm).withPrevLogIndex(prevIndex);
  }

  /** Builds a populated AppendEntries request. */
  private VersionedAppendRequest buildAppendEntriesRequest(
      final RaftMemberContext member, final long lastIndex) {
    final IndexedRaftLogEntry prevEntry = member.getCurrentEntry();

    final DefaultRaftMember leader = raft.getLeader();
    final VersionedAppendRequest.Builder builder =
        builderWithPreviousEntry(prevEntry)
            .withTerm(raft.getTerm())
            .withLeader(leader.memberId())
            .withCommitIndex(raft.getCommitIndex());

    // Build a list of entries to send to the member.
    final List<ReplicatableJournalRecord> entries = new ArrayList<>();

    // Build a list of entries up to the MAX_BATCH_SIZE. Note that entries in the log may
    // be null if they've been compacted and the member to which we're sending entries is just
    // joining the cluster or is otherwise far behind. Null entries are simply skipped and not
    // counted towards the size of the batch.
    // If there exists an entry in the log with size >= MAX_BATCH_SIZE the logic ensures that
    // entry will be sent in a batch of size one
    int size = 0;

    // Iterate through the log until the last index or the end of the log is reached.
    while (hasMoreEntries(member)) {
      // Otherwise, read the next entry and add it to the batch.
      final IndexedRaftLogEntry entry = member.nextEntry();
      final var replicatableRecord = entry.getReplicatableJournalRecord();
      entries.add(replicatableRecord);
      size += replicatableRecord.approximateSize();
      if (entry.index() == lastIndex || size >= maxBatchSizePerAppend) {
        break;
      }
    }

    // Add the entries to the request builder and build the request.
    return builder.withEntries(entries).build();
  }

  /** Connects to the member and sends a commit message. */
  private void sendAppendRequest(
      final RaftMemberContext member, final VersionedAppendRequest request) {
    // If this is a heartbeat message and a heartbeat is already in progress, skip the request.
    if (request.entries().isEmpty() && !member.canHeartbeat()) {
      return;
    }

    // Start the append to the member.
    member.startAppend();

    final long timestamp = System.currentTimeMillis();

    log.trace("Sending {} to {}", request, member.getMember().memberId());
    raft.getProtocol()
        .append(member.getMember().memberId(), request)
        .whenCompleteAsync(
            (response, error) -> {
              if (open) {
                // Complete the append to the member.
                final long appendLatency = System.currentTimeMillis() - timestamp;
                metrics.appendComplete(appendLatency, member.getMember().memberId().id());
                if (!request.entries().isEmpty()) {
                  member.completeAppend(appendLatency);
                } else {
                  member.completeAppend();
                }

                if (error == null) {
                  log.trace("Received {} from {}", response, member.getMember().memberId());
                  handleAppendResponse(member, request, response, timestamp);
                } else {
                  handleAppendResponseFailure(member, request, error);
                }
              }
            },
            raft.getThreadContext());

    if (!request.entries().isEmpty() && hasMoreEntries(member)) {
      appendEntries(member);
    }
  }

  /** Succeeds an attempt to contact a member. */
  private void succeedAttempt(final RaftMemberContext member) {
    // Reset the member failure count and time.
    member.resetFailureCount();
  }

  /**
   * Updates the configuration index of the member based on the append response. This allows members
   * which lost a configuration to update the leader and effectively request a configuration update.
   */
  private void updateConfigurationIndex(
      final RaftMemberContext member, final AppendResponse response) {
    final var configIndex = response.configurationIndex();
    if (configIndex == 0) {
      // Backwards compatibility: old members don't send a configuration index in the response.
      // When we find the default value of 0, don't update the index and rely on the old behaviour
      // where the configuration index is only updated in handleConfigureResponse.
      // Otherwise, we'd constantly update the configuration index of old members to 0 and keep
      // re-sending the configuration instead of appending.
      return;
    }
    member.setConfigIndex(configIndex);
  }

  /** Updates the match index when a response is received. */
  private void updateMatchIndex(final RaftMemberContext member, final AppendResponse response) {
    // If the replica returned a valid match index then update the existing match index.
    member.setMatchIndex(response.lastLogIndex());
    observeRemainingMemberEntries(member);
  }

  /** Resets the match index when a response fails. */
  private void resetMatchIndex(final RaftMemberContext member, final AppendResponse response) {
    if (response.lastLogIndex() < member.getMatchIndex()) {
      log.trace("Reset match index for {} to {}", member, member.getMatchIndex());
      member.setMatchIndex(response.lastLogIndex());
      observeRemainingMemberEntries(member);
    }
  }

  private void observeRemainingMemberEntries(final RaftMemberContext member) {
    metrics.observeRemainingEntries(
        member.getMember().memberId().id(), raft.getLog().getLastIndex() - member.getMatchIndex());
  }

  /** Resets the next index when a response fails. */
  private void resetNextIndex(final RaftMemberContext member, final AppendResponse response) {
    final long nextIndex = response.lastLogIndex() + 1;
    resetNextIndex(member, nextIndex);
  }

  private void resetNextIndex(final RaftMemberContext member, final long nextIndex) {
    member.reset(nextIndex);
    log.trace("Reset next index for {} to {}", member, nextIndex);
  }

  /** Resets the snapshot index of the member when a response fails. */
  private void resetSnapshotIndex(final RaftMemberContext member, final AppendResponse response) {
    final long snapshotIndex = response.lastSnapshotIndex();
    if (member.getSnapshotIndex() != snapshotIndex) {
      member.setSnapshotIndex(snapshotIndex);
      log.trace("Reset snapshot index for {} to {}", member, snapshotIndex);
    }
  }

  /** Builds a configure request for the given member. */
  private ConfigureRequest buildConfigureRequest() {
    final var leader = raft.getLeader();
    final var configuration = raft.getCluster().getConfiguration();
    return ConfigureRequest.builder()
        .withTerm(raft.getTerm())
        .withLeader(leader.memberId())
        .withIndex(configuration.index())
        .withTime(configuration.time())
        .withNewMembers(configuration.newMembers())
        .withOldMembers(configuration.oldMembers())
        .build();
  }

  /** Connects to the member and sends a configure request. */
  private void sendConfigureRequest(
      final RaftMemberContext member, final ConfigureRequest request) {
    log.debug("Configuring {} : {}", member.getMember().memberId(), request);

    // Start the configure to the member.
    member.startConfigure();

    final long timestamp = System.currentTimeMillis();

    log.trace("Sending {} to {}", request, member.getMember().memberId());
    raft.getProtocol()
        .configure(member.getMember().memberId(), request)
        .whenCompleteAsync(
            (response, error) -> {
              if (open) {
                // Complete the configure to the member.
                member.completeConfigure();

                if (error == null) {
                  log.trace("Received {} from {}", response, member.getMember().memberId());
                  handleConfigureResponse(member, request, response, timestamp);
                } else {
                  if (log.isTraceEnabled()) {
                    log.debug("Failed to configure {}", member.getMember().memberId(), error);
                  } else {
                    log.debug("Failed to configure {}", member.getMember().memberId());
                  }
                  handleConfigureResponseFailure(member, request, error);
                }
              }
            },
            raft.getThreadContext());
  }

  /** Handles a configure failure. */
  protected void handleConfigureResponseFailure(
      final RaftMemberContext member, final ConfigureRequest request, final Throwable error) {
    // Log the failed attempt to contact the member.
    failAttempt(member, request, error);
  }

  /** Handles an OK configuration response. */
  @SuppressWarnings("unused")
  protected void handleConfigureResponseOk(
      final RaftMemberContext member,
      final ConfigureRequest request,
      final ConfigureResponse response) {
    // Reset the member failure count and update the member's status if necessary.
    succeedAttempt(member);

    // Update the member's current configuration term and index according to the installed
    // configuration.
    member.setConfigTerm(request.term());
    member.setConfigIndex(request.index());

    // Recursively append entries to the member.
    appendEntries(member);
  }

  /** Builds an install request for the given member. */
  private Optional<InstallRequest> buildInstallRequest(
      final RaftMemberContext member, final PersistedSnapshot persistedSnapshot) {
    if (member.getNextSnapshotIndex() != persistedSnapshot.getIndex()) {
      try {
        final SnapshotChunkReader snapshotChunkReader = persistedSnapshot.newChunkReader();
        member.setSnapshotChunkReader(snapshotChunkReader);
      } catch (final UncheckedIOException e) {
        log.warn(
            "Expected to send Snapshot {} to {}. But could not open SnapshotChunkReader. Will retry.",
            persistedSnapshot.getId(),
            member,
            e);
        return Optional.empty();
      }
      member.setNextSnapshotIndex(persistedSnapshot.getIndex());
      member.setNextSnapshotChunkId(null);
    }

    final SnapshotChunkReader reader = member.getSnapshotChunkReader();

    try {
      // Reader might have advanced to the next chunk already. But if we want to retry a chunk the
      // reader should seek to the chunk. To handle retries and not-retries the same, we seek
      // always.
      if (member.getNextSnapshotChunk() != null) {
        reader.seek(member.getNextSnapshotChunk());
      } else {
        // member.getNextSnapshotChunk is null when it is the first chunk.
        reader.reset();
      }

      if (!reader.hasNext()) {
        return Optional.empty();
      }
      final ByteBuffer currentChunkId = reader.nextId();
      final SnapshotChunk chunk = reader.next();

      // Create the install request, indicating whether this is the last chunk of data based on
      // the number of bytes remaining in the buffer.
      final DefaultRaftMember leader = raft.getLeader();

      final InstallRequest request =
          InstallRequest.builder()
              .withCurrentTerm(raft.getTerm())
              .withLeader(leader.memberId())
              .withIndex(persistedSnapshot.getIndex())
              .withTerm(persistedSnapshot.getTerm())
              .withVersion(persistedSnapshot.version())
              .withData(new SnapshotChunkImpl(chunk).toByteBuffer())
              .withChunkId(currentChunkId)
              .withInitial(member.getNextSnapshotChunk() == null)
              .withComplete(!reader.hasNext())
              .withNextChunkId(reader.nextId())
              .build();
      return Optional.of(request);
    } catch (final UncheckedIOException e) {
      log.warn(
          "Expected to send next chunk of Snapshot {} to {}. But could not read SnapshotChunk. Snapshot may have been deleted. Will retry.",
          persistedSnapshot.getId(),
          member.getMember().memberId(),
          e);
      // If snapshot was deleted, a new reader should be created with the new snapshot
      member.setNextSnapshotIndex(0);
      member.setNextSnapshotChunkId(null);
      return Optional.empty();
    }
  }

  /** Connects to the member and sends a snapshot request. */
  private void sendInstallRequest(final RaftMemberContext member, final InstallRequest request) {
    // Start the install to the member.
    member.startInstall();

    final long timestamp = System.currentTimeMillis();

    log.trace("Sending {} to {}", request, member.getMember().memberId());
    raft.getProtocol()
        .install(member.getMember().memberId(), request)
        .whenCompleteAsync(
            (response, error) -> {
              if (open) {
                // Complete the install to the member.
                member.completeInstall();

                if (error == null) {
                  log.trace("Received {} from {}", response, member.getMember().memberId());
                  handleInstallResponse(member, request, response, timestamp);
                } else {
                  // Trigger reactions to the install response failure.
                  handleInstallResponseFailure(member, request, error);
                }
              }
            },
            raft.getThreadContext());
  }

  /** Handles an install response failure. */
  private void handleInstallResponseFailure(
      final RaftMemberContext member, final InstallRequest request, final Throwable error) {
    // Reset the member's snapshot index and offset to resend the snapshot from the start
    // once a connection to the member is re-established.
    final boolean isTimeout =
        error instanceof TimeoutException
            || (error != null && error.getCause() instanceof TimeoutException);

    if (!isTimeout) {
      member.setNextSnapshotIndex(0);
      member.setNextSnapshotChunkId(null);
    }

    // Log the failed attempt to contact the member.
    failAttempt(member, request, error);
  }

  /** Handles an OK install response. */
  private void handleInstallResponseOk(
      final RaftMemberContext member,
      final InstallRequest request,
      final InstallResponse response) {
    // Reset the member failure count and update the member's status if necessary.
    succeedAttempt(member);

    //    if not given in response defaults to 0
    if (response.preferredChunkSize() > 0) {
      member.getSnapshotChunkReader().setMaximumChunkSize(response.preferredChunkSize());
    }
    // If the install request was completed successfully, set the member's snapshotIndex and reset
    // the next snapshot index/offset.
    if (request.complete()) {
      member.setNextSnapshotIndex(0);
      member.setNextSnapshotChunkId(null);
      member.setSnapshotIndex(request.index());
      resetNextIndex(member, request.index() + 1);
    }
    // If more install requests remain, increment the member's snapshot offset.
    else {
      member.setNextSnapshotChunkId(request.nextChunkId());
    }

    // Recursively append entries to the member.
    appendEntries(member);
  }

  /** Handles an ERROR install response. */
  @SuppressWarnings("unused")
  private void handleInstallResponseError(
      final RaftMemberContext member,
      final InstallRequest request,
      final InstallResponse response) {
    log.warn(
        "Failed to send {} to member {}, with {}. Restart sending snapshot.",
        request,
        member.getMember().memberId(),
        response.error().toString());

    member.setNextSnapshotIndex(0);
    member.setNextSnapshotChunkId(null);
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
    if (raft.getCluster().isSingleMemberCluster()) {
      try {
        raft.setCommitIndex(index);
        completeCommits(index);
        return CompletableFuture.completedFuture(index);
      } catch (final CommitFailedException e) {
        return CompletableFuture.failedFuture(e);
      }
    }

    if (!open) {
      return CompletableFuture.failedFuture(
          new NoLeader("Cannot replicate entries on closed leader"));
    }

    // Only send entry-specific AppendRequests to active members of the cluster.
    return appendFutures.computeIfAbsent(
        index,
        i -> {
          for (final RaftMemberContext member : raft.getCluster().getReplicationTargets()) {
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
    if (raft.getCluster().getReplicationTargets().isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }

    // Create a heartbeat future and add it to the heartbeat futures list.
    final TimestampedFuture<Long> future = new TimestampedFuture<>();
    heartbeatFutures.add(future);

    // Iterate through members and append entries. Futures will be completed on responses from
    // followers.
    for (final RaftMemberContext member : raft.getCluster().getReplicationTargets()) {
      appendEntries(member);
    }
    return future;
  }

  /** Completes append entries attempts up to the given index. */
  private void completeCommits(final long commitIndex) {
    final var completable = appendFutures.headMap(commitIndex, true);
    completable.forEach(
        (index, future) -> {
          metrics.observeCommit();
          future.complete(index);
        });
    completable.clear();

    observeNonCommittedEntries(commitIndex);
  }

  private void handleAppendResponseFailure(
      final RaftMemberContext member, final VersionedAppendRequest request, final Throwable error) {
    failHeartbeat();

    // Log the failed attempt to contact the member.
    failAttempt(member, request, error);
  }

  private void failAttempt(
      final RaftMemberContext member, final RaftRequest request, final Throwable error) {
    // If any append error occurred, increment the failure count for the member. Log the first three
    // failures,
    // and thereafter log 1% of the failures. This keeps the log from filling up with annoying error
    // messages
    // when attempting to send entries to down followers.
    final int failures = member.incrementFailureCount();
    if (failures <= 3 || failures % 100 == 0) {
      log.warn("{} to {} failed", request, member.getMember().memberId(), error);
    }

    // Fail heartbeat futures.
    failHeartbeat();

    // Verify that the leader has contacted a majority of the cluster within the last two election
    // timeouts.
    // If the leader is not able to contact a majority of the cluster within two election timeouts,
    // assume
    // that a partition occurred and transition back to the FOLLOWER state.
    final long quorumResponseTime =
        System.currentTimeMillis() - Math.max(computeResponseTime(), leaderTime);
    if (member.getFailureCount() >= minStepDownFailureCount
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

  private void handleAppendResponse(
      final RaftMemberContext member,
      final VersionedAppendRequest request,
      final AppendResponse response,
      final long timestamp) {
    if (response.status() == RaftResponse.Status.OK) {
      handleAppendResponseOk(member, request, response);
    } else {
      handleAppendResponseError(member, request, response);
    }
    recordHeartbeat(member, timestamp);
  }

  private void handleAppendResponseOk(
      final RaftMemberContext member,
      final VersionedAppendRequest request,
      final AppendResponse response) {
    // Reset the member failure count and update the member's availability status if necessary.
    succeedAttempt(member);

    updateConfigurationIndex(member, response);

    // If replication succeeded then trigger commit futures.
    if (response.succeeded()) {
      member.appendSucceeded();
      updateMatchIndex(member, response);
      metrics.observeAppend(
          member.getMember().memberId().id(),
          request.entries().size(),
          request.entries().stream().mapToInt(ReplicatableJournalRecord::approximateSize).sum());

      commitEntries();

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

  /**
   * Sends an AppendRequest to the given member.
   *
   * @param member The member to which to send the append request.
   */
  private void appendEntries(final RaftMemberContext member) {
    if (!open) {
      // Prevent recursive, asynchronous appends from being executed if the appender has been
      // closed.
      return;
    }

    if (!member.isOpen()) {
      // Prevent recursive, asynchronous appends from being executed if the member is no longer
      // open.
      return;
    }

    if (!member.hasReplicationContext()) {
      member.openReplicationContext(raft.getLog());
    }

    // If prior requests to the member have failed, build an empty append request to send to the
    // member
    // to prevent having to read from disk to configure, install, or append to an unavailable
    // member.
    if (member.getFailureCount() >= MIN_BACKOFF_FAILURE_COUNT) {
      // If the member is not reachable for a long time, only send heartbeats to ping the follower.
      // When the follower starts acknowledging, leader will start sending the actual events.
      sendAppendRequest(member, buildAppendEmptyRequest(member));
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
        sendConfigureRequest(member, buildConfigureRequest());
      } else if (member.canHeartbeat()) {
        sendAppendRequest(member, buildAppendEmptyRequest(member));
      }
    }
    // If there's a snapshot at the member's nextIndex, replicate the snapshot.
    else if (member.getMember().getType() == RaftMember.Type.ACTIVE
        || member.getMember().getType() == RaftMember.Type.PROMOTABLE
        || member.getMember().getType() == RaftMember.Type.PASSIVE) {
      tryToReplicate(member);
    }
    // If no AppendRequest is already being sent, send an AppendRequest.
    else if (member.canAppend()) {
      sendAppendRequest(member, buildAppendRequest(member, -1));
    }
  }

  private boolean hasMoreEntries(final RaftMemberContext member) {
    // If the member's nextIndex is an entry in the local log then more entries can be sent.
    return !member.hasReplicationContext() || member.hasNextEntry();
  }

  private void handleAppendResponseError(
      final RaftMemberContext member,
      final VersionedAppendRequest request,
      final AppendResponse response) {
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
      // If any other error occurred, increment the failure count for the member. Log the first
      // three
      // failures,
      // and thereafter log 1% of the failures. This keeps the log from filling up with annoying
      // error
      // messages
      // when attempting to send entries to down followers.
      final int failures = member.incrementFailureCount();
      if (failures <= 3 || failures % 100 == 0) {
        log.warn(
            "{} to {} failed: {}",
            request,
            member.getMember().memberId(),
            response.error() != null ? response.error() : "");
      }
    }
  }

  private void handleConfigureResponse(
      final RaftMemberContext member,
      final ConfigureRequest request,
      final ConfigureResponse response,
      final long timestamp) {
    if (response.status() == RaftResponse.Status.OK) {
      handleConfigureResponseOk(member, request, response);
    }

    // In the event of a configure response error, simply do nothing and await the next heartbeat.
    // This prevents infinite loops when cluster configurations fail.
    recordHeartbeat(member, timestamp);
  }

  private void handleInstallResponse(
      final RaftMemberContext member,
      final InstallRequest request,
      final InstallResponse response,
      final long timestamp) {
    if (response.status() == RaftResponse.Status.OK) {
      handleInstallResponseOk(member, request, response);
    } else {
      handleInstallResponseError(member, request, response);
    }
    recordHeartbeat(member, timestamp);
  }

  public void close() {
    open = false;
    metrics.close();
    completeCommits(raft.getCommitIndex());
    appendFutures
        .values()
        .forEach(
            future -> future.completeExceptionally(new IllegalStateException("Inactive state")));
    heartbeatFutures.forEach(
        future ->
            future.completeExceptionally(
                new RaftException.ProtocolException("Failed to reach consensus")));
  }

  private void tryToReplicate(final RaftMemberContext member) {
    if (shouldReplicateSnapshot(member)) {
      if (!member.canInstall()) {
        return;
      }
      replicateSnapshot(member);
    } else if (member.canAppend()) {
      replicateEvents(member);
    }
  }

  private boolean shouldReplicateSnapshot(final RaftMemberContext member) {
    final var persistedSnapshot = raft.getCurrentSnapshot();
    if (persistedSnapshot == null) {
      return false;
    }
    if (member.getSnapshotIndex() >= persistedSnapshot.getIndex()) {
      // Member has the latest snapshot, replicating the snapshot again wouldn't help.
      // WARNING! This is load-bearing and not just an optimization. See
      // https://github.com/camunda/camunda/issues/9820 for context.
      return false;
    }
    if (raft.getLog().getFirstIndex() > member.getCurrentIndex()) {
      // Necessary events are not available anymore, we have to use the snapshot
      return true;
    }
    // Only use the snapshot if the number of events that would have to be replicated
    // is above the threshold
    final var memberLag = persistedSnapshot.getIndex() - member.getCurrentIndex();
    return memberLag > raft.getPreferSnapshotReplicationThreshold();
  }

  private void replicateSnapshot(final RaftMemberContext member) {
    final var persistedSnapshot = raft.getCurrentSnapshot();
    log.debug(
        "Replicating snapshot {} to {}",
        persistedSnapshot.getIndex(),
        member.getMember().memberId());
    buildInstallRequest(member, persistedSnapshot)
        .ifPresent(installRequest -> sendInstallRequest(member, installRequest));
  }

  private void replicateEvents(final RaftMemberContext member) {
    sendAppendRequest(member, buildAppendRequest(member, -1));
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
    final long quorumHeartbeatTime = computeHeartbeatTime();
    final long currentTimestamp = System.currentTimeMillis();

    // Iterate through pending timestamped heartbeat futures and complete all futures where the
    // timestamp
    // is greater than the last timestamp a quorum of the cluster was contacted.
    final Iterator<TimestampedFuture<Long>> iterator = heartbeatFutures.iterator();
    while (iterator.hasNext()) {
      final TimestampedFuture<Long> future = iterator.next();

      // If the future is timestamped prior to the last heartbeat to a majority of the cluster,
      // complete the future.
      if (future.timestamp < quorumHeartbeatTime) {
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
    return raft.getCluster()
        .getQuorumFor(RaftMemberContext::getHeartbeatTime)
        // No remote members, use current time because the local member is always reachable.
        .orElseGet(System::currentTimeMillis);
  }

  /** Attempts to send heartbeats to all followers. */
  private void sendHeartbeats() {
    for (final RaftMemberContext member : raft.getCluster().getReplicationTargets()) {
      appendEntries(member);
    }
  }

  /** Checks whether any futures can be completed. */
  private void commitEntries() {
    raft.checkThread();

    final long commitIndex =
        raft.getCluster()
            .getQuorumFor(RaftMemberContext::getMatchIndex)
            // If there are no remote members, commit up to the last log index.
            .orElseGet(() -> raft.getLog().getLastIndex());

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
      completeCommits(commitIndex);
    }
  }

  private long computeResponseTime() {
    return raft.getCluster()
        .getQuorumFor(RaftMemberContext::getResponseTime)
        // No remote members, use current time because the local member is always reachable.
        .orElseGet(System::currentTimeMillis);
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

  void observeNonCommittedEntries(final long commitIndex) {
    metrics.observeNonCommittedEntries(raft.getLog().getLastIndex() - commitIndex);
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
