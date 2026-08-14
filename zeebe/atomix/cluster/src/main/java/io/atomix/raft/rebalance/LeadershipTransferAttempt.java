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
package io.atomix.raft.rebalance;

import com.google.common.base.Throwables;
import io.atomix.cluster.MemberId;
import io.atomix.raft.LeadershipTransferResult;
import io.atomix.raft.RebalanceConfiguration;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.protocol.LeadershipTransferInitiateRequest;
import io.atomix.raft.protocol.LeadershipTransferResultRequest;
import io.atomix.raft.roles.LeaderRole;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One accepted coordinated leadership transfer, sequenced from freezing the partition to reporting
 * the result to the coordinator: pause, wait for the desired leader to catch up, promote it, then
 * resume and report. A fresh attempt is created per accepted transfer, so no state can leak from
 * one transfer into the next.
 *
 * <p>Each step runs as a {@link TransferPhase}; role and pause events are forwarded to the phase
 * currently in flight, and every terminal outcome converges on {@link #finish}.
 *
 * <pre>
 *   start()
 *     |
 *     v
 *   FREEZE WRITES (broker) ---------.
 *     |                             |
 *     |                             v
 *     |                           PAUSE_FAILED
 *     |
 *     | writes frozen
 *     v
 *   ARM RAFT PAUSE -----------------.
 *     |                             |
 *     |                             v
 *     |                           PAUSE_FAILED
 *     |                           CONFIGURATION_CHANGE_IN_PROGRESS
 *     |
 *     | frozen at the target index
 *     v
 *   CATCH UP (CatchUpWait) ---------.
 *     |                             |
 *     |                             v
 *     |                           LEADER_CHANGED         (lost leadership)
 *     |                           PAUSE_FAILED           (freeze ended early)
 *     |                           NOT_MEMBER             (left the cluster)
 *     |                           REPLICATION_TIMED_OUT
 *     |
 *     | desired leader reached the frozen log head
 *     v
 *   PROMOTE (TimeoutNowPromotion) --.
 *     |                             |
 *     |                             v
 *     |                           NOT_MEMBER             (never a member)
 *     |                           LEADER_CHANGED         (someone else won)
 *     |                           TIMEOUT_NOW_EXHAUSTED
 *     |
 *     | desired leader observed as leader
 *     v
 *   TRANSFERRED
 * </pre>
 *
 * <p>Every outcome above lands in {@link #finish}, which undoes both sides of the pause before
 * reporting to the coordinator (but only while this node is still the leader) - once leadership has
 * moved, the step-down will already have exited the pause. {@link #onPauseDeadlineExpired} leaves
 * that to the step-down for the same reason.
 */
@NullMarked
final class LeadershipTransferAttempt {
  private static final Logger LOG = LoggerFactory.getLogger(LeadershipTransferAttempt.class);

  private final RaftContext raft;
  private final LeaderRole leader;
  private final MemberId desiredLeader;
  private final MemberId coordinator;
  private final long correlationId;
  private final RebalanceConfiguration configuration;
  private final Runnable finishListener;
  private long startMs;
  private boolean finished;
  private @Nullable TransferPhase activePhase;

  LeadershipTransferAttempt(
      final RaftContext raft,
      final LeaderRole leader,
      final LeadershipTransferInitiateRequest request,
      final RebalanceConfiguration configuration,
      final Runnable finishListener) {
    this.raft = raft;
    this.leader = leader;
    desiredLeader = request.desiredLeader();
    coordinator = request.coordinator();
    correlationId = request.correlationId();
    this.configuration = configuration;
    this.finishListener = finishListener;
  }

  void start() {
    raft.checkThread();
    startMs = System.currentTimeMillis();
    raft.getLeadershipTransferWriteBarrier()
        .freeze(configuration.replicationTimeout())
        .whenCompleteAsync(
            (writesFrozenSinceMs, error) -> {
              if (error != null) {
                final var result = pauseFailureResult(error);
                LOG.warn(
                    "Failed to freeze partition writes for transfer to {}, reporting {}",
                    desiredLeader,
                    result,
                    error);
                finish(result);
                return;
              }
              armRaftPause(writesFrozenSinceMs);
            },
            raft.getThreadContext());
  }

  /**
   * Arms the Raft-side pause - the frozen catch-up target and the step-down watchdog - once the
   * broker's writes are frozen, then starts the catch-up wait.
   */
  private void armRaftPause(final long writesFrozenSinceMs) {
    final long targetIndex;
    try {
      targetIndex = leader.pauseForTransfer(pauseBudget(), writesFrozenSinceMs);
    } catch (final Exception e) {
      final var result = pauseFailureResult(e);
      LOG.warn(
          "Failed to pause partition for transfer to {}, reporting {}", desiredLeader, result, e);
      finish(result);
      return;
    }
    catchUp(targetIndex);
  }

  void onLeaderStopped() {
    if (activePhase != null) {
      activePhase.onLeaderStopped();
    }
  }

  /** The freeze ended. */
  void onPauseCleared() {
    if (activePhase != null) {
      activePhase.onPauseCleared();
    }
  }

  /**
   * The leader's freeze watchdog fired. Every step should be bounded well inside the pause budget,
   * so this condition indicates unexpected behavior. Report it and leave recovery to the watchdog
   * itself.
   */
  void onPauseDeadlineExpired() {
    if (finished) {
      return;
    }
    LOG.error(
        "Leadership transfer to {} did not finish within its pause budget of {}; abandoning it and "
            + "leaving recovery to the step-down",
        desiredLeader,
        pauseBudget());
    abandon(LeadershipTransferResult.PAUSE_FAILED);
  }

  private void catchUp(final long targetIndex) {
    final var catchUpWait =
        new CatchUpWait(
            raft,
            leader::isRunning,
            desiredLeader,
            targetIndex,
            startMs + configuration.replicationTimeout().toMillis());
    activePhase = catchUpWait;
    catchUpWait
        .start()
        .whenComplete(
            (failureReason, ignored) -> {
              activePhase = null;
              failureReason.ifPresentOrElse(this::finish, this::promote);
            });
  }

  private void promote() {
    final var promotion =
        new TimeoutNowPromotion(
            raft, leader::isRunning, desiredLeader, configuration.maxTransferAttempts());
    activePhase = promotion;
    promotion
        .start()
        .whenComplete(
            (result, ignored) -> {
              activePhase = null;
              finish(result);
            });
  }

  private void finish(final LeadershipTransferResult result) {
    raft.checkThread();
    if (finished) {
      return;
    }
    finished = true;
    finishListener.run();
    if (!leader.isRunning()) {
      // the step-down that ended this leader already lifted the freeze
      reportResult(result);
      return;
    }
    resume().whenComplete((ignored, resumeError) -> reportResult(result));
  }

  private void abandon(final LeadershipTransferResult result) {
    finished = true;
    finishListener.run();
    reportResult(result);
  }

  /** How long the partition may stay frozen before the watchdog treats the transfer as stuck. */
  private Duration pauseBudget() {
    return configuration.pauseBudget(raft.getHeartbeatInterval());
  }

  private void reportResult(final LeadershipTransferResult result) {
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
  }

  /**
   * Undoes the pause in reverse order on the terminal paths this leader survives: clear the
   * Raft-side pause first, then reopen the broker's writes. Both sides tolerate a pause that failed
   * partway. A resume that fails steps the leader down where it is detected, so the partition is
   * rebuilt rather than left frozen.
   */
  private CompletableFuture<Void> resume() {
    leader.resumeFromTransfer();
    return raft.getLeadershipTransferWriteBarrier()
        .unfreeze()
        .whenComplete(
            (ignored, error) -> {
              if (error != null) {
                LOG.error(
                    "Failed to resume partition after leadership transfer to {}; relying on the "
                        + "pause watchdog to recover if still frozen",
                    desiredLeader,
                    error);
              }
            });
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
