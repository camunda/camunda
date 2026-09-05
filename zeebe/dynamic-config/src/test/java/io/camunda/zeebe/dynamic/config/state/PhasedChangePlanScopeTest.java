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
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Groups;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * A plan's scope is the only thing that stops two plans being admitted onto the same
 * sub-configuration, so a phase kind missing from it is a silent hole: the plan reports no group,
 * conflicts with nothing, and a second plan is let through to fail later inside the
 * sub-configuration rather than being rejected as a concurrent modification.
 */
final class PhasedChangePlanScopeTest {

  private static final MemberId MEMBER = MemberId.from("0");

  @Test
  void shouldScopeAGraphPhaseToItsGroups() {
    // given — a plan whose only phase carries a dependency graph per group
    final List<Phase> phases =
        List.of(
            new PartitionGroupPhase(
                Map.of(
                    "tenant-a", graphFor(MEMBER),
                    "tenant-b", graphFor(MEMBER))));

    // when / then — both groups are in scope, exactly as they would be for a queue phase
    assertThat(PhasedChangePlan.scopeOf(phases))
        .isEqualTo(new Groups(Set.of("tenant-a", "tenant-b")));
  }

  @Test
  void shouldConflictWithAnotherPlanOnTheSameGroup() {
    // given — two plans whose graph phases share a group
    final var first =
        PhasedChangePlan.scopeOf(
            List.of(new PartitionGroupPhase(Map.of("tenant-a", graphFor(MEMBER)))));
    final var second =
        PhasedChangePlan.scopeOf(
            List.of(
                new PartitionGroupPhase(
                    Map.of(
                        "tenant-a", graphFor(MEMBER),
                        "tenant-c", graphFor(MEMBER)))));
    final var unrelated =
        PhasedChangePlan.scopeOf(
            List.of(new PartitionGroupPhase(Map.of("tenant-c", graphFor(MEMBER)))));

    // when / then — sharing tenant-a conflicts; a plan on tenant-c alone does not
    assertThat(PhasedChangePlan.conflicts(first, second)).isTrue();
    assertThat(PhasedChangePlan.conflicts(first, unrelated)).isFalse();
  }

  @Test
  void shouldScopeAMixOfPhaseKindsToTheUnionOfTheirGroups() {
    // given — a plan mixing both partition-group execution models across different groups
    final List<Phase> phases =
        List.of(
            PartitionGroupPhase.sequential(
                Map.of("tenant-a", List.of(new ModeChangeOperation(MEMBER, Mode.RECOVERING)))),
            new PartitionGroupPhase(Map.of("tenant-b", graphFor(MEMBER))));

    // when / then
    assertThat(PhasedChangePlan.scopeOf(phases))
        .isEqualTo(new Groups(Set.of("tenant-a", "tenant-b")));
  }

  @Test
  void shouldScopeAPlanWithAnyGlobalPhaseAsClusterWide() {
    // given — a global phase makes the plan cluster-wide regardless of what else it carries
    final List<Phase> phases =
        List.of(
            new PartitionGroupPhase(Map.of("tenant-a", graphFor(MEMBER))),
            new GlobalPhase(List.of(new MemberJoinOperation(MEMBER))));

    // when / then
    assertThat(PhasedChangePlan.scopeOf(phases)).isEqualTo(new PhasedChangePlan.Global());
  }

  private static OperationGraph graphFor(final MemberId memberId) {
    final var builder = OperationGraph.builder();
    builder.add(new ModeChangeOperation(memberId, Mode.RECOVERING));
    return builder.build();
  }
}
