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
import io.atomix.raft.LeadershipTransferResult;
import io.atomix.raft.RaftError;
import io.atomix.raft.RaftError.Type;
import io.atomix.raft.RaftException;
import io.atomix.raft.RaftException.AppendFailureException;
import io.atomix.raft.RaftException.NoLeader;
import io.atomix.raft.RaftServer;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.impl.RaftMemberContext;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.metrics.RebalanceMetrics;
import io.atomix.raft.protocol.AppendResponse;
import io.atomix.raft.protocol.ConfigureRequest;
import io.atomix.raft.protocol.ConfigureResponse;
import io.atomix.raft.protocol.ForceConfigureRequest;
import io.atomix.raft.protocol.ForceConfigureResponse;
import io.atomix.raft.protocol.InternalAppendRequest;
import io.atomix.raft.protocol.JoinRequest;
import io.atomix.raft.protocol.JoinResponse;
import io.atomix.raft.protocol.LeadershipTransferInitiateRequest;
import io.atomix.raft.protocol.LeadershipTransferInitiateResponse;
import io.atomix.raft.protocol.LeadershipTransferResultRequest;
import io.atomix.raft.protocol.LeaveRequest;
import io.atomix.raft.protocol.LeaveResponse;
import io.atomix.raft.protocol.PollRequest;
import io.atomix.raft.protocol.PollResponse;
import io.atomix.raft.protocol.RaftResponse;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.raft.protocol.ReconfigureRequest;
import io.atomix.raft.protocol.ReconfigureResponse;
import io.atomix.raft.protocol.TimeoutNowRequest;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Leader state. */
public final class LeaderRole extends ActiveRole implements ZeebeLogAppender {

  private static final int MAX_APPEND_ATTEMPTS = 5;
  private final LeaderAppender appender;
  private Scheduled appendTimer;
  private long configuring;
  private CompletableFuture<Void> commitInitialEntriesFuture;
  private ApplicationEntry lastZbEntry = null;
  private CompletableFuture<ReconfigureResponse> ongoingReconfigurationRequestFuture;

  // --- Coordinated leadership transfer: paused mode ---
  // The leader keeps its term and keeps replicating; the write freeze and processing pause are
  // applied by the broker. This role only owns the watchdog that guarantees the partition is never
  // left paused: if not resumed in time it steps down, which forces a role transition.
  private final RebalanceMetrics rebalanceMetrics;
  private Scheduled transferPauseWatchdog;
  private boolean transferPaused;
  private long transferPauseStartMs;
  private boolean transferInProgress;
  private Scheduled catchUpPollTimer;
  private Scheduled catchUpDeadlineTimer;
  // Held so a role transition can complete an in-flight catch-up with LEADER_CHANGED rather than
  // leaving it hanging.
  private CompletableFuture<Optional<LeadershipTransferResult>> catchUpFuture;
  // Promotion (TimeoutNow send + count-bounded retry). Sends are strictly bounded by
  // maxTransferAttempts; success requires observing the *selected* target actually becoming leader,
  // so a leader-election listener resolves the result once leadership moves.
  private Scheduled timeoutNowRetryTimer;
  private Scheduled promotionDeadline;
  private CompletableFuture<LeadershipTransferResult> timeoutNowFuture;
  private MemberId timeoutNowTarget;
  private int timeoutNowAttempts;
  private boolean steppedDownDuringTransfer;
  private Consumer<RaftMember> transferLeaderListener;

