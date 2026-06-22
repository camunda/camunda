/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.coordinator.assignor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;

public class BalancedStickyAssignor implements PartitionAssignor {

  @Override
  public PartitionAssignment assign(final PartitionAssignmentContext context) {
    final var consumers = new ArrayList<>(context.consumers());
    Collections.sort(consumers);

    if (consumers.isEmpty()) {
      return new PartitionAssignment(Map.of());
    }

    final var partitions = context.partitions();
    final var previous = context.assignment().assignments();
    final var numConsumers = consumers.size();
    final var totalPartitions = partitions.size();
    final var minLoad = totalPartitions / numConsumers;
    final var maxLoad = minLoad + (totalPartitions % numConsumers > 0 ? 1 : 0);
    final var numConsumersWithMax = totalPartitions % numConsumers;

    final Map<String, Set<Integer>> assignment = new HashMap<>();
    consumers.forEach(c -> assignment.put(c, new LinkedHashSet<>()));

    // Build partition-to-previous-owner mapping
    final var previousOwner = new HashMap<Integer, String>();
    previous.forEach(
        (consumer, owned) -> {
          if (assignment.containsKey(consumer)) {
            for (final var partition : owned) {
              previousOwner.put(partition, consumer);
            }
          }
        });

    // Step 1: Preserve sticky assignments
    final var unassigned = new TreeSet<Integer>();
    for (final var partition : partitions) {
      final var owner = previousOwner.get(partition);
      if (owner != null) {
        assignment.get(owner).add(partition);
      } else {
        unassigned.add(partition);
      }
    }

    // Step 2: Assign remaining to least-loaded
    final var queue =
        new PriorityQueue<>(
            Comparator.<String>comparingInt(c -> assignment.get(c).size())
                .thenComparing(Comparator.naturalOrder()));
    queue.addAll(consumers);

    for (final var partition : unassigned) {
      final var leastLoaded = queue.poll();
      assignment.get(leastLoaded).add(partition);
      queue.add(leastLoaded);
    }

    // Step 3: Balance overloaded → underloaded, moving non-sticky first
    rebalance(assignment, previous, consumers, minLoad, maxLoad, numConsumersWithMax);

    // Convert to List<Integer> for the PartitionAssignment contract
    final var result = new HashMap<String, List<Integer>>();
    assignment.forEach((c, parts) -> result.put(c, new ArrayList<>(parts)));

    return new PartitionAssignment(result);
  }

  private void rebalance(
      final Map<String, Set<Integer>> assignment,
      final Map<String, List<Integer>> previous,
      final List<String> consumers,
      final int minLoad,
      final int maxLoad,
      final int numConsumersWithMax) {

    // Each consumer's own target: the first numConsumersWithMax (sorted) carry maxLoad, the rest
    // minLoad. Draining/filling must respect each consumer's target — not a single global maxLoad —
    // otherwise a consumer sitting at maxLoad whose target is minLoad never relinquishes a
    // partition, and a newly joined consumer is left with none.
    final var targetLoad = new HashMap<String, Integer>();
    for (int i = 0; i < consumers.size(); i++) {
      targetLoad.put(consumers.get(i), i < numConsumersWithMax ? maxLoad : minLoad);
    }

    final var overloaded = new ArrayList<String>();
    final var underloaded = new LinkedList<String>();

    for (final var consumer : consumers) {
      final var size = assignment.get(consumer).size();
      final var target = targetLoad.get(consumer);

      if (size > target) {
        overloaded.add(consumer);
      } else if (size < target) {
        underloaded.add(consumer);
      }
    }

    if (overloaded.isEmpty() || underloaded.isEmpty()) {
      return;
    }

    for (final var from : overloaded) {
      final var fromSet = assignment.get(from);
      final var stickySet = new HashSet<>(previous.getOrDefault(from, List.of()));
      final var nonSticky = new ArrayList<Integer>();
      final var sticky = new ArrayList<Integer>();

      for (final var partition : fromSet) {
        (stickySet.contains(partition) ? sticky : nonSticky).add(partition);
      }

      movePartitions(nonSticky, fromSet, assignment, underloaded, targetLoad, targetLoad.get(from));
      movePartitions(sticky, fromSet, assignment, underloaded, targetLoad, targetLoad.get(from));
    }
  }

  private void movePartitions(
      final List<Integer> candidates,
      final Set<Integer> fromSet,
      final Map<String, Set<Integer>> assignment,
      final LinkedList<String> underloaded,
      final Map<String, Integer> targetLoad,
      final int fromTarget) {

    for (final var partition : candidates) {
      if (underloaded.isEmpty() || fromSet.size() <= fromTarget) {
        break;
      }

      fromSet.remove(partition);
      final var to = underloaded.getFirst();
      assignment.get(to).add(partition);

      if (assignment.get(to).size() >= targetLoad.get(to)) {
        underloaded.removeFirst();
      }
    }
  }
}
