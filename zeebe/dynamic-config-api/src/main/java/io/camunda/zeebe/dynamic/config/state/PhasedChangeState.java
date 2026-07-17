/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;

/**
 * Holds at most one pending {@link PhasedChangePlan} together with the last completed change.
 *
 * <p>The next plan ID is always derived as {@code lastChange.id() + 1} (or {@code 1} when no
 * previous change exists), so callers cannot accidentally supply a non-monotonic ID.
 *
 * <p>Merge semantics (gossip convergence):
 *
 * <ul>
 *   <li>{@code pending}: delegates to {@link PhasedChangePlan#merge(PhasedChangePlan)}.
 *   <li>{@code lastChange}: the side with the higher ID wins.
 * </ul>
 */
@NullMarked
public record PhasedChangeState(
    Optional<PhasedChangePlan> pending, Optional<CompletedPhasedChange> lastChange) {

  public PhasedChangeState {
    requireNonNull(pending, "pending must not be null");
    requireNonNull(lastChange, "lastChange must not be null");
    pending.ifPresent(
        p ->
            lastChange.ifPresent(
                lc -> {
                  if (p.id() <= lc.id()) {
                    throw new IllegalArgumentException(
                        "pending plan ID %d must be greater than last completed change ID %d"
                            .formatted(p.id(), lc.id()));
                  }
                }));
  }

  public static PhasedChangeState empty() {
    return new PhasedChangeState(Optional.empty(), Optional.empty());
  }

  /**
   * Creates a new pending plan from {@code phases}, deriving its ID from {@code lastChange} to
   * guarantee monotonicity.
   *
   * @throws IllegalStateException if a plan is already pending
   */
  public PhasedChangeState initPlan(final List<PhasedChangePlan.Phase> phases) {
    if (pending.isPresent()) {
      throw new IllegalStateException(
          "Cannot init a new plan while one is already pending: " + pending.get());
    }
    final long nextId = lastChange.map(c -> c.id() + 1).orElse(1L);
    final var plan = PhasedChangePlan.init(nextId, phases, Instant.now());
    return new PhasedChangeState(Optional.of(plan), lastChange);
  }

  /**
   * Moves the pending plan to {@code lastChange} with the given terminal {@code status}.
   *
   * @throws IllegalStateException if no plan is currently pending
   */
  public PhasedChangeState completePlan(final PhasedChangePlanStatus status) {
    if (pending.isEmpty()) {
      throw new IllegalStateException("Cannot complete a plan when none is pending");
    }
    final var plan = pending.get();
    final var completed =
        new CompletedPhasedChange(plan.id(), status, plan.startedAt(), Instant.now());
    return new PhasedChangeState(Optional.empty(), Optional.of(completed));
  }

  /**
   * Merges this state with {@code other} using gossip-convergence semantics.
   *
   * <p>The last completed change is resolved by taking the higher ID. The pending plan is resolved
   * by {@link PhasedChangePlan#merge(PhasedChangePlan)} and then dropped if its ID is no greater
   * than the merged last-change ID (the plan was already completed on one side).
   */
  public PhasedChangeState merge(final PhasedChangeState other) {
    final Optional<CompletedPhasedChange> mergedLastChange =
        Stream.of(lastChange, other.lastChange)
            .flatMap(Optional::stream)
            .reduce((a, b) -> a.id() >= b.id() ? a : b);

    final Optional<PhasedChangePlan> mergedPending =
        Stream.of(pending, other.pending)
            .flatMap(Optional::stream)
            .reduce(PhasedChangePlan::merge)
            .filter(p -> mergedLastChange.map(lc -> p.id() > lc.id()).orElse(true));

    return new PhasedChangeState(mergedPending, mergedLastChange);
  }
}
