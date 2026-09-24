/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import io.atomix.cluster.MemberId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.IntStream;

/**
 * Picks which pending partition a rebalance transfers next, so that the transfers temporarily
 * overload as few brokers as possible.
 *
 * <p>The next transfer goes to the broker leading the fewest partitions, across every partition
 * group, taking leadership from the broker leading the most.
 */
final class TransferOrder {

  private TransferOrder() {}

  /** The index of the pending partition to transfer next, or empty if none is pending. */
  static OptionalInt next(final List<PartitionRebalance> partitions) {
    final var led = leadersPerMember(partitions);
    return IntStream.range(0, partitions.size())
        .filter(index -> partitions.get(index).progress() == PartitionRebalanceProgress.PENDING)
        .mapToObj(index -> candidate(index, partitions.get(index), led))
        .min(
            Comparator.comparing(Candidate::leaderless)
                .thenComparingInt(Candidate::destinationLed)
                .thenComparing(Candidate::sourceLed, Comparator.reverseOrder())
                .thenComparingInt(Candidate::index))
        .map(candidate -> OptionalInt.of(candidate.index()))
        .orElseGet(OptionalInt::empty);
  }

  private static Candidate candidate(
      final int index, final PartitionRebalance partition, final Map<MemberId, Integer> led) {
    final var source = partition.currentLeader();
    return new Candidate(
        index,
        source == null,
        led.getOrDefault(partition.desiredLeader(), 0),
        source == null ? 0 : led.getOrDefault(source, 0));
  }

  private static Map<MemberId, Integer> leadersPerMember(
      final List<PartitionRebalance> partitions) {
    final Map<MemberId, Integer> counts = new HashMap<>();
    for (final var partition : partitions) {
      final var leader = partition.currentLeader();
      if (leader != null) {
        counts.merge(leader, 1, Integer::sum);
      }
    }
    return counts;
  }

  private record Candidate(int index, boolean leaderless, int destinationLed, int sourceLed) {}
}
