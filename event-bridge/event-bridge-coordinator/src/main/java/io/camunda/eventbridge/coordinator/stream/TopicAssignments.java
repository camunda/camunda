/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.coordinator.stream;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes the initial placement for a topic's partitions. Placement is decided centrally by the
 * coordinator (not derived per broker), so this is the one authority that maps each partition to
 * its replica brokers. The result is stored in the registry as data, which is what lets a future
 * rebalance simply rewrite the assignment rather than being constrained by a deterministic rule.
 */
public final class TopicAssignments {

  private TopicAssignments() {}

  /**
   * Round-robin placement of {@code partitionCount} partitions, each replicated {@code
   * replicationFactor} times (capped at the cluster size), across broker node ids {@code 0..
   * clusterSize-1}. Partition ids are 1-based to match the data and coordinator groups.
   */
  public static Map<Integer, List<Integer>> roundRobin(
      final int partitionCount, final int replicationFactor, final int clusterSize) {
    final var members = Math.max(1, clusterSize);
    final var replicas = Math.max(1, Math.min(replicationFactor, members));
    final Map<Integer, List<Integer>> assignment = new LinkedHashMap<>();
    for (var partition = 0; partition < partitionCount; partition++) {
      final List<Integer> replicaSet = new ArrayList<>(replicas);
      for (var r = 0; r < replicas; r++) {
        replicaSet.add((partition + r) % members);
      }
      assignment.put(partition + 1, replicaSet);
    }
    return assignment;
  }
}
