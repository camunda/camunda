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
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupParallelPhase;
import java.time.Instant;
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
        new PartitionGroupParallelPhase(
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
    void shouldTrimUnionedHistoryBackToTheLargerSidesSize() {
      // given — the left side was trimmed to 1 entry (id=2); the right side still has 2 entries
      // (id=1 and id=2) from before it last trimmed
      final var older =
          new CompletedPhasedChange(
              1L, PhasedChangePlanStatus.COMPLETED, Instant.EPOCH, Instant.ofEpochSecond(1));
      final var newer =
          new CompletedPhasedChange(
              2L, PhasedChangePlanStatus.COMPLETED, Instant.EPOCH, Instant.ofEpochSecond(2));
      final var left = new PhasedChangeState(3L, Map.of(), List.of(newer));
      final var right = new PhasedChangeState(3L, Map.of(), List.of(older, newer));

      // when
      final var merged = left.merge(right);

      // then — the union (2 entries) is trimmed back to the larger side's size (2), so nothing is
      // dropped here, but repeated merges never grow history past what either side already holds
      assertThat(merged.history()).extracting(CompletedPhasedChange::id).containsExactly(1L, 2L);

      // and — merging a much smaller, unrelated history back in does not let the result balloon
      // past the larger side
      final var third = new PhasedChangeState(3L, Map.of(), List.of(newer));
      final var mergedAgain = merged.merge(third);
      assertThat(mergedAgain.history()).hasSizeLessThanOrEqualTo(2);
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
