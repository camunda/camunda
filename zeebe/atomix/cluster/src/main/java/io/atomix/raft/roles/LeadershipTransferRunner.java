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
package io.atomix.raft.roles;

import com.google.common.base.Throwables;
import io.atomix.cluster.MemberId;
import io.atomix.raft.LeadershipTransferResult;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.protocol.LeadershipTransferInitiateRequest;
import io.atomix.raft.protocol.LeadershipTransferResultRequest;
import io.atomix.utils.concurrent.Scheduled;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the coordinated leadership transfers a {@link LeaderRole} accepts, one at a time. Shares the
 * role's lifecycle rather than being per-transfer: a leader that stays leader after a transfer
 * fails can run another.
 */
final class LeadershipTransferRunner {
  private static final Logger LOG = LoggerFactory.getLogger(LeadershipTransferRunner.class);
  private final RaftContext raft;
  private final LeaderRole leader;
  // Set as soon as the transfer is accepted, before the pause is entered
  private volatile boolean inProgress;
  private Scheduled catchUpPollTimer;
  private CompletableFuture<Optional<LeadershipTransferResult>> catchUpFuture;

  LeadershipTransferRunner(final RaftContext raft, final LeaderRole leader) {
    this.raft = raft;
    this.leader = leader;
  }

  /** Whether a transfer is still running, i.e. has not reached {@link #finish}. */
  boolean isInProgress() {
    return inProgress;
  }

  void start(final LeadershipTransferInitiateRequest request) {
    raft.checkThread();
    inProgress = true;
    final long startMs = System.currentTimeMillis();
    final var desiredLeader = request.desiredLeader();
    final var coordinator = request.coordinator();
    final var correlationId = request.correlationId();
    final var resumeTimeout = raft.getRebalanceReplicationTimeout();
    pause(resumeTimeout)
        .whenCompleteAsync(
            (targetIndex, error) -> {
              if (error != null) {
                final var result = pauseFailureResult(error);
                LOG.warn(
                    "Failed to pause partition for transfer to {}, reporting {}",
                    desiredLeader,
                    result,
                    error);
                finish(coordinator, desiredLeader, correlationId, result, startMs);
                return;
              }
              awaitDesiredLeaderCaughtUp(desiredLeader, targetIndex)
                  .whenComplete(
                      (catchUpResult, ignored) -> {
                        if (catchUpResult.isPresent()) {
                          finish(
                              coordinator,
                              desiredLeader,
                              correlationId,
                              catchUpResult.get(),
                              startMs);
                          return;
                        }
                        // The desired leader is caught up. We don't yet implement the actual
                        // transfer, so just resume. This run is over either way, so release the
                        // runner for the next transfer the leader is asked to make.
                        LOG.info(
                            "Desired leader {} caught up to index {}", desiredLeader, targetIndex);
                        inProgress = false;
                        resume();
                      });
            },
            raft.getThreadContext());
  }

  private void finish(
      final MemberId coordinator,
      final MemberId desiredLeader,
      final long correlationId,
      final LeadershipTransferResult result,
      final long startMs) {
    inProgress = false;
    raft.getRebalanceMetrics()
        .observeTransferDuration(result, Duration.ofMillis(System.currentTimeMillis() - startMs));
    resume()
        .whenComplete(
            (ignored, resumeError) -> {
              if (resumeError != null) {
                // Cleanup failed: the partition may still be frozen. The pause watchdog is the
                // safety net and steps the leader down once the resume deadline passes.
                LOG.error(
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
                      .withCorrelationId(correlationId)
                      .build();
              raft.getProtocol()
                  .leadershipTransferResult(coordinator, notification)
                  .whenComplete(
                      (ack, notifyError) -> {
                        if (notifyError != null) {
                          LOG.debug(
                              "Failed to notify coordinator {} of transfer result {}",
                              coordinator,
                              result,
                              notifyError);
                        }
                      });
            });
  }

  void onLeaderStopped() {
    if (catchUpFuture != null) {
      LOG.debug("Lost leadership while catching up the desired leader; reporting LEADER_CHANGED");
      completeCatchUp(Optional.of(LeadershipTransferResult.LEADER_CHANGED));
    }
  }

  /**
   * The freeze ended, so a run still waiting for the desired leader to catch up can no longer
   * succeed.
   */
  void onPauseCleared() {
    if (catchUpFuture != null) {
      completeCatchUp(Optional.of(LeadershipTransferResult.PAUSE_FAILED));
    } else {
      cancelCatchUp();
    }
  }

  /** The leader's freeze watchdog fired, so the desired leader is out of time to catch up. */
  void onPauseDeadlineExpired() {
    if (catchUpFuture != null) {
      completeCatchUp(Optional.of(LeadershipTransferResult.REPLICATION_TIMED_OUT));
    }
  }

  /**
   * Waits until {@code desiredLeader}'s {@code matchIndex} reaches {@code targetIndex}, polling on
   * the Raft thread each {@code heartbeatInterval}. The wait is bounded by the pause watchdog.
   *
   * <p>The returned future completes with an empty {@link Optional} once the desired leader is
   * fully caught up (proceed to promotion), or with a terminal reason if there was a
   * failure/timeout.
   */
  CompletableFuture<Optional<LeadershipTransferResult>> awaitDesiredLeaderCaughtUp(
      final MemberId desiredLeader, final long targetIndex) {
    raft.checkThread();
    catchUpFuture = new CompletableFuture<>();
    // Hold a local reference: completeCatchUp() clears the field, so we must return this.
    final var future = catchUpFuture;

    if (isCaughtUp(desiredLeader, targetIndex)) {
      completeCatchUp(Optional.empty());
      return future;
    }

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
                  if (!leader.isRunning()) {
                    completeCatchUp(Optional.of(LeadershipTransferResult.LEADER_CHANGED));
                  } else if (raft.getCluster().getMemberContext(desiredLeader) == null) {
                    completeCatchUp(Optional.of(LeadershipTransferResult.NOT_MEMBER));
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
    catchUpFuture = null;
  }

  private CompletableFuture<Long> pause(final Duration resumeTimeout) {
    final var control = raft.getLeadershipTransferPauseControl();
    if (control != null) {
      return control.pauseForTransfer(resumeTimeout);
    }
    // No broker control registered (e.g. Raft-only tests with no writes to freeze).
    return CompletableFuture.completedFuture(
        leader.pauseForTransfer(resumeTimeout, System.currentTimeMillis()));
  }

  private CompletableFuture<Void> resume() {
    final var control = raft.getLeadershipTransferPauseControl();
    if (control != null) {
      return control.resumeFromTransfer();
    }
    leader.resumeFromTransfer();
    return CompletableFuture.completedFuture(null);
  }

  /**
   * Distinguishes a pause the leader refused because a configuration change started after the
   * pre-check passed apart from one that failed with another exception.
   */
  private static LeadershipTransferResult pauseFailureResult(final Throwable error) {
    return Throwables.getCausalChain(error).stream()
            .anyMatch(LeaderRole.ConfigurationChangeInProgressException.class::isInstance)
        ? LeadershipTransferResult.CONFIGURATION_CHANGE_IN_PROGRESS
        : LeadershipTransferResult.PAUSE_FAILED;
  }
}
