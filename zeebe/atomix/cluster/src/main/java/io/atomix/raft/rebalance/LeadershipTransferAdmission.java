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
import io.atomix.raft.RebalanceConfiguration;
import io.atomix.raft.impl.RaftContext;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.NullMarked;

/**
 * Decides whether the leader should accept a coordinated leadership transfer. The rules read the
 * leader's own conditions - a transfer already running, a leader still initializing, a
 * configuration change in flight - through suppliers, so the admission rules live in one place
 * without owning any leader state.
 */
@NullMarked
final class LeadershipTransferAdmission {
  private final RaftContext raft;
  private final BooleanSupplier transferInProgress;
  private final BooleanSupplier initializing;
  private final BooleanSupplier configurationChanging;

  LeadershipTransferAdmission(
      final RaftContext raft,
      final BooleanSupplier transferInProgress,
      final BooleanSupplier initializing,
      final BooleanSupplier configurationChanging) {
    this.raft = raft;
    this.transferInProgress = transferInProgress;
    this.initializing = initializing;
    this.configurationChanging = configurationChanging;
  }

  /**
   * Evaluates whether we should accept a leadership transfer for {@code desiredLeader} on behalf of
   * {@code coordinator}. Returns the skip reason if any check fails, or empty if the transfer may
   * proceed.
   *
   * @param desiredLeader the desired leader
   * @param coordinator the node that requested the transfer
   * @param coordinatorConfigVersion the version of the committed cluster configuration the
   *     coordinator based its request on
   * @param configuration the settings this transfer is bounded by
   */
  Optional<LeadershipTransferResult> precheck(
      final MemberId desiredLeader,
      final MemberId coordinator,
      final long coordinatorConfigVersion,
      final RebalanceConfiguration configuration) {
    raft.checkThread();
    final var localMember = raft.getCluster().getLocalMember().memberId();

    if (desiredLeader.equals(localMember)) {
      return Optional.of(LeadershipTransferResult.ALREADY_LEADER);
    }
    if (transferInProgress.getAsBoolean()) {
      return Optional.of(LeadershipTransferResult.TRANSFER_IN_PROGRESS);
    }
    final var coordinatorRejection =
        raft.getLeadershipTransferCoordinatorCheck()
            .validate(coordinator, coordinatorConfigVersion);
    if (coordinatorRejection.isPresent()) {
      return coordinatorRejection;
    }
    // Until this term's initial entry is committed the pause refuses to freeze the log head, so
    // accepting here would freeze the partition only to roll it back again.
    if (initializing.getAsBoolean()) {
      return Optional.of(LeadershipTransferResult.LEADER_INITIALIZING);
    }
    // A configuration entry would move the frozen log head the desired leader has to catch up to,
    // and can drop the desired leader from the replica set altogether, so a transfer cannot start
    // while one is in flight. We also check this again once paused.
    if (configurationChanging.getAsBoolean()) {
      return Optional.of(LeadershipTransferResult.CONFIGURATION_CHANGE_IN_PROGRESS);
    }
    final var desiredContext = raft.getCluster().getMemberContext(desiredLeader);
    if (desiredContext == null || !raft.getCluster().isMember(desiredLeader)) {
      return Optional.of(LeadershipTransferResult.NOT_MEMBER);
    }
    if (!desiredContext.hasAckedAppend()) {
      return Optional.of(LeadershipTransferResult.NOT_REPLICATING);
    }
    // We tolerate missed appends here - only silence beyond an election timeout counts as out of
    // contact (that being the earliest point at which the desired leader may start campaigning
    // against us anyway).
    final var silenceMs = System.currentTimeMillis() - desiredContext.getResponseTime();
    if (silenceMs > raft.getElectionTimeout().toMillis()) {
      return Optional.of(LeadershipTransferResult.UNREACHABLE);
    }
    // Point-in-time lag sample: the threshold should be tuned so a passing desired leader reliably
    // catches up within replicationTimeout once the pause begins.
    if (desiredContext.getReplicationLagBytes() > configuration.replicationLagThreshold()) {
      return Optional.of(LeadershipTransferResult.LAG_TOO_HIGH);
    }
    return Optional.empty();
  }
}
