/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import io.atomix.raft.LeadershipTransferResult;

/**
 * Maps {@link LeadershipTransferResult} onto {@link PartitionRebalanceOutcome} one case at a time
 * rather than by name, so that we can verify the switch is exhaustive.
 */
final class LeadershipTransferResultMapping {

  private LeadershipTransferResultMapping() {}

  static PartitionRebalanceOutcome toOutcome(final LeadershipTransferResult result) {
    return switch (result) {
      case TRANSFERRED -> PartitionRebalanceOutcome.TRANSFERRED;
      case ALREADY_LEADER -> PartitionRebalanceOutcome.ALREADY_LEADER;
      case NOT_MEMBER -> PartitionRebalanceOutcome.NOT_MEMBER;
      case NOT_REPLICATING -> PartitionRebalanceOutcome.NOT_REPLICATING;
      case UNREACHABLE -> PartitionRebalanceOutcome.UNREACHABLE;
      case NOT_COORDINATOR -> PartitionRebalanceOutcome.NOT_COORDINATOR;
      case STALE_CONFIGURATION -> PartitionRebalanceOutcome.STALE_CONFIGURATION;
      case TRANSFER_IN_PROGRESS -> PartitionRebalanceOutcome.TRANSFER_IN_PROGRESS;
      case LAG_TOO_HIGH -> PartitionRebalanceOutcome.LAG_TOO_HIGH;
      case LEADER_INITIALIZING -> PartitionRebalanceOutcome.LEADER_INITIALIZING;
      case CONFIGURATION_CHANGE_IN_PROGRESS ->
          PartitionRebalanceOutcome.CONFIGURATION_CHANGE_IN_PROGRESS;
      case PAUSE_FAILED -> PartitionRebalanceOutcome.PAUSE_FAILED;
      case REPLICATION_TIMED_OUT -> PartitionRebalanceOutcome.REPLICATION_TIMED_OUT;
      case TIMEOUT_NOW_EXHAUSTED -> PartitionRebalanceOutcome.TIMEOUT_NOW_EXHAUSTED;
      case LEADER_CHANGED -> PartitionRebalanceOutcome.LEADER_CHANGED;
    };
  }
}
