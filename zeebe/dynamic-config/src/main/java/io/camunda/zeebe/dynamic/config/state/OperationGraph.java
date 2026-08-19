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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

  /**
   * Builds a graph and rejects it if it cannot execute correctly: a dependency cycle, or a pair of
   * operations that may run at the same time and write overlapping state.
   */
  public static OperationGraph of(final SortedMap<OperationId, PlannedOperation> operations) {
    final var graph = new OperationGraph(operations);
    graph.validateAcyclic();
    graph.validateConcurrentOperationsDoNotConflict();
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

  /**
   * Rejects a graph in which two operations that may run at the same time write the same state.
   *
   * <p>A safety net, not the ordering mechanism. It cannot see an ordering that lives outside the
   * configuration — a bootstrap and the joins that follow it write different members' entries, and
   * six operations write nothing at all (see {@link WriteSets}). Those are ordered only by the
   * edges a transformer declares.
   */
  private void validateConcurrentOperationsDoNotConflict() {
    final Map<OperationId, Set<OperationId>> memo = new HashMap<>();
    final var ids = List.copyOf(operations.keySet());
    ids.forEach(id -> dependenciesOf(id, memo));

    for (int i = 0; i < ids.size(); i++) {
      for (int j = i + 1; j < ids.size(); j++) {
        final var first = ids.get(i);
        final var second = ids.get(j);
        if (memo.getOrDefault(first, Set.of()).contains(second)
            || memo.getOrDefault(second, Set.of()).contains(first)) {
          continue; // ordered by a dependency path, so they never run together
        }
        final var firstOperation = Objects.requireNonNull(operations.get(first)).operation();
        final var secondOperation = Objects.requireNonNull(operations.get(second)).operation();
        if (!WriteSets.of(firstOperation).isDisjointFrom(WriteSets.of(secondOperation))) {
          throw new IllegalArgumentException(
              ("Operations %s (%s) and %s (%s) have no dependency between them, so they may run at "
                      + "the same time, but they write overlapping state. Either declare a "
                      + "dependency between them or narrow what they write.")
                  .formatted(first, firstOperation, second, secondOperation));
        }
      }
    }
  }

  /** Everything {@code operationId} transitively depends on, memoized across the whole walk. */
  private Set<OperationId> dependenciesOf(
      final OperationId operationId, final Map<OperationId, Set<OperationId>> memo) {
    final var known = memo.get(operationId);
    if (known != null) {
      return known;
    }
    final var all = new HashSet<OperationId>();
    // Recorded before recursing so a cycle cannot recurse forever. validateAcyclic runs first, but
    // this must not become the thing that hangs if that ever stops being true.
    memo.put(operationId, all);
    for (final var dependency : Objects.requireNonNull(operations.get(operationId)).dependsOn()) {
      all.add(dependency);
      all.addAll(dependenciesOf(dependency, memo));
    }
    return all;
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
