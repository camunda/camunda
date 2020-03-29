/*
 * Copyright 2016-present Open Networking Foundation
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

import io.atomix.raft.RaftServer;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.cluster.impl.RaftMemberContext;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.metrics.LeaderMetrics;
import io.atomix.raft.protocol.AppendRequest;
import io.atomix.raft.protocol.AppendResponse;
import io.atomix.raft.protocol.ConfigureRequest;
import io.atomix.raft.protocol.ConfigureResponse;
import io.atomix.raft.protocol.InstallRequest;
import io.atomix.raft.protocol.InstallResponse;
import io.atomix.raft.protocol.RaftRequest;
import io.atomix.raft.protocol.RaftResponse;
import io.atomix.raft.storage.log.RaftLogReader;
import io.atomix.raft.storage.log.entry.RaftLogEntry;
import io.atomix.raft.storage.snapshot.Snapshot;
import io.atomix.raft.storage.snapshot.SnapshotChunk;
import io.atomix.raft.storage.snapshot.SnapshotChunkReader;
import io.atomix.storage.journal.Indexed;
import io.atomix.utils.logging.ContextualLoggerFactory;
import io.atomix.utils.logging.LoggerContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;

/** Abstract appender. */
abstract class AbstractAppender implements AutoCloseable {

  private static final int MAX_BATCH_SIZE = 1024 * 32;
  protected final Logger log;
  protected final RaftContext raft;
  protected boolean open = true;

  private final LeaderMetrics metrics;

  AbstractAppender(final RaftContext raft) {
    this.raft = checkNotNull(raft, "context cannot be null");
    this.log =
        ContextualLoggerFactory.getLogger(
            getClass(), LoggerContext.builder(RaftServer.class).addValue(raft.getName()).build());
    this.metrics = new LeaderMetrics(raft.getName());
  }

