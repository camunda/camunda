/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;

/**
 * A pre-computed sequence of phases for cluster-spanning operations that touch both {@link
 * GlobalConfiguration} (broker lifecycle) and one or more {@link PartitionGroupConfiguration}s
 * (partition assignment).
 *
 * <p>All phases are computed at plan creation time. Activating a phase copies its operations into
 * the corresponding sub-config {@code pendingChanges}; the phase list itself is never mutated.
 *
 * <p>Merge rule (gossip convergence): if plan IDs are equal, the higher {@code currentPhaseIndex}
 * wins. If plan IDs differ, the higher plan ID always wins — plan IDs are monotonically increasing,
 * so the higher ID is always the newer plan.
 *
 * <p>{@link #scope()} identifies which sub-configuration(s) this plan touches, derived from its
 * phases: a plan with any {@link GlobalPhase} has {@link Global} scope (cluster-wide, conflicts
 * with everything); otherwise it has {@link Groups} scope, the union of every partition group named
 * across its {@link PartitionGroupPhase}s. Multiple plans with disjoint {@link Groups} scopes may
 * be pending concurrently; see {@link PhasedChangeState}.
 */
@NullMarked
public record PhasedChangePlan(
    long id, int currentPhaseIndex, List<Phase> phases, Instant startedAt) {

  public static final long RESTORED_PLAN_ID = 0;
  public static final long INITIAL_PLAN_ID = 1;

  public PhasedChangePlan {
    if (id < 0) {
      throw new IllegalArgumentException("id must be non-negative");
    }
    Objects.checkIndex(currentPhaseIndex, phases.size());
    phases = List.copyOf(phases);
  }

  public static PhasedChangePlan init(
      final long id, final List<Phase> phases, final Instant startedAt) {
    return new PhasedChangePlan(id, 0, phases, startedAt);
  }

  /**
   * Creates a plan migrated from a legacy restore change plan, assigning it {@link
   * #RESTORED_PLAN_ID} — the legacy sentinel id ({@code ClusterChangePlan.RESTORE_CHANGE_ID = -2})
   * cannot be preserved as-is since this record requires a non-negative id. See {@link
   * #hasRestorePlanId()} and {@link CurrentClusterConfiguration#isAfterRestore()}.
   */
  public static PhasedChangePlan initForRestore(final List<Phase> phases, final Instant startedAt) {
    return new PhasedChangePlan(RESTORED_PLAN_ID, 0, phases, startedAt);
  }

  /** Returns the currently active phase. */
  public Phase currentPhase() {
    return phases.get(currentPhaseIndex);
  }

  /** Returns {@code true} if there is at least one phase after the current one. */
  public boolean hasNextPhase() {
    return currentPhaseIndex < phases.size() - 1;
  }

  /** Returns a new plan with {@code currentPhaseIndex} incremented by one. */
  public PhasedChangePlan withNextPhase() {
    if (!hasNextPhase()) {
      throw new IllegalStateException(
          "Cannot advance past the last phase (index %d)".formatted(currentPhaseIndex));
    }
    return new PhasedChangePlan(id, currentPhaseIndex + 1, phases, startedAt);
  }

  /**
   * Merges this plan with {@code other} using gossip-convergence semantics.
   *
   * <ul>
   *   <li>Same plan ID → higher {@code currentPhaseIndex} wins.
   *   <li>Different plan IDs → higher plan ID wins (IDs are monotonically increasing, higher =
   *       newer).
   * </ul>
   */
  public PhasedChangePlan merge(final PhasedChangePlan other) {
    if (id == other.id) {
      if (!phases.equals(other.phases())) {
        throw new IllegalStateException(
            "Cannot merge plans with the same ID but different phases: %s vs %s"
                .formatted(phases, other.phases()));
      }
      return currentPhaseIndex >= other.currentPhaseIndex ? this : other;
    }
    return id > other.id ? this : other;
  }

  /**
   * Returns {@code true} if this plan's id is {@link #RESTORED_PLAN_ID} — i.e. it was migrated from
   * a legacy restore change plan. Note this is a necessary but not sufficient condition for {@link
   * CurrentClusterConfiguration#isAfterRestore()}, which additionally checks the plan's shape.
   */
  public boolean hasRestorePlanId() {
    return id == RESTORED_PLAN_ID;
  }

  /**
   * Returns the scope of this plan: {@link Global} if any phase is a {@link GlobalPhase} (the plan
   * touches cluster-wide broker lifecycle, and therefore conflicts with every other plan);
   * otherwise {@link Groups}, the union of every partition group id named across the plan's
   * partition-group phases.
   */
  public Scope scope() {
    return scopeOf(phases);
  }

  /**
   * Same as {@link #scope()}, computable before a plan (and its id) exists.
   *
   * <p>Switches over every phase kind rather than filtering for the one it cares about. The result
   * feeds {@link #conflicts(Scope, Scope)}, which decides whether two plans may be pending
   * concurrently — a phase kind added later and silently contributing no group ids would make its
   * plan look like it touches nothing, admitting a second plan onto the very groups it is changing.
   * That failure is invisible at runtime (a rejection that should happen, doesn't), so it has to be
   * a compile error instead.
   */
  public static Scope scopeOf(final List<Phase> phases) {
    final Set<String> groupIds = new HashSet<>();
    for (final var phase : phases) {
      switch (phase) {
        case final GlobalPhase ignored -> {
          // Cluster-wide: conflicts with everything, so no group set can narrow it.
          return new Global();
        }
        case final PartitionGroupPhase groupPhase ->
            groupIds.addAll(groupPhase.groupGraphs().keySet());
      }
    }
    return new Groups(groupIds);
  }

  /**
   * Returns {@code true} if {@code a} and {@code b} target overlapping sub-configurations and
   * therefore cannot be pending concurrently: either one of them is {@link Global} (cluster-wide,
   * conflicts with anything), or both are {@link Groups} and share at least one group id.
   */
  public static boolean conflicts(final Scope a, final Scope b) {
    if (a instanceof Global || b instanceof Global) {
      return true;
    }
    final var groupsA = ((Groups) a).groupIds();
    final var groupsB = ((Groups) b).groupIds();
    return !Collections.disjoint(groupsA, groupsB);
  }

  /** Cluster-wide scope: touches {@link GlobalConfiguration}, conflicts with every other plan. */
  public record Global() implements Scope {}

  /** Scope limited to the named partition groups. */
  public record Groups(Set<String> groupIds) implements Scope {
    public Groups {
      groupIds = Set.copyOf(groupIds);
    }
  }

  /**
   * A phase whose operations are activated into {@link GlobalConfiguration#pendingChanges} when
   * this phase starts.
   */
  public record GlobalPhase(List<GlobalChangeOperation> operations) implements Phase {
    public GlobalPhase {
      operations = List.copyOf(operations);
    }
  }

  /**
   * A phase whose named partition groups each run an {@link OperationGraph}, activated atomically
   * into the {@code pendingChanges} of every named group.
   *
   * <p>The graph is the only execution model a phase has: an operation runs once everything it
   * depends on has completed, so concurrency is the <em>absence of an edge</em>. A transformer that
   * has no parallelism to express builds its phase with {@link #sequential(Map)}, which chains
   * every operation behind its predecessor and therefore behaves exactly like the one-at-a-time
   * queue that preceded it.
   */
  public record PartitionGroupPhase(Map<String, OperationGraph> groupGraphs) implements Phase {
    public PartitionGroupPhase {
      groupGraphs = Map.copyOf(groupGraphs);
    }

    /** A phase in which each group runs its operations strictly one after another. */
    public static PartitionGroupPhase sequential(
        final Map<String, List<PartitionGroupOperation>> groupOperations) {
      return new PartitionGroupPhase(
          groupOperations.entrySet().stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      Map.Entry::getKey, e -> OperationGraph.sequential(e.getValue()))));
    }

    /** {@link #sequential(Map)} for the common single-group case. */
    public static PartitionGroupPhase sequential(
        final String groupId, final List<PartitionGroupOperation> operations) {
      return sequential(Map.of(groupId, operations));
    }

    /** Every operation of this phase per group, flattening the dependencies away for reporting. */
    public Map<String, List<PartitionGroupOperation>> groupOperations() {
      return groupGraphs.entrySet().stream()
          .collect(
              Collectors.toUnmodifiableMap(
                  Map.Entry::getKey,
                  e ->
                      e.getValue().inOrder().stream()
                          .map(PartitionGroupOperation.class::cast)
                          .toList()));
    }
  }

  /** The sub-configuration(s) a {@link PhasedChangePlan} touches. See {@link #scope()}. */
  public sealed interface Scope permits Global, Groups {}

  public sealed interface Phase permits GlobalPhase, PartitionGroupPhase {}
}
