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
  /** The desired leader is offline or not a member of the partition. */
  OFFLINE,
  /**
   * The request did not come from the current coordinator (the lowest-id member of the leader's
   * committed configuration), or carried a stale configuration version.
   */
  INVALID_COORDINATOR,
  /** This leader is already running a transfer. */
  TRANSFER_IN_PROGRESS,
  /** The desired leader's replication lag is above the configured threshold. */
  LAG_TOO_HIGH,
  /** The desired leader did not finish replicating within {@code replicationTimeout}. */
  REPLICATION_TIMED_OUT,
  /** TimeoutNow did not move leadership within {@code maxTransferAttempts}. */
  TRANSFER_FAILED,
  /**
   * Leadership changed (this node stepped down or another node was elected) during the transfer.
   */
  LEADER_CHANGED
}
