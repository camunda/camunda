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
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse;
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
 * Determines the backed-up position of one partition from the backups of every partition in the
 * cluster.
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
  private final int partitionId;
  private final Supplier<Collection<Integer>> partitionsSupplier;

  // The last candidate found completed everywhere, so its status is not read again on every poll.
  private volatile @Nullable ConfirmedBackup confirmed;

  public CommonBackupPositionSource(
      final BackupApi backupApi,
      final int partitionId,
      final Supplier<Collection<Integer>> partitionsSupplier) {
    this.backupApi = backupApi;
    this.partitionId = partitionId;
    this.partitionsSupplier = partitionsSupplier;
  }

  @Override
  public CompletionStage<OptionalLong> backedUpPosition() {
    if (partitionsSupplier.get().isEmpty()) {
      return CompletableFuture.completedFuture(OptionalLong.empty());
    }
    return backupApi.getCheckpointState().thenCompose(this::resolveFromCheckpointState);
  }

  private CompletionStage<OptionalLong> resolveFromCheckpointState(
      final CheckpointStateResponse response) {
    final var partitions = partitionsSupplier.get();
    if (partitions.isEmpty()) {
      return CompletableFuture.completedFuture(OptionalLong.empty());
    }

    final var backupsByPartition = indexByPartition(response.getBackupStates());
    final var bounds = checkpointBounds(backupsByPartition, partitions);
    if (bounds == null) {
      confirmed = null;
      return CompletableFuture.completedFuture(OptionalLong.of(-1));
    }

    if (bounds.unanimous()) {
      return confirmUnanimousBackup(bounds.min(), backupsByPartition);
    }
    return confirmCandidateBackup(bounds.min(), partitions);
  }

  private CompletionStage<OptionalLong> confirmUnanimousBackup(
      final long checkpointId, final Map<Integer, PartitionCheckpointState> backupsByPartition) {
    final var ownBackup = backupsByPartition.get(partitionId);
    if (ownBackup == null) {
      confirmed = null;
      return CompletableFuture.completedFuture(OptionalLong.of(-1));
    }
    final long ownPosition = ownBackup.checkpointPosition();
    confirmed = new ConfirmedBackup(checkpointId, ownPosition);
    return CompletableFuture.completedFuture(OptionalLong.of(ownPosition));
  }

  private CompletionStage<OptionalLong> confirmCandidateBackup(
      final long candidate, final Collection<Integer> partitions) {
    final var previous = confirmed;
    if (previous != null && previous.checkpointId() == candidate) {
      return CompletableFuture.completedFuture(OptionalLong.of(previous.position()));
    }

    final boolean regressed = previous != null && candidate < previous.checkpointId();
    return backupApi
        .getStatus(candidate)
        .thenApply(status -> applyCandidateStatus(candidate, status, partitions, regressed));
  }

  private OptionalLong applyCandidateStatus(
      final long candidate,
      final BackupStatus status,
      final Collection<Integer> partitions,
      final boolean regressed) {
    final var position = ownPositionIfCompletedEverywhere(status, partitionId, partitions);
    if (position.isPresent()) {
      confirmed = new ConfirmedBackup(candidate, position.getAsLong());
      return position;
    }
    if (regressed) {
      confirmed = null;
      return OptionalLong.of(-1);
    }
    return OptionalLong.empty();
  }

  private static @Nullable CheckpointBounds checkpointBounds(
      final Map<Integer, PartitionCheckpointState> backupsByPartition,
      final Collection<Integer> partitions) {
    long min = Long.MAX_VALUE;
    long max = Long.MIN_VALUE;
    for (final int partition : partitions) {
      final var backup = backupsByPartition.get(partition);
      if (backup == null) {
        return null;
      }
      final long checkpointId = backup.checkpointId();
      min = Math.min(min, checkpointId);
      max = Math.max(max, checkpointId);
    }
    return new CheckpointBounds(min, max);
  }

  private static Map<Integer, PartitionCheckpointState> indexByPartition(
      final Set<PartitionCheckpointState> latestBackups) {
    final var byPartition = new HashMap<Integer, PartitionCheckpointState>(latestBackups.size());
    for (final var backup : latestBackups) {
      byPartition.put(backup.partitionId(), backup);
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

  private record CheckpointBounds(long min, long max) {
    boolean unanimous() {
      return min == max;
    }
  }

  private record ConfirmedBackup(long checkpointId, long position) {}
}
