/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.systempartition;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.RaftPartitionConfig;
import io.atomix.raft.partition.RaftStorageConfig;
import io.atomix.raft.zeebe.EntryValidator;
import io.atomix.raft.zeebe.EntryValidator.NoopEntryValidator;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Builder for the system partition's {@link RaftPartition}.
 *
 * <p>The system partition is a single Raft group with id {@code 1} in group {@code "system"}. Its
 * membership is statically derived from the broker's static configuration: the lowest-{@code
 * nodeId} brokers up to {@code replicationFactor}.
 */
public final class SystemPartitionFactory {

  public static final String GROUP_NAME = "system";
  public static final int SYSTEM_PARTITION_ID = 1;

  private SystemPartitionFactory() {}

  /**
   * Resolve the static membership of the system partition. Returns the {@code replicationFactor}
   * lowest-nodeId members of {@code clusterMembers}, capped at the cluster size.
   */
  public static Set<MemberId> resolveSystemPartitionMembers(
      final Set<MemberId> clusterMembers, final int replicationFactor) {
    final TreeSet<MemberId> sorted = new TreeSet<>(Comparator.comparing(MemberId::id));
    sorted.addAll(clusterMembers);
    final int take = Math.min(replicationFactor, sorted.size());
    final TreeSet<MemberId> result = new TreeSet<>(Comparator.comparing(MemberId::id));
    int i = 0;
    for (final var m : sorted) {
      if (i++ >= take) {
        break;
      }
      result.add(m);
    }
    return result;
  }

  /** Builds the {@link PartitionMetadata} for the system partition. */
  public static PartitionMetadata buildPartitionMetadata(final Set<MemberId> members) {
    final PartitionId id = PartitionId.from(GROUP_NAME, SYSTEM_PARTITION_ID);
    final Map<MemberId, Integer> priorities = new HashMap<>();
    int p = members.size();
    for (final var m : new TreeSet<>(members)) {
      priorities.put(m, p--);
    }
    final MemberId primary =
        priorities.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    return new PartitionMetadata(id, members, priorities, members.size(), primary);
  }

  /** Returns the directory where the system partition's Raft log is stored. */
  public static Path getPartitionDirectory(final String dataDirectory) {
    return Paths.get(dataDirectory)
        .resolve(GROUP_NAME)
        .resolve("partitions")
        .resolve(Integer.toString(SYSTEM_PARTITION_ID));
  }

  /**
   * Builds an unstarted {@link RaftPartition}. The caller must call {@code bootstrap()} on it after
   * submitting a snapshot store actor.
   */
  public static RaftPartition createRaftPartition(
      final PartitionMetadata partitionMetadata,
      final Path partitionDirectory,
      final long segmentSizeBytes,
      final long freeDiskSpaceBytes,
      final MeterRegistry meterRegistry) {
    final var storageConfig = new RaftStorageConfig();
    storageConfig.setSegmentSize(segmentSizeBytes);
    storageConfig.setFreeDiskSpace(freeDiskSpaceBytes);

    final var partitionConfig = new RaftPartitionConfig();
    partitionConfig.setStorageConfig(storageConfig);
    partitionConfig.setEntryValidator(noopEntryValidator());
    partitionConfig.setTenantName(GROUP_NAME);

    return new RaftPartition(
        partitionMetadata, partitionConfig, partitionDirectory.toFile(), meterRegistry);
  }

  /** Convenience overload using the data directory string and broker defaults for storage. */
  public static RaftPartition createRaftPartition(
      final PartitionMetadata partitionMetadata,
      final String dataDirectory,
      final MeterRegistry meterRegistry) {
    final Path dir = getPartitionDirectory(dataDirectory);
    final File asFile = dir.toFile();
    if (!asFile.exists()) {
      // best-effort; the broker step actually ensures the directory exists. Kept here so unit
      // tests can use this convenience without a separate step.
      asFile.mkdirs();
    }
    return createRaftPartition(
        partitionMetadata, dir, 32L * 1024 * 1024, 1024L * 1024 * 1024, meterRegistry);
  }

  private static EntryValidator noopEntryValidator() {
    return new NoopEntryValidator();
  }
}
