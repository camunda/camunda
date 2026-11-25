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
import java.util.Collection;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Determines the backed-up position of one partition from the backups of every partition of its
 * physical tenant.
 *
 * <p>A restore uses a checkpoint that is backed up on all partitions, so a single partition's own
 * latest backup is not enough: other partitions may still be taking it, or may have failed it.
 *
 * <p>The candidate is the latest confirmed backup of the partition that is furthest behind, which
 * every other partition has passed. Its status on all partitions is then checked explicitly, since
 * a partition may have failed exactly that backup and confirmed a later one. Partitions do not need
 * to report the same latest backup at the same moment, so the position keeps advancing even when
 * backups overlap or partitions finish at different speeds. Both requests already exist for the
 * backup API.
 */
public final class CommonBackupPositionSource implements BackedUpPositionSource {

  private final BackupApi backupApi;
  private final String physicalTenantId;
  private final int partitionId;
  private final Supplier<Collection<Integer>> partitionsOfTenant;

  // The last candidate found completed everywhere, so its status is not read again on every poll.
  private volatile long confirmedCheckpoint = -1;
  private volatile long confirmedPosition = -1;

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
    return backupApi
        .getCheckpointState(physicalTenantId)
        .thenCompose(
            response -> {
              final var latestBackups = response.getBackupStates();
              if (latestBackups.isEmpty()) {
                return CompletableFuture.completedFuture(OptionalLong.of(-1));
              }
              final var candidate = candidateCheckpoint(latestBackups, partitionsOfTenant.get());
              if (candidate.isEmpty()) {
                return CompletableFuture.completedFuture(OptionalLong.empty());
              }
              final long checkpointId = candidate.getAsLong();
              if (checkpointId == confirmedCheckpoint) {
                return CompletableFuture.completedFuture(OptionalLong.of(confirmedPosition));
              }
              return backupApi
                  .getStatus(physicalTenantId, checkpointId)
                  .thenApply(
                      status -> {
                        final var position = ownPositionIfCompletedEverywhere(status, partitionId);
                        position.ifPresent(
                            value -> {
                              confirmedPosition = value;
                              confirmedCheckpoint = checkpointId;
                            });
                        return position;
                      });
            });
  }

  /**
   * Returns the lowest of the partitions' latest confirmed backups, or empty when some partition
   * has no confirmed backup yet.
   */
  static OptionalLong candidateCheckpoint(
      final Set<PartitionCheckpointState> latestBackups, final Collection<Integer> partitions) {
    if (latestBackups.size() != partitions.size()) {
      return OptionalLong.empty();
    }
    return latestBackups.stream().mapToLong(PartitionCheckpointState::checkpointId).min();
  }

  /**
   * Returns {@code partitionId}'s checkpoint position for the backup, or empty unless the backup is
   * completed on all partitions.
   */
  static OptionalLong ownPositionIfCompletedEverywhere(
      final BackupStatus status, final int partitionId) {
    if (status.status() != State.COMPLETED) {
      return OptionalLong.empty();
    }
    return status.partitions().stream()
        .filter(partition -> partition.partitionId() == partitionId)
        .map(PartitionBackupStatus::checkpointPosition)
        .filter(OptionalLong::isPresent)
        .mapToLong(OptionalLong::getAsLong)
        .findFirst();
  }
}
