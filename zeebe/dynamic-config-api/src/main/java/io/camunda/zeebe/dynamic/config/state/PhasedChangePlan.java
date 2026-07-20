/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 */
@NullMarked
public record PhasedChangePlan(
    long id, int currentPhaseIndex, List<Phase> phases, Instant startedAt) {

  public PhasedChangePlan {
    if (id <= 0) {
      throw new IllegalArgumentException("id must be positive");
    }
    Objects.checkIndex(currentPhaseIndex, phases.size());
    phases = List.copyOf(phases);
  }

  public static PhasedChangePlan init(
      final long id, final List<Phase> phases, final Instant startedAt) {
    return new PhasedChangePlan(id, 0, phases, startedAt);
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
   * A phase whose operations are activated into {@link GlobalConfiguration#pendingChanges} when
   * this phase starts.
   */
  public record GlobalPhase(List<GlobalChangeOperation> operations) implements Phase {
    public GlobalPhase {
      operations = List.copyOf(operations);
    }
  }

  /**
   * A phase whose operations are activated atomically into the {@code pendingChanges} of each named
   * partition group.
   */
  public record PartitionGroupParallelPhase(
      Map<String, List<PartitionGroupOperation>> groupOperations) implements Phase {
    public PartitionGroupParallelPhase {
      groupOperations =
          groupOperations.entrySet().stream()
              .collect(
                  Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
    }
  }

  /**
   * A single phase in a {@link PhasedChangePlan}. Exactly one of the two permitted subtypes is
   * active.
   */
  public sealed interface Phase permits GlobalPhase, PartitionGroupParallelPhase {}
}
