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

import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.protocol.LeadershipTransferInitiateRequest;
import io.atomix.raft.protocol.LeadershipTransferInitiateResponse;
import io.atomix.raft.protocol.RaftResponse.Status;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Runs the coordinated leadership transfers the leader accepts, one at a time. Shares the role's
 * lifecycle rather than being per-transfer: a leader that stays leader after a transfer fails can
 * run another. Each accepted transfer runs as its own one-shot {@link LeadershipTransferAttempt},
 * so no state survives from one transfer into the next.
 */
@NullMarked
final class LeadershipTransferRunner {
  private final RaftContext raft;
  private final LeaderRole leader;
  // Set as soon as a transfer is accepted, before the pause is entered; cleared when the attempt
  // finishes.
  private volatile @Nullable LeadershipTransferAttempt currentAttempt;

  LeadershipTransferRunner(final RaftContext raft, final LeaderRole leader) {
    this.raft = raft;
    this.leader = leader;
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
  LeadershipTransferInitiateResponse handleInitiate(
      final LeadershipTransferInitiateRequest request) {
    raft.checkThread();
    final var rejectionReason =
        leader.precheckTransfer(
            request.desiredLeader(), request.coordinator(), request.coordinatorConfigIndex());
    if (rejectionReason.isPresent()) {
      return LeadershipTransferInitiateResponse.builder()
          .withStatus(Status.OK)
          .withRejectionReason(rejectionReason.get())
          .build();
    }
    final var attempt =
        new LeadershipTransferAttempt(raft, leader, request, () -> currentAttempt = null);
    currentAttempt = attempt;
    attempt.start();
    return LeadershipTransferInitiateResponse.builder().withStatus(Status.OK).build();
  }

  void onLeaderStopped() {
    final var attempt = currentAttempt;
    if (attempt != null) {
      attempt.onLeaderStopped();
    }
  }

  /** The freeze ended. */
  void onPauseCleared() {
    final var attempt = currentAttempt;
    if (attempt != null) {
      attempt.onPauseCleared();
    }
  }

  /** The leader's freeze watchdog fired: the pause outlived its resume deadline. */
  void onPauseDeadlineExpired() {
    final var attempt = currentAttempt;
    if (attempt != null) {
      attempt.onPauseDeadlineExpired();
    }
  }
}
