/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.ClusterMembershipPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupParallelPhase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PhasedChangePlanTest {

  private static PhasedChangePlan planWithPhaseIndex(
      final long id, final int phaseIndex, final int totalPhases) {
    final List<PhasedChangePlan.Phase> phases =
        java.util.stream.IntStream.range(0, totalPhases)
            .mapToObj(i -> (PhasedChangePlan.Phase) new ClusterMembershipPhase(List.of()))
            .toList();
    return new PhasedChangePlan(id, Instant.now(), phaseIndex, phases, null);
  }

  @Test
  void shouldMergeSamePlanIdReturnHigherPhaseIndex() {
    // given
    final var planPhase0 = planWithPhaseIndex(1L, 0, 2);
    final var planPhase1 = planWithPhaseIndex(1L, 1, 2);

    // when
    final var merged = planPhase0.merge(planPhase1);

    // then
    assertThat(merged.currentPhaseIndex()).isEqualTo(1);
  }

  @Test
  void shouldMergeSamePlanIdReturnThisWhenHigher() {
    // given
    final var planPhase1 = planWithPhaseIndex(1L, 1, 2);
    final var planPhase0 = planWithPhaseIndex(1L, 0, 2);

    // when
    final var merged = planPhase1.merge(planPhase0);

    // then
    assertThat(merged).isSameAs(planPhase1);
  }

  @Test
  void shouldMergeDifferentPlanIdReturnHigherPhaseIndex() {
    // given — id=1 phase=2 vs id=2 phase=0 → return id=1 (further advanced)
    final var plan1 = planWithPhaseIndex(1L, 2, 3);
    final var plan2 = planWithPhaseIndex(2L, 0, 3);

    // when
    final var merged = plan1.merge(plan2);

    // then
    assertThat(merged.id()).isEqualTo(1L);
    assertThat(merged.currentPhaseIndex()).isEqualTo(2);
  }

  @Test
  void shouldMergeDifferentPlanIdEqualPhaseReturnHigherPlanId() {
    // given — id=3 phase=0 vs id=1 phase=0 → return id=3
    final var plan3 = planWithPhaseIndex(3L, 0, 2);
    final var plan1 = planWithPhaseIndex(1L, 0, 2);

    // when
    final var merged = plan3.merge(plan1);

    // then
    assertThat(merged.id()).isEqualTo(3L);
  }

  @Test
  void shouldReturnCurrentPhase() {
    // given
    final var phase0 =
        new ClusterMembershipPhase(List.of(new MemberJoinOperation(MemberId.from("1"))));
    final var phase1 = new ClusterMembershipPhase(List.of());
    final var plan = PhasedChangePlan.init(1L, List.of(phase0, phase1));

    // when
    final var current = plan.currentPhase();

    // then
    assertThat(current).isEqualTo(phase0);
  }

  @Test
  void shouldDetectHasNextPhase() {
    // given — single-phase plan
    final var singlePhase =
        PhasedChangePlan.init(1L, List.of(new ClusterMembershipPhase(List.of())));

    // given — two-phase plan
    final var twoPhase =
        PhasedChangePlan.init(
            2L,
            List.of(
                new ClusterMembershipPhase(List.of()), new PartitionGroupParallelPhase(Map.of())));

    // then
    assertThat(singlePhase.hasNextPhase()).isFalse();
    assertThat(twoPhase.hasNextPhase()).isTrue();
  }
}