  /**
   * Builds an append request.
   *
   * @param member The member to which to send the request.
   * @return The append request.
   */
  protected AppendRequest buildAppendRequest(final RaftMemberContext member, final long lastIndex) {
    final RaftLogReader reader = member.getLogReader();

    // If the log is empty then send an empty commit.
    // If the next index hasn't yet been set then we send an empty commit first.
    // If the next index is greater than the last index then send an empty commit.
    // If the member failed to respond to recent communication send an empty commit. This
    // helps avoid doing expensive work until we can ascertain the member is back up.
    if (!reader.hasNext()) {
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
  protected AppendRequest buildAppendEmptyRequest(final RaftMemberContext member) {
    final RaftLogReader reader = member.getLogReader();

    // Read the previous entry from the reader.
    // The reader can be null for RESERVE members.
    final Indexed<RaftLogEntry> prevEntry = reader != null ? reader.getCurrentEntry() : null;

    final DefaultRaftMember leader = raft.getLeader();
    return builderWithPreviousEntry(prevEntry)
        .withTerm(raft.getTerm())
        .withLeader(leader.memberId())
        .withEntries(Collections.emptyList())
        .withCommitIndex(raft.getCommitIndex())
        .build();
  }

  private AppendRequest.Builder builderWithPreviousEntry(final Indexed<RaftLogEntry> prevEntry) {
    long prevIndex = 0;
    long prevTerm = 0;

    if (prevEntry != null) {
      prevIndex = prevEntry.index();
      prevTerm = prevEntry.entry().term();
    } else {
      final Snapshot currentSnapshot = raft.getSnapshotStore().getCurrentSnapshot();
      if (currentSnapshot != null) {
        prevIndex = currentSnapshot.index();
        prevTerm = currentSnapshot.term();
      }
    }
    return AppendRequest.builder().withPrevLogTerm(prevTerm).withPrevLogIndex(prevIndex);
  }

  /** Builds a populated AppendEntries request. */
  @SuppressWarnings("unchecked")
  protected AppendRequest buildAppendEntriesRequest(
      final RaftMemberContext member, final long lastIndex) {
    final RaftLogReader reader = member.getLogReader();

    final Indexed<RaftLogEntry> prevEntry = reader.getCurrentEntry();

    final DefaultRaftMember leader = raft.getLeader();
    final AppendRequest.Builder builder =
        builderWithPreviousEntry(prevEntry)
            .withTerm(raft.getTerm())
            .withLeader(leader.memberId())
            .withCommitIndex(raft.getCommitIndex());

    // Build a list of entries to send to the member.
    final List<RaftLogEntry> entries = new ArrayList<>();

    // Build a list of entries up to the MAX_BATCH_SIZE. Note that entries in the log may
    // be null if they've been compacted and the member to which we're sending entries is just
    // joining the cluster or is otherwise far behind. Null entries are simply skipped and not
    // counted towards the size of the batch.
    // If there exists an entry in the log with size >= MAX_BATCH_SIZE the logic ensures that
    // entry will be sent in a batch of size one
    int size = 0;

    // Iterate through the log until the last index or the end of the log is reached.
    while (reader.hasNext()) {
      // Otherwise, read the next entry and add it to the batch.
      final Indexed<RaftLogEntry> entry = reader.next();
      entries.add(entry.entry());
      size += entry.size();
      if (entry.index() == lastIndex || size >= MAX_BATCH_SIZE) {
        break;
      }
    }

    // Add the entries to the request builder and build the request.
    return builder.withEntries(entries).build();
  }

  /** Connects to the member and sends a commit message. */
  protected void sendAppendRequest(final RaftMemberContext member, final AppendRequest request) {
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
              // Complete the append to the member.
              final long appendLatency = System.currentTimeMillis() - timestamp;
              metrics.appendComplete(appendLatency, member.getMember().memberId().id());
              if (!request.entries().isEmpty()) {
                member.completeAppend(appendLatency);
              } else {
                member.completeAppend();
              }

              if (open) {
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

  /** Handles an append failure. */
  protected void handleAppendResponseFailure(
      final RaftMemberContext member, final AppendRequest request, final Throwable error) {
    // Log the failed attempt to contact the member.
    failAttempt(member, request, error);
  }

  /** Fails an attempt to contact a member. */
  protected void failAttempt(
      final RaftMemberContext member, final RaftRequest request, final Throwable error) {
    // If any append error occurred, increment the failure count for the member. Log the first three
    // failures,
    // and thereafter log 1% of the failures. This keeps the log from filling up with annoying error
    // messages
    // when attempting to send entries to down followers.
    final int failures = member.incrementFailureCount();
    if (failures <= 3 || failures % 100 == 0) {
      log.warn("{} to {} failed: {}", request, member.getMember().memberId(), error);
    }
  }

  /** Handles an append response. */
  protected void handleAppendResponse(
      final RaftMemberContext member,
      final AppendRequest request,
      final AppendResponse response,
      final long timestamp) {
    if (response.status() == RaftResponse.Status.OK) {
      handleAppendResponseOk(member, request, response);
    } else {
      handleAppendResponseError(member, request, response);
    }
  }

  /** Handles a {@link RaftResponse.Status#OK} response. */
  protected void handleAppendResponseOk(
      final RaftMemberContext member, final AppendRequest request, final AppendResponse response) {
    // Reset the member failure count and update the member's availability status if necessary.
    succeedAttempt(member);

    // If replication succeeded then trigger commit futures.
    if (response.succeeded()) {
      updateMatchIndex(member, response);

      // If there are more entries to send then attempt to send another commit.
      if (request.prevLogIndex() != response.lastLogIndex() && hasMoreEntries(member)) {
        appendEntries(member);
      }
    } else if (response.term()
        > raft.getTerm()) { // If we've received a greater term, update the term and transition back
      // to follower.
      log.info(
          "Received higher term ({} > {}) from {}, stepping down",
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
      resetMatchIndex(member, response);
      resetNextIndex(member, response);
      resetSnapshotIndex(member, response);

      // If there are more entries to send then attempt to send another commit.
      if (response.lastLogIndex() != request.prevLogIndex() && hasMoreEntries(member)) {
        appendEntries(member);
      }
    }
  }

  /**
   * Sends an AppendRequest to the given member.
   *
   * @param member The member to which to send the append request.
   */
  protected abstract void appendEntries(RaftMemberContext member);

  /** Succeeds an attempt to contact a member. */
  protected void succeedAttempt(final RaftMemberContext member) {
    // Reset the member failure count and time.
    member.resetFailureCount();
  }

  /** Returns a boolean value indicating whether there are more entries to send. */
  protected abstract boolean hasMoreEntries(RaftMemberContext member);

  /** Updates the match index when a response is received. */
  protected void updateMatchIndex(final RaftMemberContext member, final AppendResponse response) {
    // If the replica returned a valid match index then update the existing match index.
    member.setMatchIndex(response.lastLogIndex());
  }

  /** Resets the match index when a response fails. */
  protected void resetMatchIndex(final RaftMemberContext member, final AppendResponse response) {
    if (response.lastLogIndex() < member.getMatchIndex()) {
      member.setMatchIndex(response.lastLogIndex());
      log.trace("Reset match index for {} to {}", member, member.getMatchIndex());
    }
  }

  /** Resets the next index when a response fails. */
  protected void resetNextIndex(final RaftMemberContext member, final AppendResponse response) {
    final long nextIndex = response.lastLogIndex() + 1;
    resetNextIndex(member, nextIndex);
  }

  private void resetNextIndex(final RaftMemberContext member, final long nextIndex) {
    if (member.getLogReader().getNextIndex() != nextIndex) {
      member.getLogReader().reset(nextIndex);
      log.trace("Reset next index for {} to {}", member, nextIndex);
    }
  }

  /** Resets the snapshot index of the member when a response fails. */
  protected void resetSnapshotIndex(final RaftMemberContext member, final AppendResponse response) {
    final long snapshotIndex = response.lastSnapshotIndex();
    if (member.getSnapshotIndex() != snapshotIndex) {
      member.setSnapshotIndex(snapshotIndex);
      log.trace("Reset snapshot index for {} to {}", member, snapshotIndex);
    }
  }

  /** Handles a {@link RaftResponse.Status#ERROR} response. */
  protected void handleAppendResponseError(
      final RaftMemberContext member, final AppendRequest request, final AppendResponse response) {
    // If any other error occurred, increment the failure count for the member. Log the first three
    // failures,
    // and thereafter log 1% of the failures. This keeps the log from filling up with annoying error
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

  /** Builds a configure request for the given member. */
  protected ConfigureRequest buildConfigureRequest(final RaftMemberContext member) {
    final DefaultRaftMember leader = raft.getLeader();
    return ConfigureRequest.builder()
        .withTerm(raft.getTerm())
        .withLeader(leader.memberId())
        .withIndex(raft.getCluster().getConfiguration().index())
        .withTime(raft.getCluster().getConfiguration().time())
        .withMembers(raft.getCluster().getConfiguration().members())
        .build();
  }

  /** Connects to the member and sends a configure request. */
  protected void sendConfigureRequest(
      final RaftMemberContext member, final ConfigureRequest request) {
    log.debug("Configuring {}", member.getMember().memberId());

    // Start the configure to the member.
    member.startConfigure();

    final long timestamp = System.currentTimeMillis();

    log.trace("Sending {} to {}", request, member.getMember().memberId());
    raft.getProtocol()
        .configure(member.getMember().memberId(), request)
        .whenCompleteAsync(
            (response, error) -> {
              // Complete the configure to the member.
              member.completeConfigure();

              if (open) {
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

  /** Handles a configuration response. */
  protected void handleConfigureResponse(
      final RaftMemberContext member,
      final ConfigureRequest request,
      final ConfigureResponse response,
      final long timestamp) {
    if (response.status() == RaftResponse.Status.OK) {
      handleConfigureResponseOk(member, request, response);
    } else {
      handleConfigureResponseError(member, request, response);
    }
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

  /** Handles an ERROR configuration response. */
  @SuppressWarnings("unused")
  protected void handleConfigureResponseError(
      final RaftMemberContext member,
      final ConfigureRequest request,
      final ConfigureResponse response) {
    // In the event of a configure response error, simply do nothing and await the next heartbeat.
    // This prevents infinite loops when cluster configurations fail.
  }

  /** Builds an install request for the given member. */
  protected InstallRequest buildInstallRequest(
      final RaftMemberContext member, final Snapshot snapshot) {
    if (member.getNextSnapshotIndex() != snapshot.index()) {
      member.setNextSnapshotIndex(snapshot.index());
      member.setNextSnapshotChunk(null);
    }

    final InstallRequest request;
    synchronized (snapshot) {
      // Open a new snapshot reader.
      try (final SnapshotChunkReader reader = snapshot.newChunkReader()) {
        reader.seek(member.getNextSnapshotChunk());
        final SnapshotChunk chunk = reader.next();

        // Create the install request, indicating whether this is the last chunk of data based on
        // the number
        // of bytes remaining in the buffer.
        final DefaultRaftMember leader = raft.getLeader();
        request =
            InstallRequest.builder()
                .withCurrentTerm(raft.getTerm())
                .withLeader(leader.memberId())
                .withIndex(snapshot.index())
                .withTerm(snapshot.term())
                .withTimestamp(snapshot.timestamp().unixTimestamp())
                .withVersion(snapshot.version())
                .withData(chunk.data())
                .withChunkId(chunk.id())
                .withInitial(member.getNextSnapshotChunk() == null)
                .withComplete(!reader.hasNext())
                .withNextChunkId(reader.nextId())
                .build();
      } catch (final Exception e) {
        throw new RuntimeException(e);
      }
    }

    return request;
  }

  /** Connects to the member and sends a snapshot request. */
  protected void sendInstallRequest(final RaftMemberContext member, final InstallRequest request) {
    // Start the install to the member.
    member.startInstall();

    final long timestamp = System.currentTimeMillis();

    log.trace("Sending {} to {}", request, member.getMember().memberId());
    raft.getProtocol()
        .install(member.getMember().memberId(), request)
        .whenCompleteAsync(
            (response, error) -> {
              // Complete the install to the member.
              member.completeInstall();

              if (open) {
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
  protected void handleInstallResponseFailure(
      final RaftMemberContext member, final InstallRequest request, final Throwable error) {
    // Reset the member's snapshot index and offset to resend the snapshot from the start
    // once a connection to the member is re-established.
    member.setNextSnapshotIndex(0);
    member.setNextSnapshotChunk(null);

    // Log the failed attempt to contact the member.
    failAttempt(member, request, error);
  }

  /** Handles an install response. */
  protected void handleInstallResponse(
      final RaftMemberContext member,
      final InstallRequest request,
      final InstallResponse response,
      final long timestamp) {
    if (response.status() == RaftResponse.Status.OK) {
      handleInstallResponseOk(member, request, response);
    } else {
      handleInstallResponseError(member, request, response);
    }
  }

  /** Handles an OK install response. */
  @SuppressWarnings("unused")
  protected void handleInstallResponseOk(
      final RaftMemberContext member,
      final InstallRequest request,
      final InstallResponse response) {
    // Reset the member failure count and update the member's status if necessary.
    succeedAttempt(member);

    // If the install request was completed successfully, set the member's snapshotIndex and reset
    // the next snapshot index/offset.
    if (request.complete()) {
      member.setNextSnapshotIndex(0);
      member.setNextSnapshotChunk(null);
      member.setSnapshotIndex(request.index());
      resetNextIndex(member, request.index() + 1);
    }
    // If more install requests remain, increment the member's snapshot offset.
    else {
      member.setNextSnapshotChunk(request.nextChunkId());
    }

    // Recursively append entries to the member.
    appendEntries(member);
  }

  /** Handles an ERROR install response. */
  @SuppressWarnings("unused")
  protected void handleInstallResponseError(
      final RaftMemberContext member,
      final InstallRequest request,
      final InstallResponse response) {
    log.warn(
        "Failed to send {} to member {}, with {}. Restart sending snapshot.",
        request,
        member.getMember().memberId(),
        response.error().toString());

    member.setNextSnapshotIndex(0);
    member.setNextSnapshotChunk(null);
  }

  @Override
  public void close() {
    open = false;
  }
}
