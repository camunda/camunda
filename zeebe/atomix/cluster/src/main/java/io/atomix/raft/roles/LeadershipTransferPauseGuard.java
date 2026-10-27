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

import io.atomix.raft.RaftServer;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.rebalance.LeadershipTransferRunner;
import io.atomix.raft.roles.LeaderRole.ConfigurationChangeInProgressException;
import io.atomix.utils.concurrent.Scheduled;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The Raft-side pause for a coordinated leadership transfer: while paused the leader keeps its term
 * and keeps replicating, but writes and processing are frozen. A watchdog steps the leader down to
 * follower if the pause is not resumed in time.
 *
 * <p>Owned by {@link LeaderRole} rather than by any single transfer: the watchdog is the backstop
 * for a transfer that gets stuck or fails to resume, so the guard must outlive the attempt that
 * armed it.
 */
@NullMarked
final class LeadershipTransferPauseGuard {
  private final RaftContext raft;
  private final Logger log;
  private final LeadershipTransferRunner runner;
  private final BooleanSupplier leaderRunning;
  private final BooleanSupplier initializing;
  private final BooleanSupplier configurationChanging;

  private volatile boolean paused;
  private long pauseStartMs;
  private @Nullable Scheduled watchdog;

  LeadershipTransferPauseGuard(
      final RaftContext raft,
      final Logger log,
      final LeadershipTransferRunner runner,
      final BooleanSupplier leaderRunning,
      final BooleanSupplier initializing,
      final BooleanSupplier configurationChanging) {
    this.raft = raft;
    this.log = log;
    this.runner = runner;
    this.leaderRunning = leaderRunning;
    this.initializing = initializing;
    this.configurationChanging = configurationChanging;
  }

  boolean isPaused() {
    return paused;
  }

  /**
   * Enters paused mode for a leadership transfer and sets a watchdog that steps this leader down if
   * {@link #resume()} is not called within {@code resumeTimeout}. Returns the frozen last log
   * index, i.e. the catch-up target the desired leader must reach.
   *
   * <p>The timeout is measured relative to the point at which write admission was frozen, rather
   * than the current instant.
   *
   * @param pausedSinceMs epoch millis at which write admission was frozen
   * @throws IllegalStateException if already paused, if the leader is still initializing, or if the
   *     resumption deadline was already passed before this method was invoked
   * @throws ConfigurationChangeInProgressException if a Raft configuration change is in progress
   */
  long pause(final Duration resumeTimeout, final long pausedSinceMs) {
    raft.checkThread();
    if (paused) {
      throw new IllegalStateException("Cannot pause for leadership transfer: already paused");
    }
    if (initializing.getAsBoolean()) {
      throw new IllegalStateException(
          "Cannot pause for leadership transfer: leader is still initializing");
    }
    if (configurationChanging.getAsBoolean()) {
      throw new ConfigurationChangeInProgressException(
          "Cannot pause for leadership transfer: configuration change in progress");
    }

    final long elapsedMs = Math.max(0, System.currentTimeMillis() - pausedSinceMs);
    if (elapsedMs >= resumeTimeout.toMillis()) {
      throw new IllegalStateException(
          "Cannot pause for leadership transfer: resume deadline of %s already passed"
              .formatted(resumeTimeout));
    }
    final Duration remaining = resumeTimeout.minusMillis(elapsedMs);

    paused = true;
    pauseStartMs = pausedSinceMs;

    // Writes must already have been frozen and the processor drained before this runs, so the last
    // log index is our freeze target
    final long targetIndex = raft.getLog().getLastIndex();

    log.info(
        "Set leadership-transfer watchdog; resume deadline in {} ({}ms already spent)",
        remaining,
        elapsedMs);

    watchdog = raft.getThreadContext().schedule(remaining, this::onDeadline);
    raft.getRebalanceMetrics().setPartitionPaused(true);

    return targetIndex;
  }

  /** Leaves paused mode after a coordinated leadership transfer. */
  void resume() {
    raft.checkThread();
    if (paused) {
      log.info("Resuming partition after leadership transfer");
    }
    clear();
  }

  /** Exits paused mode unconditionally, e.g. on a role transition. */
  void clear() {
    raft.checkThread();
    if (watchdog != null) {
      watchdog.cancel();
      watchdog = null;
    }
    if (paused) {
      paused = false;
      raft.getRebalanceMetrics().setPartitionPaused(false);
      raft.getRebalanceMetrics()
          .observePauseDuration(Duration.ofMillis(System.currentTimeMillis() - pauseStartMs));
    }
    runner.onPauseCleared();
  }

  private void onDeadline() {
    raft.checkThread();
    if (!paused || !leaderRunning.getAsBoolean()) {
      return;
    }
    log.error("Partition still paused after the resume deadline; stepping down to follower");
    runner.onPauseDeadlineExpired();
    clear();
    raft.transition(RaftServer.Role.FOLLOWER);
  }
}
