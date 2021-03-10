/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.broker.protocol.brokerapi.data;

import java.util.Objects;

public final class BrokerPartitionState {
  public static final String LEADER_STATE = "LEADER";
  public static final String FOLLOWER_STATE = "FOLLOWER";

  private final String state;
  private final int partitionId;
  private final int replicationFactor;

  public BrokerPartitionState(
      final String state, final int partitionId, final int replicationFactor) {
    this.state = state;
    this.partitionId = partitionId;
    this.replicationFactor = replicationFactor;
  }

  public String getState() {
    return state;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public int getReplicationFactor() {
    return replicationFactor;
  }

  @Override
  public int hashCode() {
    return Objects.hash(state, partitionId, replicationFactor);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BrokerPartitionState that = (BrokerPartitionState) o;
    return partitionId == that.partitionId
        && replicationFactor == that.replicationFactor
        && Objects.equals(state, that.state);
  }

  @Override
  public String toString() {
    return "BrokerPartitionState{"
        + "state='"
        + state
        + '\''
        + ", partitionId="
        + partitionId
        + ", replicationFactor="
        + replicationFactor
        + '}';
  }
}
