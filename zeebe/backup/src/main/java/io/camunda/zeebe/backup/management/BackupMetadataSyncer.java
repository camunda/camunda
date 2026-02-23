/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.management;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupMetadataCodec;
import io.camunda.zeebe.backup.common.BackupMetadataManifest;
import io.camunda.zeebe.backup.common.BackupMetadataManifest.CheckpointEntry;
import io.camunda.zeebe.backup.common.BackupMetadataManifest.RangeEntry;
import io.camunda.zeebe.backup.processing.state.DbBackupRangeState;
import io.camunda.zeebe.backup.processing.state.DbCheckpointMetadataState;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Syncs checkpoint metadata and backup ranges from RocksDB column families to a per-partition JSON
 * file in the backup store. A single file per partition is overwritten on each sync. If the file is
 * found to be corrupted on read, it will be overwritten on the next sync (which happens on every
 * mutation and on leader election).
 */
public final class BackupMetadataSyncer {

  private static final Logger LOG = LoggerFactory.getLogger(BackupMetadataSyncer.class);

  private final BackupStore backupStore;

  public BackupMetadataSyncer(final BackupStore backupStore) {
    this.backupStore = backupStore;
  }

  /**
   * Syncs the current state of the given column families to the backup store for the specified
   * partition. Reads all checkpoints and ranges, serializes them to JSON, and overwrites the file.
   *
   * @param partitionId the partition to sync
   * @param checkpointMetadataState source of checkpoint metadata
   * @param backupRangeState source of backup ranges
   * @return a future that completes when the sync is done
   */
  public CompletableFuture<Void> sync(
      final int partitionId,
      final DbCheckpointMetadataState checkpointMetadataState,
      final DbBackupRangeState backupRangeState) {

    final var checkpoints =
        checkpointMetadataState.getAllCheckpoints().stream()
            .map(
                entry ->
                    new CheckpointEntry(
                        entry.checkpointId(),
                        entry.checkpointPosition(),
                        Instant.ofEpochMilli(entry.checkpointTimestamp()),
                        entry.checkpointType(),
                        entry.firstLogPosition(),
                        entry.numberOfPartitions(),
                        entry.brokerVersion()))
            .toList();

    final var ranges =
        backupRangeState.getAllRanges().stream()
            .map(range -> new RangeEntry(range.start(), range.end()))
            .toList();

    final var manifest =
        new BackupMetadataManifest(partitionId, Instant.now(), checkpoints, ranges);

    try {
      final var content = BackupMetadataCodec.serialize(manifest);
      return backupStore
          .storeBackupMetadata(partitionId, content)
          .whenComplete(
              (result, error) -> {
                if (error != null) {
                  LOG.warn("Failed to sync backup metadata for partition {}", partitionId, error);
                } else {
                  LOG.debug("Synced backup metadata for partition {}", partitionId);
                }
              });
    } catch (final JsonProcessingException e) {
      LOG.error("Failed to serialize backup metadata for partition {}", partitionId, e);
      return CompletableFuture.failedFuture(e);
    }
  }

  /**
   * Loads the backup metadata manifest for the given partition.
   *
   * @param partitionId the partition to load metadata for
   * @return the manifest, or empty if no valid metadata exists
   */
  public CompletableFuture<Optional<BackupMetadataManifest>> load(final int partitionId) {
    return BackupMetadataCodec.load(backupStore, partitionId);
  }
}
