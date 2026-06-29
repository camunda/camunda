/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.backupapi;

import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern;
import io.camunda.zeebe.backup.api.BackupRangeStatus;
import io.camunda.zeebe.backup.api.BackupRangeStatus.CheckpointInfo;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.common.BackupMetadata;
import io.camunda.zeebe.backup.common.BackupMetadata.CheckpointEntry;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import io.camunda.zeebe.backup.management.BackupMetadataSyncer;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only backup service used while a partition is in recovery mode. Serves the subset of backup
 * operations that do not require access to the partition's RocksDB state: {@code QUERY_STATUS} and
 * {@code LIST} are answered directly from the {@link BackupStore}, while {@code QUERY_RANGES} is
 * answered from the per-partition metadata manifest in the backup store via the {@link
 * BackupMetadataSyncer}. No backups can be taken or deleted during recovery.
 */
public final class RecoveryBackupService extends Actor {

  private final int nodeId;
  private final int partitionId;
  private final BackupStore backupStore;
  private final BackupMetadataSyncer metadataSyncer;

  public RecoveryBackupService(
      final int nodeId,
      final int partitionId,
      final BackupStore backupStore,
      final MeterRegistry meterRegistry) {
    this.nodeId = nodeId;
    this.partitionId = partitionId;
    this.backupStore = backupStore;
    metadataSyncer = new BackupMetadataSyncer(backupStore, meterRegistry);
  }

  @Override
  public String getName() {
    return buildActorName("recovery-backup-service", partitionId);
  }

  @Override
  protected void onActorClosing() {
    metadataSyncer.close();
  }

  /** Returns the latest known status of the backup with the given checkpoint id. */
  public ActorFuture<BackupStatus> getBackupStatus(final long checkpointId) {
    final ActorFuture<BackupStatus> result = actor.createFuture();
    actor.run(
        () ->
            backupStore
                .list(wildcard(CheckpointPattern.of(checkpointId)))
                .whenCompleteAsync(
                    (statuses, error) -> {
                      if (error != null) {
                        result.completeExceptionally(error);
                      } else {
                        result.complete(
                            statuses.stream()
                                .max(BackupStatusCode.BY_STATUS)
                                .orElseGet(() -> doesNotExist(checkpointId)));
                      }
                    },
                    actor));
    return result;
  }

  /** Lists all backups of this partition matching the given pattern. */
  public ActorFuture<Collection<BackupStatus>> listBackups(final String pattern) {
    final ActorFuture<Collection<BackupStatus>> result = actor.createFuture();
    actor.run(
        () ->
            backupStore
                .list(wildcard(CheckpointPattern.of(pattern)))
                .whenCompleteAsync(
                    (statuses, error) -> {
                      if (error != null) {
                        result.completeExceptionally(error);
                      } else {
                        result.complete(statuses);
                      }
                    },
                    actor));
    return result;
  }

  /** Returns the backup ranges read from the metadata manifest stored in the backup store. */
  public ActorFuture<Collection<BackupRangeStatus>> getBackupRangeStatus() {
    final ActorFuture<Collection<BackupRangeStatus>> result = actor.createFuture();
    actor.run(
        () ->
            metadataSyncer
                .load(partitionId)
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

  private BackupIdentifierWildcardImpl wildcard(final CheckpointPattern checkpointPattern) {
    return new BackupIdentifierWildcardImpl(
        Optional.empty(), Optional.of(partitionId), checkpointPattern);
  }

  private BackupStatus doesNotExist(final long checkpointId) {
    return new BackupStatusImpl(
        new BackupIdentifierImpl(nodeId, partitionId, checkpointId),
        Optional.empty(),
        BackupStatusCode.DOES_NOT_EXIST,
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
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
