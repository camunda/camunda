/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the legacy per-partition {@code .exporterPaused} state from disk, so it can be migrated
 * into the dynamic cluster configuration on startup. See {@link
 * io.camunda.zeebe.dynamic.config.PartitionGroupExportingStateInitializer}.
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
    // Legacy .exporterPaused files live at <dataDir>/<group>/partitions/<number>/.
    // Enumerate the partition directories that already exist on disk
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
            final var state = readLegacyExportingState(partitionDir);
            legacyExportingStates.put(new PartitionId(groupId, partitionId), state);
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

  private static ExportingState readLegacyExportingState(final Path partitionDir) {
    return PartitionProcessingState.readPersistedExporterPhase(partitionDir).toExportingState();
  }
}
