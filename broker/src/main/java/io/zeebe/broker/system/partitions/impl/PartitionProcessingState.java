/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.partitions.impl;

import io.atomix.raft.partition.RaftPartition;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class PartitionProcessingState {

  private static final String PERSISTED_PAUSE_STATE_FILENAME = ".paused";
  private boolean isProcessingPaused;
  private final RaftPartition raftPartition;
  private boolean diskSpaceAvailable;

  public PartitionProcessingState(final RaftPartition raftPartition) {
    this.raftPartition = raftPartition;
    initProcessingStatus();
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
    final File persistedPauseState = getPersistedPauseState();
    Files.deleteIfExists(persistedPauseState.toPath());
    if (!persistedPauseState.exists()) {
      isProcessingPaused = false;
    }
  }

  @SuppressWarnings({"squid:S899"})
  public void pauseProcessing() throws IOException {
    final File persistedPauseState = getPersistedPauseState();
    persistedPauseState.createNewFile();
    if (persistedPauseState.exists()) {
      isProcessingPaused = true;
    }
  }

  private File getPersistedPauseState() {
    return raftPartition.dataDirectory().toPath().resolve(PERSISTED_PAUSE_STATE_FILENAME).toFile();
  }

  private void initProcessingStatus() {
    isProcessingPaused = getPersistedPauseState().exists();
  }

  public boolean shouldProcess() {
    return isDiskSpaceAvailable() && !isProcessingPaused();
  }
}
