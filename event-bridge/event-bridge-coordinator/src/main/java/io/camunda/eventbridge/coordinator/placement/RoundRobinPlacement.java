/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.coordinator.placement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Round-robin placement: partition {@code i} starts on the {@code i}-th broker and takes the next
 * {@code replicationFactor} brokers (wrapping), so leadership preference rotates evenly. Operates
 * on the broker node ids it is given, so it works the same for a fixed or a dynamic broker set.
 */
public final class RoundRobinPlacement implements PlacementStrategy {

  @Override
  public Map<Integer, List<Integer>> assign(
      final int partitionCount, final int replicationFactor, final List<Integer> brokers) {
    final var n = brokers.size();
    if (n == 0) {
      return Map.of();
    }
    final var replicas = Math.max(1, Math.min(replicationFactor, n));
    final Map<Integer, List<Integer>> assignment = new LinkedHashMap<>();
    for (var partition = 0; partition < partitionCount; partition++) {
      final List<Integer> replicaSet = new ArrayList<>(replicas);
      for (var r = 0; r < replicas; r++) {
        replicaSet.add(brokers.get((partition + r) % n));
      }
      assignment.put(partition + 1, replicaSet);
    }
    return assignment;
  }
}
