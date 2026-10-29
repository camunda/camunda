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

import io.atomix.raft.LeadershipTransferResult;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.protocol.LeadershipTransferInitiateRequest;
import io.atomix.raft.protocol.LeadershipTransferInitiateResponse;
import io.atomix.raft.protocol.LeadershipTransferResultRequest;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.raft.roles.LeaderRole;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The single entry point for coordinated leadership transfers on the leader: decides whether to
 * accept a transfer and runs the accepted ones, one at a time. Shares the role's lifecycle rather
 * than being per-transfer: a leader that stays leader after a transfer fails can run another. Each
 * accepted transfer runs as its own one-shot {@link LeadershipTransferAttempt}, so no state
 * survives from one transfer into the next.
 */
@NullMarked
public final class LeadershipTransferRunner {
  private static final Logger LOG = LoggerFactory.getLogger(LeadershipTransferRunner.class);
  private final RaftContext raft;
  private final LeaderRole leader;
  private final LeadershipTransferAdmission admission;
  // Set as soon as a transfer is accepted, before the pause is entered; cleared when the attempt
  // finishes.
  private volatile @Nullable LeadershipTransferAttempt currentAttempt;

  public LeadershipTransferRunner(
      final RaftContext raft,
      final LeaderRole leader,
      final BooleanSupplier pausedForTransfer,
      final BooleanSupplier initializing,
      final BooleanSupplier configurationChanging) {
    this.raft = raft;
    this.leader = leader;
    admission =
        new LeadershipTransferAdmission(
            raft,
            () -> isInProgress() || pausedForTransfer.getAsBoolean(),
            initializing,
            configurationChanging);
  }

  /** Whether a transfer is still running, i.e. the accepted attempt has not finished. */
  boolean isInProgress() {
    return currentAttempt != null;
  }

  /**
   * Accepts or rejects an initiate request. A rejection carries its reason back in the response; an
   * accepted request starts a one-shot attempt, whose outcome reaches the coordinator separately
   * once the transfer finishes.
   */
  public LeadershipTransferInitiateResponse handleInitiate(
      final LeadershipTransferInitiateRequest request) {
    raft.checkThread();
    final var configuration = request.effectiveConfiguration(raft.getRebalanceConfiguration());
    final var rejectionReason =
        admission.precheck(
            request.desiredLeader(),
            request.coordinator(),
            request.coordinatorConfigVersion(),
            configuration);
    if (rejectionReason.isPresent()) {
      return LeadershipTransferInitiateResponse.builder()
          .withStatus(Status.OK)
          .withRejectionReason(rejectionReason.get())
          .build();
    }
    final var attempt =
        new LeadershipTransferAttempt(
            raft,
            leader,
            request.desiredLeader(),
            configuration,
            () -> currentAttempt = null,
            reportingTo(raft, request));
    currentAttempt = attempt;
    attempt.start();
    return LeadershipTransferInitiateResponse.builder().withStatus(Status.OK).build();
  }

  public void onLeaderStopped() {
    raft.checkThread();
    final var attempt = currentAttempt;
    if (attempt != null) {
      attempt.onLeaderStopped();
    }
  }

  /** The freeze ended. */
  public void onPauseCleared() {
    raft.checkThread();
    final var attempt = currentAttempt;
    if (attempt != null) {
      attempt.onPauseCleared();
    }
  }

  /** The leader's freeze watchdog fired: the pause outlived its resume deadline. */
  public void onPauseDeadlineExpired() {
    raft.checkThread();
    final var attempt = currentAttempt;
    if (attempt != null) {
      attempt.onPauseDeadlineExpired();
    }
  }

  /** Reports the outcome of a transfer to the coordinator that requested it. */
  static Consumer<LeadershipTransferResult> reportingTo(
      final RaftContext raft, final LeadershipTransferInitiateRequest request) {
    final var coordinator = request.coordinator();
    return result -> {
      final var notification =
          LeadershipTransferResultRequest.builder()
              .withLeader(raft.getCluster().getLocalMember().memberId())
              .withDesiredLeader(request.desiredLeader())
              .withResult(result)
              .withCorrelationId(request.correlationId())
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
    };
  }
}
