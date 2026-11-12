/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.job;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides how many jobs to ask from which partition while a request is activating jobs on several
 * partitions at once.
 *
 * <p>A partition that returns fewer jobs than asked has no more activatable jobs, unless its
 * response was truncated. So only partitions that returned everything we asked for (saturated) can
 * have more.
 *
 * <p>There are no rounds: asks that are still in flight reserve part of the remaining amount, and
 * every response may release a reservation or reveal a saturated partition. After each response the
 * caller asks {@link #nextAsks()} for asks to send right away. The asks in flight never add up to
 * more than the remaining amount, so no job is activated beyond what the client wanted, except for
 * the last attempt per partition: that one asks for everything that is not reserved yet, which can
 * over-activate but ends the request without further attempts.
 *
 * <p>While no partition is known to have jobs, up to {@link #UNKNOWN_FAN_OUT} partitions are asked
 * for one job each, even if fewer jobs are wanted. This can activate more jobs than the request
 * needs. The caller gives those jobs back.
 *
 * <p>Partitions are asked in this order: saturated partitions (known to have jobs), partitions
 * hinted to have jobs, then the others. If there are hinted partitions before anything is known
 * about the others, the others are still asked for one job each to find out if they have jobs.
 *
 * <p>Not thread-safe.
 */
final class ActivationPlanner {

  /**
   * While no partition is known to have jobs, asks go to up to this many partitions at once, even
   * if fewer jobs are wanted. Then a poll that finds no jobs needs one wait instead of many. A
   * partition that has a job then returns it, and the jobs that were not wanted are given back.
   */
  static final int UNKNOWN_FAN_OUT = 8;

  /** After this many asks to the same partition, the next one asks for everything unreserved. */
  static final int MAX_ASKS_PER_PARTITION = 4;

  private final Set<Integer> unexplored;
  private final Set<Integer> hinted;
  private final Set<Integer> saturated = new LinkedHashSet<>();
  private final Map<Integer, Integer> asksPerPartition = new HashMap<>();
  private final List<Ask> inFlight = new ArrayList<>();

  private int remaining;
  private boolean anyAskIssued;

  /**
   * @param remaining the number of jobs still to activate
   * @param partitionIds the partitions to consider, in the order in which they should be asked if
   *     nothing else is known
   * @param hintedPartitions partitions that are believed to have jobs, e.g. because they had some
   *     recently
   */
  ActivationPlanner(
      final int remaining,
      final Collection<Integer> partitionIds,
      final Collection<Integer> hintedPartitions) {
    this.remaining = remaining;
    unexplored = new LinkedHashSet<>(partitionIds);
    hinted = new LinkedHashSet<>();
    partitionIds.stream().filter(hintedPartitions::contains).forEach(hinted::add);
  }

  /**
   * Returns the asks to send now, and registers them as in flight. Empty if nothing should be asked
   * at the moment.
   */
  List<Ask> nextAsks() {
    final var unreserved = remaining - inFlight.stream().mapToInt(Ask::maxJobs).sum();
    if (unreserved <= 0) {
      return List.of();
    }

    final var inFlightPartitions = inFlight.stream().map(Ask::partitionId).toList();
    final var hot = new ArrayList<Integer>();
    saturated.stream().filter(p -> !inFlightPartitions.contains(p)).forEach(hot::add);
    unexplored.stream().filter(hinted::contains).forEach(hot::add);
    final var cold = new ArrayList<Integer>();
    unexplored.stream().filter(p -> !hinted.contains(p)).forEach(cold::add);

    final var asks = new ArrayList<Ask>();
    if (hot.isEmpty()) {
      final var fanOut = Math.min(cold.size(), UNKNOWN_FAN_OUT);
      split(cold, Math.max(unreserved, fanOut), unreserved, asks);
    } else {
      final var probes =
          anyAskIssued ? 0 : Math.min(cold.size(), Math.max(0, unreserved - hot.size()));
      split(hot, unreserved - probes, unreserved, asks);
      cold.stream().limit(probes).forEach(p -> asks.add(new Ask(p, 1)));
    }

    asks.forEach(
        ask -> {
          inFlight.add(ask);
          unexplored.remove(ask.partitionId());
          asksPerPartition.merge(ask.partitionId(), 1, Integer::sum);
        });
    anyAskIssued |= !asks.isEmpty();
    return asks;
  }

  /**
   * Records the answer of a partition to an ask.
   *
   * @param deliveredJobs the number of jobs that were sent to the client
   * @param returnedJobs the number of jobs the partition activated
   * @return whether the partition may have more jobs
   */
  boolean onResponse(
      final Ask ask, final int deliveredJobs, final int returnedJobs, final boolean truncated) {
    inFlight.remove(ask);
    remaining -= deliveredJobs;
    if (returnedJobs >= ask.maxJobs() || truncated) {
      saturated.add(ask.partitionId());
      return true;
    }
    saturated.remove(ask.partitionId());
    return false;
  }

  /** Excludes the partition of the ask from all further asks, e.g. because it failed. */
  void onFailure(final Ask ask) {
    inFlight.remove(ask);
    saturated.remove(ask.partitionId());
  }

  boolean hasInFlightAsks() {
    return !inFlight.isEmpty();
  }

  int remaining() {
    return remaining;
  }

  /**
   * Splits the budget evenly over as many of the partitions as there are jobs to ask for. The
   * partitions that have been asked often enough ask for all that is unreserved instead.
   */
  private void split(
      final List<Integer> partitions,
      final int budget,
      final int unreserved,
      final List<Ask> asks) {
    final var asked = Math.min(budget, partitions.size());
    if (asked <= 0) {
      return;
    }
    final var share = budget / asked;
    final var extra = budget % asked;
    for (int i = 0; i < asked; i++) {
      final var partitionId = partitions.get(i);
      final var lastAttempt =
          asksPerPartition.getOrDefault(partitionId, 0) >= MAX_ASKS_PER_PARTITION - 1;
      asks.add(new Ask(partitionId, lastAttempt ? unreserved : share + (i < extra ? 1 : 0)));
    }
  }

  record Ask(int partitionId, int maxJobs) {}
}
