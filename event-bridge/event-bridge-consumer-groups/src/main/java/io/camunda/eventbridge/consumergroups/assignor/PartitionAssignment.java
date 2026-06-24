/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.assignor;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PartitionAssignment {

  private final Map<String, List<Integer>> assignments;
  private final Map<String, Set<Integer>> assignmentSets;

  public PartitionAssignment(final Map<String, List<Integer>> raw) {
    final var lists = new HashMap<String, List<Integer>>();
    final var sets = new HashMap<String, Set<Integer>>();

    raw.forEach(
        (consumer, partitions) -> {
          lists.put(consumer, List.copyOf(partitions));
          sets.put(consumer, Set.copyOf(partitions));
        });

    assignments = Collections.unmodifiableMap(lists);
    assignmentSets = Collections.unmodifiableMap(sets);
  }

  public List<Integer> forConsumer(final String memberId) {
    return assignments.getOrDefault(memberId, List.of());
  }

  public Map<String, List<Integer>> assignments() {
    return assignments;
  }

  public ReconciliationResult computeDelta(
      final String memberId, final List<Integer> ownedPartitions, final Set<Integer> ownedSet) {
    final var assignment = forConsumer(memberId);
    final var assignmentSet = assignmentSets.getOrDefault(memberId, Set.of());
    final var revoke = ownedPartitions.stream().filter(p -> !assignmentSet.contains(p)).toList();
    final var assign = assignment.stream().filter(p -> !ownedSet.contains(p)).toList();
    return new ReconciliationResult(revoke, assign, assignment);
  }

  public record ReconciliationResult(
      List<Integer> revoke, List<Integer> assign, List<Integer> assignment) {

    public static ReconciliationResult noop(final List<Integer> currentAssignment) {
      return new ReconciliationResult(List.of(), List.of(), currentAssignment);
    }
  }
}