  public LeaderRole(final RaftContext context) {
    super(context);
    appender = new LeaderAppender(this);
    rebalanceMetrics = new RebalanceMetrics(context.getName(), context.getMeterRegistry());
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
                ongoingReconfigurationRequestFuture = new CompletableFuture<>();
                leaveJointConsensus(currentMembers, raft.getCluster().getConfiguration());
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
      /*
       If the request is a duplicate, return the current future. This is essential for completing
       the join of a second member into a single-member cluster. During a join retry, if the
       joining member receives an error, it may shut down the Raft partition and restart. In such
       cases, the reconfiguration request cannot complete because the joining member might already
       be part of the quorum and must be active to commit the configuration change.
      */
      if (isDuplicateReconfigureRequest(request)) {
        return ongoingReconfigurationRequestFuture;
      }
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

    // A configuration with members but no ACTIVE ones could neither elect a leader nor commit
    // entries, permanently stranding the remaining members. The empty configuration stays allowed:
    // it is the result of the last member leaving when scaling a partition down to zero.
    final var hasActiveMember =
        updatedMembers.stream().anyMatch(member -> member.getType() == RaftMember.Type.ACTIVE);
    if (!updatedMembers.isEmpty() && !hasActiveMember) {
      return CompletableFuture.completedFuture(
          logResponse(
              ReconfigureResponse.builder()
                  .withStatus(RaftResponse.Status.ERROR)
                  .withError(
                      Type.CONFIGURATION_ERROR,
                      "Requested configuration %s must be empty or have at least one active member"
                          .formatted(updatedMembers))
                  .build()));
    }

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

    ongoingReconfigurationRequestFuture = new CompletableFuture<>();
    configure(updatedMembers, currentMembers)
        .whenComplete(
            (jointConsensusIndex, jointConsensusError) -> {
              if (jointConsensusError == null) {
                leaveJointConsensus(updatedMembers, configuration);
              } else {
                ongoingReconfigurationRequestFuture.complete(
                    logResponse(
                        ReconfigureResponse.builder()
                            .withStatus(RaftResponse.Status.ERROR)
                            .withError(
                                RaftError.Type.PROTOCOL_ERROR, jointConsensusError.getMessage())
                            .build()));
              }
            });
    return ongoingReconfigurationRequestFuture;
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

  private void leaveJointConsensus(
      final Collection<RaftMember> updatedMembers, final Configuration configuration) {
    configure(updatedMembers, List.of())
        .whenComplete(
            (leftJointConsensusIndex, leftJointConsensusError) -> {
              if (leftJointConsensusError == null) {
                ongoingReconfigurationRequestFuture.complete(
                    logResponse(
                        ReconfigureResponse.builder()
                            .withStatus(Status.OK)
                            .withIndex(leftJointConsensusIndex)
                            .withTerm(configuration.term())
                            .withTime(configuration.time())
                            .withMembers(updatedMembers)
                            .build()));
              } else {
                ongoingReconfigurationRequestFuture.complete(
                    logResponse(
                        ReconfigureResponse.builder()
                            .withStatus(Status.ERROR)
                            .withError(Type.PROTOCOL_ERROR, leftJointConsensusError.getMessage())
                            .build()));
              }
            });
  }

  private boolean isDuplicateReconfigureRequest(final ReconfigureRequest request) {
    final var requestedUpdate = request.members();
    final var ongoingUpdate = raft.getCluster().getConfiguration().newMembers();
    return equalMembership(requestedUpdate, ongoingUpdate);
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
    // Losing leadership while a TimeoutNow transfer is in flight means the term advanced. Do not
    // conclude success here: another node may have campaigned instead of the selected target. Stop
    // sending, record that we stepped down, and let the leader-election listener resolve the result
    // once the new leader is observed (the promotion deadline is the backstop).
    if (timeoutNowFuture != null) {
      log.debug(
          "Stepping down during TimeoutNow transfer to {}; awaiting the new leader",
          timeoutNowTarget);
      steppedDownDuringTransfer = true;
      if (timeoutNowRetryTimer != null) {
        timeoutNowRetryTimer.cancel();
        timeoutNowRetryTimer = null;
      }
    }
    // Losing leadership while still catching the desired leader up (before promotion) aborts the
    // transfer: report LEADER_CHANGED rather than leaving the caller waiting.
    if (catchUpFuture != null) {
      log.debug("Lost leadership while catching up the desired leader; reporting LEADER_CHANGED");
      completeCatchUp(Optional.of(LeadershipTransferResult.LEADER_CHANGED));
    }
    // Paused mode always exits on a role transition (stop() runs when leadership is lost).
    clearTransferPause();
  }

  /**
   * Enters paused mode for a coordinated leadership transfer. Arms a watchdog on the Raft thread
   * that steps this leader down if {@link #resumeFromTransfer()} is not called within {@code
   * resumeTimeout}, so a partition is never left paused and unavailable. The leader keeps its term
   * and keeps replicating existing entries; the broker applies the write freeze and processing
   * pause. Idempotent while already paused (the watchdog is not re-armed). Must run on the Raft
   * thread.
   *
   * @param resumeTimeout how long the partition may remain paused before the leader steps down
   * @return the frozen last log index, captured on the Raft thread after every already-enqueued
   *     append has been applied; this is the catch-up target the desired leader must reach
   */
  public long pauseForTransfer(final Duration resumeTimeout) {
    raft.checkThread();
    if (transferPaused) {
      // Idempotent: the watchdog is already armed. Report the current head so a repeat caller still
      // gets a usable target.
      return raft.getLog().getLastIndex();
    }
    transferPaused = true;
    transferPauseStartMs = System.currentTimeMillis();
    // The broker has frozen write admission and paused (and drained) the stream processor before
    // this Raft-thread task runs, so by FIFO ordering every write admitted before the freeze has
    // already been appended: the last index is the true frozen head.
    final long targetIndex = raft.getLog().getLastIndex();
    log.info("Pausing partition for leadership transfer; resume deadline in {}", resumeTimeout);
    transferPauseWatchdog =
        raft.getThreadContext().schedule(resumeTimeout, this::onTransferPauseDeadline);
    return targetIndex;
  }

  /** Leaves paused mode after a coordinated leadership transfer. Must run on the Raft thread. */
  public void resumeFromTransfer() {
    raft.checkThread();
    if (transferPaused) {
      log.info("Resuming partition after leadership transfer");
    }
    clearTransferPause();
  }

  public boolean isTransferPaused() {
    return transferPaused;
  }

  private void onTransferPauseDeadline() {
    raft.checkThread();
    if (!transferPaused || !isRunning()) {
      return;
    }
    log.warn(
        "Partition still paused after the resume deadline; stepping down to follower so services "
            + "restart and a new leader can be elected");
    // A catch-up may still be pending (the watchdog fired before the desired leader caught up).
    // Complete it with a terminal result first: clearTransferPause() would otherwise discard the
    // future without completing it, hanging the awaitDesiredLeaderCaughtUp caller forever.
    if (catchUpFuture != null) {
      completeCatchUp(Optional.of(LeadershipTransferResult.REPLICATION_TIMED_OUT));
    }
    clearTransferPause();
    raft.transition(RaftServer.Role.FOLLOWER);
  }

  private void clearTransferPause() {
    if (transferPauseWatchdog != null) {
      transferPauseWatchdog.cancel();
      transferPauseWatchdog = null;
    }
    if (transferPaused) {
      transferPaused = false;
      rebalanceMetrics.observePauseDuration(
          Duration.ofMillis(System.currentTimeMillis() - transferPauseStartMs));
    }
    // Never discard a pending catch-up without completing it. The precise reason is set by the
    // callers that know it (watchdog expiry, role transition); this is the safety net for any other
    // clear path so the awaitDesiredLeaderCaughtUp caller can never hang.
    if (catchUpFuture != null) {
      completeCatchUp(Optional.of(LeadershipTransferResult.LEADER_CHANGED));
    } else {
      cancelCatchUp();
    }
    transferInProgress = false;
  }

  /**
   * Evaluates the coordinated-leadership-transfer pre-checks for {@code desiredLeader} on behalf of
   * {@code coordinator}. Returns the skip reason if any check fails, or empty if the transfer may
   * proceed. Must run on the Raft thread.
   *
   * @param desiredLeader the intended successor (highest-priority node)
   * @param coordinator the node that requested the transfer
   * @param coordinatorConfigVersion the configuration version the coordinator based its request on
   */
  public Optional<LeadershipTransferResult> precheckTransfer(
      final MemberId desiredLeader,
      final MemberId coordinator,
      final long coordinatorConfigVersion) {
    raft.checkThread();
    final var localMember = raft.getCluster().getLocalMember().memberId();

    if (desiredLeader.equals(localMember)) {
      return Optional.of(LeadershipTransferResult.ALREADY_LEADER);
    }
    if (transferInProgress) {
      return Optional.of(LeadershipTransferResult.TRANSFER_IN_PROGRESS);
    }
    if (!isCurrentCoordinator(coordinator, coordinatorConfigVersion)) {
      return Optional.of(LeadershipTransferResult.INVALID_COORDINATOR);
    }
    final var desiredContext = raft.getCluster().getMemberContext(desiredLeader);
    // OFFLINE must reflect actual availability, not just membership: a configured member can be
    // down or partitioned. The leader resets a member's failure count on every successful contact
    // and increments it on each failed append/heartbeat, so a non-zero count means the most recent
    // contact did not succeed — the desired leader is not currently reachable.
    //
    // The failure count and replication lag are also both zeroed when this leader opens the
    // member's replication context (post-election), and the lag reader starts at the log head — so
    // before the first current-term response a member that is actually offline or far behind still
    // reads as reachable and fully caught up. Require a successful append in this term
    // (hasAckedAppend) before trusting those counters; until then we treat availability as unknown
    // and decline, so a rebalance triggered right after an election cannot pause a partition for
    // such a member.
    if (desiredContext == null
        || !raft.getCluster().isMember(desiredLeader)
        || !desiredContext.hasAckedAppend()
        || desiredContext.getFailureCount() > 0) {
      return Optional.of(LeadershipTransferResult.OFFLINE);
    }
    // An unknown snapshot size must fail closed: it is not zero, and treating it as zero would let
    // a follower with a potentially large snapshot outstanding pass as caught up.
    if (desiredContext.isSnapshotReplicationLagUnknown()) {
      return Optional.of(LeadershipTransferResult.LAG_TOO_HIGH);
    }
    // Point-in-time lag sample: the threshold is tuned so a passing desired leader reliably catches
    // up within replicationTimeout; we do not wait for the lag to reach the value here.
    if (desiredContext.getReplicationLagBytes() > raft.getRebalanceReplicationLagThreshold()) {
      return Optional.of(LeadershipTransferResult.LAG_TOO_HIGH);
    }
    return Optional.empty();
  }

  /**
   * The coordinator is the lowest-id member of the leader's committed configuration. A request from
   * any other member, or one carrying a configuration version older than the leader's, is rejected
   * so a stale or non-coordinator node cannot drive a transfer.
   */
  private boolean isCurrentCoordinator(
      final MemberId coordinator, final long coordinatorConfigVersion) {
    final var configuration = raft.getCluster().getConfiguration();
    if (configuration == null) {
      return false;
    }
    if (coordinatorConfigVersion < configuration.index()) {
      return false;
    }
    return configuration.newMembers().stream()
        .map(RaftMember::memberId)
        .min(MemberId.ID_COMPARATOR)
        .map(coordinator::equals)
        .orElse(false);
  }

  /**
   * Marks a transfer as in progress and waits until {@code desiredLeader}'s {@code matchIndex}
   * reaches {@code targetIndex} (the leader's last log index, frozen by the broker's write freeze),
   * polling on the Raft thread each {@code heartbeatInterval}. The returned future completes with
   * an empty {@link Optional} once the desired leader is fully caught up (proceed to promotion), or
   * with a terminal reason: {@code REPLICATION_TIMED_OUT} if it does not catch up within {@code
   * timeout}, {@code OFFLINE} if the desired leader leaves the partition, or {@code LEADER_CHANGED}
   * if this node stops being the leader (it stepped down or another node was elected) — see {@link
   * #cancelTimers()}. Must run on the Raft thread.
   */
  public CompletableFuture<Optional<LeadershipTransferResult>> awaitDesiredLeaderCaughtUp(
      final MemberId desiredLeader, final long targetIndex, final Duration timeout) {
    raft.checkThread();
    transferInProgress = true;
    catchUpFuture = new CompletableFuture<>();
    // Hold a local reference: completeCatchUp() clears the field, so we must return this.
    final var future = catchUpFuture;

    if (isCaughtUp(desiredLeader, targetIndex)) {
      completeCatchUp(Optional.empty());
      return future;
    }

    catchUpDeadlineTimer =
        raft.getThreadContext()
            .schedule(
                timeout,
                () -> {
                  log.info(
                      "Desired leader {} did not catch up to index {} within {}",
                      desiredLeader,
                      targetIndex,
                      timeout);
                  completeCatchUp(Optional.of(LeadershipTransferResult.REPLICATION_TIMED_OUT));
                });
    final var pollInterval = raft.getHeartbeatInterval();
    catchUpPollTimer =
        raft.getThreadContext()
            .schedule(
                pollInterval,
                pollInterval,
                () -> {
                  if (catchUpFuture == null) {
                    return;
                  }
                  if (!isRunning()) {
                    completeCatchUp(Optional.of(LeadershipTransferResult.LEADER_CHANGED));
                  } else if (raft.getCluster().getMemberContext(desiredLeader) == null) {
                    // The desired leader left the partition: report failure.
                    completeCatchUp(Optional.of(LeadershipTransferResult.OFFLINE));
                  } else if (isCaughtUp(desiredLeader, targetIndex)) {
                    completeCatchUp(Optional.empty());
                  }
                });
    return future;
  }

  private boolean isCaughtUp(final MemberId desiredLeader, final long targetIndex) {
    final var context = raft.getCluster().getMemberContext(desiredLeader);
    return context != null && context.getMatchIndex() >= targetIndex;
  }

  private void completeCatchUp(final Optional<LeadershipTransferResult> result) {
    final var future = catchUpFuture;
    cancelCatchUp();
    if (future != null) {
      future.complete(result);
    }
  }

  private void cancelCatchUp() {
    if (catchUpPollTimer != null) {
      catchUpPollTimer.cancel();
      catchUpPollTimer = null;
    }
    if (catchUpDeadlineTimer != null) {
      catchUpDeadlineTimer.cancel();
      catchUpDeadlineTimer = null;
    }
    catchUpFuture = null;
  }

  /**
   * Promotes {@code target} by sending it TimeoutNow, resending every {@code heartbeatInterval}
   * until leadership actually moves or {@code maxTransferAttempts} sends are spent. The number of
   * TimeoutNow messages is strictly count-bounded by {@code maxTransferAttempts}. Completes with
   * {@link LeadershipTransferResult#TRANSFERRED} only once the <em>selected</em> {@code target} is
   * observed to have become leader; if a different node wins instead, or leadership otherwise moves
   * away, {@link LeadershipTransferResult#LEADER_CHANGED}; and {@link
   * LeadershipTransferResult#TRANSFER_FAILED} if leadership does not move while this node stays
   * leader for the whole attempt budget. Must run on the Raft thread.
   */
  public CompletableFuture<LeadershipTransferResult> promoteDesiredLeader(final MemberId target) {
    raft.checkThread();
    if (timeoutNowFuture != null) {
      return CompletableFuture.completedFuture(LeadershipTransferResult.TRANSFER_IN_PROGRESS);
    }
    if (!raft.getCluster().isMember(target)) {
      return CompletableFuture.completedFuture(LeadershipTransferResult.OFFLINE);
    }

    timeoutNowTarget = target;
    timeoutNowAttempts = 0;
    steppedDownDuringTransfer = false;
    timeoutNowFuture = new CompletableFuture<>();
    // Success requires the selected target to actually win. Observe leadership changes so a genuine
    // transfer to the target is told apart from another node campaigning and winning instead.
    transferLeaderListener = this::onLeaderObservedDuringTransfer;
    raft.addLeaderElectionListener(transferLeaderListener);

    // Backstop covering the whole attempt: the send budget plus enough time to observe the new
    // leader after stepping down. Guarantees the caller is never left waiting.
    final Duration deadline =
        raft.getHeartbeatInterval()
            .multipliedBy(raft.getRebalanceMaxTransferAttempts())
            .plus(raft.getElectionTimeout().multipliedBy(2));
    log.info(
        "Starting TimeoutNow leadership transfer to {} (up to {} attempts, resending every {})",
        target,
        raft.getRebalanceMaxTransferAttempts(),
        raft.getHeartbeatInterval());
    promotionDeadline = raft.getThreadContext().schedule(deadline, this::onPromotionDeadline);

    attemptTimeoutNow();
    return timeoutNowFuture;
  }

  /**
   * Sends the next TimeoutNow while this node is still the leader and the attempt budget is not
   * spent, rescheduling itself from the previous attempt so the number of sends is strictly
   * count-bounded. Once leadership actually moves the election listener owns the result.
   *
   * <p>Membership is rechecked on every attempt, not only before the first send: the target can be
   * removed from the partition after catch-up completes but before it is promoted, and each retry
   * is the next point on the Raft thread where that removal is visible.
   */
  private void attemptTimeoutNow() {
    raft.checkThread();
    if (timeoutNowFuture == null || !isRunning()) {
      // Resolved already, or we stepped down: the listener / promotion deadline owns completion.
      return;
    }
    if (!raft.getCluster().isMember(timeoutNowTarget)) {
      log.info(
          "Selected leader {} is no longer a member of the partition; abandoning the transfer",
          timeoutNowTarget);
      completeTransfer(LeadershipTransferResult.OFFLINE);
      return;
    }
    if (timeoutNowAttempts >= raft.getRebalanceMaxTransferAttempts()) {
      log.info(
          "TimeoutNow transfer to {} did not move leadership within {} attempts while still "
              + "leader; giving up",
          timeoutNowTarget,
          timeoutNowAttempts);
      completeTransfer(LeadershipTransferResult.TRANSFER_FAILED);
      return;
    }
    sendTimeoutNow();
    timeoutNowRetryTimer =
        raft.getThreadContext().schedule(raft.getHeartbeatInterval(), this::attemptTimeoutNow);
  }

  private void sendTimeoutNow() {
    raft.checkThread();
    timeoutNowAttempts++;
    final var request =
        TimeoutNowRequest.builder()
            .withTerm(raft.getTerm())
            .withLeader(raft.getCluster().getLocalMember().memberId())
            .build();
    log.debug("Sending TimeoutNow to {} (attempt {})", timeoutNowTarget, timeoutNowAttempts);
    // The RPC ack is only for tracing: the authoritative success signal is the term advancing
    // (i.e. losing leadership), so a lost or failed ack simply falls through to the next attempt.
    raft.getProtocol()
        .timeoutNow(timeoutNowTarget, request)
        .whenCompleteAsync(
            (response, error) -> {
              if (error != null) {
                log.trace(
                    "TimeoutNow to {} failed, will retry if budget remains",
                    timeoutNowTarget,
                    error);
              } else {
                log.trace("TimeoutNow to {} acknowledged: {}", timeoutNowTarget, response);
              }
            },
            raft.getThreadContext());
  }

  /** Resolves the transfer result once leadership is observed to move to another node. */
  private void onLeaderObservedDuringTransfer(final RaftMember newLeader) {
    raft.checkThread();
    if (timeoutNowFuture == null) {
      return;
    }
    final var localMember = raft.getCluster().getLocalMember().memberId();
    if (newLeader.memberId().equals(localMember)) {
      // We are the leader. If we have not stepped down yet, keep waiting; if we were re-elected
      // after stepping down, the transfer did not take, so leadership effectively changed.
      if (steppedDownDuringTransfer) {
        completeTransfer(LeadershipTransferResult.LEADER_CHANGED);
      }
      return;
    }
    completeTransfer(
        newLeader.memberId().equals(timeoutNowTarget)
            ? LeadershipTransferResult.TRANSFERRED
            : LeadershipTransferResult.LEADER_CHANGED);
  }

  private void onPromotionDeadline() {
    raft.checkThread();
    if (timeoutNowFuture == null) {
      return;
    }
    // We stepped down (else attemptTimeoutNow would already have given up) but never observed a new
    // leader within the window: leadership left this node, but the target's win is unconfirmed.
    log.info(
        "No new leader observed after stepping down for the transfer to {}; reporting a leadership "
            + "change",
        timeoutNowTarget);
    completeTransfer(
        steppedDownDuringTransfer
            ? LeadershipTransferResult.LEADER_CHANGED
            : LeadershipTransferResult.TRANSFER_FAILED);
  }

  private void completeTransfer(final LeadershipTransferResult result) {
    final var future = timeoutNowFuture;
    cancelTimeoutNow();
    if (future != null) {
      future.complete(result);
    }
  }

  private void cancelTimeoutNow() {
    if (timeoutNowRetryTimer != null) {
      timeoutNowRetryTimer.cancel();
      timeoutNowRetryTimer = null;
    }
    if (promotionDeadline != null) {
      promotionDeadline.cancel();
      promotionDeadline = null;
    }
    if (transferLeaderListener != null) {
      raft.removeLeaderElectionListener(transferLeaderListener);
      transferLeaderListener = null;
    }
    timeoutNowFuture = null;
    timeoutNowTarget = null;
    timeoutNowAttempts = 0;
    steppedDownDuringTransfer = false;
  }

  /**
   * Leader-side composing handler for a coordinated leadership transfer. Runs the pre-checks; on a
   * failure it acknowledges immediately with the skip result, otherwise it accepts the request and
   * drives the transfer asynchronously — freeze (pause barrier) → catch the desired leader up to
   * the frozen index → promote via TimeoutNow — then reports the terminal outcome to the
   * coordinator. On any failure/timeout/role change the partition is resumed before reporting. The
   * whole attempt is timed on the transfer-duration metric, labelled with the result.
   */
  @Override
  public CompletableFuture<LeadershipTransferInitiateResponse> onLeadershipTransferInitiate(
      final LeadershipTransferInitiateRequest request) {
    raft.checkThread();
    logRequest(request);
    final var self = raft.getCluster().getLocalMember().memberId();
    final var precheck =
        precheckTransfer(
            request.desiredLeader(), request.coordinator(), request.coordinatorConfigVersion());
    if (precheck.isPresent()) {
      return CompletableFuture.completedFuture(
          logResponse(
              LeadershipTransferInitiateResponse.builder()
                  .withStatus(Status.OK)
                  .withAccepted(false)
                  .withResult(precheck.get())
                  .withLeader(self)
                  .build()));
    }
    // Accepted: claim the transfer so a concurrent request is rejected, then drive it and report
    // the
    // terminal outcome to the coordinator asynchronously.
    transferInProgress = true;
    runCoordinatedTransfer(request);
    return CompletableFuture.completedFuture(
        logResponse(
            LeadershipTransferInitiateResponse.builder()
                .withStatus(Status.OK)
                .withAccepted(true)
                .withLeader(self)
                .build()));
  }

  private void runCoordinatedTransfer(final LeadershipTransferInitiateRequest request) {
    raft.checkThread();
    final long startMs = System.currentTimeMillis();
    final var desiredLeader = request.desiredLeader();
    final var coordinator = request.coordinator();
    final var resumeTimeout = raft.getRebalanceReplicationTimeout();
    pauseForCoordinatedTransfer(resumeTimeout)
        .whenCompleteAsync(
            (targetIndex, error) -> {
              if (error != null) {
                log.warn("Failed to pause partition for transfer to {}", desiredLeader, error);
                finishCoordinatedTransfer(
                    coordinator, desiredLeader, LeadershipTransferResult.TRANSFER_FAILED, startMs);
                return;
              }
              awaitDesiredLeaderCaughtUp(desiredLeader, targetIndex, resumeTimeout)
                  .whenComplete(
                      (catchUpResult, e) -> {
                        if (catchUpResult.isPresent()) {
                          finishCoordinatedTransfer(
                              coordinator, desiredLeader, catchUpResult.get(), startMs);
                        } else {
                          promoteDesiredLeader(desiredLeader)
                              .whenComplete(
                                  (promoteResult, e2) ->
                                      finishCoordinatedTransfer(
                                          coordinator, desiredLeader, promoteResult, startMs));
                        }
                      });
            },
            raft.getThreadContext());
  }

  private void finishCoordinatedTransfer(
      final MemberId coordinator,
      final MemberId desiredLeader,
      final LeadershipTransferResult result,
      final long startMs) {
    rebalanceMetrics.observeTransferDuration(
        result, Duration.ofMillis(System.currentTimeMillis() - startMs));
    // Undo the pause on any outcome (best-effort: on success leadership has already moved and the
    // role transition rebuilt the partition unpaused), then report the result to the coordinator.
    resumeForCoordinatedTransfer()
        .whenComplete(
            (ignored, resumeError) -> {
              if (resumeError != null) {
                // Cleanup failed: the partition may still be frozen. On a successful transfer this
                // is moot (leadership already moved and the role transition rebuilds it unpaused);
                // otherwise the pause watchdog is the safety net and steps the leader down once the
                // resume deadline passes. Log prominently so a stuck-paused partition is
                // diagnosable rather than silent.
                log.error(
                    "Failed to resume partition after leadership transfer to {} (result {}); "
                        + "relying on the pause watchdog to recover if still frozen",
                    desiredLeader,
                    result,
                    resumeError);
              }
              final var notification =
                  LeadershipTransferResultRequest.builder()
                      .withLeader(raft.getCluster().getLocalMember().memberId())
                      .withDesiredLeader(desiredLeader)
                      .withResult(result)
                      .build();
              raft.getProtocol()
                  .leadershipTransferResult(coordinator, notification)
                  .whenComplete(
                      (ack, notifyError) -> {
                        if (notifyError != null) {
                          log.debug(
                              "Failed to notify coordinator {} of transfer result {}",
                              coordinator,
                              result,
                              notifyError);
                        }
                      });
            });
  }

  private CompletableFuture<Long> pauseForCoordinatedTransfer(final Duration resumeTimeout) {
    final var control = raft.getLeadershipTransferPauseControl();
    if (control != null) {
      return control.pauseForTransfer(resumeTimeout);
    }
    // No broker control registered (e.g. Raft-only tests with no writes to freeze): capture the
    // frozen index and arm the watchdog directly on the Raft thread.
    return CompletableFuture.completedFuture(pauseForTransfer(resumeTimeout));
  }

  private CompletableFuture<Void> resumeForCoordinatedTransfer() {
    final var control = raft.getLeadershipTransferPauseControl();
    if (control != null) {
      return control.resumeFromTransfer();
    }
    resumeFromTransfer();
    return CompletableFuture.completedFuture(null);
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

  /** Commits the given configuration. */
  private CompletableFuture<Long> configure(
      final Collection<RaftMember> newMembers, final Collection<RaftMember> oldMembers) {
    raft.checkThread();

    final long term = raft.getTerm();

    final var configurationEntry =
        new ConfigurationEntry(System.currentTimeMillis(), newMembers, oldMembers);
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
    final int entryBytes = indexedEntry.size();
    raft.getCluster()
        .getReplicationTargets()
        .forEach(member -> member.recordAppendedBytes(entryBytes));
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

          // have the state machine apply the index which should do nothing but ensures it keeps
          // up to date with the latest entries, so it can handle configuration and initial
          // entries properly on fail over
          if (commitError == null) {
            if (isRunning()) {
              appendListener.onCommit(commitIndex, committedPosition);
            }
          } else {
            long index = -1L;
            if (commitError instanceof final AppendFailureException appendFailureException) {
              index = appendFailureException.getIndex();
            }
            appendListener.onCommitError(index, commitError);
            // replicating the entry will be retried on the next append request
            log.warn("Failed to replicate entry: {}", commitIndex, commitError);
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
