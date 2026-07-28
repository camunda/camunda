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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Orchestrates one accepted coordinated leadership-transfer attempt. */
final class LeadershipTransferAttempt {
  private static final Logger LOG = LoggerFactory.getLogger(LeadershipTransferAttempt.class);
  private final RaftContext raft;
  private final LeaderRole leader;

  LeadershipTransferAttempt(final RaftContext raft, final LeaderRole leader) {
    this.raft = raft;
    this.leader = leader;
  }

  void start(final LeadershipTransferInitiateRequest request) {
    raft.checkThread();
    final long startMs = System.currentTimeMillis();
    final var desiredLeader = request.desiredLeader();
    final var coordinator = request.coordinator();
    // Belongs to this attempt only: a later request rejected while this one runs cannot change it.
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
              leader
                  .awaitDesiredLeaderCaughtUp(desiredLeader, targetIndex, resumeTimeout)
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
                        // transfer, so just resume.
                        LOG.info(
                            "Desired leader {} caught up to index {}", desiredLeader, targetIndex);
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
        : LeadershipTransferResult.CANCELLED;
  }
}
