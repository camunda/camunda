/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.zeebe.backup.client.api.BackupApi;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse.PartitionCheckpointState;
import java.util.Collection;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Determines the backed-up position of one partition from the latest confirmed backup of every
 * partition of its physical tenant.
 *
 * <p>A restore uses a checkpoint that is backed up on all partitions, so a single partition's own
 * latest backup is not enough: other partitions may still be taking it, or may have failed it. This
 * source asks all partitions for their latest confirmed backup, using the same request as the
 * backup scheduler, and only reports a position when every partition reports the same checkpoint.
 * That checkpoint is then confirmed everywhere, and this partition's position for it is safe to
 * export up to.
 */
public final class CommonBackupPositionSource implements BackedUpPositionSource {

  private final BackupApi backupApi;
  private final String physicalTenantId;
  private final int partitionId;
  private final Supplier<Collection<Integer>> partitionsOfTenant;

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
        .thenApply(
            response ->
                commonBackupPosition(
                    response.getBackupStates(), partitionsOfTenant.get(), partitionId));
  }

  /**
   * Returns {@code partitionId}'s checkpoint position of the backup that all {@code partitions}
   * report as their latest, {@code -1} when no partition has a backup, or empty when the partitions
   * disagree or some have no backup yet.
   */
  static OptionalLong commonBackupPosition(
      final Set<PartitionCheckpointState> latestBackups,
      final Collection<Integer> partitions,
      final int partitionId) {
    if (latestBackups.isEmpty()) {
      return OptionalLong.of(-1);
    }
    if (latestBackups.size() != partitions.size()
        || latestBackups.stream()
                .mapToLong(PartitionCheckpointState::checkpointId)
                .distinct()
                .count()
            != 1) {
      return OptionalLong.empty();
    }
    return latestBackups.stream()
        .filter(backup -> backup.partitionId() == partitionId)
        .mapToLong(PartitionCheckpointState::checkpointPosition)
        .findFirst();
  }
}
