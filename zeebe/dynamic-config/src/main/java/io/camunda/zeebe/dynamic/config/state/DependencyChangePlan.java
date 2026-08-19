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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The ongoing change of one sub-configuration, modelled as operations with declared dependencies
 * rather than as a queue.
 *
 * <p>An operation is <em>runnable</em> once every operation it depends on has completed. Brokers
 * apply whichever of their own operations are runnable, so concurrency is the <em>absence of an
 * edge</em> rather than the presence of a container: broker-parallel, partition-parallel and
 * group-parallel execution are all the same thing, and a future axis costs nothing structurally.
 *
 * <h4>Why dependencies are declared and not inferred</h4>
 *
 * <p>It is tempting to keep an ordered list and derive concurrency from write-set disjointness.
 * That is unsafe. {@code PartitionBootstrapOperation} and the {@code PartitionJoinOperation}s that
 * follow it write different members' entries — disjoint — yet the join cannot precede the
 * bootstrap, because the Raft partition must exist first. And six operations write nothing in the
 * configuration at all (see {@link WriteSets}), so there is nothing to analyse. Disjointness is
 * kept as a check, never as the mechanism.
 *
 * <h4>Convergence</h4>
 *
 * <p>{@code operations} is fixed when the plan is created. {@code completed} is grow-only and every
 * entry has exactly one writer — the broker that applies that operation — so merge is a flat union
 * with the earliest timestamp winning a collision. Commutative, associative and idempotent by
 * construction, with no version to move and no barrier to release.
 */
