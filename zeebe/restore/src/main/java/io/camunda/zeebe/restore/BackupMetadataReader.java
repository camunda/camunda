/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupMetadataManifest;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads per-partition backup metadata manifests from the backup store. Loads both slots ("a" and
 * "b") and picks the one with the higher valid sequence number, providing crash-safe reads against
 * the two-file atomic swap written by {@code BackupMetadataSyncer}.
 */
@NullMarked
public final class BackupMetadataReader {

  private static final Logger LOG = LoggerFactory.getLogger(BackupMetadataReader.class);
  private static final String SLOT_A = "a";
  private static final String SLOT_B = "b";

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .registerModule(new JavaTimeModule())
          .disable(WRITE_DATES_AS_TIMESTAMPS);

  private final BackupStore backupStore;

  public BackupMetadataReader(final BackupStore backupStore) {
    this.backupStore = backupStore;
  }

  /**
   * Loads the most recent valid backup metadata manifest for the given partition. Reads both slots
   * and returns the one with the higher valid sequence number.
   *
   * @param partitionId the partition to load metadata for
   * @return the manifest, or empty if no valid metadata exists in either slot
   */
  public CompletableFuture<Optional<BackupMetadataManifest>> load(final int partitionId) {
    final var futureA = loadSlot(partitionId, SLOT_A);
    final var futureB = loadSlot(partitionId, SLOT_B);

    return futureA.thenCombine(
        futureB,
        (manifestA, manifestB) -> {
          if (manifestA.isPresent() && manifestB.isPresent()) {
            final var a = manifestA.get();
            final var b = manifestB.get();
            return Optional.of(a.sequenceNumber() >= b.sequenceNumber() ? a : b);
          } else if (manifestA.isPresent()) {
            return manifestA;
          } else if (manifestB.isPresent()) {
            return manifestB;
          } else {
            return Optional.empty();
          }
        });
  }

  private CompletableFuture<Optional<BackupMetadataManifest>> loadSlot(
      final int partitionId, final String slot) {
    return backupStore
        .loadBackupMetadata(partitionId, slot)
        .thenApply(
            optBytes ->
                optBytes.flatMap(
                    bytes -> {
                      try {
                        return Optional.of(MAPPER.readValue(bytes, BackupMetadataManifest.class));
                      } catch (final IOException e) {
                        LOG.warn(
                            "Failed to parse backup metadata from slot {} for partition {}",
                            slot,
                            partitionId,
                            e);
                        return Optional.empty();
                      }
                    }))
        .exceptionally(
            error -> {
              LOG.warn(
                  "Failed to load backup metadata from slot {} for partition {}",
                  slot,
                  partitionId,
                  error);
              return Optional.empty();
            });
  }
}
