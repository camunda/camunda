/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.management;

import io.atomix.primitive.partition.PartitionId;
import io.camunda.zeebe.backup.api.BackupRangeStatus;
import io.camunda.zeebe.backup.api.BackupRangeStatus.CheckpointInfo;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.api.ReadOnlyBackupManager;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupMetadata;
import io.camunda.zeebe.backup.common.BackupMetadata.CheckpointEntry;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only {@link ReadOnlyBackupManager} used while a partition is in recovery mode. Serves the
 * subset of backup operations that do not require access to the partition's RocksDB state: {@code
 * QUERY_STATUS} and {@code LIST} are answered directly from the {@link BackupStore} via {@link
 * BackupStoreQueries} (shared with the leader-side backup manager), while {@code QUERY_RANGES} is
 * answered from the per-partition metadata manifest in the backup store via the {@link
 * BackupMetadataSyncer}. No backups can be taken or deleted during recovery. The read-write
 * counterpart used while the partition is in processing mode is {@link BackupService}.
 */
public final class ReadOnlyBackupService extends Actor implements ReadOnlyBackupManager {

  private final int nodeId;
  private final PartitionId partition;
  private final BackupStoreQueries storeQueries;
  private final BackupMetadataSyncer metadataSyncer;

  public ReadOnlyBackupService(
      final int nodeId,
      final PartitionId partition,
      final BackupStore backupStore,
      final MeterRegistry meterRegistry) {
    this.nodeId = nodeId;
    this.partition = partition;
    storeQueries = new BackupStoreQueries(backupStore);
    metadataSyncer = new BackupMetadataSyncer(backupStore, meterRegistry);
  }

  @Override
  public String getName() {
    return buildActorName("ReadOnlyBackupService", partition.number());
  }

  @Override
  protected void onActorClosing() {
    metadataSyncer.close();
  }

  /** Returns the latest known status of the backup with the given checkpoint id. */
  @Override
  public ActorFuture<BackupStatus> getBackupStatus(final long checkpointId) {
    final ActorFuture<BackupStatus> result = actor.createFuture();
    actor.run(
        () ->
            storeQueries
                .getBackupStatus(partition.number(), checkpointId, actor)
                .onComplete(
                    (status, error) -> {
                      if (error != null) {
                        result.completeExceptionally(error);
                      } else {
                        result.complete(status.orElseGet(() -> doesNotExist(checkpointId)));
                      }
                    }));
    return result;
  }

  /** Lists all backups of this partition matching the given pattern. */
  @Override
  public ActorFuture<Collection<BackupStatus>> listBackups(final String pattern) {
    final ActorFuture<Collection<BackupStatus>> result = actor.createFuture();
    actor.run(
        () -> storeQueries.listBackups(partition.number(), pattern, actor).onComplete(result));
    return result;
  }

  /** Returns the backup ranges read from the metadata manifest stored in the backup store. */
  @Override
  public ActorFuture<Collection<BackupRangeStatus>> getBackupRangeStatus() {
    final ActorFuture<Collection<BackupRangeStatus>> result = actor.createFuture();
    actor.run(
        () ->
            metadataSyncer
                .load(partition.number())
                .whenCompleteAsync(
                    (metadata, error) -> {
                      if (error != null) {
                        result.completeExceptionally(error);
                      } else {
                        result.complete(toRangeStatuses(metadata.orElse(null)));
                      }
                    },
                    actor));
    return result;
  }

  private BackupStatus doesNotExist(final long checkpointId) {
    return BackupStatusImpl.doesNotExist(
        new BackupIdentifierImpl(nodeId, partition.number(), checkpointId));
  }

  private static Collection<BackupRangeStatus> toRangeStatuses(final BackupMetadata metadata) {
    if (metadata == null) {
      return List.of();
    }
    final Map<Long, CheckpointEntry> checkpointsById =
        metadata.checkpoints().stream()
            .collect(Collectors.toMap(CheckpointEntry::checkpointId, Function.identity()));
    return metadata.ranges().stream()
        .map(
            range -> {
              final var first = checkpointsById.get(range.start());
              final var last = checkpointsById.get(range.end());
              if (first == null || last == null) {
                return null;
              }
              return new BackupRangeStatus(toCheckpointInfo(first), toCheckpointInfo(last));
            })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  private static CheckpointInfo toCheckpointInfo(final CheckpointEntry entry) {
    return new CheckpointInfo(
        entry.checkpointId(),
        entry.checkpointPosition(),
        entry.checkpointTimestamp().toEpochMilli(),
        entry.checkpointType(),
        entry.getFirstLogPositionOrDefault());
  }
}
