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
import java.util.Arrays;
import java.util.stream.Stream;

public class PartitionProcessingState {

  private static final String PERSISTED_PAUSE_STATE_FILENAME = ".processorPaused";
  private static final String PERSISTED_EXPORTER_PAUSE_STATE_FILENAME = ".exporterPaused";
  private static final String PAUSED = "paused";
  private static final String SOFT_PAUSED = "softPaused";
  private static final String EXPORTING = "";
  private boolean isProcessingPaused;
  private String exporterState;
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
    return exporterState.equals(PAUSED);
  }

  public boolean isExportingSoftPaused() {
    return exporterState.equals(SOFT_PAUSED);
  }

  @SuppressWarnings({"squid:S899"})
  /** Returns true if exporting is paused. This method overrides the effects of soft pause. */
  public boolean pauseExporting() {
    try {
      setPersistedExporterState(PAUSED);
    } catch (final IOException e) {
      return false;
    }
    return true;
  }

  /** Returns true if soft exporting is paused. This method overrides the effects of hard pause. */
  public boolean softPauseExporting() {
    try {
      setPersistedExporterState(SOFT_PAUSED);
    } catch (final IOException e) {
      return false;
    }
    return true;
  }

  /** Returns true if exporting is resumed. This method resumes both soft and "hard" exporting. */
  public boolean resumeExporting() {
    try {
      setPersistedExporterState(EXPORTING);
    } catch (final IOException e) {
      return false;
    }
    return true;
  }

  void setPersistedExporterState(final String state) throws IOException {
    final File persistedExporterPauseState =
        getPersistedPauseState(PERSISTED_EXPORTER_PAUSE_STATE_FILENAME);
    Files.write(persistedExporterPauseState.toPath(), Arrays.asList(state), StandardCharsets.UTF_8);
    exporterState = state;
  }

  private void initExportingState() {
    Stream<String> stream = null;
    try {
      if (!getPersistedPauseState(PERSISTED_EXPORTER_PAUSE_STATE_FILENAME).exists()) {
        setPersistedExporterState(EXPORTING);
      } else {
        stream =
            Files.lines(getPersistedPauseState(PERSISTED_EXPORTER_PAUSE_STATE_FILENAME).toPath());
        exporterState = stream.findFirst().get();
        stream.close();
      }
    } catch (final IOException e) {
      // exporting is the default state
      exporterState = EXPORTING;
    }
  }
}
