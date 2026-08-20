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
 *       resolved via {@link PhasedChangePlan#merge}. An id present on only one side is dropped
 *       (treated as already resolved on the <em>other</em> side) if either: (a) the other side's
 *       own {@code nextId}/{@code pending} shows it as resolved (see {@link
 *       #isResolvedAccordingToItself}) — this is the primary, effectively permanent signal, since a
 *       single writer (the coordinator) never re-admits an id, so its absence, once witnessed, is
 *       never "forgotten" the way a bounded window would be; or (b) it is present in the merged
 *       {@link #history} — a weaker, bounded fallback that only matters for the one id the counter
 *       doesn't cover, {@link PhasedChangePlan#RESTORED_PLAN_ID}.
 *   <li>{@code history}: union of both sides' entries by id (a given id completes at most once, so
 *       this is a simple union, not a per-id merge), trimmed to a <em>fixed</em> {@link
 *       #DEFAULT_HISTORY_LIMIT} — not a value derived from either side's current size. Top-k
 *       selection over a union is commutative/associative/idempotent only when k is fixed; deriving
 *       it from input sizes (as this used to) makes the result depend on which nodes happen to
 *       merge in which order, so two brokers that have each seen every completion can permanently
 *       disagree about which ones they remember.
 * </ul>
 */
@NullMarked
public record PhasedChangeState(
    long nextId, Map<Long, PhasedChangePlan> pending, List<CompletedPhasedChange> history) {

  /** Default number of completed changes retained in {@link #history}. See {@link #wasIssued}. */
  public static final int DEFAULT_HISTORY_LIMIT = 10;

  public static int historyLimit = DEFAULT_HISTORY_LIMIT;

  /**
   * Total order over {@link CompletedPhasedChange}, most-recently-completed last. Ties on {@code
   * completedAt} (possible with coarse clock resolution, or across two different nodes' clocks) are
   * broken by id, so ordering/trimming is fully deterministic regardless of which node computes it
   * — required for {@link #merge} to converge.
   */
  private static final Comparator<CompletedPhasedChange> COMPLETION_ORDER =
      Comparator.comparing(CompletedPhasedChange::completedAt)
          .thenComparingLong(CompletedPhasedChange::id);

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

  public static void setHistoryLimit(final int limit) {
    if (limit < 0) {
      throw new IllegalArgumentException("historyLimit must be non-negative, got " + limit);
    }
    historyLimit = limit;
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
   * Returns {@code true} if this state's own bookkeeping shows {@code id} as resolved: it was
   * issued by the counter ({@code id < nextId}) and is not currently in {@link #pending}.
   *
   * <p>Deliberately excludes {@link PhasedChangePlan#RESTORED_PLAN_ID}: that sentinel bypasses the
   * counter entirely (it is assigned directly during legacy migration, not via {@link #initPlan}),
   * so {@code nextId} advancing past it proves nothing about its history — every state's {@code
   * nextId} is at least {@link PhasedChangePlan#INITIAL_PLAN_ID}, so this signal would otherwise
   * unconditionally (and wrongly) call it resolved even for a state that has never witnessed it.
   * {@link #merge} falls back to the (bounded, but adequate for a once-per-cluster-lifetime event)
   * {@link #history}-based check for that one id.
   *
   * <p>For every other id, this is a permanent signal, unlike {@link #history}: a single writer
   * (the coordinator) only ever removes a counter-issued id from {@link #pending} by explicitly
   * resolving it, so once some state has witnessed {@code id < nextId} without it being pending,
   * that fact never becomes false again, no matter how many unrelated ids are later issued.
   */
  private boolean isResolvedAccordingToItself(final long id) {
    return id >= PhasedChangePlan.INITIAL_PLAN_ID && id < nextId && !pending.containsKey(id);
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
    return history.stream().max(COMPLETION_ORDER);
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
    sorted.sort(COMPLETION_ORDER);
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
    // Fixed limit, not derived from either side's size: top-k of a union only converges regardless
    // of merge order when k is constant. See the class javadoc.
    final var mergedHistory = trimHistory(List.copyOf(mergedHistoryById.values()), historyLimit);

    final Map<Long, PhasedChangePlan> mergedPending = new HashMap<>();
    pending.forEach(
        (id, plan) -> {
          if (!other.isResolvedAccordingToItself(id) && !mergedHistoryById.containsKey(id)) {
            mergedPending.merge(id, plan, (a, b) -> a.merge(b));
          }
        });
    other.pending.forEach(
        (id, plan) -> {
          if (!isResolvedAccordingToItself(id) && !mergedHistoryById.containsKey(id)) {
            mergedPending.merge(id, plan, (a, b) -> a.merge(b));
          }
        });

    return new PhasedChangeState(mergedNextId, mergedPending, mergedHistory);
  }
}
