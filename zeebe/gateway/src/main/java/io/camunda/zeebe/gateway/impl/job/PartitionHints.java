/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.job;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers which partitions are believed to have jobs of a type, so that the next activation can
 * ask them first. A partition becomes hinted when it returned everything we asked for, or when a
 * broker said it has new jobs. It stops being hinted as soon as it returns less than we asked for.
 *
 * <p>A hint is only a priority, never a reason to skip a partition, so a stale hint costs at most a
 * larger ask that returns less. Thread-safe.
 */
final class PartitionHints {

  private final ConcurrentHashMap<Key, Set<Integer>> hintedPartitions = new ConcurrentHashMap<>();

  void hint(final String partitionGroup, final String jobType, final int partitionId) {
    hintedPartitions
        .computeIfAbsent(new Key(partitionGroup, jobType), k -> ConcurrentHashMap.newKeySet())
        .add(partitionId);
  }

  void clear(final String partitionGroup, final String jobType, final int partitionId) {
    final var partitions = hintedPartitions.get(new Key(partitionGroup, jobType));
    if (partitions != null) {
      partitions.remove(partitionId);
    }
  }

  Set<Integer> hinted(final String partitionGroup, final String jobType) {
    final var partitions = hintedPartitions.get(new Key(partitionGroup, jobType));
    return partitions == null ? Set.of() : Set.copyOf(partitions);
  }

  private record Key(String partitionGroup, String jobType) {}
}
