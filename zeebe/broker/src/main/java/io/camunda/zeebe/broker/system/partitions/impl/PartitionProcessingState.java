/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.broker.exporter.stream.ExporterPhase;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class PartitionProcessingState {

  public static final String PERSISTED_EXPORTER_PAUSE_STATE_FILENAME = ".exporterPaused";
  private static final String PERSISTED_PAUSE_STATE_FILENAME = ".processorPaused";
  private boolean isProcessingPaused;
  private ExporterPhase exporterPhase;
  private final RaftPartition raftPartition;
  private boolean diskSpaceAvailable;
  // Transient, not persisted like other pause states - a crash must never leave the partition
  // durably paused
  private boolean pausedForTransfer;

  public PartitionProcessingState(final RaftPartition raftPartition) {
    this.raftPartition = raftPartition;
    initProcessingStatus();
    initExportingState();
  }

  public boolean isDiskSpaceAvailable() {
    return diskSpaceAvailable;
  }

  public void setDiskSpaceAvailable(final boolean diskSpaceAvailable) {
    this.diskSpaceAvailable = diskSpaceAvailable;
  }

  public boolean isProcessingPaused() {
    return isProcessingPaused;
  }

  public void resumeProcessing() throws IOException {
    final File persistedPauseState = getPersistedPauseState(PERSISTED_PAUSE_STATE_FILENAME);
    Files.deleteIfExists(persistedPauseState.toPath());
    if (!persistedPauseState.exists()) {
      isProcessingPaused = false;
    }
  }

  @SuppressWarnings({"squid:S899"})
  public void pauseProcessing() throws IOException {
    final File persistedPauseState = getPersistedPauseState(PERSISTED_PAUSE_STATE_FILENAME);
    persistedPauseState.createNewFile();
    if (persistedPauseState.exists()) {
      isProcessingPaused = true;
    }
  }

  private File getPersistedPauseState(final String filename) {
    return raftPartition.dataDirectory().toPath().resolve(filename).toFile();
  }

  private void initProcessingStatus() {
    isProcessingPaused = getPersistedPauseState(PERSISTED_PAUSE_STATE_FILENAME).exists();
  }

  public boolean isPausedForTransfer() {
    return pausedForTransfer;
  }

  public void setPausedForTransfer(final boolean pausedForTransfer) {
    this.pausedForTransfer = pausedForTransfer;
  }

  public boolean shouldProcess() {
    return isDiskSpaceAvailable() && !isProcessingPaused() && !pausedForTransfer;
  }

  public boolean isExportingPaused() {
    return exporterPhase.equals(ExporterPhase.PAUSED);
  }

  public ExporterPhase getExporterPhase() {
    return exporterPhase;
  }

  @SuppressWarnings({"squid:S899"})
  /** Returns true if exporting is paused. This method overrides the effects of soft pause. */
  public boolean pauseExporting() {
    try {
      setPersistedExporterPhase(ExporterPhase.PAUSED);
    } catch (final IOException e) {
      return false;
    }
    return true;
  }

  /** Returns true if soft exporting is paused. This method overrides the effects of hard pause. */
  public boolean softPauseExporting() {
    try {
      setPersistedExporterPhase(ExporterPhase.SOFT_PAUSED);
    } catch (final IOException e) {
      return false;
    }
    return true;
  }

  /** Returns true if exporting is resumed. This method resumes both soft and "hard" exporting. */
  public boolean resumeExporting() {
    try {
      setPersistedExporterPhase(ExporterPhase.EXPORTING);
    } catch (final IOException e) {
      return false;
    }
    return true;
  }

  void setPersistedExporterPhase(final ExporterPhase state) throws IOException {
    exporterPhase = state;
    if (state.equals(ExporterPhase.EXPORTING)) {
      // since exporting is the default state, we can delete the file
      Files.deleteIfExists(
          getPersistedPauseState(PERSISTED_EXPORTER_PAUSE_STATE_FILENAME).toPath());
      return;
    }

    final File persistedExporterPauseState =
        getPersistedPauseState(PERSISTED_EXPORTER_PAUSE_STATE_FILENAME);
    Files.writeString(
        persistedExporterPauseState.toPath(),
        state.name(),
        StandardCharsets.UTF_8,
        StandardOpenOption.DSYNC,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  /**
   * Reads the exporter phase persisted for a partition, without instantiating the surrounding
   * state. Callers that only have the partition directory - for example the startup code migrating
   * the legacy phase into the dynamic cluster configuration, which runs before any partition exists
   * - use this instead of constructing a {@link PartitionProcessingState}.
   */
  public static ExporterPhase readPersistedExporterPhase(final Path partitionDirectory) {
    final var persistedState = partitionDirectory.resolve(PERSISTED_EXPORTER_PAUSE_STATE_FILENAME);
    try {
      if (!Files.exists(persistedState)) {
        // exporting is the default state, so the file is absent while exporting
        return ExporterPhase.EXPORTING;
      }
      final var state = Files.readString(persistedState, StandardCharsets.UTF_8).trim();
      if (state.isBlank()) {
        // Backwards compatibility. If the file exists, it is paused.
        return ExporterPhase.PAUSED;
      }
      return ExporterPhase.valueOf(state);
    } catch (final IOException e) {
      // exporting is the default state
      return ExporterPhase.EXPORTING;
    }
  }

  private void initExportingState() {
    exporterPhase = readPersistedExporterPhase(raftPartition.dataDirectory().toPath());
  }
}
