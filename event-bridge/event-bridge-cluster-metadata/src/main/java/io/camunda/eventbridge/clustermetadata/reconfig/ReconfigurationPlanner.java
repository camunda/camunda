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
   * The next single step assuming all members are live and nothing is mid-grow (pure
   * grow-before-shrink). For live-member reassignments.
   */
  public static Optional<ReconfigurationOp> nextOp(
      final String topic,
      final Map<Integer, List<Integer>> committed,
      final Map<Integer, List<Integer>> target) {
    return nextOp(topic, committed, Map.of(), target, null, ReassignmentStrategy.GROW_FIRST);
  }

  /**
   * The next single step with known liveness but nothing mid-grow, using shrink-first for a dead
   * member (remove the dead member before adding the replacement).
   *
   * @param liveMembers the live broker ids; {@code null} treats all members as live
   */
  public static Optional<ReconfigurationOp> nextOp(
      final String topic,
      final Map<Integer, List<Integer>> committed,
      final Map<Integer, List<Integer>> target,
      final Set<Integer> liveMembers) {
    return nextOp(
        topic, committed, Map.of(), target, liveMembers, ReassignmentStrategy.SHRINK_FIRST);
  }

  /**
   * The next single step to move a partition's voting set ({@code committed}) toward {@code
   * target}, given the in-flight non-voting members ({@code passive}), broker liveness and the
   * strategy for replacing a dead member. Returns empty when every partition's voting set equals
   * its target and no passive members remain.
   *
   * <p>Per partition, in priority order:
   *
   * <ol>
   *   <li><b>Promote</b> a passive member that is wanted (finish an in-flight grow) —
   *       leader-driven, catch-up gated.
   *   <li><b>Drop</b> a passive member that is no longer wanted (an abandoned grow).
   *   <li><b>Grow-first dead replacement</b> (only when a dead voting member is being replaced and
   *       strategy is {@link ReassignmentStrategy#GROW_FIRST}): passive-join the replacement, so it
   *       catches up without affecting quorum before it is promoted and the dead member removed.
   *   <li><b>Remove a dead voting member</b> (shrink-first for a dead member, or the final removal
   *       of a grow-first sequence).
   *   <li><b>Add a missing voting member</b> (active join — for a live reassignment, a top-up, or
   *       the post-shrink add of a shrink-first heal).
   *   <li><b>Remove a live extra voting member</b> (grow-before-shrink for a live reassignment).
   * </ol>
   *
   * @param liveMembers the live broker ids; {@code null} treats all members as live
   */
  public static Optional<ReconfigurationOp> nextOp(
      final String topic,
      final Map<Integer, List<Integer>> committed,
      final Map<Integer, List<Integer>> passive,
      final Map<Integer, List<Integer>> target,
      final Set<Integer> liveMembers,
      final ReassignmentStrategy strategy) {
    // Deterministic order so a re-derivation after failover picks the same next step.
    final var partitions = new TreeSet<Integer>();
    partitions.addAll(committed.keySet());
    partitions.addAll(target.keySet());
    partitions.addAll(passive.keySet());

    for (final var partitionId : partitions) {
      final var voting = committed.getOrDefault(partitionId, List.of());
      final var pass = passive.getOrDefault(partitionId, List.of());
      final var wanted = target.getOrDefault(partitionId, List.of());

      // 1. Promote a passive member that is wanted (finish an in-flight grow).
      for (final var member : pass) {
        if (wanted.contains(member)) {
          return Optional.of(new ReconfigurationOp(Kind.PROMOTE, topic, partitionId, member));
        }
      }
      // 2. Drop a passive member that is no longer wanted (abandoned grow).
      for (final var member : pass) {
        if (!wanted.contains(member)) {
          return Optional.of(new ReconfigurationOp(Kind.LEAVE, topic, partitionId, member));
        }
      }

      final var hasDeadVoting =
          voting.stream().anyMatch(m -> !wanted.contains(m) && !isLive(m, liveMembers));
      final var firstMissing =
          wanted.stream()
              .filter(m -> !voting.contains(m) && !pass.contains(m))
              .findFirst()
              .orElse(null);

      // 3. Grow-first dead replacement: passive-join the replacement before removing the dead
      // member.
      if (strategy == ReassignmentStrategy.GROW_FIRST && hasDeadVoting && firstMissing != null) {
        return Optional.of(
            new ReconfigurationOp(Kind.JOIN_PASSIVE, topic, partitionId, firstMissing));
      }
      // 4. Remove a dead voting member (shrink-first, or the final removal after a grow-first).
      for (final var member : voting) {
        if (!wanted.contains(member) && !isLive(member, liveMembers)) {
          return Optional.of(new ReconfigurationOp(Kind.LEAVE, topic, partitionId, member));
        }
      }
      // 5. Add a missing voting member (active join).
      if (firstMissing != null) {
        return Optional.of(new ReconfigurationOp(Kind.JOIN, topic, partitionId, firstMissing));
      }
      // 6. Remove a live extra voting member (grow-before-shrink for a live reassignment).
      for (final var member : voting) {
        if (!wanted.contains(member)) {
          return Optional.of(new ReconfigurationOp(Kind.LEAVE, topic, partitionId, member));
        }
      }
    }
    return Optional.empty();
  }

  private static boolean isLive(final int member, final Set<Integer> liveMembers) {
    return liveMembers == null || liveMembers.contains(member);
  }

  /**
   * Returns a new voting assignment with {@code op} applied: a JOIN or PROMOTE adds the member, a
   * LEAVE removes it, a JOIN_PASSIVE leaves the voting set unchanged (see {@link #applyPassive}).
   */
  public static Map<Integer, List<Integer>> apply(
      final Map<Integer, List<Integer>> committed, final ReconfigurationOp op) {
    final Map<Integer, List<Integer>> next = copy(committed);
    final var replicas = next.computeIfAbsent(op.partitionId(), p -> new ArrayList<>());
    switch (op.kind()) {
      case JOIN, PROMOTE -> {
        if (!replicas.contains(op.member())) {
          replicas.add(op.member());
        }
      }
      case LEAVE -> replicas.remove(Integer.valueOf(op.member()));
      case JOIN_PASSIVE -> {
        /* voting set unchanged */
      }
    }
    return next;
  }

  /**
   * Returns a new passive (non-voting) set with {@code op} applied: a JOIN_PASSIVE adds the member,
   * a PROMOTE or LEAVE removes it, a JOIN leaves it unchanged.
   */
  public static Map<Integer, List<Integer>> applyPassive(
      final Map<Integer, List<Integer>> passive, final ReconfigurationOp op) {
    final Map<Integer, List<Integer>> next = copy(passive);
    switch (op.kind()) {
      case JOIN_PASSIVE -> {
        final var replicas = next.computeIfAbsent(op.partitionId(), p -> new ArrayList<>());
        if (!replicas.contains(op.member())) {
          replicas.add(op.member());
        }
      }
      case PROMOTE, LEAVE -> {
        final var replicas = next.get(op.partitionId());
        if (replicas != null) {
          replicas.remove(Integer.valueOf(op.member()));
          if (replicas.isEmpty()) {
            next.remove(op.partitionId());
          }
        }
      }
      case JOIN -> {
        /* passive set unchanged */
      }
    }
    return next;
  }

  private static Map<Integer, List<Integer>> copy(final Map<Integer, List<Integer>> source) {
    final Map<Integer, List<Integer>> next = new LinkedHashMap<>();
    source.forEach((partitionId, replicas) -> next.put(partitionId, new ArrayList<>(replicas)));
    return next;
  }
}
