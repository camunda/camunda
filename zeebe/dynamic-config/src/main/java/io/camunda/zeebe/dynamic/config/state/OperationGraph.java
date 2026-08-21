/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.NullMarked;

/**
 * The operations of one change and the order constraints between them — the immutable template a
 * {@link DependencyChangePlan} tracks progress against.
 *
 * <p>Kept separate from the plan because a {@link PhasedChangePlan} phase carries the template
 * before any sub-configuration has started it, and therefore before an id or any progress exists.
 *
 * <p>Two operations with no dependency path between them may run at the same time. That is the
 * whole concurrency model: broker-parallel, partition-parallel and group-parallel execution are all
 * just missing edges, and adding an axis costs nothing structurally.
 *
 * <h4>Correctness of the edges is the author's, and only the author's</h4>
 *
 * <p>Nothing here checks that concurrently-runnable operations are actually safe together. A
 * missing edge is not rejected; it is executed.
 *
 * <p>The rule that actually makes the current adopters safe: <b>the broker applying an operation
 * must be the only writer of the entry it writes</b> ({@code operation.memberId()} must equal
 * whichever member's entry the applier touches). Every adopter applier writes only its own member's
 * entry, and a broker serialises its own completions on its actor, so that entry's version
 * increments monotonically and two brokers merging never see a same-version conflict on it. This is
 * <em>not</em> the same as "two operations naming the same member need an edge unless they name
 * different partitions" — a member's partitions all live in one {@code BrokerPartitionState} under
 * one version, so two unordered operations on different partitions of the same member still write
 * the same entry, and land on the branch below.
 *
 * <p>The failure mode when the rule is violated is not quiet. {@code
 * BrokerPartitionState#merge(BrokerPartitionState)} throws on two same-version copies that differ,
 * rather than picking one — so a broker whose peer has written an entry it also wrote rejects that
 * merge outright, leaves its local state unchanged, and repeats the rejection on every later gossip
 * round against that peer: <b>permanent non-convergence between the two brokers</b>, not a lost
 * transition. (A silent loss is still possible, but only in the narrower case where one broker's
 * write has not yet reached the other at merge time.)
 *
 * <p>Two operations that violate the rule outright, learned the hard way, and out of scope for
 * concurrent execution under the current merge until either write-set validation is reinstated for
 * these two shapes or {@code BrokerPartitionState.merge} stops throwing on same-version conflicts:
 *
 * <ul>
 *   <li>{@code MemberRemoveOperation} writes the entry of {@code memberToRemove()}, not of {@code
 *       memberId()} — it dispatches to {@code MemberLeaveApplier(op.memberToRemove(), ...)}. The
 *       broker applying it and the entry it writes are different members.
 *   <li>{@code PartitionForceReconfigureOperation} writes an entry set that cannot be derived from
 *       the operation at all: its applier loops {@code group.members().keySet()} and removes the
 *       partition from every member outside the target replication group, so what it touches
 *       depends on the live configuration, not just on its own fields.
 * </ul>
 */
@NullMarked
public record OperationGraph(SortedMap<OperationId, PlannedOperation> operations) {

  /**
   * Rejects a graph that cannot execute: empty (nothing to run, and {@link
   * DependencyChangePlan#toCompletedChange()} has no timestamp to derive a completion from), or
   * cyclic (see {@link #validateAcyclic}). Enforced here, in the canonical constructor, rather than
   * only in {@link #of} — the compact constructor is not the only way to build one of these; a bare
   * {@code new OperationGraph(...)} must reject the same graphs {@link #of} does, or a decode path
   * that does not go through either factory could construct one that violates both.
   */
  public OperationGraph {
    operations = Collections.unmodifiableSortedMap(new TreeMap<>(operations));
    if (operations.isEmpty()) {
      throw new IllegalArgumentException("A dependency graph must have at least one operation");
    }
    for (final var entry : operations.entrySet()) {
      for (final var dependency : entry.getValue().dependsOn()) {
        if (!operations.containsKey(dependency)) {
          throw new IllegalArgumentException(
              "Operation %s depends on %s, which is not part of the graph"
                  .formatted(entry.getKey(), dependency));
        }
      }
    }
    validateAcyclic(operations);
  }

