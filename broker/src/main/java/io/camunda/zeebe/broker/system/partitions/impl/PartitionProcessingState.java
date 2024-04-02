/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import io.atomix.raft.partition.RaftPartition;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

public class PartitionProcessingState {

  private static final String PERSISTED_PAUSE_STATE_FILENAME = ".processorPaused";
  private static final String PERSISTED_EXPORTER_PAUSE_STATE_FILENAME = ".exporterPaused";
  private boolean isProcessingPaused;
  private ExporterState exporterState;
  private final RaftPartition raftPartition;
  private boolean diskSpaceAvailable;

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

  public boolean shouldProcess() {
    return isDiskSpaceAvailable() && !isProcessingPaused();
  }

  public ExporterState getExporterState() {
    return exporterState;
  }

  @SuppressWarnings({"squid:S899"})
  /** Returns true if exporting is paused. This method overrides the effects of soft pause. */
  public boolean pauseExporting() {
    try {
      setPersistedExporterState(ExporterState.PAUSED);
    } catch (final IOException e) {
      return false;
    }
    return true;
  }

  /** Returns true if soft exporting is paused. This method overrides the effects of hard pause. */
  public boolean softPauseExporting() {
    try {
      setPersistedExporterState(ExporterState.SOFT_PAUSED);
    } catch (final IOException e) {
      return false;
    }
    return true;
  }

  /** Returns true if exporting is resumed. This method resumes both soft and "hard" exporting. */
  public boolean resumeExporting() {
    try {
      setPersistedExporterState(ExporterState.EXPORTING);
    } catch (final IOException e) {
      return false;
    }
    return true;
  }

  void setPersistedExporterState(final ExporterState state) throws IOException {
    exporterState = state;
    if (state.equals(ExporterState.EXPORTING)) {
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
        StandardOpenOption.CREATE);
  }

  private void initExportingState() {
    try {
      if (!getPersistedPauseState(PERSISTED_EXPORTER_PAUSE_STATE_FILENAME).exists()) {
        setPersistedExporterState(ExporterState.EXPORTING);
        exporterState = ExporterState.EXPORTING;
      } else {
        final var state =
            Files.readString(
                getPersistedPauseState(PERSISTED_EXPORTER_PAUSE_STATE_FILENAME).toPath());
        if (state == null || state.isEmpty() || state.isBlank()) {
          // Backwards compatibility. If the file exists, it is paused.
          exporterState = ExporterState.PAUSED;
          return;
        }
        exporterState = ExporterState.valueOf(state);
      }
    } catch (final IOException e) {
      // exporting is the default state
      exporterState = ExporterState.EXPORTING;
    }
  }

  public enum ExporterState {
    PAUSED,
    SOFT_PAUSED,
    EXPORTING;
  }
}
