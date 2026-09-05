/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateIncarnationNumberOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PhasedChangeStateTest {

  private static final MemberId MEMBER_1 = MemberId.from("1");

  private final GlobalPhase globalPhase =
      new GlobalPhase(List.of(new MemberJoinOperation(MEMBER_1)));
  private final List<PhasedChangePlan.Phase> twoGlobalPhases = List.of(globalPhase, globalPhase);

  private List<PhasedChangePlan.Phase> groupPhase(final String groupId) {
    return List.of(
        PartitionGroupPhase.sequential(
            Map.of(groupId, List.of(new UpdateIncarnationNumberOperation(MEMBER_1)))));
  }

  @Nested
  class InitPlan {

    @Test
    void shouldStartWithIdOneWhenEmpty() {
      // given
      final var state = PhasedChangeState.empty();

      // when
      final var next = state.initPlan(twoGlobalPhases);

      // then
      assertThat(next.pending()).containsOnlyKeys(1L);
      assertThat(next.pending().get(1L).id()).isEqualTo(1L);
    }

    @Test
    void shouldIssueIdsFromMonotonicCounterNotFromHistory() {
      // given — nextId only ever advances forward, independent of what's in history
      final var state =
          PhasedChangeState.empty()
              .initPlan(twoGlobalPhases)
              .completePlan(1L, PhasedChangePlanStatus.COMPLETED, 10);

      // when
      final var next = state.initPlan(twoGlobalPhases);

      // then
      assertThat(next.pending().keySet()).containsExactly(2L);
    }

    @Test
    void shouldAllowConcurrentPendingPlansWithDisjointGroupScopes() {
      // given
      final var withTenantA = PhasedChangeState.empty().initPlan(groupPhase("tenantA"));

      // when — a second, non-conflicting plan for a different tenant
      final var withBoth = withTenantA.initPlan(groupPhase("tenantB"));

      // then
      assertThat(withBoth.pending()).containsOnlyKeys(1L, 2L);
    }

    @Test
    void shouldRejectSecondPlanTargetingSameGroup() {
      // given
      final var withTenantA = PhasedChangeState.empty().initPlan(groupPhase("tenantA"));

      // when / then
      assertThatThrownBy(() -> withTenantA.initPlan(groupPhase("tenantA")))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectGlobalPlanWhileAnyPlanIsPending() {
      // given
      final var withTenantA = PhasedChangeState.empty().initPlan(groupPhase("tenantA"));

      // when / then
      assertThatThrownBy(() -> withTenantA.initPlan(twoGlobalPhases))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectGroupPlanWhileGlobalPlanIsPending() {
      // given
      final var withGlobal = PhasedChangeState.empty().initPlan(twoGlobalPhases);

      // when / then
      assertThatThrownBy(() -> withGlobal.initPlan(groupPhase("tenantA")))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  class CompletePlan {

    @Test
    void shouldMoveOnlyTheCompletedPlanToHistory() {
      // given — two concurrently pending plans
      final var state =
          PhasedChangeState.empty().initPlan(groupPhase("tenantA")).initPlan(groupPhase("tenantB"));

      // when
      final var completed = state.completePlan(1L, PhasedChangePlanStatus.COMPLETED, 10);

      // then — tenantA resolved, tenantB still pending and untouched
      assertThat(completed.pending()).containsOnlyKeys(2L);
      assertThat(completed.history()).extracting(CompletedPhasedChange::id).containsExactly(1L);
      assertThat(completed.history().get(0).status()).isEqualTo(PhasedChangePlanStatus.COMPLETED);
    }

    @Test
    void shouldTrimHistoryToTheConfiguredLimit() {
      // given / when — five plans completed one at a time, history capped at 2
      var state = PhasedChangeState.empty();
      for (int i = 0; i < 5; i++) {
        final long id = state.nextId();
        state =
            state.initPlan(twoGlobalPhases).completePlan(id, PhasedChangePlanStatus.COMPLETED, 2);
      }

      // then — only the two most recent completions survive
      assertThat(state.history()).extracting(CompletedPhasedChange::id).containsExactly(4L, 5L);
    }

    @Test
    void shouldThrowWhenPlanNotPending() {
      // given
      final var state = PhasedChangeState.empty();

      // when / then
      assertThatThrownBy(() -> state.completePlan(1L, PhasedChangePlanStatus.COMPLETED, 10))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldEvictByCompletionOrderNotById() {
      // given — two concurrently pending plans; tenantB (id=2, admitted second) completes first
      final var bothPending =
          PhasedChangeState.empty().initPlan(groupPhase("tenantA")).initPlan(groupPhase("tenantB"));
      final var tenantBCompletedFirst =
          bothPending.completePlan(2L, PhasedChangePlanStatus.COMPLETED, 1);

      // when — tenantA (id=1, admitted first but slower) completes chronologically later, with
      // the history capped to a single entry
      final var tenantACompletedSecond =
          tenantBCompletedFirst.completePlan(1L, PhasedChangePlanStatus.COMPLETED, 1);

      // then — the entry retained is the one that finished most recently (id=1), not the lower id
      assertThat(tenantACompletedSecond.history())
          .extracting(CompletedPhasedChange::id)
          .containsExactly(1L);
    }
  }

  @Nested
  class LastChange {

    @Test
    void shouldOrderByCompletedAtNotById() {
      // given — id=1 (tenant A, admitted first) finishes later than id=2 (tenant B, admitted
      // after A but faster)
      final var tenantA =
          new CompletedPhasedChange(
              1L, PhasedChangePlanStatus.COMPLETED, Instant.EPOCH, Instant.ofEpochSecond(100));
      final var tenantB =
          new CompletedPhasedChange(
              2L, PhasedChangePlanStatus.COMPLETED, Instant.EPOCH, Instant.ofEpochSecond(50));
      final var state = new PhasedChangeState(3L, Map.of(), List.of(tenantA, tenantB));

      // when / then — the change that finished later (tenantA, id=1) is "last", not the higher id
      assertThat(state.lastChange()).contains(tenantA);
    }
  }

  @Nested
  class WasIssued {

    @Test
    void shouldReportPendingIdAsIssued() {
      final var state = PhasedChangeState.empty().initPlan(twoGlobalPhases);
      assertThat(state.wasIssued(1L)).isTrue();
    }

    @Test
    void shouldReportResolvedIdAsIssuedEvenAfterAgingOutOfHistory() {
      var state = PhasedChangeState.empty();
      for (int i = 0; i < 5; i++) {
        final long id = state.nextId();
        state =
            state.initPlan(twoGlobalPhases).completePlan(id, PhasedChangePlanStatus.COMPLETED, 1);
      }
      // id=1 is long gone from the bounded history window, but it was issued
      assertThat(state.history()).extracting(CompletedPhasedChange::id).doesNotContain(1L);
      assertThat(state.wasIssued(1L)).isTrue();
    }

    @Test
    void shouldReportNeverIssuedIdAsNotIssued() {
      final var state = PhasedChangeState.empty().initPlan(twoGlobalPhases);
      assertThat(state.wasIssued(99L)).isFalse();
    }
  }

  @Nested
  class Merge {

    @Test
    void shouldReturnEmptyWhenBothEmpty() {
      final var merged = PhasedChangeState.empty().merge(PhasedChangeState.empty());
      assertThat(merged.pending()).isEmpty();
      assertThat(merged.history()).isEmpty();
      assertThat(merged.nextId()).isEqualTo(PhasedChangePlan.INITIAL_PLAN_ID);
    }

    @Test
    void shouldUnionDisjointPendingPlansFromBothSides() {
      // given — the coordinator (the single admission point, so ids are always distinct) admits
      // two concurrent, non-conflicting plans; one node's local state has only observed the first
      // gossip update (tenantA) and the other has observed both
      final var withOnlyTenantA = PhasedChangeState.empty().initPlan(groupPhase("tenantA"));
      final var withBoth = withOnlyTenantA.initPlan(groupPhase("tenantB"));

      // when
      final var merged = withOnlyTenantA.merge(withBoth);

      // then — both survive with their own distinct ids, and the counter takes the max
      assertThat(merged.pending()).containsOnlyKeys(1L, 2L);
      assertThat(merged.nextId()).isEqualTo(3L);
    }

    @Test
    void shouldTakeHigherPhaseIndexForSamePlanIdOnMerge() {
      // given — same plan id, one side advanced further
      final var earlier = PhasedChangeState.empty().initPlan(twoGlobalPhases);
      final var advancedPlan = earlier.pending().get(1L).withNextPhase();
      final var later = earlier.withAdvancedPlan(advancedPlan);

      // when
      final var merged = earlier.merge(later);

      // then
      assertThat(merged.pending().get(1L).currentPhaseIndex()).isEqualTo(1);
    }

    @Test
    void shouldDropPendingPlanSupersededByHistoryOnMerge() {
      // given — one side still thinks plan 1 is pending; the other already completed it
      final var stillPending = PhasedChangeState.empty().initPlan(twoGlobalPhases);
      final var alreadyCompleted =
          stillPending.completePlan(1L, PhasedChangePlanStatus.COMPLETED, 10);

      // when
      final var merged = stillPending.merge(alreadyCompleted);

      // then
      assertThat(merged.pending()).isEmpty();
      assertThat(merged.history()).extracting(CompletedPhasedChange::id).containsExactly(1L);
    }

    @Test
    void shouldUnionHistoryEntriesFromBothSides() {
      final var withOne =
          PhasedChangeState.empty()
              .initPlan(groupPhase("tenantA"))
              .completePlan(1L, PhasedChangePlanStatus.COMPLETED, 10);
      final var withTwo =
          withOne
              .initPlan(groupPhase("tenantA"))
              .completePlan(2L, PhasedChangePlanStatus.FAILED, 10);

      final var merged = withOne.merge(withTwo);

      assertThat(merged.history()).extracting(CompletedPhasedChange::id).containsExactly(1L, 2L);
    }

    @Test
    void shouldTrimUnionedHistoryToTheFixedDefaultLimitRegardlessOfEitherSidesSize() {
      // given — 11 completions (one more than DEFAULT_HISTORY_LIMIT), split so neither side alone
      // exceeds the limit, but their union does
      final var all = completedChangesWithSequentialCompletionTimes(11);
      final var left = historyOnlyState(11L, all.subList(0, 5));
      final var right = historyOnlyState(11L, all.subList(5, 11));

      // when
      final var merged = left.merge(right);

      // then — trimmed to exactly DEFAULT_HISTORY_LIMIT entries, dropping only the very oldest
      // (id=0); this is a FIXED cap, not derived from either side's size (5 and 6, neither of
      // which is 10)
      assertThat(merged.history())
          .extracting(CompletedPhasedChange::id)
          .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
    }

    @Test
    void shouldConvergeHistoryToTheSameResultRegardlessOfMergeOrder() {
      // given — 11 completions split three ways across nodes A, B, C
      final var all = completedChangesWithSequentialCompletionTimes(11);
      final var a = historyOnlyState(11L, all.subList(0, 4));
      final var b = historyOnlyState(11L, all.subList(4, 7));
      final var c = historyOnlyState(11L, all.subList(7, 11));

      // when — merged in every possible pairwise order
      final var abThenC = a.merge(b).merge(c);
      final var acThenB = a.merge(c).merge(b);
      final var bcThenA = b.merge(c).merge(a);
      final var baThenC = b.merge(a).merge(c);

      // then — every order converges to the identical trimmed history; a size-derived cap (the
      // previous behaviour) would make this depend on which nodes happened to merge first
      assertThat(abThenC.history()).isEqualTo(acThenB.history());
      assertThat(abThenC.history()).isEqualTo(bcThenA.history());
      assertThat(abThenC.history()).isEqualTo(baThenC.history());
    }

    @Test
    void shouldDropStaleResurrectedPendingEntryEvenAfterItHasAgedOutOfHistory() {
      // given — a broker went offline while plan 1 (tenantA) was pending, then stayed offline
      // through 11 unrelated completions elsewhere, long enough for plan 1's own completion record
      // to age out of the (fixed, DEFAULT_HISTORY_LIMIT-sized) history window entirely
      var upToDate =
          PhasedChangeState.empty()
              .initPlan(groupPhase("tenantA"))
              .completePlan(
                  1L, PhasedChangePlanStatus.COMPLETED, PhasedChangeState.DEFAULT_HISTORY_LIMIT);
      for (int i = 0; i < PhasedChangeState.DEFAULT_HISTORY_LIMIT; i++) {
        final long id = upToDate.nextId();
        upToDate =
            upToDate
                .initPlan(twoGlobalPhases)
                .completePlan(
                    id, PhasedChangePlanStatus.COMPLETED, PhasedChangeState.DEFAULT_HISTORY_LIMIT);
      }
      assertThat(upToDate.history()).extracting(CompletedPhasedChange::id).doesNotContain(1L);
      final var staleBroker =
          new PhasedChangeState(2L, Map.of(1L, planFor(1L, groupPhase("tenantA"))), List.of());

      // when
      final var merged = upToDate.merge(staleBroker);

      // then — plan 1 does not resurrect: upToDate's own nextId/pending already prove it resolved,
      // independent of whether history still remembers it
      assertThat(merged.pending()).doesNotContainKey(1L);
    }

    @Test
    void shouldDropTheStaleEntryButKeepALegitimateConcurrentPlanForTheSameGroup() {
      // given — same resurrection as above, but tenantA now also has a genuinely live change (12)
      // running concurrently
      final var upToDate =
          new PhasedChangeState(13L, Map.of(12L, planFor(12L, groupPhase("tenantA"))), List.of());
      final var staleBroker =
          new PhasedChangeState(2L, Map.of(1L, planFor(1L, groupPhase("tenantA"))), List.of());

      // when
      final var merged = upToDate.merge(staleBroker);

      // then — the stale, lower-id entry for tenantA is dropped; the legitimate one survives.
      // Without this fix both would coexist, violating "one pending plan per group".
      assertThat(merged.pending()).containsOnlyKeys(12L);
    }

    @Test
    void shouldNotDropAPendingEntryWhenTheOtherSideHasNotWitnessedThatFarYet() {
      // given — a fresh/lagging node that has never witnessed id 6 being admitted at all (its own
      // nextId is still 1), merging with a node for which plan 6 is genuinely, currently pending
      final var withPlanSix =
          new PhasedChangeState(7L, Map.of(6L, planFor(6L, groupPhase("tenantB"))), List.of());
      final var lagging = PhasedChangeState.empty();

      // when
      final var merged = withPlanSix.merge(lagging);

      // then — id 6 is kept: the lagging side's absence of it proves nothing, since it hasn't
      // witnessed far enough for that absence to mean "resolved" rather than "not yet known"
      assertThat(merged.pending()).containsOnlyKeys(6L);
    }

    @Test
    void shouldFallBackToHistoryToResolveAStaleRestoredPlanId() {
      // given — RESTORED_PLAN_ID (0) bypasses the nextId counter entirely (assigned directly
      // during legacy migration), so nextId alone can never prove it resolved: every state's
      // nextId is already >= INITIAL_PLAN_ID (1), i.e. trivially "past" id 0, even for a state
      // that has never witnessed it. The bounded history is the only signal available for this
      // one id, which is acceptable since a restore happens at most once per cluster lifetime.
      final var restorePlan = PhasedChangePlan.initForRestore(groupPhase("tenantA"), Instant.EPOCH);
      final var resolvedRestore =
          new CompletedPhasedChange(
              PhasedChangePlan.RESTORED_PLAN_ID,
              PhasedChangePlanStatus.COMPLETED,
              Instant.EPOCH,
              Instant.EPOCH);
      final var upToDate = new PhasedChangeState(5L, Map.of(), List.of(resolvedRestore));
      final var staleBroker =
          new PhasedChangeState(
              1L, Map.of(PhasedChangePlan.RESTORED_PLAN_ID, restorePlan), List.of());

      // when
      final var merged = upToDate.merge(staleBroker);

      // then
      assertThat(merged.pending()).doesNotContainKey(PhasedChangePlan.RESTORED_PLAN_ID);
    }

    /**
     * Returns {@code count} completed changes with ids {@code 0..count-1} and strictly increasing
     * {@code completedAt} timestamps (so id order and completion order coincide, for readable test
     * assertions), all with distinct {@code startedAt} to avoid incidental equality.
     */
    private List<CompletedPhasedChange> completedChangesWithSequentialCompletionTimes(
        final int count) {
      final List<CompletedPhasedChange> result = new ArrayList<>();
      for (int i = 0; i < count; i++) {
        result.add(
            new CompletedPhasedChange(
                i, PhasedChangePlanStatus.COMPLETED, Instant.EPOCH, Instant.EPOCH.plusSeconds(i)));
      }
      return result;
    }

    /** A state with no pending plans, only the given (already-resolved) history entries. */
    private PhasedChangeState historyOnlyState(
        final long nextId, final List<CompletedPhasedChange> history) {
      return new PhasedChangeState(nextId, Map.of(), history);
    }

    private PhasedChangePlan planFor(final long id, final List<PhasedChangePlan.Phase> phases) {
      return PhasedChangePlan.init(id, phases, Instant.EPOCH);
    }
  }

  @Nested
  class Invariants {

    @Test
    void shouldThrowWhenNextIdIsBelowInitialPlanId() {
      assertThatThrownBy(() -> new PhasedChangeState(0L, Map.of(), List.of()))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenPendingKeyDoesNotMatchPlanId() {
      final var plan = PhasedChangePlan.init(1L, twoGlobalPhases, Instant.EPOCH);
      assertThatThrownBy(() -> new PhasedChangeState(2L, Map.of(2L, plan), List.of()))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenPendingIdIsNotBelowNextId() {
      final var plan = PhasedChangePlan.init(3L, twoGlobalPhases, Instant.EPOCH);
      assertThatThrownBy(() -> new PhasedChangeState(3L, Map.of(3L, plan), List.of()))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