  /** Same validation as the canonical constructor; kept for readability at the call site. */
  public static OperationGraph of(final SortedMap<OperationId, PlannedOperation> operations) {
    return new OperationGraph(operations);
  }

  /**
   * A graph in which each operation waits for the one before it — the behaviour every change had
   * before dependencies existed, and what an unmigrated transformer gets by default.
   */
  public static OperationGraph sequential(
      final List<? extends ClusterConfigurationChangeOperation> operations) {
    final var builder = builder();
    OperationId previous = null;
    for (final var operation : operations) {
      previous =
          previous == null ? builder.add(operation) : builder.add(operation, Set.of(previous));
    }
    return builder.build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isEmpty() {
    return operations.isEmpty();
  }

  public int size() {
    return operations.size();
  }

  /** The operations in id order, which is the stable order every derived view renders in. */
  public List<ClusterConfigurationChangeOperation> inOrder() {
    return operations.values().stream().map(PlannedOperation::operation).toList();
  }

  /**
   * A cycle would leave every operation in it permanently un-runnable and the change would stall
   * with no error at all, so it is rejected here rather than discovered in production.
   *
   * <p>Static, and takes {@code operations} as a parameter rather than reading the field: called
   * from the compact constructor, before the field is assigned.
   */
  private static void validateAcyclic(final SortedMap<OperationId, PlannedOperation> operations) {
    final Map<OperationId, Integer> remaining = new HashMap<>();
    operations.forEach((id, planned) -> remaining.put(id, planned.dependsOn().size()));
    final var ready = new ArrayDeque<OperationId>();
    remaining.forEach(
        (id, count) -> {
          if (count == 0) {
            ready.add(id);
          }
        });

    int settled = 0;
    while (!ready.isEmpty()) {
      final var next = ready.poll();
      settled++;
      operations.forEach(
          (id, planned) -> {
            if (planned.dependsOn().contains(next) && remaining.merge(id, -1, Integer::sum) == 0) {
              ready.add(id);
            }
          });
    }
    if (settled != operations.size()) {
      throw new IllegalArgumentException(
          "The change has a dependency cycle; these operations are never runnable: "
              + remaining.entrySet().stream()
                  .filter(e -> e.getValue() > 0)
                  .map(Map.Entry::getKey)
                  .toList());
    }
  }

  /** One operation and what it waits for. */
  public record PlannedOperation(
      ClusterConfigurationChangeOperation operation, SortedSet<OperationId> dependsOn) {

    public PlannedOperation {
      dependsOn = Collections.unmodifiableSortedSet(new TreeSet<>(dependsOn));
    }

    public static PlannedOperation of(final ClusterConfigurationChangeOperation operation) {
      return new PlannedOperation(operation, new TreeSet<>());
    }
  }

  /** Assigns ids in insertion order and lets a transformer name only the edges it needs. */
  public static final class Builder {

    private final SortedMap<OperationId, PlannedOperation> operations = new TreeMap<>();
    private int nextId = 0;

    /** Adds an operation with no dependencies, so it is runnable immediately. */
    public OperationId add(final ClusterConfigurationChangeOperation operation) {
      return add(operation, Set.of());
    }

    public OperationId add(
        final ClusterConfigurationChangeOperation operation, final Set<OperationId> dependsOn) {
      final var operationId = OperationId.of(nextId++);
      operations.put(operationId, new PlannedOperation(operation, new TreeSet<>(dependsOn)));
      return operationId;
    }

    public boolean isEmpty() {
      return operations.isEmpty();
    }

    public OperationGraph build() {
      return OperationGraph.of(operations);
    }
  }
}
