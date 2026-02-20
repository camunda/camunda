/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.common;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.zeebe.backup.api.BackupStore;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared serialization and two-slot loading logic for {@link BackupMetadataManifest}. Used by both
 * the syncer (write path) and the reader (restore path) to avoid duplicating ObjectMapper setup and
 * slot-loading logic.
 */
public final class BackupMetadataCodec {

  private static final Logger LOG = LoggerFactory.getLogger(BackupMetadataCodec.class);
  private static final String SLOT_A = "a";
  private static final String SLOT_B = "b";

  public static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .registerModule(new JavaTimeModule())
          .disable(WRITE_DATES_AS_TIMESTAMPS);

  private BackupMetadataCodec() {}

  /** Serializes a manifest to JSON bytes. */
  public static byte[] serialize(final BackupMetadataManifest manifest)
      throws JsonProcessingException {
    return MAPPER.writeValueAsBytes(manifest);
  }

  /** Deserializes JSON bytes to a manifest, returning empty on parse failure. */
  public static Optional<BackupMetadataManifest> deserialize(final byte[] bytes) {
    try {
      return Optional.of(MAPPER.readValue(bytes, BackupMetadataManifest.class));
    } catch (final IOException e) {
      LOG.warn("Failed to deserialize backup metadata manifest", e);
      return Optional.empty();
    }
  }

  /**
   * Loads the most recent valid backup metadata manifest for the given partition from the backup
   * store. Reads both slots ("a" and "b") and returns the one with the higher valid sequence
   * number.
   *
   * @param backupStore the backup store to read from
   * @param partitionId the partition to load metadata for
   * @return the manifest, or empty if no valid metadata exists in either slot
   */
  public static CompletableFuture<Optional<BackupMetadataManifest>> load(
      final BackupStore backupStore, final int partitionId) {
    final var futureA = loadSlot(backupStore, partitionId, SLOT_A);
    final var futureB = loadSlot(backupStore, partitionId, SLOT_B);

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

  /**
   * Loads a single slot from the backup store and deserializes it.
   *
   * @param backupStore the backup store to read from
   * @param partitionId the partition to load metadata for
   * @param slot the slot identifier ("a" or "b")
   * @return the deserialized manifest, or empty if the slot is missing or corrupt
   */
  public static CompletableFuture<Optional<BackupMetadataManifest>> loadSlot(
      final BackupStore backupStore, final int partitionId, final String slot) {
    return backupStore
        .loadBackupMetadata(partitionId, slot)
        .thenApply(optBytes -> optBytes.flatMap(BackupMetadataCodec::deserialize))
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
