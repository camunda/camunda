/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.reconfig;

import io.camunda.eventbridge.clustermetadata.reconfig.ReconfigurationOp.Kind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pure logic for driving a topic's committed assignment toward a target, one safe Raft step at a
 * time. No state, no I/O — the change-coordinator owns scheduling, persistence and execution; this
 * just answers "what is the next single step?" and "what does committed look like after it?".
 *
 * <p>Within a partition, a missing target member is <b>added before</b> an extra member is removed,
 * so a replica set is never shrunk below its target before the replacement has joined. Exactly one
 * member changes per step, so each Raft group reconfigures by a single member at a time.
 */
public final class ReconfigurationPlanner {

  private ReconfigurationPlanner() {}

  /**
   * The next single step to move {@code committed} toward {@code target}, or empty if equal,
   * assuming all members are live (pure grow-before-shrink). Prefer {@link #nextOp(String, Map,
   * Map, Set)} where liveness is known.
   */
  public static Optional<ReconfigurationOp> nextOp(
      final String topic,
      final Map<Integer, List<Integer>> committed,
      final Map<Integer, List<Integer>> target) {
    return nextOp(topic, committed, target, null);
  }

  /**
   * The next single step to move {@code committed} toward {@code target}, or empty if equal.
   *
   * <p>Ordering per partition is liveness-aware:
   *
   * <ul>
   *   <li><b>Dead member to remove → shrink first.</b> A replica that is being dropped and is not
   *       in {@code liveMembers} is removed <em>before</em> the replacement joins. Growing first
   *       here would add a voter to a group that still counts the dead member, inflating the quorum
   *       to a size the live members can't reach until the (still-catching-up) joiner acks — which
   *       stalls the join and can cost the group its leader. Removing the dead member first is safe
   *       (the removal commit drags any lagging survivor up to the committed prefix) and keeps the
   *       transition on a config the survivors alone can commit.
   *   <li><b>Otherwise → grow before shrink.</b> For a live-member reassignment the replacement
   *       joins before the old replica leaves, so the replica set is never shrunk below target with
   *       a healthy member still in place.
   * </ul>
   *
   * @param liveMembers the currently live (registered, unfenced) broker ids; {@code null} treats
   *     all members as live (pure grow-before-shrink)
   */
  public static Optional<ReconfigurationOp> nextOp(
      final String topic,
      final Map<Integer, List<Integer>> committed,
      final Map<Integer, List<Integer>> target,
      final Set<Integer> liveMembers) {
    // Deterministic order so a re-derivation after failover picks the same next step.
    final var partitions = new TreeSet<Integer>();
    partitions.addAll(committed.keySet());
    partitions.addAll(target.keySet());

    for (final var partitionId : partitions) {
      final var current = committed.getOrDefault(partitionId, List.of());
      final var wanted = target.getOrDefault(partitionId, List.of());
      // Remove a dead extra replica first (shrink before grow for a dead member).
      for (final var member : current) {
        if (!wanted.contains(member) && liveMembers != null && !liveMembers.contains(member)) {
          return Optional.of(new ReconfigurationOp(Kind.LEAVE, topic, partitionId, member));
        }
      }
      // Add a missing replica (grow before shrink for a live reassignment).
      for (final var member : wanted) {
        if (!current.contains(member)) {
          return Optional.of(new ReconfigurationOp(Kind.JOIN, topic, partitionId, member));
        }
      }
      // Then remove an extra (live) replica.
      for (final var member : current) {
        if (!wanted.contains(member)) {
          return Optional.of(new ReconfigurationOp(Kind.LEAVE, topic, partitionId, member));
        }
      }
    }
    return Optional.empty();
  }

  /** Returns a new committed assignment with {@code op} applied to its partition. */
  public static Map<Integer, List<Integer>> apply(
      final Map<Integer, List<Integer>> committed, final ReconfigurationOp op) {
    final Map<Integer, List<Integer>> next = new LinkedHashMap<>();
    committed.forEach((partitionId, replicas) -> next.put(partitionId, new ArrayList<>(replicas)));
    final var replicas = next.computeIfAbsent(op.partitionId(), p -> new ArrayList<>());
    if (op.kind() == Kind.JOIN) {
      if (!replicas.contains(op.member())) {
        replicas.add(op.member());
      }
    } else {
      replicas.remove(Integer.valueOf(op.member()));
    }
    return next;
  }
}
