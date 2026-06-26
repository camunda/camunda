/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.reconfig;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.clustermetadata.reconfig.ReconfigurationOp.Kind;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ReconfigurationPlannerTest {

  @Test
  void shouldHaveNoOpWhenCommittedEqualsTarget() {
    final var assignment = Map.of(1, List.of(0, 1), 2, List.of(1, 2));
    assertThat(ReconfigurationPlanner.nextOp("t", assignment, assignment)).isEmpty();
  }

  @Test
  void shouldAddMissingReplicaBeforeRemovingExtra() {
    // given a move of partition 1 from {0,1} to {1,2}
    final var committed = Map.of(1, List.of(0, 1));
    final var target = Map.of(1, List.of(1, 2));

    // when/then — the join (grow) comes first
    final var first = ReconfigurationPlanner.nextOp("t", committed, target).orElseThrow();
    assertThat(first.kind()).isEqualTo(Kind.JOIN);
    assertThat(first.member()).isEqualTo(2);
    assertThat(first.partitionId()).isEqualTo(1);
  }

  @Test
  void shouldConvergeCommittedToTargetOneStepAtATime() {
    // given a 2-partition move
    final var target = Map.of(1, List.of(1, 2), 2, List.of(2, 0));
    var committed = Map.of(1, List.of(0, 1), 2, List.of(1, 2));

    // when applying steps until none remain
    final var steps = new java.util.ArrayList<ReconfigurationOp>();
    for (var guard = 0; guard < 100; guard++) {
      final var op = ReconfigurationPlanner.nextOp("t", committed, target);
      if (op.isEmpty()) {
        break;
      }
      steps.add(op.get());
      committed = ReconfigurationPlanner.apply(committed, op.get());
    }

    // then it converges, and never removes before the replacement is added (each partition's
    // replica set is only ever one member away from both endpoints)
    assertThat(committed).isEqualTo(target);
    assertThat(steps).isNotEmpty();
    assertThat(steps.stream().filter(o -> o.kind() == Kind.JOIN).count()).isEqualTo(2);
    assertThat(steps.stream().filter(o -> o.kind() == Kind.LEAVE).count()).isEqualTo(2);
  }

  @Test
  void shouldGrowAndShrinkReplicationFactor() {
    // given RF increase 1 -> 2 on partition 1
    assertThat(
            ReconfigurationPlanner.nextOp("t", Map.of(1, List.of(0)), Map.of(1, List.of(0, 1)))
                .orElseThrow()
                .kind())
        .isEqualTo(Kind.JOIN);
    // given RF decrease 2 -> 1 on partition 1
    assertThat(
            ReconfigurationPlanner.nextOp("t", Map.of(1, List.of(0, 1)), Map.of(1, List.of(0)))
                .orElseThrow()
                .kind())
        .isEqualTo(Kind.LEAVE);
  }

  @Test
  void shouldRemoveDeadMemberBeforeAddingReplacement() {
    // given partition 1 healing off a dead broker 3, replaced by spare 0: {2,1,3} -> {2,1,0}
    final var committed = Map.of(1, List.of(2, 1, 3));
    final var target = Map.of(1, List.of(2, 1, 0));
    final var live = Set.of(0, 1, 2); // broker 3 is dead

    // when/then — the dead member's LEAVE comes first (shrink before grow)
    final var first = ReconfigurationPlanner.nextOp("t", committed, target, live).orElseThrow();
    assertThat(first.kind()).isEqualTo(Kind.LEAVE);
    assertThat(first.member()).isEqualTo(3);

    // and after removing it, the spare joins
    final var afterLeave = ReconfigurationPlanner.apply(committed, first);
    final var second = ReconfigurationPlanner.nextOp("t", afterLeave, target, live).orElseThrow();
    assertThat(second.kind()).isEqualTo(Kind.JOIN);
    assertThat(second.member()).isEqualTo(0);
  }

  @Test
  void shouldGrowFirstForDeadMemberPassiveJoinThenPromoteThenRemove() {
    // given partition 1 healing off dead broker 3 with grow-first: {2,1,3} -> {2,1,0}
    final var target = Map.of(1, List.of(2, 1, 0));
    var committed = Map.of(1, List.of(2, 1, 3));
    var passive = Map.<Integer, List<Integer>>of();
    final var live = Set.of(0, 1, 2); // broker 3 is dead

    // when applying steps until none remain
    final var kinds = new java.util.ArrayList<Kind>();
    for (var guard = 0; guard < 100; guard++) {
      final var op =
          ReconfigurationPlanner.nextOp(
              "t", committed, passive, target, live, ReassignmentStrategy.GROW_FIRST);
      if (op.isEmpty()) {
        break;
      }
      kinds.add(op.get().kind());
      committed = ReconfigurationPlanner.apply(committed, op.get());
      passive = ReconfigurationPlanner.applyPassive(passive, op.get());
    }

    // then it passive-joins the spare, promotes it, then removes the dead member — and converges
    assertThat(kinds).containsExactly(Kind.JOIN_PASSIVE, Kind.PROMOTE, Kind.LEAVE);
    assertThat(committed).isEqualTo(target);
    assertThat(passive).isEmpty();
  }

  @Test
  void shouldGrowBeforeShrinkWhenRemovedMemberIsLive() {
    // given a live reassignment {0,1} -> {1,2}, all brokers live — grow-before-shrink preserved
    final var committed = Map.of(1, List.of(0, 1));
    final var target = Map.of(1, List.of(1, 2));
    final var live = Set.of(0, 1, 2);

    final var first = ReconfigurationPlanner.nextOp("t", committed, target, live).orElseThrow();
    assertThat(first.kind()).isEqualTo(Kind.JOIN);
    assertThat(first.member()).isEqualTo(2);
  }
}
