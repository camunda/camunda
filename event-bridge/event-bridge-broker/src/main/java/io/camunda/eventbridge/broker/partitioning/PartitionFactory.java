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
import io.camunda.zeebe.db.impl.rocksdb.ChecksumProviderRocksDBImpl;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.snapshots.CRC32CChecksumProvider;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
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

  /** Dedicated Raft group for the consumer-group coordinator (separate from data partitions). */
  public static final String COORDINATOR_GROUP_NAME = "event-bridge-coordinator";

  /** Dedicated single-partition Raft group for the topic registry (the metadata control plane). */
  public static final String METADATA_GROUP_NAME = "event-bridge-metadata";

  private static final Logger LOG = LoggerFactory.getLogger(PartitionFactory.class);

  /** The Raft group name hosting a topic's partitions. Each topic is its own group. */
  public static String topicGroupName(final String topic) {
    return io.camunda.eventbridge.core.topic.TopicGroups.name(topic);
  }

  private final EventBridgeProperties properties;
  private final ActorSchedulingService actorScheduler;

  public PartitionFactory(
      final EventBridgeProperties properties, final ActorSchedulingService actorScheduler) {
    this.properties = properties;
    this.actorScheduler = actorScheduler;
  }

  /** Creates a data partition in the default data group. The raft partition is not bootstrapped. */
  public CreatedPartition create(
      final int partitionId, final Set<MemberId> members, final MemberId localMemberId) {
    return createData(GROUP_NAME, partitionId, members, localMemberId);
  }

  /**
   * Creates a data-style partition (event log + marker snapshot store) in an arbitrary Raft group.
   * Used both for the default data group at boot and for per-topic groups ({@code
   * event-bridge-topic-<name>}) provisioned at runtime. The raft partition is not bootstrapped yet.
   */
  public CreatedPartition createData(
      final String groupName,
      final int partitionId,
      final Set<MemberId> members,
      final MemberId localMemberId) {
    final var partitionDir = getPartitionDirectory(groupName, partitionId);
    ensureDirectoryExists(partitionDir, partitionId);

    final var raftPartition =
        createRaftPartition(
            groupName,
            partitionId,
            members,
            localMemberId,
            partitionDir,
            new ApplicationEntryCursorAdapter());

    // Data partitions are pure event logs with no state machine, but they still use a real snapshot
    // store: retention takes an empty marker snapshot at the compaction bound, which both drives
    // log
    // compaction and gives Raft an InstallSnapshot fallback to catch up a replica that fell behind
    // the retained window. A no-op checksum provider is used since there is no RocksDB state to
    // checksum (the store still CRCs the marker file itself).
    final CRC32CChecksumProvider noStateChecksums = path -> Map.of();
    final var snapshotStore =
        new FileBasedSnapshotStore(
            parseNodeId(localMemberId),
            partitionId,
            partitionDir,
            noStateChecksums,
            new SimpleMeterRegistry());
    actorScheduler.submitActor(snapshotStore, SchedulingHints.ioBound());

    LOG.info("Partition {}/{} — raft partition and snapshot store created", groupName, partitionId);
    return new CreatedPartition(partitionId, raftPartition, snapshotStore);
  }

  /**
   * Creates a coordinator Raft partition in its own group, backed by a {@link
   * FileBasedSnapshotStore} so the StreamProcessor's state can be snapshotted, replicated to
   * lagging followers, and the log compacted. Uses the default journal index cursor (the
   * coordinator log carries StreamProcessor records, not EventBridge batches).
   */
  public CreatedPartition createCoordinator(
      final int partitionId, final Set<MemberId> members, final MemberId localMemberId) {
    final var partitionDir = getPartitionDirectory(COORDINATOR_GROUP_NAME, partitionId);
    ensureDirectoryExists(partitionDir, partitionId);

    final var raftPartition =
        createRaftPartition(
            COORDINATOR_GROUP_NAME, partitionId, members, localMemberId, partitionDir, null);

    final var snapshotStore =
        new FileBasedSnapshotStore(
            parseNodeId(localMemberId),
            partitionId,
            partitionDir,
            new ChecksumProviderRocksDBImpl(),
            new SimpleMeterRegistry());
    actorScheduler.submitActor(snapshotStore, SchedulingHints.ioBound());

    LOG.info(
        "Partition {}/{} — raft partition and file-based snapshot store created",
        COORDINATOR_GROUP_NAME,
        partitionId);
    return new CreatedPartition(partitionId, raftPartition, snapshotStore);
  }

  /**
   * Creates a metadata Raft partition in its own group, backed by a {@link FileBasedSnapshotStore}
   * so the topic registry's {@code StreamProcessor} state can be snapshotted, replicated to lagging
   * followers, and the log compacted. Identical to {@link #createCoordinator} but in the metadata
   * group (its own tenant name → its own Raft subjects).
   */
  public CreatedPartition createMetadata(
      final int partitionId, final Set<MemberId> members, final MemberId localMemberId) {
    final var partitionDir = getPartitionDirectory(METADATA_GROUP_NAME, partitionId);
    ensureDirectoryExists(partitionDir, partitionId);

    final var raftPartition =
        createRaftPartition(
            METADATA_GROUP_NAME, partitionId, members, localMemberId, partitionDir, null);

    final var snapshotStore =
        new FileBasedSnapshotStore(
            parseNodeId(localMemberId),
            partitionId,
            partitionDir,
            new ChecksumProviderRocksDBImpl(),
            new SimpleMeterRegistry());
    actorScheduler.submitActor(snapshotStore, SchedulingHints.ioBound());

    LOG.info(
        "Partition {}/{} — raft partition and file-based snapshot store created",
        METADATA_GROUP_NAME,
        partitionId);
    return new CreatedPartition(partitionId, raftPartition, snapshotStore);
  }

  private static int parseNodeId(final MemberId memberId) {
    try {
      return Integer.parseInt(memberId.id().replaceAll("[^0-9]", ""));
    } catch (final NumberFormatException e) {
      return 0;
    }
  }

  public Path getPartitionDirectory(final int partitionId) {
    return getPartitionDirectory(GROUP_NAME, partitionId);
  }

  public Path getPartitionDirectory(final String groupName, final int partitionId) {
    return Paths.get(properties.data().directory())
        .resolve(groupName)
        .resolve("partitions")
        .resolve(String.valueOf(partitionId));
  }

  private RaftPartition createRaftPartition(
      final String groupName,
      final int partitionId,
      final Set<MemberId> members,
      final MemberId localMemberId,
      final Path partitionDirectory,
      final ApplicationEntryCursorAdapter journalIndexCursor) {

    final var storageConfig = buildStorageConfig();
    // The Raft messaging subject prefix is "<tenantName>-partition-<id>", so the two groups MUST
    // use
    // distinct tenant names — otherwise the data and coordinator partitions that share an id (1, 2,
    // …) would collide on their Raft vote/append subjects and never elect a stable leader.
    final var partitionConfig = buildPartitionConfig(storageConfig, groupName);
    final var metadata = buildMetadata(groupName, partitionId, members, localMemberId);

    return journalIndexCursor == null
        ? new RaftPartition(
            metadata, partitionConfig, partitionDirectory.toFile(), new SimpleMeterRegistry())
        : new RaftPartition(
            metadata,
            partitionConfig,
            partitionDirectory.toFile(),
            new SimpleMeterRegistry(),
            journalIndexCursor);
  }

  private RaftStorageConfig buildStorageConfig() {
    final var config = new RaftStorageConfig();
    config.setFlusherFactory(RaftLogFlusher.Factory::direct);
    config.setSegmentSize(properties.retention().segmentSizeBytes());
    return config;
  }

  private static RaftPartitionConfig buildPartitionConfig(
      final RaftStorageConfig storageConfig, final String tenantName) {
    final var config = new RaftPartitionConfig();
    config.setStorageConfig(storageConfig);
    config.setPriorityElectionEnabled(false);
    // Per-group tenant name → per-group Raft subjects (see createRaftPartition).
    config.setTenantName(tenantName);
    config.setSendOnLegacySubject(false);
    config.setReceiveOnLegacySubject(false);
    config.setEntryValidator(new NoopEntryValidator());
    // Runtime membership changes (join/leave) send their RPCs with this timeout; it has no default
    // and would otherwise be null, NPE-ing the join. Needed for the change-coordinator's joins.
    config.setConfigurationChangeTimeout(java.time.Duration.ofSeconds(10));
    return config;
  }

  private static PartitionMetadata buildMetadata(
      final String groupName,
      final int partitionId,
      final Set<MemberId> members,
      final MemberId preferredLeader) {
    final var raftPartitionId = PartitionId.from(groupName, partitionId);
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
      int partitionId, RaftPartition raftPartition, ReceivableSnapshotStore snapshotStore) {}
}
