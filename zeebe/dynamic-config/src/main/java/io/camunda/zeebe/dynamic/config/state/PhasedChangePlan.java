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
import java.util.Optional;
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
 * <p>One legitimate source of same-ID plans with unequal phases: {@link
 * CurrentClusterConfiguration#fromLegacy} re-derives a plan's phases from a legacy {@code
 * ClusterChangePlan}'s <em>remaining</em> operations every time it is called, so a plan
 * reconstructed this way after some operations already completed carries fewer operations in its
 * current phase than the same plan tracked natively (where the phase list stays fixed and progress
 * is tracked separately). When one side's phases are otherwise identical to the other's except for
 * such a trailing-operations shrink within the current phase, that side is recognized as a stale,
 * lossily-reconstructed view of the same phase and the more complete side wins; any other kind of
 * mismatch (different phase kinds/counts, or operations that aren't a trailing subset) still
 * throws.
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
        final var morePhases = moreCompletePhases(phases, other.phases());
        if (morePhases.isEmpty()) {
          throw new IllegalStateException(
              "Cannot merge plans with the same ID but different phases: %s vs %s"
                  .formatted(phases, other.phases()));
        }
        return new PhasedChangePlan(
            id, Math.max(currentPhaseIndex, other.currentPhaseIndex), morePhases.get(), startedAt);
      }
      return currentPhaseIndex >= other.currentPhaseIndex ? this : other;
    }
    return id > other.id ? this : other;
  }

  /**
   * If {@code a} and {@code b} differ only in that the current phase's operations on one side are a
   * trailing subset of the operations on the other (see the class-level javadoc for why this
   * happens), returns the side with the fuller operations list. Otherwise returns empty, meaning
   * the mismatch is a genuine conflict.
   */
  private static Optional<List<Phase>> moreCompletePhases(
      final List<Phase> a, final List<Phase> b) {
    if (a.size() != b.size()) {
      return Optional.empty();
    }
    List<Phase> fuller = null;
    for (int i = 0; i < a.size(); i++) {
      final var phaseA = a.get(i);
      final var phaseB = b.get(i);
      if (phaseA.equals(phaseB)) {
        continue;
      }
      final var fullerPhase = fullerPhase(phaseA, phaseB);
      if (fullerPhase.isEmpty() || fuller != null) {
        // either a genuine mismatch, or a second phase already differed - not the single-phase
        // trailing-shrink case this method recognizes
        return Optional.empty();
      }
      fuller = fullerPhase.get() == phaseA ? a : b;
    }
    return Optional.ofNullable(fuller);
  }

  private static Optional<Phase> fullerPhase(final Phase a, final Phase b) {
    if (a instanceof GlobalPhase globalA && b instanceof GlobalPhase globalB) {
      // a is the trailing subset of b => b is fuller, and vice versa
      return isTrailingSubset(globalA.operations(), globalB.operations())
          ? Optional.of(b)
          : isTrailingSubset(globalB.operations(), globalA.operations())
              ? Optional.of(a)
              : Optional.empty();
    }
    if (a instanceof PartitionGroupParallelPhase partitionA
        && b instanceof PartitionGroupParallelPhase partitionB) {
      if (!partitionA.groupOperations().keySet().equals(partitionB.groupOperations().keySet())) {
        return Optional.empty();
      }
      boolean aIsFuller = false;
      boolean bIsFuller = false;
      for (final var entry : partitionA.groupOperations().entrySet()) {
        final var opsA = entry.getValue();
        // keySets were checked equal above, so this is always present
        final var opsB = Objects.requireNonNull(partitionB.groupOperations().get(entry.getKey()));
        if (opsA.equals(opsB)) {
          continue;
        }
        // opsA is the trailing subset of opsB => b is fuller for this group, and vice versa
        if (isTrailingSubset(opsA, opsB)) {
          bIsFuller = true;
        } else if (isTrailingSubset(opsB, opsA)) {
          aIsFuller = true;
        } else {
          return Optional.empty();
        }
      }
      if (aIsFuller == bIsFuller) {
        // either no group differed after all, or groups disagree on which side is fuller
        return Optional.empty();
      }
      return Optional.of(aIsFuller ? a : b);
    }
    return Optional.empty();
  }

  /** Returns {@code true} if {@code shorter} equals the trailing elements of {@code longer}. */
  private static <T> boolean isTrailingSubset(final List<T> shorter, final List<T> longer) {
    if (shorter.size() >= longer.size()) {
      return false;
    }
    return longer.subList(longer.size() - shorter.size(), longer.size()).equals(shorter);
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
