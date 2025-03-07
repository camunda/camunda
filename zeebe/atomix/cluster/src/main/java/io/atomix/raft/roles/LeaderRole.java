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

import com.google.common.base.Throwables;
import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftError;
import io.atomix.raft.RaftError.Type;
import io.atomix.raft.RaftException;
import io.atomix.raft.RaftException.NoLeader;
import io.atomix.raft.RaftServer;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.impl.RaftMemberContext;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.protocol.AppendResponse;
import io.atomix.raft.protocol.ConfigureRequest;
import io.atomix.raft.protocol.ConfigureResponse;
import io.atomix.raft.protocol.ForceConfigureRequest;
import io.atomix.raft.protocol.ForceConfigureResponse;
import io.atomix.raft.protocol.InternalAppendRequest;
import io.atomix.raft.protocol.JoinRequest;
import io.atomix.raft.protocol.JoinResponse;
import io.atomix.raft.protocol.LeaveRequest;
import io.atomix.raft.protocol.LeaveResponse;
import io.atomix.raft.protocol.PollRequest;
import io.atomix.raft.protocol.PollResponse;
import io.atomix.raft.protocol.RaftResponse;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.raft.protocol.ReconfigureRequest;
import io.atomix.raft.protocol.ReconfigureResponse;
import io.atomix.raft.protocol.TransferRequest;
import io.atomix.raft.protocol.TransferResponse;
import io.atomix.raft.protocol.VoteRequest;
import io.atomix.raft.protocol.VoteResponse;
import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.storage.log.RaftLogReader;
import io.atomix.raft.storage.log.entry.ApplicationEntry;
import io.atomix.raft.storage.log.entry.ConfigurationEntry;
import io.atomix.raft.storage.log.entry.InitialEntry;
import io.atomix.raft.storage.log.entry.RaftLogEntry;
import io.atomix.raft.storage.log.entry.SerializedApplicationEntry;
import io.atomix.raft.storage.log.entry.UnserializedApplicationEntry;
import io.atomix.raft.storage.system.Configuration;
import io.atomix.raft.zeebe.CompactionBoundInformer;
import io.atomix.raft.zeebe.EntryValidator.ValidationResult;
import io.atomix.raft.zeebe.ZeebeLogAppender;
import io.atomix.utils.concurrent.Scheduled;
import io.camunda.zeebe.journal.JournalException;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

