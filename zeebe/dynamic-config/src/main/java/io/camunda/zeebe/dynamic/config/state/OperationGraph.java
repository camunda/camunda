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
 * missing edge is not rejected; it is executed. The failure mode is quiet: two operations writing
 * the same member entry both succeed, and the sub-configuration merge keeps one of them by version,
 * so the other's effect is simply gone. Expect a rare stall or a lost transition, not a test
 * failure.
 *
 * <p>Two traps that are not visible from an operation's own fields, learned the hard way:
 *
 * <ul>
 *   <li>{@code MemberRemoveOperation} writes the entry of {@code memberToRemove()}, not of {@code
 *       memberId()} — it dispatches to {@code MemberLeaveApplier(op.memberToRemove(), ...)}. The
 *       broker applying it and the entry it writes are different members.
 *   <li>{@code PartitionForceReconfigureOperation} writes an entry set that cannot be derived from
 *       the operation at all: its applier loops {@code group.members().keySet()} and removes the
 *       partition from every member outside the target replication group, so what it touches
 *       depends on the live configuration. Order it after everything in its group.
 * </ul>
 *
 * <p>The rule of thumb the current adopters follow: two operations naming the same member need an
 * edge between them unless they name different partitions of it.
 */
@NullMarked
public record OperationGraph(SortedMap<OperationId, PlannedOperation> operations) {

  public OperationGraph {
    operations = Collections.unmodifiableSortedMap(new TreeMap<>(operations));
    for (final var entry : operations.entrySet()) {
      for (final var dependency : entry.getValue().dependsOn()) {
        if (!operations.containsKey(dependency)) {
          throw new IllegalArgumentException(
              "Operation %s depends on %s, which is not part of the graph"
                  .formatted(entry.getKey(), dependency));
        }
      }
    }
  }

  /** Builds a graph and rejects it if it cannot execute: see {@link #validateAcyclic()}. */
  public static OperationGraph of(final SortedMap<OperationId, PlannedOperation> operations) {
    final var graph = new OperationGraph(operations);
    graph.validateAcyclic();
    return graph;
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
   */
  private void validateAcyclic() {
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
