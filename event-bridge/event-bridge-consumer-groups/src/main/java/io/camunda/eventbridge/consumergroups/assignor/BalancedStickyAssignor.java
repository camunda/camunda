/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.assignor;

import io.camunda.eventbridge.protocol.topic.TopicPartition;
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

/**
 * Sticky least-loaded assignor for active partition ownership, extended (consumer-groups ADR 0006
 * decision 1, retained by event-bridge-streaming ADR 0009) with standby placement: anti-affine to
 * the partition's active owner, sticky across rebalances, bounded per-member ({@link
 * #maxWarmingPartitionsPerMember}), and consulted for <b>ready-only promotion</b> — a partition
 * whose previous active owner left the group is reassigned only to a member the caller reports as a
 * ready standby for it ({@link PartitionAssignor.PartitionAssignmentContext#readyStandbys()});
 * absent a ready standby, the partition is left unassigned this round rather than handed to an
 * arbitrary least-loaded member (correctness over availability, mirroring the halt-instead-of-
 * continue discipline event-bridge-streaming ADR 0009 decision 4 uses on the changelog-producer
 * side). This gate only ever engages when the group has {@code standbyReplicas > 0}; a group with
 * none behaves byte-for-byte as before standby support existed.
 *
 * <p>Anti-affinity here is member-level only (a partition's standbys never share a member with its
 * active owner, nor with each other): the assignor has no failure-domain/rack input to place
 * standbys away from the active's host or availability zone, since no such topology is modeled
 * anywhere in this codebase today.
 */
public class BalancedStickyAssignor implements PartitionAssignor {

  private final int maxWarmingPartitionsPerMember;

  public BalancedStickyAssignor() {
    this(Integer.MAX_VALUE);
  }

  /**
   * @param maxWarmingPartitionsPerMember the warming cap (ADR 0006 decision 1); unbounded if <= 0.
   */
  public BalancedStickyAssignor(final int maxWarmingPartitionsPerMember) {
    this.maxWarmingPartitionsPerMember =
        maxWarmingPartitionsPerMember <= 0 ? Integer.MAX_VALUE : maxWarmingPartitionsPerMember;
  }

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

    final Map<String, Set<TopicPartition>> assignment = new HashMap<>();
    consumers.forEach(c -> assignment.put(c, new LinkedHashSet<>()));

    // Build partition-to-previous-owner mapping
    final var previousOwner = new HashMap<TopicPartition, String>();
    previous.forEach(
        (consumer, owned) -> {
          if (assignment.containsKey(consumer)) {
            for (final var partition : owned) {
              previousOwner.put(partition, consumer);
            }
          }
        });

    // Every partition that had an active owner at all before this round (even one now gone) --
    // distinguishes "orphaned by a departed active" (a failover, subject to ready-only promotion)
    // from "never assigned before" (initial assignment, always filled immediately).
    final var everHadOwner = new HashSet<TopicPartition>();
    previous.values().forEach(everHadOwner::addAll);

    // Step 1: Preserve sticky assignments; gate orphaned partitions behind ready-only promotion.
    final var unassigned = new TreeSet<TopicPartition>();
    for (final var partition : partitions) {
      final var owner = previousOwner.get(partition);
      if (owner != null) {
        assignment.get(owner).add(partition);
        continue;
      }
      if (context.standbyReplicas() > 0 && everHadOwner.contains(partition)) {
        final var readyMember = readyStandbyFor(partition, consumers, context.readyStandbys());
        if (readyMember != null) {
          assignment.get(readyMember).add(partition);
        }
        // No ready standby: leave unassigned this round rather than filling it below.
        continue;
      }
      unassigned.add(partition);
    }

    // Step 2: Assign remaining (brand-new partitions, or standbys disabled) to least-loaded
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

    // Convert to List<TopicPartition> for the PartitionAssignment contract
    final var result = new HashMap<String, List<TopicPartition>>();
    assignment.forEach((c, parts) -> result.put(c, new ArrayList<>(parts)));

