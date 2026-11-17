/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import java.util.List;
import org.jspecify.annotations.NullMarked;

/**
 * The cluster's current leadership/balance status (whether all partitions are on their desired
 * leaders).
 */
@NullMarked
public record ClusterLeadershipStatus(State state, List<PartitionLeadershipStatus> partitions) {
  public static ClusterLeadershipStatus aggregateOf(
      final List<PartitionLeadershipStatus> partitions) {
    var clusterState = State.BALANCED;
    for (final var partition : partitions) {
      clusterState =
          switch (partition.state()) {
            case TRANSFERRING -> State.BALANCING;
            case UNBALANCED -> clusterState == State.BALANCING ? clusterState : State.UNBALANCED;
            case BALANCED -> clusterState;
          };
    }
    return new ClusterLeadershipStatus(clusterState, partitions);
  }

  public enum State {
    BALANCING,
    UNBALANCED,
    BALANCED
  }
}
