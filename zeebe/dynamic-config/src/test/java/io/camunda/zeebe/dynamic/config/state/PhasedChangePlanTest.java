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
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateIncarnationNumberOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupParallelPhase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PhasedChangePlanTest {

  private static final MemberId MEMBER_1 = MemberId.from("1");
  private static final MemberId MEMBER_2 = MemberId.from("2");

  private final GlobalPhase globalPhase0 =
      new GlobalPhase(List.of(new MemberJoinOperation(MEMBER_1)));
  private final PartitionGroupParallelPhase groupPhase1 =
      new PartitionGroupParallelPhase(
          Map.of("groupA", List.of(new UpdateIncarnationNumberOperation(MEMBER_2))));
  private final GlobalPhase globalPhase2 =
      new GlobalPhase(List.of(new MemberLeaveOperation(MEMBER_1)));

  private PhasedChangePlan planWithThreePhases(final long id) {
    return PhasedChangePlan.init(
        id, List.of(globalPhase0, groupPhase1, globalPhase2), Instant.EPOCH);
  }

  @Nested
  class CurrentPhase {
    @Test
    void shouldReturnFirstPhaseAtInit() {
      // given
      final var plan = planWithThreePhases(1L);

      // when / then
      assertThat(plan.currentPhase()).isEqualTo(globalPhase0);
    }

    @Test
    void shouldReturnCorrectPhaseAfterAdvancing() {
      // given
      final var plan = planWithThreePhases(1L).withNextPhase();

      // when / then
      assertThat(plan.currentPhase()).isEqualTo(groupPhase1);
    }
  }

  @Nested
  class HasNextPhase {
    @Test
    void shouldReturnTrueWhenNotOnLastPhase() {
      // given
      final var plan = planWithThreePhases(1L);

      // when / then
      assertThat(plan.hasNextPhase()).isTrue();
    }

    @Test
    void shouldReturnFalseOnLastPhase() {
      // given
      final var plan = planWithThreePhases(1L).withNextPhase().withNextPhase();

      // when / then
      assertThat(plan.hasNextPhase()).isFalse();
    }
  }

  @Nested
  class WithNextPhase {
    @Test
    void shouldAdvancePhaseIndex() {
      // given
      final var plan = planWithThreePhases(1L);

      // when
      final var advanced = plan.withNextPhase();

      // then
      assertThat(advanced.currentPhaseIndex()).isEqualTo(1);
      assertThat(advanced.phases()).isEqualTo(plan.phases());
    }

    @Test
    void shouldThrowWhenAlreadyOnLastPhase() {
      // given
      final var lastPlan = planWithThreePhases(1L).withNextPhase().withNextPhase();

      // when / then
      assertThatThrownBy(lastPlan::withNextPhase).isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  class Merge {

    @Test
    void shouldReturnHigherPhaseIndexWhenSamePlanId() {
      // given
      final var earlier = planWithThreePhases(42L);
      final var later = earlier.withNextPhase();

      // when / then
      assertThat(earlier.merge(later)).isSameAs(later);
      assertThat(later.merge(earlier)).isSameAs(later);
    }

    @Test
    void shouldReturnSelfWhenSamePlanIdAndSamePhaseIndex() {
      // given
      final var plan = planWithThreePhases(42L);
      final var copy = planWithThreePhases(42L);

      // when / then — both are equal, so either is correct; currentPhaseIndex tie → first wins
      assertThat(plan.merge(copy).currentPhaseIndex()).isEqualTo(0);
    }

    @Test
    void shouldReturnHigherPlanIdForDifferentPlanIds() {
      // given — newer plan (id=20) happens to be one phase further; both rules agree here
      final var olderPlan = planWithThreePhases(10L);
      final var newerPlan = planWithThreePhases(20L).withNextPhase();

      // when / then — higher plan ID wins
      assertThat(olderPlan.merge(newerPlan)).isSameAs(newerPlan);
      assertThat(newerPlan.merge(olderPlan)).isSameAs(newerPlan);
    }

    @Test
    void shouldReturnHigherPlanIdEvenWhenNewerPlanHasLowerPhaseIndex() {
      // given — newer plan (id=99) is on phase 0; older plan (id=5) has advanced to phase 2
      final var newerPlan = planWithThreePhases(99L);
      final var olderPlanAdvanced = planWithThreePhases(5L).withNextPhase().withNextPhase();

      // when / then — higher plan ID wins regardless of phase index
      assertThat(newerPlan.merge(olderPlanAdvanced)).isSameAs(newerPlan);
      assertThat(olderPlanAdvanced.merge(newerPlan)).isSameAs(newerPlan);
    }

    @Test
    void shouldReturnHigherPlanIdWhenDifferentPlanIdsAndEqualPhaseIndex() {
      // given — both on phase 0, but different plan IDs
      final var olderPlan = planWithThreePhases(5L);
      final var newerPlan = planWithThreePhases(99L);

      // when / then — higher plan ID wins
      assertThat(olderPlan.merge(newerPlan)).isSameAs(newerPlan);
      assertThat(newerPlan.merge(olderPlan)).isSameAs(newerPlan);
    }
  }

  @Nested
  class ImmutableCollections {
    @Test
    void shouldDefensivelyCopyPhaseList() {
      // given
      final var mutablePhases =
          new java.util.ArrayList<PhasedChangePlan.Phase>(List.of(globalPhase0));
      final var plan = new PhasedChangePlan(1L, 0, mutablePhases, Instant.EPOCH);

      // when — mutate the original list after construction
      mutablePhases.add(groupPhase1);

      // then — plan is unaffected
      assertThat(plan.phases()).hasSize(1);
    }

    @Test
    void shouldDefensivelyCopyGlobalPhaseOperations() {
      // given
      final var mutableOps =
          new java.util.ArrayList<GlobalChangeOperation>(
              List.of(new MemberJoinOperation(MEMBER_1)));
      final var phase = new GlobalPhase(mutableOps);

      // when
      mutableOps.add(new MemberLeaveOperation(MEMBER_2));

      // then
      assertThat(phase.operations()).hasSize(1);
    }

    @Test
    void shouldDefensivelyCopyPartitionGroupParallelPhaseOperations() {
      // given
      final var mutableOps =
          new java.util.ArrayList<PartitionGroupOperation>(
              List.of(new UpdateIncarnationNumberOperation(MEMBER_1)));
      final var phase = new PartitionGroupParallelPhase(Map.of("g1", mutableOps));

      // when
      mutableOps.add(new UpdateIncarnationNumberOperation(MEMBER_2));

      // then
      assertThat(phase.groupOperations().get("g1")).hasSize(1);
    }
  }
}
