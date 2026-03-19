/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.partition;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.RaftPartitionConfig;
import io.atomix.raft.partition.RaftStorageConfig;
import io.atomix.raft.storage.log.RaftLogFlusher;
import io.atomix.raft.zeebe.EntryValidator.NoopEntryValidator;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creates {@link RaftPartition} instances for the Event Bridge, following the same pattern as
 * {@code RaftPartitionFactory} in the zeebe-broker module.
 *
 * <p>Partition data is stored under:
 *
 * <pre>
 *   {event-bridge.data.directory}/{@value GROUP_NAME}/partitions/{partitionId}/
 * </pre>
 *
 * <p>The RAFT configuration intentionally omits priority-based elections and legacy subjects so
 * that Event Bridge partitions do not conflict with Zeebe broker partitions when both run in the
 * same JVM.
 */
public final class EventBridgePartitionFactory {

  /** RAFT group name used to identify Event Bridge partitions in {@link PartitionId}. */
  public static final String GROUP_NAME = "event-bridge-partition";

  private final EventBridgeProperties properties;

  public EventBridgePartitionFactory(final EventBridgeProperties properties) {
    this.properties = properties;
  }

  /**
   * Creates a configured (but not yet bootstrapped) {@link RaftPartition}.
   *
   * <p>The partition data directory is automatically created if it does not yet exist.
   *
   * @param partitionId logical partition ID
   * @param members RAFT member set for this partition
   * @param localMemberId the local node's SWIM {@link MemberId} (must be in {@code members})
   * @param meterRegistry meter registry for partition metrics
   * @return configured {@link RaftPartition}
   */
  public RaftPartition createPartition(
      final int partitionId,
      final Set<MemberId> members,
      final MemberId localMemberId,
      final MeterRegistry meterRegistry) {

    final var partitionDir = getPartitionDirectory(partitionId);
    try {
      FileUtil.ensureDirectoryExists(partitionDir);
    } catch (final IOException e) {
      throw new UncheckedIOException(
          "Failed to create data directory for partition " + partitionId, e);
    }
    return createPartition(partitionId, members, localMemberId, partitionDir, meterRegistry);
  }

  /**
   * Creates a configured {@link RaftPartition} using the given {@code partitionDirectory} instead
   * of the auto-computed one. Useful in tests where a temporary directory is preferred.
   *
   * @param partitionId logical partition ID
   * @param members RAFT member set
   * @param localMemberId local SWIM member ID (must be in {@code members})
   * @param partitionDirectory directory that RAFT will use for its storage
   * @param meterRegistry meter registry for partition metrics
   * @return configured {@link RaftPartition}
   */
  public RaftPartition createPartition(
      final int partitionId,
      final Set<MemberId> members,
      final MemberId localMemberId,
      final Path partitionDirectory,
      final MeterRegistry meterRegistry) {

    final var storageConfig = buildStorageConfig();
    final var partitionConfig = buildPartitionConfig(storageConfig);
    final var metadata = buildMetadata(partitionId, members, localMemberId);

    return new RaftPartition(metadata, partitionConfig, partitionDirectory.toFile(), meterRegistry);
  }

  /**
   * Returns the canonical on-disk directory for the given partition under the configured data root.
   */
  public Path getPartitionDirectory(final int partitionId) {
    return Paths.get(properties.data().directory())
        .resolve(GROUP_NAME)
        .resolve("partitions")
        .resolve(String.valueOf(partitionId));
  }

  // -------------------------------------------------------------------------
  // Private builders

  private RaftStorageConfig buildStorageConfig() {
    final var config = new RaftStorageConfig();
    // Use direct (synchronous) flushing for durability.
    // Delayed or disabled flushing can be added later as a configurable option.
    config.setFlusherFactory(RaftLogFlusher.Factory::direct);
    return config;
  }

  private static RaftPartitionConfig buildPartitionConfig(final RaftStorageConfig storageConfig) {
    final var config = new RaftPartitionConfig();
    config.setStorageConfig(storageConfig);
    // Disable priority-based elections in standalone/dev mode; all members are equal.
    config.setPriorityElectionEnabled(false);
    // Use a distinct engine name so Event Bridge RAFT subjects do not clash with Zeebe subjects
    // when both run in the same JVM (e.g. in an all-in-one StandaloneCamunda deployment).
    config.setEngineName("event-bridge");
    // Do not register on legacy Zeebe RAFT subjects.
    config.setSendOnLegacySubject(false);
    config.setReceiveOnLegacySubject(false);
    config.setEntryValidator(new NoopEntryValidator());
    return config;
  }

  private static PartitionMetadata buildMetadata(
      final int partitionId, final Set<MemberId> members, final MemberId preferredLeader) {
    final var raftPartitionId = PartitionId.from(GROUP_NAME, partitionId);
    // Assign equal weight (priority 1) to every member.
    final Map<MemberId, Integer> priorities =
        members.stream().collect(Collectors.toMap(m -> m, m -> 1));
    // targetPriority = 1 so that every member is a valid leader candidate.
    return new PartitionMetadata(raftPartitionId, members, priorities, 1, preferredLeader);
  }
}
