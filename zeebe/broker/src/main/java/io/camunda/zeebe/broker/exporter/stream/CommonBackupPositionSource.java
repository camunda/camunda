/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.zeebe.backup.client.api.BackupApi;
import io.camunda.zeebe.backup.client.api.BackupStatus;
import io.camunda.zeebe.backup.client.api.PartitionBackupStatus;
import io.camunda.zeebe.backup.client.api.State;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse.PartitionCheckpointState;
import io.camunda.zeebe.protocol.management.BackupStatusCode;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Determines the backed-up position of one partition from the backups of every partition of its
 * physical tenant.
 *
 * <p>A restore uses a checkpoint that is backed up on all partitions, so a single partition's own
 * latest backup is not enough: other partitions may still be taking it, or may have failed it.
 *
 * <p>When every partition reports the same latest confirmed backup, that checkpoint is already
 * confirmed everywhere and this partition's position is returned directly without reading the
 * backup store. Otherwise, the candidate is the lowest of the partitions' latest confirmed backups,
 * which every other partition has passed, and its status on all partitions is checked explicitly
 * since a partition may have failed that backup and confirmed a later one. Partitions do not need
 * to report the same latest backup at the same moment, so the position keeps advancing even when
 * backups overlap or partitions finish at different speeds.
 */
@NullMarked
public final class CommonBackupPositionSource implements BackedUpPositionSource {

  private final BackupApi backupApi;
  private final String physicalTenantId;
  private final int partitionId;
  private final Supplier<Collection<Integer>> partitionsOfTenant;

  // The last candidate found completed everywhere, so its status is not read again on every poll.
  private volatile @Nullable ConfirmedBackup confirmed;

  public CommonBackupPositionSource(
      final BackupApi backupApi,
      final String physicalTenantId,
      final int partitionId,
      final Supplier<Collection<Integer>> partitionsOfTenant) {
    this.backupApi = backupApi;
    this.physicalTenantId = physicalTenantId;
    this.partitionId = partitionId;
    this.partitionsOfTenant = partitionsOfTenant;
  }

  @Override
  public CompletionStage<OptionalLong> backedUpPosition() {
    if (partitionsOfTenant.get().isEmpty()) {
      return CompletableFuture.completedFuture(OptionalLong.empty());
    }
    return backupApi
        .getCheckpointState(physicalTenantId)
        .thenCompose(
            response -> {
              final var partitions = partitionsOfTenant.get();
              if (partitions.isEmpty()) {
                return CompletableFuture.completedFuture(OptionalLong.empty());
              }

              final var backupsByPartition = indexByPartition(response.getBackupStates());
              long minCheckpointId = Long.MAX_VALUE;
              long maxCheckpointId = Long.MIN_VALUE;
              for (final int partition : partitions) {
                final var backup = backupsByPartition.get(partition);
                if (backup == null) {
                  confirmed = null;
                  return CompletableFuture.completedFuture(OptionalLong.of(-1));
                }
                final long checkpointId = backup.checkpointId();
                minCheckpointId = Math.min(minCheckpointId, checkpointId);
                maxCheckpointId = Math.max(maxCheckpointId, checkpointId);
              }

              final var ownBackup = backupsByPartition.get(partitionId);
              if (ownBackup == null) {
                confirmed = null;
                return CompletableFuture.completedFuture(OptionalLong.of(-1));
              }

              if (minCheckpointId == maxCheckpointId) {
                final long ownPosition = ownBackup.checkpointPosition();
                confirmed = new ConfirmedBackup(minCheckpointId, ownPosition);
                return CompletableFuture.completedFuture(OptionalLong.of(ownPosition));
              }

              final var previous = confirmed;
              if (previous != null && previous.checkpointId() == minCheckpointId) {
                return CompletableFuture.completedFuture(OptionalLong.of(previous.position()));
              }

              final long candidate = minCheckpointId;
              final boolean regressed = previous != null && candidate < previous.checkpointId();
              return backupApi
                  .getStatus(physicalTenantId, candidate)
                  .thenApply(
                      status -> {
                        final var position =
                            ownPositionIfCompletedEverywhere(status, partitionId, partitions);
                        if (position.isPresent()) {
                          confirmed = new ConfirmedBackup(candidate, position.getAsLong());
                          return position;
                        }
                        if (regressed) {
                          confirmed = null;
                          return OptionalLong.of(-1);
                        }
                        return OptionalLong.empty();
                      });
            });
  }

  private static Map<Integer, PartitionCheckpointState> indexByPartition(
      final Set<PartitionCheckpointState> latestBackups) {
    final var byPartition = new HashMap<Integer, PartitionCheckpointState>(latestBackups.size());
    for (final var backup : latestBackups) {
      byPartition.merge(
          backup.partitionId(),
          backup,
          (first, second) -> first.checkpointId() >= second.checkpointId() ? first : second);
    }
    return byPartition;
  }

  /**
   * Returns {@code partitionId}'s checkpoint position for the backup, or empty unless the backup is
   * completed on all {@code partitions}.
   */
  static OptionalLong ownPositionIfCompletedEverywhere(
      final BackupStatus status, final int partitionId, final Collection<Integer> partitions) {
    if (status.status() != State.COMPLETED) {
      return OptionalLong.empty();
    }
    final var completedPartitions =
        status.partitions().stream()
            .filter(partition -> partition.status() == BackupStatusCode.COMPLETED)
            .map(PartitionBackupStatus::partitionId)
            .collect(Collectors.toSet());
    if (!completedPartitions.containsAll(partitions)) {
      return OptionalLong.empty();
    }
    return status.partitions().stream()
        .filter(partition -> partition.partitionId() == partitionId)
        .map(PartitionBackupStatus::checkpointPosition)
        .filter(OptionalLong::isPresent)
        .mapToLong(OptionalLong::getAsLong)
        .findFirst();
  }

  private record ConfirmedBackup(long checkpointId, long position) {}
}