    final var standbyResult =
        context.standbyReplicas() <= 0
            ? Map.<String, List<TopicPartition>>of()
            : assignStandbys(context, consumers, partitions, result);

    return new PartitionAssignment(result, standbyResult);
  }

  /** The first (sorted) consumer that reports readiness for {@code partition}, or {@code null}. */
  private String readyStandbyFor(
      final TopicPartition partition,
      final List<String> consumers,
      final Map<String, Set<TopicPartition>> readyStandbys) {
    for (final var consumer : consumers) {
      if (readyStandbys.getOrDefault(consumer, Set.of()).contains(partition)) {
        return consumer;
      }
    }
    return null;
  }

  private Map<String, List<TopicPartition>> assignStandbys(
      final PartitionAssignmentContext context,
      final List<String> consumers,
      final List<TopicPartition> partitions,
      final Map<String, List<TopicPartition>> activeResult) {

    final var activeOwner = new HashMap<TopicPartition, String>();
    activeResult.forEach((member, owned) -> owned.forEach(p -> activeOwner.put(p, member)));

    final var previousStandbyOwner = new HashMap<TopicPartition, List<String>>();
    context
        .assignment()
        .standbyAssignments()
        .forEach(
            (member, owned) ->
                owned.forEach(
                    p ->
                        previousStandbyOwner
                            .computeIfAbsent(p, k -> new ArrayList<>())
                            .add(member)));

    final Map<String, Set<TopicPartition>> standby = new HashMap<>();
    consumers.forEach(c -> standby.put(c, new LinkedHashSet<>()));
    final var warmingCount = new HashMap<String, Integer>();
    consumers.forEach(c -> warmingCount.put(c, 0));

    for (final var partition : partitions) {
      final var active = activeOwner.get(partition);
      final var candidates = new LinkedHashSet<String>(consumers);
      candidates.remove(active);

      final var chosen = new LinkedHashSet<String>();
      // Sticky: keep previous standby holders that are still valid candidates.
      previousStandbyOwner.getOrDefault(partition, List.of()).stream()
          .filter(candidates::contains)
          .filter(m -> warmingCount.get(m) < maxWarmingPartitionsPerMember)
          .limit(context.standbyReplicas())
          .forEach(chosen::add);

      // Fill remaining slots least-loaded-first among the candidates not already chosen.
      final var remaining = new ArrayList<>(candidates);
      remaining.removeAll(chosen);
      remaining.sort(
          Comparator.<String>comparingInt(warmingCount::get)
              .thenComparing(Comparator.naturalOrder()));
      for (final var candidate : remaining) {
        if (chosen.size() >= context.standbyReplicas()) {
          break;
        }
        if (warmingCount.get(candidate) < maxWarmingPartitionsPerMember) {
          chosen.add(candidate);
        }
      }

      chosen.forEach(
          member -> {
            standby.get(member).add(partition);
            warmingCount.merge(member, 1, Integer::sum);
          });
    }

    final var result = new HashMap<String, List<TopicPartition>>();
    standby.forEach((c, parts) -> result.put(c, new ArrayList<>(parts)));
    return result;
  }

  private void rebalance(
      final Map<String, Set<TopicPartition>> assignment,
      final Map<String, List<TopicPartition>> previous,
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
      final var nonSticky = new ArrayList<TopicPartition>();
      final var sticky = new ArrayList<TopicPartition>();

      for (final var partition : fromSet) {
        (stickySet.contains(partition) ? sticky : nonSticky).add(partition);
      }

      movePartitions(nonSticky, fromSet, assignment, underloaded, targetLoad, targetLoad.get(from));
      movePartitions(sticky, fromSet, assignment, underloaded, targetLoad, targetLoad.get(from));
    }
  }

  private void movePartitions(
      final List<TopicPartition> candidates,
      final Set<TopicPartition> fromSet,
      final Map<String, Set<TopicPartition>> assignment,
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
