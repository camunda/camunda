/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.placement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Picks the single next replica relocation that moves a topic's committed assignment toward an even
 * spread of replicas across the active brokers — the incremental, minimal-diff counterpart to
 * {@link SpreadPlacement}'s from-scratch layout.
 *
 * <p>Each call proposes at most <b>one</b> move: relocate one replica from the most-loaded broker
 * to the least-loaded one, on a partition where that helps. The other {@code RF-1} replicas of the
 * partition are preserved, so a partition is <b>never fully swapped</b> — there is always a
 * surviving replica to anchor quorum and serve the log while the new replica catches up (grow-first
 * passive join → promote → leave the old). The controller applies one move, waits for it to
 * converge, then asks again; repeated application reaches balance (max − min replica count ≤ 1).
 *
 * <p>It only acts when every replica is already on an active broker — a replica on a
 * fenced/draining broker is the placement-heal's concern and takes priority, so rebalancing defers
 * until the topic is healthy. Leadership is left with surviving replicas (a moved replica joins as
 * a follower); even leadership balancing is a later refinement.
 */
public final class RebalancePlanner {

  private RebalancePlanner() {}

  /**
   * A single proposed relocation: the partition and its new replica list (first = preferred
   * leader).
   */
  public record Move(int partitionId, List<Integer> newReplicas) {}

  /**
   * The next single relocation toward balance, or empty when already balanced (within {@code
   * minImbalance}), when fewer than two brokers are available, or when a replica still sits on a
   * non-active broker (left to the placement heal).
   *
   * @param minImbalance the smallest load gap (most-loaded − least-loaded) worth moving for; a move
   *     only fires when some broker carries at least this many more replicas than another, so
   *     balance settles at a gap of at most {@code minImbalance - 1} rather than oscillating.
   */
  public static Optional<Move> nextMove(
      final Map<Integer, List<Integer>> assignment,
      final List<Integer> activeBrokers,
      final int minImbalance) {
    if (activeBrokers.size() < 2 || assignment.isEmpty()) {
      return Optional.empty();
    }
    final var active = Set.copyOf(activeBrokers);
    // Defer to the placement heal while any replica is on a non-active broker.
    for (final var replicas : assignment.values()) {
      for (final var replica : replicas) {
        if (!active.contains(replica)) {
          return Optional.empty();
        }
      }
    }

    final Map<Integer, Integer> load = new HashMap<>();
    activeBrokers.forEach(broker -> load.put(broker, 0));
    assignment.values().forEach(replicas -> replicas.forEach(r -> load.merge(r, 1, Integer::sum)));

    // Most-loaded sources first, least-loaded targets first; ties broken by broker id for
    // determinism (so a re-derivation after a leader failover proposes the same move).
    final var sources =
        activeBrokers.stream()
            .sorted(
                Comparator.comparingInt((Integer b) -> load.get(b))
                    .reversed()
                    .thenComparingInt(b -> b))
            .toList();
    final var targets =
        activeBrokers.stream()
            .sorted(Comparator.comparingInt((Integer b) -> load.get(b)).thenComparingInt(b -> b))
            .toList();

    for (final var from : sources) {
      for (final var to : targets) {
        if (load.get(from) - load.get(to) < minImbalance) {
          continue; // gap too small to be worth a move (would just oscillate)
        }
        final var move = relocate(assignment, from, to);
        if (move.isPresent()) {
          return move;
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Finds a partition whose replica set contains {@code from} but not {@code to} and moves that one
   * replica. Prefers a partition where {@code from} is a follower, so a balance move does not force
   * a leader election; falls back to a leader-held partition only if no follower move exists.
   */
  private static Optional<Move> relocate(
      final Map<Integer, List<Integer>> assignment, final int from, final int to) {
    Integer leaderHeldPartition = null;
    // Deterministic partition order.
    for (final var entry : new TreeMap<>(assignment).entrySet()) {
      final var replicas = entry.getValue();
      if (!replicas.contains(from) || replicas.contains(to)) {
        continue;
      }
      if (!replicas.get(0).equals(from)) {
        return Optional.of(new Move(entry.getKey(), withReplaced(replicas, from, to)));
      }
      if (leaderHeldPartition == null) {
        leaderHeldPartition = entry.getKey();
      }
    }
    return leaderHeldPartition == null
        ? Optional.empty()
        : Optional.of(
            new Move(
                leaderHeldPartition, withReplaced(assignment.get(leaderHeldPartition), from, to)));
  }

  /**
   * The replica list with {@code from} removed and {@code to} appended as the last (follower)
   * replica — so the freshly added, still-empty replica never starts as the preferred leader, and
   * if {@code from} was the leader, leadership passes to a surviving, caught-up replica.
   */
  private static List<Integer> withReplaced(
      final List<Integer> replicas, final int from, final int to) {
    final var result = new ArrayList<Integer>(replicas.size());
    for (final var replica : replicas) {
      if (!replica.equals(from)) {
        result.add(replica);
      }
    }
    result.add(to);
    return result;
  }
}
