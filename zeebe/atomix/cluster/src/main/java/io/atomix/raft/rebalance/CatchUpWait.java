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
import io.atomix.utils.concurrent.Scheduled;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Waits until the desired leader's {@code matchIndex} reaches the frozen log head, polling on the
 * Raft thread each heartbeat interval until it catches up or {@code deadlineMs} passes.
 *
 * <p>The future returned by {@link #start()} completes with an empty {@link Optional} once the
 * desired leader is fully caught up (proceed to promotion), or with a terminal reason if there was
 * a failure/timeout.
 */
@NullMarked
final class CatchUpWait implements TransferPhase {
  private static final Logger LOG = LoggerFactory.getLogger(CatchUpWait.class);

  private final RaftContext raft;
  private final BooleanSupplier leaderRunning;
  private final MemberId desiredLeader;
  private final long targetIndex;
  private final long deadlineMs;
  private final CompletableFuture<Optional<LeadershipTransferResult>> result =
      new CompletableFuture<>();
  private @Nullable Scheduled pollTimer;

  CatchUpWait(
      final RaftContext raft,
      final BooleanSupplier leaderRunning,
      final MemberId desiredLeader,
      final long targetIndex,
      final long deadlineMs) {
    this.raft = raft;
    this.leaderRunning = leaderRunning;
    this.desiredLeader = desiredLeader;
    this.targetIndex = targetIndex;
    this.deadlineMs = deadlineMs;
  }

  CompletableFuture<Optional<LeadershipTransferResult>> start() {
    raft.checkThread();
    if (isCaughtUp()) {
      succeed();
      return result;
    }
    final var pollInterval = raft.getHeartbeatInterval();
    pollTimer = raft.getThreadContext().schedule(pollInterval, pollInterval, this::poll);
    return result;
  }

  @Override
  public void onLeaderStopped() {
    if (result.isDone()) {
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

  private void poll() {
    if (result.isDone()) {
      return;
    }
    if (!leaderRunning.getAsBoolean()) {
      failWith(LeadershipTransferResult.LEADER_CHANGED);
    } else if (raft.getCluster().getMemberContext(desiredLeader) == null) {
      failWith(LeadershipTransferResult.NOT_MEMBER);
    } else if (isCaughtUp()) {
      succeed();
    } else if (System.currentTimeMillis() >= deadlineMs) {
      LOG.warn(
          "Desired leader {} did not reach index {} in time; reporting REPLICATION_TIMED_OUT",
          desiredLeader,
          targetIndex);
      failWith(LeadershipTransferResult.REPLICATION_TIMED_OUT);
    }
  }

  private boolean isCaughtUp() {
    final var context = raft.getCluster().getMemberContext(desiredLeader);
    return context != null && context.getMatchIndex() >= targetIndex;
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
    if (pollTimer != null) {
      pollTimer.cancel();
      pollTimer = null;
    }
    result.complete(failureReason);
  }
}
