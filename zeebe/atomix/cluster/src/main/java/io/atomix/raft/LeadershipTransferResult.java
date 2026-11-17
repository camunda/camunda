/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft;

/**
 * The terminal outcome of a per-partition coordinated leadership transfer, reported by the current
 * leader to the rebalancing coordinator. Also used as the {@code result} label on the
 * transfer-duration metric.
 */
public enum LeadershipTransferResult {
  /** The desired leader was promoted and took over leadership. */
  TRANSFERRED,
  /** The desired leader is already the leader; nothing to do. */
  ALREADY_LEADER,
  /** The desired leader is not a member of the partition. */
  NOT_MEMBER,
  /**
   * The desired leader has not acknowledged an append in this term, so its log has not converged.
   */
  NOT_REPLICATING,
  /** The desired leader is out of contact with this leader. */
  UNREACHABLE,
  /**
   * The request did not come from the current coordinator, the lowest-id member of the leader's
   * committed configuration.
   */
  NOT_COORDINATOR,
  /** The request carried a cluster configuration version other than the leader's current one. */
  STALE_CONFIGURATION,
  /** This leader is already running a transfer. */
  TRANSFER_IN_PROGRESS,
  /** The desired leader's replication lag is above the configured threshold. */
  LAG_TOO_HIGH,
  /** This leader has not committed the initial entry of its term yet. */
  LEADER_INITIALIZING,
  /**
   * A Raft configuration change is in progress, so the log head the desired leader would catch up
   * to cannot be frozen.
   */
  CONFIGURATION_CHANGE_IN_PROGRESS,
  /** The leader could not freeze the partition for the transfer. */
  PAUSE_FAILED,
  /** The desired leader did not finish replicating within {@code replicationTimeout}. */
  REPLICATION_TIMED_OUT,
  /** TimeoutNow did not move leadership within {@code maxTransferAttempts}. */
  TIMEOUT_NOW_EXHAUSTED,
  /**
   * Leadership changed (this node stepped down or another node was elected) during the transfer.
   */
  LEADER_CHANGED
}
