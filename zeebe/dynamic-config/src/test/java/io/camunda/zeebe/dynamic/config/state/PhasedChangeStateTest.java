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
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PhasedChangeStateTest {

  private static final MemberId MEMBER_1 = MemberId.from("1");

  private final GlobalPhase phase0 = new GlobalPhase(List.of(new MemberJoinOperation(MEMBER_1)));
  private final GlobalPhase phase1 = new GlobalPhase(List.of(new MemberJoinOperation(MEMBER_1)));

  private final List<PhasedChangePlan.Phase> twoPhases = List.of(phase0, phase1);

  @Nested
  class InitPlan {

    @Test
    void shouldStartWithIdOneWhenNoPreviousChange() {
      // given
      final var state = PhasedChangeState.empty();

      // when
      final var next = state.initPlan(twoPhases);

      // then
      assertThat(next.pending()).isPresent();
      assertThat(next.pending().get().id()).isEqualTo(1L);
    }

    @Test
    void shouldDeriveNextIdFromLastChange() {
      // given
      final var lastChange =
          new CompletedPhasedChange(
              7L, PhasedChangePlanStatus.COMPLETED, Instant.EPOCH, Instant.EPOCH);
      final var state = new PhasedChangeState(Optional.empty(), Optional.of(lastChange));

      // when
      final var next = state.initPlan(twoPhases);

      // then
      assertThat(next.pending().get().id()).isEqualTo(8L);
    }

    @Test
    void shouldRetainLastChangeAfterInitPlan() {
      // given
      final var lastChange =
          new CompletedPhasedChange(
              3L, PhasedChangePlanStatus.COMPLETED, Instant.EPOCH, Instant.EPOCH);
      final var state = new PhasedChangeState(Optional.empty(), Optional.of(lastChange));

      // when
      final var next = state.initPlan(twoPhases);

      // then
      assertThat(next.lastChange()).contains(lastChange);
    }

    @Test
    void shouldThrowWhenPlanAlreadyPending() {
      // given
      final var state = PhasedChangeState.empty().initPlan(twoPhases);

      // when / then
      assertThatThrownBy(() -> state.initPlan(twoPhases)).isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  class CompletePlan {

    @Test
    void shouldMovePendingToLastChange() {
      // given
      final var state = PhasedChangeState.empty().initPlan(twoPhases);
      final long planId = state.pending().get().id();

      // when
      final var completed = state.completePlan(PhasedChangePlanStatus.COMPLETED);

      // then
      assertThat(completed.pending()).isEmpty();
      assertThat(completed.lastChange()).isPresent();
      assertThat(completed.lastChange().get().id()).isEqualTo(planId);
      assertThat(completed.lastChange().get().status()).isEqualTo(PhasedChangePlanStatus.COMPLETED);
    }

    @Test
    void shouldPreserveStartedAtTimestamp() {
      // given
      final var state = PhasedChangeState.empty().initPlan(twoPhases);
      final var expectedStartedAt = state.pending().get().startedAt();

      // when
      final var completed = state.completePlan(PhasedChangePlanStatus.COMPLETED);

      // then
      assertThat(completed.lastChange().get().startedAt()).isEqualTo(expectedStartedAt);
    }

    @Test
    void shouldThrowWhenNoPlanPending() {
      // given
      final var state = PhasedChangeState.empty();

      // when / then
      assertThatThrownBy(() -> state.completePlan(PhasedChangePlanStatus.COMPLETED))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  class Merge {

    @Test
    void shouldReturnEmptyWhenBothEmpty() {
      // given
      final var a = PhasedChangeState.empty();
      final var b = PhasedChangeState.empty();

      // when
      final var merged = a.merge(b);

      // then
      assertThat(merged.pending()).isEmpty();
      assertThat(merged.lastChange()).isEmpty();
    }

    @Test
    void shouldAdoptPendingPlanFromOtherWhenThisHasNone() {
      // given
      final var withPlan = PhasedChangeState.empty().initPlan(twoPhases);
      final var withoutPlan = PhasedChangeState.empty();

      // when
      final var merged = withoutPlan.merge(withPlan);

      // then
      assertThat(merged.pending()).isEqualTo(withPlan.pending());
    }

    @Test
    void shouldUsePlanMergeSemanticForPendingPlans() {
      // given — same plan ID, this is at phase 0, other is at phase 1
      final var earlier = PhasedChangeState.empty().initPlan(twoPhases);
      final var planId = earlier.pending().get().id();
      final var advancedPlan = earlier.pending().get().withNextPhase();
      final var later = new PhasedChangeState(Optional.of(advancedPlan), Optional.empty());

      // when
      final var merged = earlier.merge(later);

      // then — higher phase index wins
      assertThat(merged.pending().get().currentPhaseIndex()).isEqualTo(1);
      assertThat(merged.pending().get().id()).isEqualTo(planId);
    }

    @Test
    void shouldTakeLastChangeWithHigherId() {
      // given
      final var older =
          new CompletedPhasedChange(
              2L, PhasedChangePlanStatus.COMPLETED, Instant.EPOCH, Instant.EPOCH);
      final var newer =
          new CompletedPhasedChange(
              5L, PhasedChangePlanStatus.FAILED, Instant.EPOCH, Instant.EPOCH);
      final var a = new PhasedChangeState(Optional.empty(), Optional.of(older));
      final var b = new PhasedChangeState(Optional.empty(), Optional.of(newer));

      // when / then
      assertThat(a.merge(b).lastChange()).contains(newer);
      assertThat(b.merge(a).lastChange()).contains(newer);
    }

    @Test
    void shouldAdoptLastChangeFromOtherWhenThisHasNone() {
      // given
      final var lastChange =
          new CompletedPhasedChange(
              1L, PhasedChangePlanStatus.COMPLETED, Instant.EPOCH, Instant.EPOCH);
      final var withLast = new PhasedChangeState(Optional.empty(), Optional.of(lastChange));
      final var withoutLast = PhasedChangeState.empty();

      // when
      final var merged = withoutLast.merge(withLast);

      // then
      assertThat(merged.lastChange()).contains(lastChange);
    }
  }

  @Nested
  class IdMonotonicity {

    @Test
    void shouldAlwaysIncreaseIdAcrossInitAndComplete() {
      // given / when — three consecutive init+complete cycles
      var state = PhasedChangeState.empty();
      long previousId = 0L;

      for (int i = 0; i < 3; i++) {
        state = state.initPlan(twoPhases);
        final long currentId = state.pending().get().id();
        assertThat(currentId).isGreaterThan(previousId);
        previousId = currentId;
        state = state.completePlan(PhasedChangePlanStatus.COMPLETED);
      }
    }
  }

  @Nested
  class Invariants {

    @Test
    void shouldThrowWhenPendingIdEqualsLastChangeId() {
      // given
      final var plan = PhasedChangePlan.init(3L, twoPhases, Instant.EPOCH);
      final var completed =
          new CompletedPhasedChange(
              3L, PhasedChangePlanStatus.COMPLETED, Instant.EPOCH, Instant.EPOCH);

      // when / then
      assertThatThrownBy(() -> new PhasedChangeState(Optional.of(plan), Optional.of(completed)))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenPendingIdIsLowerThanLastChangeId() {
      // given
      final var plan = PhasedChangePlan.init(2L, twoPhases, Instant.EPOCH);
      final var completed =
          new CompletedPhasedChange(
              5L, PhasedChangePlanStatus.COMPLETED, Instant.EPOCH, Instant.EPOCH);

      // when / then
      assertThatThrownBy(() -> new PhasedChangeState(Optional.of(plan), Optional.of(completed)))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldDropPendingPlanSupersededByLastChangeOnMerge() {
      // given — one side has pending plan id=3 (previously completed by the other side)
      final var stateWithPending =
          new PhasedChangeState(
              Optional.of(PhasedChangePlan.init(3L, twoPhases, Instant.EPOCH)),
              Optional.of(
                  new CompletedPhasedChange(
                      2L, PhasedChangePlanStatus.COMPLETED, Instant.EPOCH, Instant.EPOCH)));
      final var stateWithCompleted =
          new PhasedChangeState(
              Optional.empty(),
              Optional.of(
                  new CompletedPhasedChange(
                      3L, PhasedChangePlanStatus.COMPLETED, Instant.EPOCH, Instant.EPOCH)));

      // when
      final var merged = stateWithPending.merge(stateWithCompleted);

      // then — the pending plan is superseded by the completed change
      assertThat(merged.pending()).isEmpty();
      assertThat(merged.lastChange().get().id()).isEqualTo(3L);
    }
  }
}
