/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.raft.partition.RaftPartition;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PartitionProcessingStateTest {
  private static final String PERSISTED_PAUSE_STATE_FILENAME = ".processorPaused";
  private static final String PERSISTED_EXPORTER_PAUSE_STATE_FILENAME = ".exporterPaused";
  private static final String TEST_DIR = "src/test/resources";
  private static final Path PATH_TO_EXPORTER_STATE =
      new File(TEST_DIR + PERSISTED_EXPORTER_PAUSE_STATE_FILENAME).toPath();
  private static final Path PATH_TO_PROCESSOR_STATE =
      new File(TEST_DIR + PERSISTED_EXPORTER_PAUSE_STATE_FILENAME).toPath();
  private static final RaftPartition MOCK_RAFT_PARTITION =
      mock(RaftPartition.class, RETURNS_DEEP_STUBS);

  private static final File PROCESSOR_STATE_FILE = mock(File.class);
  private static final File EXPORTER_STATE_FILE = mock(File.class);

  @BeforeAll
  static void setUp() {
    when(MOCK_RAFT_PARTITION
            .dataDirectory()
            .toPath()
            .resolve(PERSISTED_PAUSE_STATE_FILENAME)
            .toFile())
        .thenReturn(PROCESSOR_STATE_FILE);
    when(MOCK_RAFT_PARTITION
            .dataDirectory()
            .toPath()
            .resolve(PERSISTED_EXPORTER_PAUSE_STATE_FILENAME)
            .toFile())
        .thenReturn(EXPORTER_STATE_FILE);
    when(PROCESSOR_STATE_FILE.toPath()).thenReturn(PATH_TO_PROCESSOR_STATE);
    when(EXPORTER_STATE_FILE.toPath()).thenReturn(PATH_TO_EXPORTER_STATE);
  }

  @AfterAll
  static void afterAll() {
    PATH_TO_EXPORTER_STATE.toFile().delete();
    PATH_TO_PROCESSOR_STATE.toFile().delete();
  }

  @Test
  void shouldPauseAndResumeProcessing() throws IOException {
    // given
    final var partitionProcessingState = new PartitionProcessingState(MOCK_RAFT_PARTITION);
    when(PROCESSOR_STATE_FILE.exists()).thenReturn(true);
    partitionProcessingState.pauseProcessing();

    assertTrue(partitionProcessingState.isProcessingPaused());

    // when
    when(PROCESSOR_STATE_FILE.exists()).thenReturn(false);
    partitionProcessingState.resumeProcessing();

    // then
    assertFalse(partitionProcessingState.isProcessingPaused());
  }

  @Test
  void shouldPauseAndResumeExporting() {
    // given
    final var partitionProcessingState = new PartitionProcessingState(MOCK_RAFT_PARTITION);
    partitionProcessingState.pauseExporting();

    assertTrue(partitionProcessingState.isExportingPaused());

    // when
    partitionProcessingState.resumeExporting();

    // then
    assertFalse(partitionProcessingState.isExportingPaused());
  }

  @Test
  void shouldSoftPauseAndResumeExporter() {
    // given
    final var partitionProcessingState = new PartitionProcessingState(MOCK_RAFT_PARTITION);
    partitionProcessingState.softPauseExporting();

    assertTrue(partitionProcessingState.isExportingSoftPaused());

    // when
    partitionProcessingState.resumeExporting();

    // then
    assertFalse(partitionProcessingState.isExportingSoftPaused());
  }

  @Test
  void shouldOverwriteExporterStates() {
    // given
    final var partitionProcessingState = new PartitionProcessingState(MOCK_RAFT_PARTITION);

    partitionProcessingState.pauseExporting();

    assertTrue(partitionProcessingState.isExportingPaused());

    // we overwrite the pause state
    partitionProcessingState.softPauseExporting();

    assertTrue(partitionProcessingState.isExportingSoftPaused());

    // then we resume again
    partitionProcessingState.resumeExporting();

    assertFalse(partitionProcessingState.isExportingPaused());
  }
}
