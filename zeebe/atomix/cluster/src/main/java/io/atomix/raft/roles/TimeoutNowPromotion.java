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
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.protocol.TimeoutNowRequest;
import io.atomix.utils.concurrent.Scheduled;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Promotes the desired leader by sending it TimeoutNow, resending every {@code heartbeatInterval}
 * until leadership actually moves or {@code maxTransferAttempts} sends are spent. Completes with
 * {@link LeadershipTransferResult#TRANSFERRED} only once the <em>selected</em> target is observed
 * to have become leader; if a different node wins instead, or leadership otherwise moves away, we
 * report {@link LeadershipTransferResult#LEADER_CHANGED}. If the leadership doesn't move before our
 * attempt budget runs out, we report {@link LeadershipTransferResult#TIMEOUT_NOW_EXHAUSTED}.
 */
@NullMarked
final class TimeoutNowPromotion implements TransferPhase {
  private static final Logger LOG = LoggerFactory.getLogger(TimeoutNowPromotion.class);

  private final RaftContext raft;
  private final BooleanSupplier leaderRunning;
  private final MemberId target;
  private final CompletableFuture<LeadershipTransferResult> result = new CompletableFuture<>();
  private @Nullable Scheduled retryTimer;
  private @Nullable Consumer<RaftMember> leaderListener;
  private int attempts;
  private boolean steppedDown;

  TimeoutNowPromotion(
      final RaftContext raft, final BooleanSupplier leaderRunning, final MemberId target) {
    this.raft = raft;
    this.leaderRunning = leaderRunning;
    this.target = target;
  }

  CompletableFuture<LeadershipTransferResult> start() {
    raft.checkThread();
    if (!raft.getCluster().isMember(target)) {
      result.complete(LeadershipTransferResult.NOT_MEMBER);
      return result;
    }

    leaderListener = this::onLeaderObserved;
    raft.addLeaderElectionListener(leaderListener);

    LOG.info(
        "Starting TimeoutNow leadership transfer to {} (up to {} attempts, resending every {})",
        target,
        raft.getRebalanceMaxTransferAttempts(),
        raft.getHeartbeatInterval());

    attemptTimeoutNow();
    return result;
  }

  @Override
  public void onLeaderStopped() {
    if (result.isDone()) {
      return;
    }
    LOG.debug("Stepping down during TimeoutNow transfer to {}; awaiting the new leader", target);
    steppedDown = true;
    if (retryTimer != null) {
      retryTimer.cancel();
      retryTimer = null;
    }
  }

  private void attemptTimeoutNow() {
    raft.checkThread();
    if (result.isDone() || !leaderRunning.getAsBoolean()) {
      return;
    }
    if (attempts >= raft.getRebalanceMaxTransferAttempts()) {
      LOG.info(
          "TimeoutNow transfer to {} did not move leadership within {} attempts while still "
              + "leader; giving up",
          target,
          attempts);
      complete(LeadershipTransferResult.TIMEOUT_NOW_EXHAUSTED);
      return;
    }
    sendTimeoutNow();
    retryTimer =
        raft.getThreadContext().schedule(raft.getHeartbeatInterval(), this::attemptTimeoutNow);
  }

  private void sendTimeoutNow() {
    raft.checkThread();
    attempts++;
    final var request =
        TimeoutNowRequest.builder()
            .withTerm(raft.getTerm())
            .withLeader(raft.getCluster().getLocalMember().memberId())
            .build();
    LOG.debug("Sending TimeoutNow to {} (attempt {})", target, attempts);
    raft.getProtocol()
        .timeoutNow(target, request)
        .whenCompleteAsync(
            (response, error) -> {
              if (error != null) {
                LOG.trace("TimeoutNow to {} failed, will retry if budget remains", target, error);
              } else {
                LOG.trace("TimeoutNow to {} acknowledged: {}", target, response);
              }
            },
            raft.getThreadContext());
  }

  private void onLeaderObserved(final RaftMember newLeader) {
    raft.checkThread();
    if (result.isDone()) {
      return;
    }
    final var localMember = raft.getCluster().getLocalMember().memberId();
    if (newLeader.memberId().equals(localMember)) {
      if (steppedDown) {
        complete(LeadershipTransferResult.LEADER_CHANGED);
      }
      return;
    }
    complete(
        newLeader.memberId().equals(target)
            ? LeadershipTransferResult.TRANSFERRED
            : LeadershipTransferResult.LEADER_CHANGED);
  }

  private void complete(final LeadershipTransferResult outcome) {
    if (retryTimer != null) {
      retryTimer.cancel();
      retryTimer = null;
    }
    if (leaderListener != null) {
      raft.removeLeaderElectionListener(leaderListener);
      leaderListener = null;
    }
    result.complete(outcome);
  }
}
