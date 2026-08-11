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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

/**
 * Holds every currently pending {@link PhasedChangePlan}, keyed by id, plus a bounded window of
 * recently completed changes.
 *
 * <p>Plan ids are issued from a single monotonically increasing counter ({@link #nextId}), never
 * derived from the completed-change history. This lets multiple plans be pending at once (with
 * distinct ids) while still giving every id an unambiguous, cluster-wide-agreed meaning: an id is
 * either currently {@link #pending}, or resolved (present in {@link #history}, or aged out of the
 * bounded window — in which case it is <em>known</em> to have resolved, since {@code nextId} only
 * ever advances past ids that were tracked in {@code pending} at some point), or, if {@code id >=
 * nextId}, never issued at all. See {@link #wasIssued(long)}.
 *
 * <p>At most one pending plan may target a given partition group at a time: a new plan is only
 * admitted (see {@link #initPlan}) if its {@link PhasedChangePlan.Scope} does not {@link
 * PhasedChangePlan#conflicts} with any already-pending plan's scope. A {@link
 * PhasedChangePlan.Global}-scoped plan conflicts with every other plan and vice versa, so at most
 * one plan is ever pending while any global-scoped plan is pending.
 *
 * <p>Merge semantics (gossip convergence):
 *
 * <ul>
 *   <li>{@code nextId}: the higher value wins (it only ever increases).
 *   <li>{@code pending}: union of both sides' entries by id; an id present on both sides is
 *       resolved via {@link PhasedChangePlan#merge}; an id resolved (present in the merged {@link
 *       #history}) on either side is dropped from the merged pending map.
 *   <li>{@code history}: union of both sides' entries by id (a given id completes at most once, so
 *       this is a simple union, not a per-id merge), trimmed back to (at most) the larger side's
 *       size, oldest {@code completedAt} first, so a plain union does not grow unboundedly.
 * </ul>
 */
