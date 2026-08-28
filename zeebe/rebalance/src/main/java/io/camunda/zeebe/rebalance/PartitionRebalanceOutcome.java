/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

/**
 * The terminal outcome for a single partition within a rebalance.
 *
 * <p>Mirrors {@link io.atomix.raft.LeadershipTransferResult} so that a partition's outcome
 * preserves exactly what the leader reported. The only additions are outcomes the rebalance
 * coordinator itself produces: {@link #NO_LEADER}, {@link #NO_RESPONSE}, {@link #CANCELLED} and
 * {@link #PHYSICAL_TENANT_DISABLED}.
 */
public enum PartitionRebalanceOutcome {
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
  /** The leader was already running another transfer. */
  TRANSFER_IN_PROGRESS,
  /** The desired leader's replication lag was above the configured threshold. */
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
  /** The desired leader did not finish replicating within the configured timeout. */
  REPLICATION_TIMED_OUT,
  /** TimeoutNow did not move leadership within the configured number of attempts. */
  TIMEOUT_NOW_EXHAUSTED,
  /** Leadership changed to a member other than the desired leader during the transfer. */
  LEADER_CHANGED,
  /** The partition had no leader when the rebalance reached it. */
  NO_LEADER,
  /** The partition's leader could not be reached to ask for the transfer. */
  NO_RESPONSE,
  /** The rebalance was cancelled before this partition's transfer completed. */
  CANCELLED,
  /** The partition's physical tenant was disabled after the rebalance was planned. */
  PHYSICAL_TENANT_DISABLED
}
