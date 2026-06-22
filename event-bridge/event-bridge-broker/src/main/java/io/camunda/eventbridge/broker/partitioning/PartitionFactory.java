/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.partitioning;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.RaftPartitionConfig;
import io.atomix.raft.partition.RaftStorageConfig;
import io.atomix.raft.storage.log.RaftLogFlusher;
import io.atomix.raft.zeebe.EntryValidator.NoopEntryValidator;
import io.camunda.eventbridge.broker.logstreams.ApplicationEntryCursorAdapter;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates raft-level components for an EventBridge partition: directory, raft partition, and
 * snapshot store. Does not create lifecycle actors — that's the bootstrapper's concern.
 */
public final class PartitionFactory {

  public static final String GROUP_NAME = "event-bridge-partition";

  private static final Logger LOG = LoggerFactory.getLogger(PartitionFactory.class);

  private final EventBridgeProperties properties;
  private final ActorSchedulingService actorScheduler;

  public PartitionFactory(
      final EventBridgeProperties properties, final ActorSchedulingService actorScheduler) {
    this.properties = properties;
    this.actorScheduler = actorScheduler;
  }

  /** Creates the raft partition and snapshot store. The raft partition is not bootstrapped yet. */
  public CreatedPartition create(
      final int partitionId, final Set<MemberId> members, final MemberId localMemberId) {

    final var partitionDir = getPartitionDirectory(partitionId);
    ensureDirectoryExists(partitionDir, partitionId);

    final var raftPartition =
        createRaftPartition(partitionId, members, localMemberId, partitionDir);

    final var snapshotStore = new NoopSnapshotStore(partitionId);
    actorScheduler.submitActor(snapshotStore);

    LOG.info("Partition {} — raft partition and snapshot store created", partitionId);

    return new CreatedPartition(partitionId, raftPartition, snapshotStore);
  }

  public Path getPartitionDirectory(final int partitionId) {
    return Paths.get(properties.data().directory())
        .resolve(GROUP_NAME)
        .resolve("partitions")
        .resolve(String.valueOf(partitionId));
  }

  private RaftPartition createRaftPartition(
      final int partitionId,
      final Set<MemberId> members,
      final MemberId localMemberId,
      final Path partitionDirectory) {

    final var storageConfig = buildStorageConfig();
    final var partitionConfig = buildPartitionConfig(storageConfig);
    final var metadata = buildMetadata(partitionId, members, localMemberId);

    return new RaftPartition(
        metadata,
        partitionConfig,
        partitionDirectory.toFile(),
        new SimpleMeterRegistry(),
        new ApplicationEntryCursorAdapter());
  }

  private RaftStorageConfig buildStorageConfig() {
    final var config = new RaftStorageConfig();
    config.setFlusherFactory(RaftLogFlusher.Factory::direct);
    return config;
  }

  private static RaftPartitionConfig buildPartitionConfig(final RaftStorageConfig storageConfig) {
    final var config = new RaftPartitionConfig();
    config.setStorageConfig(storageConfig);
    config.setPriorityElectionEnabled(false);
    config.setTenantName("event-bridge");
    config.setSendOnLegacySubject(false);
    config.setReceiveOnLegacySubject(false);
    config.setEntryValidator(new NoopEntryValidator());
    return config;
  }

  private static PartitionMetadata buildMetadata(
      final int partitionId, final Set<MemberId> members, final MemberId preferredLeader) {
    final var raftPartitionId = PartitionId.from(GROUP_NAME, partitionId);
    final Map<MemberId, Integer> priorities =
        members.stream().collect(Collectors.toMap(m -> m, m -> 1));
    return new PartitionMetadata(raftPartitionId, members, priorities, 1, preferredLeader);
  }

  private static void ensureDirectoryExists(final Path dir, final int partitionId) {
    try {
      FileUtil.ensureDirectoryExists(dir);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to create directory for partition " + partitionId, e);
    }
  }

  /** Holds the raft-level components for a single partition. */
  public record CreatedPartition(
      int partitionId, RaftPartition raftPartition, NoopSnapshotStore snapshotStore) {}
}
