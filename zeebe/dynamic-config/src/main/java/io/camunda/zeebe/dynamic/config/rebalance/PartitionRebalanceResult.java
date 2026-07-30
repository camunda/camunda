/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.rebalance;

/**
 * Why a rebalance ended up where it did with one partition, in the cases the partition's leader
 * never gave a reason for. Where it did, its {@code LeadershipTransferResult} is the reason
 * instead: between the two, every partition of a rebalance has one.
 *
 * <p>Bounded and coarse on purpose. It is what a rebalance is accounted for by, so an operator
 * asking why leadership did not move needs to be able to count the answers rather than read them.
 * The fuller account of a partition stays in its free-text reason on the rebalance status.
 */
public enum PartitionRebalanceResult {
  /** Leadership was already with the desired leader, so the rebalance left the partition alone. */
  ALREADY_BALANCED,
  /** No member of the cluster configuration is eligible to lead it, so there is nowhere to move. */
  NO_DESIRED_LEADER,
  /** There is somewhere to move leadership to, but the topology shows nobody holding it. */
  NO_LEADER,
  /** The request to transfer never reached the partition's leader. */
  LEADER_UNREACHABLE,
  /** The partition's leader answered the request with a Raft-level error rather than a reason. */
  LEADER_ERROR,
  /**
   * The leader took the transfer on and then neither reported an outcome nor gave up leadership.
   */
  LEADER_SILENT,
  /** The operator cancelled the rebalance before this partition's transfer began. */
  CANCELLED
}
