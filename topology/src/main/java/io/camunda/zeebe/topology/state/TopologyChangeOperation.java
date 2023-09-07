/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.state;

import io.atomix.cluster.MemberId;
import java.util.Optional;

/**
 * An operation that changes the topology. The operation could be a member join or leave a cluster,
 * or a member join or leave partition.
 */
public interface TopologyChangeOperation {

  MemberId memberId();

  default Operation getOperation() {
    return Operation.NONE;
  }

  record PartitionOperation(
      int partitionId, PartitionOperationType operationType, Optional<Integer> priority)
      implements Operation {
    @Override
    public boolean isPartitionOperation() {
      return true;
    }
  }

  interface Operation {
    Operation NONE = new Operation() {};

    default boolean isPartitionOperation() {
      return false;
    }
  }

  enum PartitionOperationType {
    UNKNOWN,
    JOIN,
    LEAVE
    // Add PROMOTE and DEMOTE when we want to support them
  }
}
