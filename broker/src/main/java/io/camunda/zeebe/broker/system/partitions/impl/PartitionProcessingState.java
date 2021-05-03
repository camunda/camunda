/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.partitions.impl;

import io.atomix.raft.partition.RaftPartition;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class PartitionProcessingState {

  private static final String PERSISTED_PAUSE_STATE_FILENAME = ".processorPaused";
  private static final String PERSISTED_EXPORTER_PAUSE_STATE_FILENAME = ".exporterPaused";
  private boolean isProcessingPaused;
  private boolean isExportingPaused;
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

  public boolean isExportingPaused() {
    return isExportingPaused;
  }

  @SuppressWarnings({"squid:S899"})
  /** Returns true if exporting is paused */
  public boolean pauseExporting() throws IOException {
    final File persistedExporterPauseState =
        getPersistedPauseState(PERSISTED_EXPORTER_PAUSE_STATE_FILENAME);
    persistedExporterPauseState.createNewFile();
    if (persistedExporterPauseState.exists()) {
      isExportingPaused = true;
      return true;
    }
    return false;
  }

  /** Returns true if exporting is resumed */
  public boolean resumeExporting() throws IOException {
    final File persistedExporterPauseState =
        getPersistedPauseState(PERSISTED_EXPORTER_PAUSE_STATE_FILENAME);
    Files.deleteIfExists(persistedExporterPauseState.toPath());
    if (!persistedExporterPauseState.exists()) {
      isExportingPaused = false;
      return true;
    }
    return false;
  }

  private void initExportingState() {
    isExportingPaused = getPersistedPauseState(PERSISTED_EXPORTER_PAUSE_STATE_FILENAME).exists();
  }
}
