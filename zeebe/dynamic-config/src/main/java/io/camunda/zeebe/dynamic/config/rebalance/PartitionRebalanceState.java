/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.rebalance;

/**
 * Where one partition stands in a rebalance. The states are mutually exclusive, and every partition
 * of a finished rebalance is in one of the four terminal ones.
 */
public enum PartitionRebalanceState {
  /**
   * The rebalance has not reached this partition yet. In a dry run this is the plan's decision to
   * transfer, since a dry run never reaches any partition.
   */
  PENDING,
  /** The partition's leader accepted the transfer and is running it. */
  TRANSFERRING,
  /** Leadership is with the desired leader. */
  TRANSFERRED,
  /**
   * The rebalance had nothing to do for this partition: leadership was already with the desired
   * leader, or the configuration names no leader to transfer towards.
   */
  SKIPPED,
  /**
   * The rebalance wanted to move this partition's leadership and did not manage to: the leader
   * declined the request, or accepted it and left leadership where it was, or could not be asked or
   * heard back from at all. Which of those it was is in the partition's reason.
   */
  FAILED,
  /**
   * The operator cancelled the rebalance before its turn came. Unlike {@link #PENDING}, this is a
   * terminal record of what the rebalance left undone, not a partition still waiting its turn.
   */
  CANCELLED
}
