/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.management;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.zeebe.backup.api.BackupRange;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.api.Checkpoint;
import io.camunda.zeebe.backup.common.BackupMetadata;
import io.camunda.zeebe.backup.common.BackupMetadata.RangeEntry;
import io.camunda.zeebe.backup.metrics.BackupMetadataSyncerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.SequencedCollection;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Syncs checkpoint metadata and backup ranges from RocksDB column families to a per-partition JSON
 * file in the backup store.
 */
public final class BackupMetadataSyncer implements AutoCloseable {

  static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .registerModule(new JavaTimeModule())
          .disable(WRITE_DATES_AS_TIMESTAMPS);
  private static final Logger LOG = LoggerFactory.getLogger(BackupMetadataSyncer.class);
  private final BackupStore backupStore;
  private final BackupMetadataSyncerMetrics metrics;

  public BackupMetadataSyncer(final BackupStore backupStore, final MeterRegistry meterRegistry) {
    this.backupStore = backupStore;
    metrics = new BackupMetadataSyncerMetrics(meterRegistry);
  }

  /**
   * Syncs the current state of the given column families to the backup store for the specified
   * partition. Reads all checkpoints and ranges, serializes them to JSON, and writes to the next
   * slot.
   *
   * @param partitionId partition ID to sync metadata for
   * @param checkpoints source of checkpoint metadata
   * @param ranges source of backup ranges
   * @return a future that completes when the sync is done
   */
  public CompletableFuture<Void> store(
      final int partitionId,
      final SequencedCollection<Checkpoint> checkpoints,
      final SequencedCollection<BackupRange> ranges) {
    final var timer = metrics.startSyncTimer(partitionId);
    final var metadata = createMetadata(partitionId, checkpoints, ranges);

    try {
      final var content = MAPPER.writeValueAsBytes(metadata);
      final var serializedSize = content.length;
      return backupStore
          .storeBackupMetadata(partitionId, content)
          .whenComplete(
              (result, error) -> {
                timer.close();
                if (error != null) {
                  LOG.warn("Failed to sync backup metadata", error);
                  metrics.recordFailedSync(partitionId);
                } else {
                  LOG.debug("Synced backup metadata");
                  metrics.recordSuccessfulSync(partitionId, serializedSize);
                }
              });
    } catch (final JsonProcessingException e) {
      LOG.error("Failed to serialize backup metadata", e);
      timer.close();
      metrics.recordFailedSync(partitionId);
      return CompletableFuture.failedFuture(e);
    }
  }

  private static @NonNull BackupMetadata createMetadata(
      final int partitionId,
      @NonNull final SequencedCollection<Checkpoint> checkpoints,
      @NonNull final SequencedCollection<BackupRange> ranges) {
    return new BackupMetadata(
        BackupMetadata.VERSION,
        partitionId,
        Instant.now(),
        checkpoints.stream()
            .map(
                entry ->
                    new BackupMetadata.CheckpointEntry(
                        entry.checkpointId(),
                        entry.checkpointPosition(),
                        Instant.ofEpochMilli(entry.checkpointTimestamp()),
                        entry.checkpointType(),
                        entry.firstLogPosition() > 0
                            ? OptionalLong.of(entry.firstLogPosition())
                            : OptionalLong.empty()))
            .toList(),
        ranges.stream().map(range -> new RangeEntry(range.start(), range.end())).toList());
  }

  /**
   * Loads the most recent valid backup metadata manifest for the given partition. Reads both slots
   * and returns the one with the higher valid sequence number.
   *
   * @param partitionId the partition to load metadata for
   * @return the manifest, or empty if no valid metadata exists
   */
  public CompletableFuture<Optional<BackupMetadata>> load(final int partitionId) {
    return backupStore
        .loadBackupMetadata(partitionId)
        .thenApply(
            optBytes ->
                optBytes.flatMap(
                    bytes -> {
                      try {
                        return Optional.of(MAPPER.readValue(bytes, BackupMetadata.class));
                      } catch (final IOException e) {
                        LOG.warn("Failed to parse backup metadata", e);
                        return Optional.empty();
                      }
                    }))
        .exceptionally(
            error -> {
              LOG.warn("Failed to load backup metadata", error);
              return Optional.empty();
            });
  }

  @Override
  public void close() {
    metrics.close();
  }
}
