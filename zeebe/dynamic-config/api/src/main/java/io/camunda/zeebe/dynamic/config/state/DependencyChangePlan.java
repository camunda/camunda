/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.CompletedOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.Status;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The ongoing change of one sub-configuration: an {@link OperationGraph} plus which of its
 * operations have completed.
 *
 * <p>An operation is <em>runnable</em> once everything it depends on has completed. Brokers apply
 * whichever of their own operations are runnable, so concurrency is the <em>absence of an edge</em>
 * rather than the presence of a container — broker-parallel, partition-parallel and group-parallel
 * execution are the same thing, and a future axis costs nothing structurally.
 *
 * <h4>Convergence</h4>
 *
 * <p>The graph is fixed for the plan's lifetime. {@code completed} is grow-only and every entry has
 * exactly one writer — the broker that applies that operation — so merge is a flat union.
 * Commutative, associative, and idempotent by construction, with no version to move and no barrier
 * to release.
 */
@NullMarked
public record DependencyChangePlan(
    long id,
    Status status,
    Instant startedAt,
    OperationGraph graph,
    SortedMap<OperationId, Instant> completed)
    implements ChangePlan {

  public DependencyChangePlan {
    completed = Collections.unmodifiableSortedMap(new TreeMap<>(completed));
    for (final var operationId : completed.keySet()) {
      final var planned = graph.operations().get(operationId);
      if (planned == null) {
        throw new IllegalArgumentException(
            "Operation %s is marked complete, but is not part of this plan's graph"
                .formatted(operationId));
      }
      final var incompleteDependencies = new TreeSet<>(planned.dependsOn());
      incompleteDependencies.removeAll(completed.keySet());
      if (!incompleteDependencies.isEmpty()) {
        throw new IllegalArgumentException(
            "Operation %s is marked complete, but the operations it depends on are not: %s"
                .formatted(operationId, incompleteDependencies));
      }
    }
  }

  public static DependencyChangePlan init(final long id, final OperationGraph graph) {
    return new DependencyChangePlan(id, Status.IN_PROGRESS, Instant.now(), graph, new TreeMap<>());
  }

  /** A plan that runs {@code operations} strictly one after another — today's behaviour. */
  public static DependencyChangePlan sequential(
      final long id, final List<? extends ClusterConfigurationChangeOperation> operations) {
    return init(id, OperationGraph.sequential(operations));
  }

  public SortedMap<OperationId, OperationGraph.PlannedOperation> operations() {
    return graph.operations();
  }

  public boolean isComplete(final OperationId operationId) {
    return completed.containsKey(operationId);
  }

  /** Whether everything this operation depends on has completed, and it has not run itself. */
  public boolean isRunnable(final OperationId operationId) {
    final var planned = graph.operations().get(operationId);
    if (planned == null || isComplete(operationId)) {
      return false;
    }
    return completed.keySet().containsAll(planned.dependsOn());
  }

  /**
   * Everything {@code memberId} may start right now, in ascending operation-id order so that two
   * brokers evaluating the same converged state agree on what to do first.
   */
  public SortedMap<OperationId, ClusterConfigurationChangeOperation> runnableFor(
      final MemberId memberId) {
    final SortedMap<OperationId, ClusterConfigurationChangeOperation> runnable = new TreeMap<>();
    graph
        .operations()
        .forEach(
            (operationId, planned) -> {
              if (planned.operation().memberId().equals(memberId) && isRunnable(operationId)) {
                runnable.put(operationId, planned.operation());
              }
            });
    return runnable;
  }

  @Override
  public boolean hasPendingChanges() {
    return completed.size() < graph.size();
  }

  /**
   * The operations still to run, in operation-id order. Ordering is positional rather than by
   * arrival so that two brokers rendering the same converged plan produce identical lists.
   */
  @Override
  public List<ClusterConfigurationChangeOperation> pendingOperations() {
    final var pending = new ArrayList<ClusterConfigurationChangeOperation>();
    graph
        .operations()
        .forEach(
            (operationId, planned) -> {
              if (!isComplete(operationId)) {
                pending.add(planned.operation());
              }
            });
    return List.copyOf(pending);
  }

  /**
   * The operations still to run, ordered so that every operation follows the ones it depends on — a
   * valid sequential execution of this change.
   *
   * <p>{@link #pendingOperations()} orders by operation id, which is topological for every graph
   * {@link OperationGraph.Builder} produces (it only lets an operation depend on ids already
   * issued) but not for one built by hand or decoded from the wire, where the ids are whatever the
   * sender wrote. Reading a change is fine with id order; <em>executing</em> it in id order is not,
   * and {@link ClusterChangePlan#flatten} hands the result to a broker that will execute it.
   *
   * <p>Ties are broken by ascending id rather than left to iteration order, because two brokers
   * flattening the same plan must produce the same queue: the result is gossiped into the legacy
   * field and merged there by a version that says nothing about which ordering produced it.
   *
   * <p>A dependency that has already completed does not constrain anything, so only incomplete
   * operations are counted — which is what makes the result executable from the current state
   * rather than from the start of the change.
   */
  public List<ClusterConfigurationChangeOperation> pendingOperationsInDependencyOrder() {
    final var outstanding = new TreeMap<OperationId, SortedSet<OperationId>>();
    graph
        .operations()
        .forEach(
            (operationId, planned) -> {
              if (isComplete(operationId)) {
                return;
              }
              final var blockers = new TreeSet<OperationId>();
              planned.dependsOn().stream().filter(d -> !isComplete(d)).forEach(blockers::add);
              outstanding.put(operationId, blockers);
            });

    final var ordered = new ArrayList<ClusterConfigurationChangeOperation>();
    while (!outstanding.isEmpty()) {
      final var next =
          outstanding.entrySet().stream()
              .filter(entry -> entry.getValue().isEmpty())
              .map(Map.Entry::getKey)
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Cannot order the outstanding operations of change %d: every one is"
                                  .formatted(id)
                              + " blocked, which means a dependency cycle OperationGraph should"
                              + " have rejected: "
                              + outstanding));
      outstanding.remove(next);
      outstanding.values().forEach(blockers -> blockers.remove(next));
      ordered.add(operation(next));
    }
    return List.copyOf(ordered);
  }

  /** The operations already run, in operation-id order. See {@link #pendingOperations()}. */
  @Override
  public List<CompletedOperation> completedOperations() {
    final var done = new ArrayList<CompletedOperation>();
    graph
        .operations()
        .forEach(
            (operationId, planned) -> {
              final var at = completed.get(operationId);
              if (at != null) {
                done.add(new CompletedOperation(planned.operation(), at));
              }
            });
    return List.copyOf(done);
  }

  @Override
  public CompletedChange cancel() {
    return new CompletedChange(id, Status.CANCELLED, startedAt, Instant.now());
  }

  /**
   * What each incomplete operation is still waiting for; an empty value means it is runnable.
   *
   * <p>Exists so a stalled change can be diagnosed. With no step index to read, "which operations
   * are outstanding, and on what" is the only way an operator can tell why nothing is moving.
   */
  public SortedMap<OperationId, SortedSet<OperationId>> blockedBy() {
    final SortedMap<OperationId, SortedSet<OperationId>> blocked = new TreeMap<>();
    graph
        .operations()
        .forEach(
            (operationId, planned) -> {
              if (isComplete(operationId)) {
                return;
              }
              final var waitingOn = new TreeSet<OperationId>();
              planned.dependsOn().stream().filter(d -> !isComplete(d)).forEach(waitingOn::add);
              blocked.put(operationId, Collections.unmodifiableSortedSet(waitingOn));
            });
    return blocked;
  }

  /**
   * Records that {@code operationId} finished. Package-private: only a sub-configuration may move a
   * plan, so that the accompanying member-state update happens in the same transition.
   *
   * @throws IllegalStateException if the operation is unknown or was not runnable
   */
  DependencyChangePlan completeOperation(final OperationId operationId) {
    if (!graph.operations().containsKey(operationId)) {
      throw new IllegalStateException(
          "Expected to complete %s, but it is not part of this plan".formatted(operationId));
    }
    if (isComplete(operationId)) {
      return this;
    }
    if (!isRunnable(operationId)) {
      throw new IllegalStateException(
          "Expected to complete %s, but it is still waiting on %s"
              .formatted(operationId, blockedBy().get(operationId)));
    }
    final var updated = new TreeMap<>(completed);
    updated.put(operationId, Instant.now());
    return new DependencyChangePlan(id, status, startedAt, graph, updated);
  }

  /**
   * Merges two copies of this plan seen by different brokers.
   *
   * <p>A flat union: the graph is fixed and identical for two copies of the <em>same</em> plan, and
   * each completion has a single writer — the operation's owning member, which never re-stamps an
   * entry it already holds — so one key cannot carry two timestamps and nothing can be lost.
   *
   * <p>"Same plan" is asserted, not assumed. The id is the enclosing sub-configuration's version,
   * derived locally by whichever broker starts the change — two self-believed coordinators (the
   * forced-request path, which bypasses the single-coordinator check) can derive the same id for
   * two different graphs. {@code OperationGraph.Builder} numbers operations {@code 0..n-1} in
   * insertion order, so two unrelated graphs almost always share operation ids; blindly unioning
   * {@code completed} in that case would mark one graph's operation complete because the other's,
   * coincidentally sharing its id, ran — silently, since the merged plan still drains and completes
   * normally. Mirrors the same-id-different-content guard {@link PhasedChangePlan#merge} already
   * has for phases.
   */
  public DependencyChangePlan merge(final @Nullable DependencyChangePlan other) {
    if (other == null) {
      return this;
    }
    if (id != other.id) {
      // Resolved deterministically rather than by favouring the receiver, so that merge stays
      // commutative.
      return id > other.id ? this : other;
    }
    if (!graph.equals(other.graph)) {
      throw new IllegalStateException(
          "Cannot merge plans with the same ID but different graphs: %s vs %s"
              .formatted(graph, other.graph));
    }
    final var merged = new TreeMap<>(completed);
    merged.putAll(other.completed);
    return new DependencyChangePlan(id, status, startedAt, graph, merged);
  }

  /**
   * The completed-change record for a drained plan, timestamped with the last operation's
   * completion rather than with the wall clock.
   *
   * <p>Any broker may observe the plan complete, and two doing so a moment apart would otherwise
   * stamp different values. Neither sub-configuration merge reconciles {@code lastChange} at equal
   * versions, so those two values would never converge and the cluster would report two different
   * completion times for one change, permanently.
   */
  CompletedChange toCompletedChange() {
    final var completedAt =
        completed.values().stream().max(Instant::compareTo).orElseGet(Instant::now);
    return new CompletedChange(id, Status.COMPLETED, startedAt, completedAt);
  }

  /** The operation behind an id, for callers that already know it exists. */
  public ClusterConfigurationChangeOperation operation(final OperationId operationId) {
    return Objects.requireNonNull(
            graph.operations().get(operationId),
            () -> "%s is not part of this plan".formatted(operationId))
        .operation();
  }
}