@NullMarked
public record PhasedChangeState(
    long nextId, Map<Long, PhasedChangePlan> pending, List<CompletedPhasedChange> history) {

  /** Default number of completed changes retained in {@link #history}. See {@link #wasIssued}. */
  public static final int DEFAULT_HISTORY_LIMIT = 10;

  public PhasedChangeState {
    requireNonNull(pending, "pending must not be null");
    requireNonNull(history, "history must not be null");
    if (nextId < PhasedChangePlan.INITIAL_PLAN_ID) {
      throw new IllegalArgumentException(
          "nextId must be at least %d, got %d".formatted(PhasedChangePlan.INITIAL_PLAN_ID, nextId));
    }
    pending.forEach(
        (id, plan) -> {
          if (!id.equals(plan.id())) {
            throw new IllegalArgumentException(
                "pending map key %d does not match plan id %d".formatted(id, plan.id()));
          }
          if (id >= nextId) {
            throw new IllegalArgumentException(
                "pending plan id %d must be less than nextId %d".formatted(id, nextId));
          }
        });
    history.forEach(
        c -> {
          if (c.id() >= nextId) {
            throw new IllegalArgumentException(
                "history entry id %d must be less than nextId %d".formatted(c.id(), nextId));
          }
        });
    pending = Map.copyOf(pending);
    history = List.copyOf(history);
  }

  public static PhasedChangeState empty() {
    return new PhasedChangeState(PhasedChangePlan.INITIAL_PLAN_ID, Map.of(), List.of());
  }

  /**
   * Returns {@code true} if {@code id} was ever issued by this (or a merged-in) counter — i.e. it
   * is either currently pending, resolved and still within the retained {@link #history} window, or
   * resolved and aged out of that window. The only ids for which this returns {@code false} are
   * ones the counter never handed out, including any id at or beyond {@link #nextId}.
   */
  public boolean wasIssued(final long id) {
    return id < nextId;
  }

  /**
   * Returns the single pending plan, assuming exactly one is pending. Convenience for call sites
   * (tests, and legacy single-plan flows such as {@link CurrentClusterConfiguration#fromLegacy})
   * that know only one plan can be in flight.
   *
   * @throws IllegalStateException if zero or more than one plan is pending
   */
  public PhasedChangePlan onlyPending() {
    if (pending.size() != 1) {
      throw new IllegalStateException(
          "Expected exactly one pending plan, found %d".formatted(pending.size()));
    }
    return pending.values().iterator().next();
  }

  /** Returns the completed change for {@code id}, if it is still within the retained window. */
  public Optional<CompletedPhasedChange> historyFor(final long id) {
    return history.stream().filter(c -> c.id() == id).findFirst();
  }

  /**
   * Convenience accessor returning the most recently completed change, if any is retained. Used by
   * consumers that only care about the latest completion (e.g. observability), not the full
   * history. Ordered by {@code completedAt}, not by id — once plans can run concurrently, a lower
   * id (admitted earlier) can finish after a higher id (admitted later but faster), so id order no
   * longer tracks completion order.
   */
  public Optional<CompletedPhasedChange> lastChange() {
    return history.stream().max(Comparator.comparing(CompletedPhasedChange::completedAt));
  }

  /**
   * Creates a new pending plan from {@code phases}, assigning it the next id from the monotonic
   * counter.
   *
   * @throws IllegalStateException if {@code phases}' scope conflicts with an already-pending plan
   */
  public PhasedChangeState initPlan(final List<PhasedChangePlan.Phase> phases) {
    final var plan = PhasedChangePlan.init(nextId, phases, Instant.now());
    final var scope = plan.scope();
    for (final var existing : pending.values()) {
      if (PhasedChangePlan.conflicts(scope, existing.scope())) {
        throw new IllegalStateException(
            "Cannot init a new plan: its scope conflicts with pending plan '%d': %s"
                .formatted(existing.id(), existing));
      }
    }
    final var newPending = new HashMap<>(pending);
    newPending.put(plan.id(), plan);
    return new PhasedChangeState(nextId + 1, newPending, history);
  }

  /**
   * Replaces the pending plan for {@code advanced.id()} with {@code advanced} (e.g. after moving to
   * the next phase).
   *
   * @throws IllegalStateException if no plan with that id is currently pending
   */
  public PhasedChangeState withAdvancedPlan(final PhasedChangePlan advanced) {
    if (!pending.containsKey(advanced.id())) {
      throw new IllegalStateException(
          "Cannot advance plan '%d': no such plan is pending".formatted(advanced.id()));
    }
    final var newPending = new HashMap<>(pending);
    newPending.put(advanced.id(), advanced);
    return new PhasedChangeState(nextId, newPending, history);
  }

  /**
   * Moves the pending plan {@code id} into {@link #history} with the given terminal {@code status},
   * trimming the history to at most {@code historyLimit} entries, oldest {@code completedAt} first.
   *
   * @throws IllegalStateException if no plan with that id is currently pending
   */
  public PhasedChangeState completePlan(
      final long id, final PhasedChangePlanStatus status, final int historyLimit) {
    final var plan = pending.get(id);
    if (plan == null) {
      throw new IllegalStateException(
          "Cannot complete plan '%d': no such plan is pending".formatted(id));
    }
    final var completed = new CompletedPhasedChange(id, status, plan.startedAt(), Instant.now());
    final var newPending = new HashMap<>(pending);
    newPending.remove(id);
    final var newHistory = trimHistory(concat(history, completed), historyLimit);
    return new PhasedChangeState(nextId, newPending, newHistory);
  }

  /**
   * Returns {@code entries} sorted by {@code completedAt} ascending and trimmed to at most {@code
   * limit} entries, dropping the oldest (lowest {@code completedAt}) first.
   */
  private static List<CompletedPhasedChange> trimHistory(
      final List<CompletedPhasedChange> entries, final int limit) {
    final var sorted = new ArrayList<>(entries);
    sorted.sort(Comparator.comparing(CompletedPhasedChange::completedAt));
    final int from = Math.max(0, sorted.size() - Math.max(limit, 0));
    return List.copyOf(sorted.subList(from, sorted.size()));
  }

  private static List<CompletedPhasedChange> concat(
      final List<CompletedPhasedChange> entries, final CompletedPhasedChange extra) {
    final var result = new ArrayList<>(entries);
    result.add(extra);
    return result;
  }

  /**
   * Merges this state with {@code other} using gossip-convergence semantics; see the class javadoc.
   */
  public PhasedChangeState merge(final PhasedChangeState other) {
    final long mergedNextId = Math.max(nextId, other.nextId);

    final Map<Long, CompletedPhasedChange> mergedHistoryById = new HashMap<>();
    history.forEach(c -> mergedHistoryById.put(c.id(), c));
    other.history.forEach(c -> mergedHistoryById.merge(c.id(), c, (a, b) -> a));
    // Bounded to the larger side's current size (not an explicit limit, which merge has no access
    // to): each side was already trimmed to its own configured limit by completePlan, so this keeps
    // a plain gossip union from growing history without bound across repeated merges.
    final var mergedHistory =
        trimHistory(
            List.copyOf(mergedHistoryById.values()),
            Math.max(history.size(), other.history.size()));

    final Map<Long, PhasedChangePlan> mergedPending = new HashMap<>();
    pending.forEach((id, plan) -> mergedPending.merge(id, plan, (a, b) -> a.merge(b)));
    other.pending.forEach((id, plan) -> mergedPending.merge(id, plan, (a, b) -> a.merge(b)));
    mergedHistoryById.keySet().forEach(mergedPending::remove);

    return new PhasedChangeState(mergedNextId, mergedPending, mergedHistory);
  }
}
