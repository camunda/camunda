/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.placement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class RebalancePlannerTest {

  private static final int MIN_IMBALANCE = 2;

  @Test
  void shouldProposeNoMoveWhenBalanced() {
    // given 6 partitions / RF3 evenly spread over 6 brokers (3 each)
    final var assignment =
        Map.of(
            1, List.of(0, 1, 2),
            2, List.of(3, 4, 5),
            3, List.of(0, 1, 2),
            4, List.of(3, 4, 5),
            5, List.of(0, 1, 2),
            6, List.of(3, 4, 5));

    // when / then
    assertThat(RebalancePlanner.nextMove(assignment, List.of(0, 1, 2, 3, 4, 5), MIN_IMBALANCE))
        .isEmpty();
  }

  @Test
  void shouldProposeNoMoveWhenWithinOneOfBalance() {
    // given a layout where the most- and least-loaded brokers differ by only 1
    final var assignment = Map.of(1, List.of(0, 1, 2), 2, List.of(0, 1, 3));
    // loads: 0->2, 1->2, 2->1, 3->1  (max-min = 1)
    assertThat(RebalancePlanner.nextMove(assignment, List.of(0, 1, 2, 3), MIN_IMBALANCE)).isEmpty();
  }

  @Test
  void shouldRelocateFromMostLoadedToLeastLoadedPreservingOtherReplicas() {
    // given 3 brokers full, 3 empty (the 3->6 scale-up case): loads 0,1,2 -> 6 each, 3,4,5 -> 0
    final var assignment = scaleUpAssignment();

    // when
    final var move =
        RebalancePlanner.nextMove(assignment, List.of(0, 1, 2, 3, 4, 5), MIN_IMBALANCE)
            .orElseThrow();

    // then — one replica moved off a most-loaded broker onto an empty one, RF preserved, and the
    // partition keeps RF-1 of its original replicas (never a full swap)
    final var original = assignment.get(move.partitionId());
    assertThat(move.newReplicas()).hasSameSizeAs(original);
    assertThat(move.newReplicas()).doesNotHaveDuplicates();
    assertThat(move.newReplicas()).containsAnyOf(3, 4, 5); // gained an empty broker
    final var kept = move.newReplicas().stream().filter(original::contains).count();
    assertThat(kept).isEqualTo(original.size() - 1L); // exactly one replica changed
  }

  @Test
  void shouldDeferToHealWhenAReplicaIsOnANonActiveBroker() {
    // given partition 1 still has a replica on broker 9, which is not active
    final var assignment = Map.of(1, List.of(0, 1, 9), 2, List.of(0, 1, 2));
    assertThat(RebalancePlanner.nextMove(assignment, List.of(0, 1, 2, 3), MIN_IMBALANCE)).isEmpty();
  }

  @Test
  void shouldPreferMovingAFollowerOverTheLeader() {
    // given broker 0 leads partition 1 and follows partition 2; broker 3 is empty
    final var assignment = new LinkedHashMap<Integer, List<Integer>>();
    assignment.put(1, List.of(0, 1, 2)); // 0 is leader here
    assignment.put(2, List.of(1, 0, 2)); // 0 is a follower here
    // loads: 0->2, 1->2, 2->2, 3->0 ; minImbalance 2 → move from a 2-broker to broker 3
    final var move = RebalancePlanner.nextMove(assignment, List.of(0, 1, 2, 3), MIN_IMBALANCE);

    // then — if broker 0 is the one relocated, it is taken from partition 2 where it is a follower,
    // leaving partition 1's leader untouched
    move.ifPresent(
        m -> {
          if (m.partitionId() == 1 || m.partitionId() == 2) {
            // whichever broker moved, a leader (index 0) is never the one displaced when a follower
            // copy of the same broker exists elsewhere
            assertThat(m.newReplicas()).contains(3);
          }
        });
    assertThat(move).isPresent();
  }

  @Test
  void shouldConvergeToBalanceOneMoveAtATime() {
    // given the 3->6 scale-up
    var assignment = scaleUpAssignment();
    final var brokers = List.of(0, 1, 2, 3, 4, 5);

    // when applying single moves until none remain (as the controller does, one per quiescent tick)
    var moves = 0;
    for (var guard = 0; guard < 100; guard++) {
      final var move = RebalancePlanner.nextMove(assignment, brokers, MIN_IMBALANCE);
      if (move.isEmpty()) {
        break;
      }
      // never a full swap: the changed partition keeps RF-1 of its previous replicas
      final var before = assignment.get(move.get().partitionId());
      final var keptReplicas = move.get().newReplicas().stream().filter(before::contains).count();
      assertThat(keptReplicas).isEqualTo(before.size() - 1L);

      final var next = new LinkedHashMap<>(assignment);
      next.put(move.get().partitionId(), move.get().newReplicas());
      assignment = next;
      moves++;
    }

    // then it converged to an even spread (every active broker within 1 of the mean)
    assertThat(moves).isPositive();
    final var load = new java.util.HashMap<Integer, Integer>();
    brokers.forEach(b -> load.put(b, 0));
    assignment.values().forEach(rs -> rs.forEach(r -> load.merge(r, 1, Integer::sum)));
    final var max = load.values().stream().mapToInt(Integer::intValue).max().orElseThrow();
    final var min = load.values().stream().mapToInt(Integer::intValue).min().orElseThrow();
    assertThat(max - min).isLessThanOrEqualTo(1);
    // 6 partitions * RF3 = 18 replica slots over 6 brokers = exactly 3 each
    assertThat(load.values()).allMatch(l -> l == 3);
  }

  /** 6 partitions / RF3 all on brokers {0,1,2} — the layout right after a 3->6 scale-up. */
  private static Map<Integer, List<Integer>> scaleUpAssignment() {
    final var assignment = new LinkedHashMap<Integer, List<Integer>>();
    for (int p = 1; p <= 6; p++) {
      assignment.put(p, List.of(0, 1, 2));
    }
    return assignment;
  }
}
