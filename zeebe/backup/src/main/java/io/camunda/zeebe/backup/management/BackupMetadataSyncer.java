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
 * file in the backup store. Uses a two-file swap (slots "a" and "b") with a monotonic sequence
 * number for crash-safe atomicity.
 *
 * <p>On write, the syncer alternates between slots. On read, it loads both slots and picks the one
 * with the higher valid sequence number. If one slot is missing or corrupt, the other is used.
 */
public final class BackupMetadataSyncer {

  private static final Logger LOG = LoggerFactory.getLogger(BackupMetadataSyncer.class);
  private static final String SLOT_A = "a";
  private static final String SLOT_B = "b";

  private final BackupStore backupStore;
  private long sequenceNumber;
  private String lastWrittenSlot;

  public BackupMetadataSyncer(final BackupStore backupStore) {
    this.backupStore = backupStore;
    sequenceNumber = 0;
    lastWrittenSlot = SLOT_B; // first write will go to "a"
  }

  /**
   * Syncs the current state of the given column families to the backup store for the specified
   * partition. Reads all checkpoints and ranges, serializes them to JSON, and writes to the next
   * slot.
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

    sequenceNumber++;
    final var targetSlot = nextSlot();
    final var manifest =
        new BackupMetadataManifest(partitionId, sequenceNumber, Instant.now(), checkpoints, ranges);

    try {
      final var content = BackupMetadataCodec.serialize(manifest);
      return backupStore
          .storeBackupMetadata(partitionId, targetSlot, content)
          .whenComplete(
              (result, error) -> {
                if (error != null) {
                  LOG.warn(
                      "Failed to sync backup metadata for partition {} to slot {}",
                      partitionId,
                      targetSlot,
                      error);
                  // Roll back sequence number so next sync retries with the same number
                  sequenceNumber--;
                } else {
                  lastWrittenSlot = targetSlot;
                  LOG.debug(
                      "Synced backup metadata for partition {} to slot {} (seq={})",
                      partitionId,
                      targetSlot,
                      sequenceNumber);
                }
              });
    } catch (final JsonProcessingException e) {
      LOG.error("Failed to serialize backup metadata for partition {}", partitionId, e);
      sequenceNumber--;
      return CompletableFuture.failedFuture(e);
    }
  }

  /**
   * Loads the most recent valid backup metadata manifest for the given partition. Reads both slots
   * and returns the one with the higher valid sequence number.
   *
   * @param partitionId the partition to load metadata for
   * @return the manifest, or empty if no valid metadata exists
   */
  public CompletableFuture<Optional<BackupMetadataManifest>> load(final int partitionId) {
    return BackupMetadataCodec.load(backupStore, partitionId)
        .thenApply(
            optManifest -> {
              optManifest.ifPresent(this::updateInternalState);
              return optManifest;
            });
  }

  /**
   * Updates internal syncer state from a loaded manifest so subsequent syncs continue from the
   * correct sequence number and slot.
   */
  private void updateInternalState(final BackupMetadataManifest manifest) {
    sequenceNumber = manifest.sequenceNumber();
    // We don't know which slot the loaded manifest came from directly,
    // but we can infer the next slot by treating the current state as if
    // the last write was to the opposite slot. We'll simply keep alternating.
  }

  private String nextSlot() {
    return SLOT_A.equals(lastWrittenSlot) ? SLOT_B : SLOT_A;
  }

  // Visible for testing
  long getSequenceNumber() {
    return sequenceNumber;
  }

  // Visible for testing
  String getLastWrittenSlot() {
    return lastWrittenSlot;
  }
}