/** Leader state. */
public final class LeaderRole extends ActiveRole
    implements ZeebeLogAppender, CompactionBoundInformer {

  private static final int MAX_APPEND_ATTEMPTS = 5;
  private final LeaderAppender appender;
  private Scheduled appendTimer;
  private long configuring;
  private CompletableFuture<Void> commitInitialEntriesFuture;
  private ApplicationEntry lastZbEntry = null;

  public LeaderRole(final RaftContext context) {
    super(context);
    appender = new LeaderAppender(this);
  }

  @Override
  public synchronized CompletableFuture<RaftRole> start() {
    raft.getRaftRoleMetrics()
        .setElectionLatency(System.currentTimeMillis() - raft.getLastHeartbeat());

    // Reset state for the leader.
    takeLeadership();

    // Append initial entries to the log, including an initial no-op entry and the server's
    // configuration.
    appendInitialEntries();
    commitInitialEntriesFuture = commitInitialEntries();
    lastZbEntry = findLastZeebeEntry();

    if (jointConsensus() || forcedConfiguration()) {
      // Come out of joint consensus or forced configuration
      raft.getThreadContext()
          .execute(
              () -> {
                final var currentMembers = raft.getCluster().getConfiguration().newMembers();
                configure(currentMembers, List.of());
              });
    }

    return super.start().thenRun(this::startTimers).thenApply(v -> this);
  }

  @Override
  public synchronized CompletableFuture<Void> stop() {
    raft.resetLastHeartbeat();
    // Close open resources (eg:- journal readers) used for replication by the leader
    raft.getCluster().getReplicationTargets().forEach(RaftMemberContext::closeReplicationContext);

    return super.stop()
        .thenRun(appender::close)
        .thenRun(this::cancelTimers)
        .thenRun(this::stepDown);
  }

  @Override
  public RaftServer.Role role() {
    return RaftServer.Role.LEADER;
  }

  @Override
  public CompletableFuture<ReconfigureResponse> onReconfigure(final ReconfigureRequest request) {
    raft.checkThread();
    logRequest(request);

    // Configuration changes should not be allowed until the leader has committed a no-op entry.
    // See https://groups.google.com/forum/#!topic/raft-dev/t4xj6dJTP6E
    if (initializing()) {
      return CompletableFuture.completedFuture(
          logResponse(
              ReconfigureResponse.builder()
                  .withStatus(RaftResponse.Status.ERROR)
                  .withError(Type.CONFIGURATION_ERROR, "Not ready to make configuration changes")
                  .build()));
    }

    // If another configuration change is already under way, reject the configuration.
    if (configuring() || jointConsensus()) {
      return CompletableFuture.completedFuture(
          logResponse(
              ReconfigureResponse.builder()
                  .withStatus(RaftResponse.Status.ERROR)
                  .withError(
                      Type.CONFIGURATION_ERROR, "Another configuration change is in progress")
                  .build()));
    }

    // If the configuration request index is less than the last known configuration index for
    // the leader, fail the request to ensure servers can't reconfigure an old configuration.
    final var configuration = raft.getCluster().getConfiguration();
    if (request.index() > 0 && request.index() < configuration.index()
        || request.term() != configuration.term()) {
      return CompletableFuture.completedFuture(
          logResponse(
              ReconfigureResponse.builder()
                  .withStatus(RaftResponse.Status.ERROR)
                  .withError(RaftError.Type.CONFIGURATION_ERROR, "Stale configuration")
                  .build()));
    }

    // Write a new configuration entry with the updated member list.
    final var currentMembers = raft.getCluster().getMembers();
    final var updatedMembers = request.members();

    if (equalMembership(currentMembers, updatedMembers)) {
      return CompletableFuture.completedFuture(
          logResponse(
              ReconfigureResponse.builder()
                  .withStatus(Status.OK)
                  .withIndex(configuration.index())
                  .withTerm(configuration.term())
                  .withTime(configuration.time())
                  .withMembers(currentMembers)
                  .build()));
    }

    final CompletableFuture<ReconfigureResponse> future = new CompletableFuture<>();
    configure(updatedMembers, currentMembers)
        .whenComplete(
            (jointConsensusIndex, jointConsensusError) -> {
              if (jointConsensusError == null) {
                configure(updatedMembers, List.of())
                    .whenComplete(
                        (leftJointConsensusIndex, leftJointConsensusError) -> {
                          if (leftJointConsensusError == null) {
                            future.complete(
                                logResponse(
                                    ReconfigureResponse.builder()
                                        .withStatus(RaftResponse.Status.OK)
                                        .withIndex(leftJointConsensusIndex)
                                        .withTerm(configuration.term())
                                        .withTime(configuration.time())
                                        .withMembers(updatedMembers)
                                        .build()));
                          } else {
                            future.complete(
                                logResponse(
                                    ReconfigureResponse.builder()
                                        .withStatus(RaftResponse.Status.ERROR)
                                        .withError(
                                            RaftError.Type.PROTOCOL_ERROR,
                                            leftJointConsensusError.getMessage())
                                        .build()));
                          }
                        });
              } else {
                future.complete(
                    logResponse(
                        ReconfigureResponse.builder()
                            .withStatus(RaftResponse.Status.ERROR)
                            .withError(
                                RaftError.Type.PROTOCOL_ERROR, jointConsensusError.getMessage())
                            .build()));
              }
            });
    return future;
  }

  @Override
  public CompletableFuture<ForceConfigureResponse> onForceConfigure(
      final ForceConfigureRequest request) {

    // Do not force-configure when you are leader.
    raft.transition(Role.FOLLOWER);
    return super.onForceConfigure(request);
  }

  @Override
  public CompletableFuture<JoinResponse> onJoin(final JoinRequest request) {
    raft.checkThread();
    final var currentConfiguration = raft.getCluster().getConfiguration();
    return onReconfigure(
            ReconfigureRequest.builder()
                .withIndex(currentConfiguration.index())
                .withTerm(currentConfiguration.term())
                .withMembers(currentConfiguration.newMembers())
                // Override local member with the new type.
                .withMember(request.joiningMember())
                .from(raft.getCluster().getLocalMember().memberId().id())
                .build())
        .handle(
            (reconfigureResponse, throwable) -> {
              if (throwable != null) {
                return JoinResponse.builder()
                    .withStatus(Status.ERROR)
                    .withError(Type.PROTOCOL_ERROR, throwable.getMessage())
                    .build();
              }
              if (reconfigureResponse.status() == Status.OK) {
                return JoinResponse.builder().withStatus(Status.OK).build();
              } else {
                return JoinResponse.builder()
                    .withStatus(Status.ERROR)
                    .withError(reconfigureResponse.error())
                    .build();
              }
            });
  }

  @Override
  public CompletableFuture<LeaveResponse> onLeave(final LeaveRequest request) {
    raft.checkThread();
    final var currentConfiguration = raft.getCluster().getConfiguration();

    final var updatedMembers =
        currentConfiguration.newMembers().stream()
            .filter(member -> !member.memberId().equals(request.leavingMember().memberId()))
            .toList();
    return onReconfigure(
            ReconfigureRequest.builder()
                .withIndex(currentConfiguration.index())
                .withTerm(currentConfiguration.term())
                .withMembers(updatedMembers)
                .from(raft.getCluster().getLocalMember().memberId().id())
                .build())
        .handle(
            (reconfigureResponse, throwable) -> {
              if (throwable != null) {
                return LeaveResponse.builder()
                    .withStatus(Status.ERROR)
                    .withError(Type.PROTOCOL_ERROR, throwable.getMessage())
                    .build();
              }
              if (reconfigureResponse.status() == Status.OK) {
                return LeaveResponse.builder().withStatus(Status.OK).build();
              } else {
                return LeaveResponse.builder()
                    .withStatus(Status.ERROR)
                    .withError(reconfigureResponse.error())
                    .build();
              }
            });
  }

  /** Checks if the membership is equal in terms of member ids and types. */
  private boolean equalMembership(
      final Collection<RaftMember> currentMembers, final Collection<RaftMember> updatedMembers) {
    // Unpack member id and type because DefaultRaftMember#equals only compares the id
    record MemberIdAndType(MemberId memberId, RaftMember.Type type) {}

    final var currentMembersWithTypes =
        currentMembers.stream()
            .map(member -> new MemberIdAndType(member.memberId(), member.getType()))
            .collect(Collectors.toSet());
    final var updatedMembersWithTypes =
        updatedMembers.stream()
            .map(member -> new MemberIdAndType(member.memberId(), member.getType()))
            .collect(Collectors.toSet());
    return currentMembersWithTypes.equals(updatedMembersWithTypes);
  }

  private ApplicationEntry findLastZeebeEntry() {
    try (final RaftLogReader reader = raft.getLog().openUncommittedReader()) {
      reader.seekToAsqn(Long.MAX_VALUE);

      if (reader.hasNext()) {
        final IndexedRaftLogEntry lastEntry = reader.next();
        if (lastEntry != null && lastEntry.isApplicationEntry()) {
          return lastEntry.getApplicationEntry();
        }
      }
      return null;
    }
  }

  /** Cancels the timers. */
  private void cancelTimers() {
    if (appendTimer != null) {
      log.trace("Cancelling append timer");
      appendTimer.cancel();
    }
  }

  /** Ensures the local server is not the leader. */
  private void stepDown() {
    if (raft.getLeader() != null && raft.getLeader().equals(raft.getCluster().getLocalMember())) {
      raft.setLeader(null);
    }
  }

  /** Sets the current node as the cluster leader. */
  private void takeLeadership() {
    raft.setLeader(raft.getCluster().getLocalMember().memberId());
    raft.getCluster().reset();
    raft.getCluster()
        .getReplicationTargets()
        .forEach(member -> member.openReplicationContext(raft.getLog()));
  }

  /** Appends initial entries to the log to take leadership. */
  private void appendInitialEntries() {
    final long term = raft.getTerm();
    appendEntry(new RaftLogEntry(term, new InitialEntry()));
  }

  /** Commits a no-op entry to the log, ensuring any entries from a previous term are committed. */
  private CompletableFuture<Void> commitInitialEntries() {
    // The Raft protocol dictates that leaders cannot commit entries from previous terms until
    // at least one entry from their current term has been stored on a majority of servers. Thus,
    // we force entries to be appended up to the leader's no-op entry. The LeaderAppender will
    // ensure
    // that the commitIndex is not increased until the no-op entry (appender.index()) is committed.
    final CompletableFuture<Void> future = new CompletableFuture<>();
    appender
        .appendEntries(appender.getIndex())
        .whenComplete(
            (resultIndex, error) -> {
              raft.checkThread();
              if (isRunning()) {
                if (error == null) {
                  future.complete(null);
                } else {
                  log.info("Failed to commit the initial entry, stepping down");
                  raft.setLeader(null);
                  raft.transition(RaftServer.Role.FOLLOWER);
                }
              }
            });
    return future;
  }

  /** Starts sending AppendEntries requests to all cluster members. */
  private void startTimers() {
    // Set a timer that will be used to periodically synchronize with other nodes
    // in the cluster. This timer acts as a heartbeat to ensure this node remains
    // the leader.
    log.trace("Starting append timer on fix rate of {}", raft.getHeartbeatInterval());
    appendTimer =
        raft.getThreadContext()
            .schedule(Duration.ZERO, raft.getHeartbeatInterval(), this::appendMembers);
  }

  /**
   * Sends AppendEntries requests to members of the cluster that haven't heard from the leader in a
   * while.
   */
  private void appendMembers() {
    raft.checkThread();
    if (isRunning()) {
      appender.appendEntries();
    }
  }

  /**
   * Returns a boolean value indicating whether a configuration is currently being committed.
   *
   * @return Indicates whether a configuration is currently being committed.
   */
  private boolean configuring() {
    return configuring > 0;
  }

  /**
   * Returns a boolean value indicating whether the leader is still being initialized.
   *
   * @return Indicates whether the leader is still being initialized.
   */
  private boolean initializing() {
    // If the leader index is 0 or is greater than the commitIndex, do not allow configuration
    // changes.
    // Configuration changes should not be allowed until the leader has committed a no-op entry.
    // See https://groups.google.com/forum/#!topic/raft-dev/t4xj6dJTP6E
    return appender.getIndex() == 0 || raft.getCommitIndex() < appender.getIndex();
  }

  private boolean jointConsensus() {
    return raft.getCluster().inJointConsensus();
  }

  private boolean forcedConfiguration() {
    return raft.getCluster().getConfiguration().force();
  }

  @Override
  public void updateCompactionBound(final long compactionBound) {
    raft.getThreadContext().execute(() -> updateCompactionBoundInternal(compactionBound));
  }

  private void updateCompactionBoundInternal(final long compactionBound) {
    raft.checkThread();
    log.warn("Updating compaction bound {}", compactionBound);

    final var currentMembers = raft.getCluster().getConfiguration().newMembers();
    final var oldMembers = raft.getCluster().getConfiguration().oldMembers();
    final var configurationEntry =
        new ConfigurationEntry(
            System.currentTimeMillis(), currentMembers, oldMembers, compactionBound);
    final var term = raft.getTerm();
    final var entry = appendEntry(new RaftLogEntry(term, configurationEntry));
    configuring = entry.index();
    raft.getCluster()
        .configure(
            new Configuration(
                entry.index(),
                entry.term(),
                configurationEntry.timestamp(),
                configurationEntry.newMembers(),
                configurationEntry.oldMembers(),
                false,
                configurationEntry.compactionBound()));
    appender
        .appendEntries(entry.index())
        .whenCompleteAsync((index, error) -> configuring = 0, raft.getThreadContext());
  }

  /** Commits the given configuration. */
  private CompletableFuture<Long> configure(
      final Collection<RaftMember> newMembers, final Collection<RaftMember> oldMembers) {
    raft.checkThread();

    final long term = raft.getTerm();

    final var configurationEntry =
        new ConfigurationEntry(
            System.currentTimeMillis(), newMembers, oldMembers, raft.getCompactionBound());
    final IndexedRaftLogEntry entry;
    try {
      entry = appendEntry(new RaftLogEntry(term, configurationEntry));
    } catch (final Exception e) {
      return CompletableFuture.failedFuture(e);
    }

    // Store the index of the configuration entry in order to prevent other configurations
    // from being logged and committed concurrently. This is an important safety property of Raft.
    configuring = entry.index();
    raft.getCluster()
        .configure(
            new Configuration(
                entry.index(),
                entry.term(),
                configurationEntry.timestamp(),
                configurationEntry.newMembers(),
                configurationEntry.oldMembers()));

    return appender
        .appendEntries(entry.index())
        .whenCompleteAsync((index, error) -> configuring = 0, raft.getThreadContext());
  }

  @Override
  public CompletableFuture<ConfigureResponse> onConfigure(final ConfigureRequest request) {
    if (updateTermAndLeader(request.term(), request.leader())) {
      raft.transition(Role.FOLLOWER);
    }
    return super.onConfigure(request);
  }

  @Override
  public CompletableFuture<TransferResponse> onTransfer(final TransferRequest request) {
    logRequest(request);

    if (!raft.getCluster().isMember(request.member())) {
      return CompletableFuture.completedFuture(
          logResponse(
              TransferResponse.builder()
                  .withStatus(RaftResponse.Status.ERROR)
                  .withError(RaftError.Type.ILLEGAL_MEMBER_STATE)
                  .build()));
    }

    final CompletableFuture<TransferResponse> future = new CompletableFuture<>();
    appender
        .appendEntries(raft.getLog().getLastIndex())
        .whenComplete(
            (result, error) -> {
              if (isRunning()) {
                if (error == null) {
                  log.info("Transferring leadership to {}", request.member());
                  raft.transition(RaftServer.Role.FOLLOWER);
                  future.complete(
                      logResponse(
                          TransferResponse.builder().withStatus(RaftResponse.Status.OK).build()));
                } else if (error instanceof CompletionException
                    && error.getCause() instanceof RaftException) {
                  future.complete(
                      logResponse(
                          TransferResponse.builder()
                              .withStatus(RaftResponse.Status.ERROR)
                              .withError(
                                  ((RaftException) error.getCause()).getType(), error.getMessage())
                              .build()));
                } else if (error instanceof RaftException) {
                  future.complete(
                      logResponse(
                          TransferResponse.builder()
                              .withStatus(RaftResponse.Status.ERROR)
                              .withError(((RaftException) error).getType(), error.getMessage())
                              .build()));
                } else {
                  future.complete(
                      logResponse(
                          TransferResponse.builder()
                              .withStatus(RaftResponse.Status.ERROR)
                              .withError(RaftError.Type.PROTOCOL_ERROR, error.getMessage())
                              .build()));
                }
              } else {
                future.complete(
                    logResponse(
                        TransferResponse.builder()
                            .withStatus(RaftResponse.Status.ERROR)
                            .withError(RaftError.Type.ILLEGAL_MEMBER_STATE)
                            .build()));
              }
            });
    return future;
  }

  @Override
  public CompletableFuture<AppendResponse> onAppend(final InternalAppendRequest request) {
    raft.checkThread();
    if (updateTermAndLeader(request.term(), request.leader())) {
      final CompletableFuture<AppendResponse> future = super.onAppend(request);
      raft.transition(RaftServer.Role.FOLLOWER);
      return future;
    } else if (request.term() < raft.getTerm()) {
      logRequest(request);
      return CompletableFuture.completedFuture(
          logResponse(
              AppendResponse.builder()
                  .withStatus(RaftResponse.Status.OK)
                  .withTerm(raft.getTerm())
                  .withSucceeded(false)
                  .withLastLogIndex(raft.getLog().getLastIndex())
                  .withLastSnapshotIndex(raft.getCurrentSnapshotIndex())
                  .build()));
    } else {
      raft.setLeader(request.leader());
      raft.transition(RaftServer.Role.FOLLOWER);
      return super.onAppend(request);
    }
  }

  @Override
  public CompletableFuture<PollResponse> onPoll(final PollRequest request) {
    logRequest(request);

    // If a member sends a PollRequest to the leader, that indicates that it likely healed from
    // a network partition and may have had its status set to UNAVAILABLE by the leader. In order
    // to ensure heartbeats are immediately stored to the member, update its status if necessary.
    final RaftMemberContext member = raft.getCluster().getMemberContext(request.candidate());
    if (member != null) {
      member.resetFailureCount();
    }

    return CompletableFuture.completedFuture(
        logResponse(
            PollResponse.builder()
                .withStatus(RaftResponse.Status.OK)
                .withTerm(raft.getTerm())
                .withAccepted(false)
                .build()));
  }

  @Override
  public CompletableFuture<VoteResponse> onVote(final VoteRequest request) {
    if (updateTermAndLeader(request.term(), null)) {
      log.info("Received greater term from {}", request.candidate());
      raft.transition(RaftServer.Role.FOLLOWER);
      return super.onVote(request);
    } else {
      logRequest(request);
      return CompletableFuture.completedFuture(
          logResponse(
              VoteResponse.builder()
                  .withStatus(RaftResponse.Status.OK)
                  .withTerm(raft.getTerm())
                  .withVoted(false)
                  .build()));
    }
  }

  private IndexedRaftLogEntry appendEntry(final RaftLogEntry entry) {
    try {
      return appendWithRetry(entry);
    } catch (final Exception e) {
      log.error("Failed to append to local log, stepping down", e);
      raft.transition(Role.FOLLOWER);
      throw e;
    }
  }

  private IndexedRaftLogEntry appendWithRetry(final RaftLogEntry entry) {
    int retries = 0;

    RuntimeException lastError = null;
    // we retry in a blocking fashion to avoid interleaving append requests; this however blocks the
    // raft thread.
    while (retries <= MAX_APPEND_ATTEMPTS) {
      try {
        return append(entry);
      } catch (final JournalException.OutOfDiskSpace e) {
        // ignore the replication threshold in order to free as much data as possible
        if (!raft.getLogCompactor().compactIgnoringReplicationThreshold()) {
          // no reason to retry if we failed to delete any data
          throw e;
        }

        lastError = e;
        retries++;

        log.warn(
            "Out of disk space while appending entry {}, compacted and retrying... (try {} out of {})",
            entry,
            retries,
            MAX_APPEND_ATTEMPTS,
            e);
      } catch (final JournalException
          | UncheckedIOException e) { // JournalException will wrap most IOException
        lastError = e;

        retries++;
        log.warn(
            "Error on appending entry {}, retrying... (try {} out of {})",
            entry,
            retries,
            MAX_APPEND_ATTEMPTS,
            e);
      }
    }

    log.warn("Failed to append to local log after {} retries", retries, lastError);
    throw lastError;
  }

  private IndexedRaftLogEntry append(final RaftLogEntry entry) {
    final var indexedEntry = raft.getLog().append(entry);
    raft.getReplicationMetrics().setAppendIndex(indexedEntry.index());
    log.trace("Appended {}", indexedEntry);
    appender.observeNonCommittedEntries(raft.getCommitIndex());
    return indexedEntry;
  }

  @Override
  public void appendEntry(final ApplicationEntry entry, final AppendListener appendListener) {
    raft.getThreadContext().execute(() -> safeAppendEntry(entry, appendListener));
  }

  @Override
  public void appendEntry(
      final long lowestPosition,
      final long highestPosition,
      final ByteBuffer data,
      final AppendListener appendListener) {
    raft.getThreadContext()
        .execute(
            () ->
                safeAppendEntry(
                    new SerializedApplicationEntry(lowestPosition, highestPosition, data),
                    appendListener));
  }

  @Override
  public void appendEntry(
      final long lowestPosition,
      final long highestPosition,
      final BufferWriter data,
      final AppendListener appendListener) {
    raft.getThreadContext()
        .execute(
            () ->
                safeAppendEntry(
                    new UnserializedApplicationEntry(lowestPosition, highestPosition, data),
                    appendListener));
  }

  private void safeAppendEntry(final ApplicationEntry entry, final AppendListener appendListener) {
    raft.checkThread();

    if (!isRunning()) {
      appendListener.onWriteError(
          new NoLeader("LeaderRole is closed and cannot be used as appender"));
      return;
    }

    final ValidationResult result = raft.getEntryValidator().validateEntry(lastZbEntry, entry);
    if (result.failed()) {
      appendListener.onWriteError(new IllegalStateException(result.errorMessage()));
      raft.transition(Role.FOLLOWER);
      return;
    }

    final IndexedRaftLogEntry indexed;
    try {
      indexed = appendEntry(new RaftLogEntry(raft.getTerm(), entry));
    } catch (final Exception e) {
      appendListener.onWriteError(Throwables.getRootCause(e));
      return;
    }

    if (indexed.isApplicationEntry()) {
      lastZbEntry = indexed.getApplicationEntry();
    }

    appendListener.onWrite(indexed);
    replicate(indexed, appendListener);
  }

  private void replicate(final IndexedRaftLogEntry indexed, final AppendListener appendListener) {
    raft.checkThread();
    final var appendEntriesFuture = appender.appendEntries(indexed.index());
    final var committedPosition =
        indexed.isApplicationEntry() ? indexed.getApplicationEntry().highestPosition() : -1;

    if (indexed.isApplicationEntry()) {
      // We have some services which are waiting for the application records, especially position
      // to be committed. This is our glue code to notify them, instead of
      // passing the complete object (IndexedRaftLogEntry) threw the listeners and
      // keep them in heap until they are committed. This had the risk of going out of OOM
      // if records can't be committed, see https://github.com/camunda/camunda/issues/14275
      appendEntriesFuture.whenCompleteAsync(
          (commitIndex, commitError) -> {
            if (isRunning() && commitError == null) {
              raft.notifyApplicationEntryCommittedPositionListeners(committedPosition);
            }
          },
          raft.getThreadContext());
    }

    appendEntriesFuture.whenCompleteAsync(
        (commitIndex, commitError) -> {
          if (!isRunning()) {
            return;
          }

          // have the state machine apply the index which should do nothing but ensures it keeps
          // up to date with the latest entries, so it can handle configuration and initial
          // entries properly on fail over
          if (commitError == null) {
            appendListener.onCommit(commitIndex, committedPosition);
          } else {
            appendListener.onCommitError(commitIndex, commitError);
            // replicating the entry will be retried on the next append request
            log.error("Failed to replicate entry: {}", commitIndex, commitError);
          }
        },
        raft.getThreadContext());
  }

  public synchronized void onInitialEntriesCommitted(final Runnable runnable) {
    commitInitialEntriesFuture.whenComplete(
        (v, error) -> {
          if (error == null) {
            runnable.run();
          }
        });
  }
}
