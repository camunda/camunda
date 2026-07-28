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

import io.atomix.cluster.MemberId;
import io.atomix.raft.LeadershipTransferResult;
import io.atomix.raft.impl.RaftContext;
import io.atomix.utils.concurrent.Scheduled;
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
  private Scheduled catchUpPollTimer;
  private CompletableFuture<Optional<LeadershipTransferResult>> catchUpFuture;

  LeadershipTransferRunner(final RaftContext raft, final LeaderRole leader) {
    this.raft = raft;
    this.leader = leader;
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
}
