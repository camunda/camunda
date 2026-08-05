/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LegacyExportingStateReaderTest {

  @TempDir private Path dataDirectory;

  @Test
  void shouldReturnEmptyWhenDataDirectoryDoesNotExist() {
    // given
    final var missing = dataDirectory.resolve("does-not-exist");

    // when
    final var states = LegacyExportingStateReader.readLegacyExportingStates(missing.toString());

    // then
    assertThat(states).isEmpty();
  }

  @Test
  void shouldReadLegacyExportingStateFromDiskWithoutPartitionDistribution() throws IOException {
    // given - partition directories laid out as <dataDir>/<group>/partitions/<number>/, mirroring
    // how the broker persists them. No StaticConfiguration or partition distributor is involved,
    // which is exactly why reading from disk survives cluster configurations (e.g. zone-aware)
    // where distribution generation is not yet valid at startup.
    writeExporterPausedFile("default", 1, "PAUSED");
    writeExporterPausedFile("default", 2, "SOFT_PAUSED");
    createPartitionDirectory("default", 3); // no file -> EXPORTING
    writeExporterPausedFile("default", 4, ""); // blank -> PAUSED for backwards compatibility

    // when
    final var states =
        LegacyExportingStateReader.readLegacyExportingStates(dataDirectory.toString());

    // then
    assertThat(states)
        .containsOnly(
            entry(new PartitionId("default", 1), ExportingState.PAUSED),
            entry(new PartitionId("default", 2), ExportingState.SOFT_PAUSED),
            entry(new PartitionId("default", 3), ExportingState.EXPORTING),
            entry(new PartitionId("default", 4), ExportingState.PAUSED));
  }

  @Test
  void shouldReadPartitionsAcrossMultipleGroups() throws IOException {
    // given
    writeExporterPausedFile("default", 1, "PAUSED");
    writeExporterPausedFile("tenant-a", 1, "SOFT_PAUSED");

    // when
    final var states =
        LegacyExportingStateReader.readLegacyExportingStates(dataDirectory.toString());

    // then
    assertThat(states)
        .containsOnly(
            entry(new PartitionId("default", 1), ExportingState.PAUSED),
            entry(new PartitionId("tenant-a", 1), ExportingState.SOFT_PAUSED));
  }

  @Test
  void shouldIgnoreNonNumericPartitionDirectories() throws IOException {
    // given
    writeExporterPausedFile("default", 1, "PAUSED");
    Files.createDirectories(dataDirectory.resolve("default").resolve("partitions").resolve("junk"));

    // when
    final var states =
        LegacyExportingStateReader.readLegacyExportingStates(dataDirectory.toString());

    // then
    assertThat(states).containsOnlyKeys(new PartitionId("default", 1));
  }

  @Test
  void shouldThrowWhenPersistedPhaseIsUnknown() throws IOException {
    // given
    writeExporterPausedFile("default", 1, "PAUSED");
    writeExporterPausedFile("default", 2, "NOT_A_PHASE");

    // when / then
    assertThatThrownBy(
            () -> LegacyExportingStateReader.readLegacyExportingStates(dataDirectory.toString()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "No enum constant io.camunda.zeebe.broker.exporter.stream.ExporterPhase.NOT_A_PHASE");
  }

  @Test
  void shouldReturnEmptyWhenNoPartitionDirectoriesExist() {
    // given - a fresh broker whose partitions have not been created yet

    // when
    final var states =
        LegacyExportingStateReader.readLegacyExportingStates(dataDirectory.toString());

    // then
    assertThat(states).isEmpty();
  }

  private Path createPartitionDirectory(final String group, final int partitionId)
      throws IOException {
    final var partitionDir =
        dataDirectory.resolve(group).resolve("partitions").resolve(String.valueOf(partitionId));
    Files.createDirectories(partitionDir);
    return partitionDir;
  }

  private void writeExporterPausedFile(
      final String group, final int partitionId, final String content) throws IOException {
    final var partitionDir = createPartitionDirectory(group, partitionId);
    Files.writeString(
        partitionDir.resolve(PartitionProcessingState.PERSISTED_EXPORTER_PAUSE_STATE_FILENAME),
        content,
        StandardCharsets.UTF_8);
  }
}