@NullMarked
public record DependencyChangePlan(
    long id,
    Status status,
    Instant startedAt,
    SortedMap<OperationId, PlannedOperation> operations,
    SortedMap<OperationId, Instant> completed) {

  public DependencyChangePlan {
    operations = Collections.unmodifiableSortedMap(new TreeMap<>(operations));
    completed = Collections.unmodifiableSortedMap(new TreeMap<>(completed));
    for (final var entry : operations.entrySet()) {
      for (final var dependency : entry.getValue().dependsOn()) {
        if (!operations.containsKey(dependency)) {
          throw new IllegalArgumentException(
              "Operation %s depends on %s, which is not part of the plan"
                  .formatted(entry.getKey(), dependency));
        }
      }
    }
  }

  /**
   * Builds a plan and validates it: no cycles, and every pair of operations that could run
   * concurrently has disjoint write sets.
   *
   * @throws IllegalArgumentException if the graph has a cycle or an unordered pair conflicts
   */
  public static DependencyChangePlan init(
      final long id, final SortedMap<OperationId, PlannedOperation> operations) {
    final var plan =
        new DependencyChangePlan(
            id, Status.IN_PROGRESS, Instant.now(), operations, new TreeMap<>());
    plan.validateAcyclic();
    plan.validateConcurrentOperationsDoNotConflict();
    return plan;
  }

  /** A plan that runs {@code operations} strictly one after another — today's behaviour. */
  public static DependencyChangePlan sequential(
      final long id, final List<? extends ClusterConfigurationChangeOperation> operations) {
    final var builder = builder();
    OperationId previous = null;
    for (final var operation : operations) {
      previous =
          previous == null ? builder.add(operation) : builder.add(operation, Set.of(previous));
    }
    return builder.build(id);
  }

  public static Builder builder() {
    return new Builder();
  }

  // ---------------------------------------------------------------------------
  // Derived views — nothing below is stored.
  // ---------------------------------------------------------------------------

  public boolean isComplete(final OperationId operationId) {
    return completed.containsKey(operationId);
  }

  /** Whether every operation this one depends on has completed, and it has not run itself. */
  public boolean isRunnable(final OperationId operationId) {
    final var planned = operations.get(operationId);
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
    operations.forEach(
        (operationId, planned) -> {
          if (planned.operation().memberId().equals(memberId) && isRunnable(operationId)) {
            runnable.put(operationId, planned.operation());
          }
        });
    return runnable;
  }

  public boolean hasPendingChanges() {
    return completed.size() < operations.size();
  }

  /**
   * The operations still to run, in operation-id order. Ordering is positional rather than by
   * arrival so that two brokers rendering the same converged plan produce identical lists.
   */
  public List<ClusterConfigurationChangeOperation> pendingOperations() {
    final var pending = new ArrayList<ClusterConfigurationChangeOperation>();
    operations.forEach(
        (operationId, planned) -> {
          if (!isComplete(operationId)) {
            pending.add(planned.operation());
          }
        });
    return List.copyOf(pending);
  }

  /** The operations already run, in operation-id order. See {@link #pendingOperations()}. */
  public List<CompletedOperation> completedOperations() {
    final var done = new ArrayList<CompletedOperation>();
    operations.forEach(
        (operationId, planned) -> {
          final var at = completed.get(operationId);
          if (at != null) {
            done.add(new CompletedOperation(planned.operation(), at));
          }
        });
    return List.copyOf(done);
  }

  /**
   * What each incomplete operation is still waiting for. Empty value means it is runnable. Exists
   * so a stalled plan can be diagnosed — with no step index to read, "which operations are
   * outstanding and on what" is the only way an operator can tell why nothing is moving.
   */
  public SortedMap<OperationId, SortedSet<OperationId>> blockedBy() {
    final SortedMap<OperationId, SortedSet<OperationId>> blocked = new TreeMap<>();
    operations.forEach(
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

  // ---------------------------------------------------------------------------
  // Progress
  // ---------------------------------------------------------------------------

  /**
   * Records that {@code operationId} finished. Package-private: only a sub-configuration may move a
   * plan, so that the accompanying member-state update happens in the same transition.
   *
   * @throws IllegalStateException if the operation is unknown or was not runnable
   */
  DependencyChangePlan completeOperation(final OperationId operationId) {
    if (!operations.containsKey(operationId)) {
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
    return new DependencyChangePlan(id, status, startedAt, operations, updated);
  }

  /**
   * Merges two copies of this plan seen by different brokers.
   *
   * <p>A flat union: {@code operations} is fixed and identical, and each completion has a single
   * writer, so nothing can be lost. The earliest timestamp wins a collision so that a broker
   * re-stamping after a restart still converges with its peers.
   */
  public DependencyChangePlan merge(final @Nullable DependencyChangePlan other) {
    if (other == null) {
      return this;
    }
    if (id != other.id) {
      // Not reachable in practice — a plan id comes from the enclosing sub-configuration's version,
      // and that merge only delegates here when the versions are equal. Resolved deterministically
      // rather than by favouring the receiver, so that merge stays commutative.
      return id > other.id ? this : other;
    }
    final var merged = new TreeMap<>(completed);
    other.completed.forEach(
        (operationId, at) ->
            merged.merge(operationId, at, (mine, theirs) -> mine.isBefore(theirs) ? mine : theirs));
    return new DependencyChangePlan(id, status, startedAt, operations, merged);
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

  public CompletedChange cancel() {
    return new CompletedChange(id, Status.CANCELLED, startedAt, Instant.now());
  }

  // ---------------------------------------------------------------------------
  // Validation
  // ---------------------------------------------------------------------------

  /**
   * A cycle would leave every operation in it permanently un-runnable, and the plan would stall
   * with no error — so it is rejected at construction rather than discovered in production.
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
          "The change plan has a dependency cycle; operations never runnable: "
              + remaining.entrySet().stream()
                  .filter(e -> e.getValue() > 0)
                  .map(Map.Entry::getKey)
                  .toList());
    }
  }

  /**
   * Rejects a plan in which two operations that may run at the same time write the same state.
   *
   * <p>"May run at the same time" means neither is reachable from the other — there is no path of
   * dependencies ordering them. Their write sets must then be disjoint, or the two brokers applying
   * them race and the merge silently keeps one result.
   *
   * <p>This is a safety net, not the ordering mechanism. It cannot see an ordering that lives
   * outside the configuration: a bootstrap and the joins that follow it write different members'
   * entries, and six operations write nothing at all (see {@link WriteSets}). Those are ordered
   * only by the edges a transformer declares.
   */
  private void validateConcurrentOperationsDoNotConflict() {
    final Map<OperationId, Set<OperationId>> memo = new HashMap<>();
    final var ids = List.copyOf(operations.keySet());
    for (final var operationId : ids) {
      dependenciesOf(operationId, memo);
    }

    for (int i = 0; i < ids.size(); i++) {
      for (int j = i + 1; j < ids.size(); j++) {
        final var first = ids.get(i);
        final var second = ids.get(j);
        final var firstDependsOn = memo.getOrDefault(first, Set.of());
        final var secondDependsOn = memo.getOrDefault(second, Set.of());
        if (firstDependsOn.contains(second) || secondDependsOn.contains(first)) {
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
    // Placed before recursion so a cycle cannot recurse forever. validateAcyclic already rejected
    // cycles, but this must not become the thing that hangs if that ever stops being true.
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

  /** Assigns ids in insertion order and lets a transformer name the edges it needs. */
  public static final class Builder {

    private final SortedMap<OperationId, PlannedOperation> operations = new TreeMap<>();
    private int nextId = 0;

    /** Adds an operation with no dependencies — runnable immediately. */
    public OperationId add(final ClusterConfigurationChangeOperation operation) {
      return add(operation, Set.of());
    }

    public OperationId add(
        final ClusterConfigurationChangeOperation operation, final Set<OperationId> dependsOn) {
      final var operationId = OperationId.of(nextId++);
      operations.put(operationId, new PlannedOperation(operation, new TreeSet<>(dependsOn)));
      return operationId;
    }

    public Optional<OperationId> lastAdded() {
      return operations.isEmpty() ? Optional.empty() : Optional.of(operations.lastKey());
    }

    public DependencyChangePlan build(final long id) {
      return DependencyChangePlan.init(id, operations);
    }
  }
}
