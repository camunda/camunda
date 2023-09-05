/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.state;

import java.util.List;

/**
 * Represents the ongoing cluster topology changes. The pendingOperations are executed sequentially.
 * Only after completing one operation, the next operation is started. Once an operation is
 * completed, it should be removed from the plan, so that the next operation can be picked up.
 */
public record ClusterChangePlan(int version, List<TopologyChangeOperation> pendingOperations) {
  public static ClusterChangePlan empty() {
    return new ClusterChangePlan(0, List.of());
  }

  public static ClusterChangePlan init(final List<TopologyChangeOperation> operations) {
    return new ClusterChangePlan(1, List.copyOf(operations));
  }

  /** To be called when the first operation is completed. */
  ClusterChangePlan advance() {
    return new ClusterChangePlan(
        version + 1, pendingOperations.subList(1, pendingOperations.size()));
  }

  public ClusterChangePlan merge(final ClusterChangePlan other) {
    // Pick the highest version
    if (other == null) {
      return this;
    }
    if (other.version > version) {
      return other;
    }
    return this;
  }
}
