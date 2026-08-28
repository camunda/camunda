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

import io.atomix.cluster.MemberId;
import io.atomix.raft.LeadershipTransferResult;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.roles.LeaderRole;
import io.atomix.utils.concurrent.Scheduled;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Waits until the desired leader's {@code matchIndex} reaches the frozen log head (with updates
 * driven from append responses), or {@code deadlineMs} passes.
 *
 * <p>The future returned by {@link #start()} completes with an empty {@link Optional} once the
 * desired leader is fully caught up (proceed to promotion), or with a terminal reason if there was
 * a failure/timeout.
 */
@NullMarked
final class CatchUpWait implements TransferPhase {
  private static final Logger LOG = LoggerFactory.getLogger(CatchUpWait.class);

  private final RaftContext raft;
  private final LeaderRole leader;
  private final MemberId desiredLeader;
  private final long targetIndex;
  private final long deadlineMs;
  private final CompletableFuture<Optional<LeadershipTransferResult>> result =
      new CompletableFuture<>();
  private @Nullable CompletableFuture<Void> replication;
  private @Nullable Scheduled deadlineTimer;
  private boolean completed;

  CatchUpWait(
      final RaftContext raft,
      final LeaderRole leader,
      final MemberId desiredLeader,
      final long targetIndex,
      final long deadlineMs) {
    this.raft = raft;
    this.leader = leader;
    this.desiredLeader = desiredLeader;
    this.targetIndex = targetIndex;
    this.deadlineMs = deadlineMs;
  }

  CompletableFuture<Optional<LeadershipTransferResult>> start() {
    raft.checkThread();

    if (!leader.isRunning()) {
      failWith(LeadershipTransferResult.LEADER_CHANGED);
      return result;
    }
    if (raft.getCluster().getMemberContext(desiredLeader) == null) {
      failWith(LeadershipTransferResult.NOT_MEMBER);
      return result;
    }
    if (System.currentTimeMillis() >= deadlineMs) {
      failWith(LeadershipTransferResult.REPLICATION_TIMED_OUT);
      return result;
    }

    replication = leader.awaitReplication(desiredLeader, targetIndex);
    replication.whenComplete(
        (ignored, error) -> {
          raft.checkThread();
          if (completed) {
            return;
          }
          if (error == null) {
            succeed();
          } else {
            LOG.debug(
                "Replication to the desired leader {} ended before it reached index {}; reporting "
                    + "LEADER_CHANGED",
                desiredLeader,
                targetIndex,
                error);
            failWith(LeadershipTransferResult.LEADER_CHANGED);
          }
        });

    if (!completed) {
      final var remaining = Duration.ofMillis(deadlineMs - System.currentTimeMillis());
      deadlineTimer = raft.getThreadContext().schedule(remaining, this::onDeadline);
    }
    return result;
  }

  @Override
  public void onLeaderStopped() {
    if (completed) {
      return;
    }
    LOG.debug("Lost leadership while catching up the desired leader; reporting LEADER_CHANGED");
    failWith(LeadershipTransferResult.LEADER_CHANGED);
  }

  /** The freeze ended, so the desired leader can no longer catch up to a frozen log head. */
  @Override
  public void onPauseCleared() {
    failWith(LeadershipTransferResult.PAUSE_FAILED);
  }

  private void onDeadline() {
    if (raft.getCluster().getMemberContext(desiredLeader) == null) {
      LOG.warn("Desired leader {} left the partition while catching up", desiredLeader);
      failWith(LeadershipTransferResult.NOT_MEMBER);
    } else {
      LOG.warn(
          "Desired leader {} did not reach index {} in time; reporting REPLICATION_TIMED_OUT",
          desiredLeader,
          targetIndex);
      failWith(LeadershipTransferResult.REPLICATION_TIMED_OUT);
    }
  }

  /** The desired leader reached the frozen log head, so the transfer can carry on. */
  private void succeed() {
    complete(Optional.empty());
  }

  /** The desired leader will not catch up, so the transfer ends with {@code reason}. */
  private void failWith(final LeadershipTransferResult reason) {
    complete(Optional.of(reason));
  }

  private void complete(final Optional<LeadershipTransferResult> failureReason) {
    if (completed) {
      return;
    }
    completed = true;

    if (deadlineTimer != null) {
      deadlineTimer.cancel();
      deadlineTimer = null;
    }
    if (replication != null) {
      replication.cancel(false);
      replication = null;
    }

    result.complete(failureReason);
  }
}
