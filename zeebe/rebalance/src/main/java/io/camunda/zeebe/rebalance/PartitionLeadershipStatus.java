/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import io.atomix.cluster.MemberId;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** One partition's current leadership/balance status. */
@NullMarked
public record PartitionLeadershipStatus(
    String physicalTenantId,
    int partitionId,
    @Nullable MemberId currentLeader,
    MemberId desiredLeader,
    State state) {

  /** The ID of the current leader, or {@code null} if the partition has none. */
  public @Nullable String currentLeaderId() {
    return currentLeader == null ? null : currentLeader.id();
  }

  /** The ID of the desired leader. */
  public String desiredLeaderId() {
    return desiredLeader.id();
  }

  public enum State {
    TRANSFERRING,
    UNBALANCED,
    BALANCED
  }
}
