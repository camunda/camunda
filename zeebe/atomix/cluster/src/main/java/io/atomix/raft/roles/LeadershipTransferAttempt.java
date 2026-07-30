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
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One accepted coordinated leadership transfer, sequenced from freezing the partition to reporting
 * the outcome: pause, wait for the desired leader to catch up, then resume. A fresh attempt is
 * created per accepted transfer, so no state can leak from one transfer into the next.
 *
 * <p>Role and pause events are forwarded to the step currently in flight. Failures converge on
 * {@link #finish}, which reports them to the coordinator; the transfer is not executed yet, so a
 * desired leader that catches up simply ends the attempt.
 */
@NullMarked
final class LeadershipTransferAttempt {
  private static final Logger LOG = LoggerFactory.getLogger(LeadershipTransferAttempt.class);

  private final RaftContext raft;
  private final LeaderRole leader;
  private final MemberId desiredLeader;
  private final MemberId coordinator;
  private final long correlationId;
  private final Runnable finishListener;
  private long startMs;
  private @Nullable CatchUpWait activeCatchUp;

  LeadershipTransferAttempt(
      final RaftContext raft,
      final LeaderRole leader,
      final LeadershipTransferInitiateRequest request,
      final Runnable finishListener) {
    this.raft = raft;
    this.leader = leader;
    desiredLeader = request.desiredLeader();
    coordinator = request.coordinator();
    correlationId = request.correlationId();
    this.finishListener = finishListener;
  }

  void start() {
    raft.checkThread();
    startMs = System.currentTimeMillis();
    pause(raft.getRebalanceReplicationTimeout())
        .whenCompleteAsync(
            (targetIndex, error) -> {
              if (error != null) {
                final var result = pauseFailureResult(error);
                LOG.warn(
                    "Failed to pause partition for transfer to {}, reporting {}",
                    desiredLeader,
                    result,
                    error);
                finish(result);
                return;
              }
              catchUp(targetIndex);
            },
            raft.getThreadContext());
  }

  void onLeaderStopped() {
    if (activeCatchUp != null) {
      activeCatchUp.onLeaderStopped();
    }
  }

  /** The freeze ended. */
  void onPauseCleared() {
    if (activeCatchUp != null) {
      activeCatchUp.onPauseCleared();
    }
  }

  /** The leader's freeze watchdog fired: the pause outlived its resume deadline. */
  void onPauseDeadlineExpired() {
    if (activeCatchUp != null) {
      activeCatchUp.onPauseDeadlineExpired();
    }
  }

  private void catchUp(final long targetIndex) {
    final var catchUpWait = new CatchUpWait(raft, leader::isRunning, desiredLeader, targetIndex);
    activeCatchUp = catchUpWait;
    catchUpWait
        .start()
        .whenComplete(
            (failureReason, ignored) -> {
              activeCatchUp = null;
              failureReason.ifPresentOrElse(this::finish, () -> onCaughtUp(targetIndex));
            });
  }

  /**
   * The desired leader is caught up, which is as far as a transfer gets until the promotion step
   * exists: the attempt is measured as a success and the partition resumed, but there is no
   * leadership change to report to the coordinator yet.
   */
  private void onCaughtUp(final long targetIndex) {
    LOG.info("Desired leader {} caught up to index {}", desiredLeader, targetIndex);
    finishListener.run();
    observeDuration(LeadershipTransferResult.TRANSFERRED);
    resume();
  }

  private void finish(final LeadershipTransferResult result) {
    finishListener.run();
    observeDuration(result);
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

  private void observeDuration(final LeadershipTransferResult result) {
    raft.getRebalanceMetrics()
        .observeTransferDuration(result, Duration.ofMillis(System.currentTimeMillis() - startMs));
  }

  private CompletableFuture<Long> pause(final Duration resumeTimeout) {
    final var control = raft.getLeadershipTransferPauseControl();
    if (control != null) {
      return control.pauseForTransfer(resumeTimeout);
    }
    // e.g. Raft-only tests, where there are no writes to freeze
    LOG.debug("No broker pause control registered, pausing the Raft side only");
    return CompletableFuture.completedFuture(
        leader.pauseForTransfer(resumeTimeout, System.currentTimeMillis()));
  }

  private CompletableFuture<Void> resume() {
    final var control = raft.getLeadershipTransferPauseControl();
    if (control != null) {
      return control.resumeFromTransfer();
    }
    // e.g. Raft-only tests, where there are no writes to reopen
    LOG.debug("No broker pause control registered, resuming the Raft side only");
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
