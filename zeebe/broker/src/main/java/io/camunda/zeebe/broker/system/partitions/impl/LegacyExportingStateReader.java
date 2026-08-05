/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.broker.exporter.stream.ExporterPhase;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the legacy per-partition {@code .exporterPaused} state from disk, so it can be migrated
 * into the dynamic cluster configuration on startup. See {@link
 * io.camunda.zeebe.dynamic.config.ExportingStateInitializer}.
 */
@NullMarked
public final class LegacyExportingStateReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(LegacyExportingStateReader.class);

  private LegacyExportingStateReader() {}

  /** Reads the persisted exporting state of every partition the local member has on disk. */
  public static Map<PartitionId, ExportingState> readLegacyExportingStates(
      final String dataDirectory) {
    final Map<PartitionId, ExportingState> legacyExportingStates = new HashMap<>();
    final var dataDir = Path.of(dataDirectory);
    if (!Files.isDirectory(dataDir)) {
      return legacyExportingStates;
    }
    // Legacy .exporterPaused files live at <dataDir>/<group>/partitions/<number>/. Enumerate the
    // partition directories that already exist on disk rather than computing the partition
    // distribution: distribution generation goes through the partition distributor, which is not
    // valid at this early startup point in some cluster configurations (e.g. zone-aware, where the
    // static replication factor does not yet match the per-zone replica sum). A partition the local
    // member does not host has no directory here, so there is nothing to migrate anyway.
    try (final var groupDirs = Files.newDirectoryStream(dataDir, Files::isDirectory)) {
      for (final var groupDir : groupDirs) {
        final var groupId = groupDir.getFileName().toString();
        final var partitionsDir = groupDir.resolve("partitions");
        if (!Files.isDirectory(partitionsDir)) {
          continue;
        }
        try (final var partitionDirs =
            Files.newDirectoryStream(partitionsDir, Files::isDirectory)) {
          for (final var partitionDir : partitionDirs) {
            final var partitionId = tryParsePartitionId(partitionDir);
            if (partitionId == null) {
              continue;
            }
            readLegacyExportingState(partitionDir)
                .ifPresent(
                    state ->
                        legacyExportingStates.put(new PartitionId(groupId, partitionId), state));
          }
        }
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return legacyExportingStates;
  }

  private static @Nullable Integer tryParsePartitionId(final Path partitionDir) {
    try {
      return Integer.parseInt(partitionDir.getFileName().toString());
    } catch (final NumberFormatException e) {
      return null;
    }
  }

  private static Optional<ExportingState> readLegacyExportingState(final Path partitionDir) {
    final ExporterPhase phase;
    try {
      phase = PartitionProcessingState.readPersistedExporterPhase(partitionDir);
    } catch (final IllegalArgumentException e) {
      // The file holds a phase this version does not know. Leave the partition UNKNOWN instead of
      // failing broker startup: the partition itself still reads the same file when it starts, so
      // the failure stays scoped to that partition, as it was before exporting moved into the
      // dynamic configuration.
      LOGGER.warn("Ignoring unreadable legacy exporting state in {}", partitionDir, e);
      return Optional.empty();
    }
    return Optional.of(
        switch (phase) {
          case PAUSED -> ExportingState.PAUSED;
          case SOFT_PAUSED -> ExportingState.SOFT_PAUSED;
          // CLOSED is never persisted; it only exists while an exporter director shuts down.
          case EXPORTING, CLOSED -> ExportingState.EXPORTING;
        });
  }
}
