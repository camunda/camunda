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
  private static final String testDir = "src/test/resources";
  private static final Path pathToExporterState =
      new File(testDir + PERSISTED_EXPORTER_PAUSE_STATE_FILENAME).toPath();
  private static final Path pathToProcessorState =
      new File(testDir + PERSISTED_EXPORTER_PAUSE_STATE_FILENAME).toPath();
  private static final RaftPartition mockRaftPartition =
      mock(RaftPartition.class, RETURNS_DEEP_STUBS);

  private static final File processorStateFile = mock(File.class);
  private static final File exporterStateFile = mock(File.class);

  @BeforeAll
  static void beforeAll() throws IOException {

    when(mockRaftPartition
            .dataDirectory()
            .toPath()
            .resolve(PERSISTED_PAUSE_STATE_FILENAME)
            .toFile())
        .thenReturn(processorStateFile);
    when(mockRaftPartition
            .dataDirectory()
            .toPath()
            .resolve(PERSISTED_EXPORTER_PAUSE_STATE_FILENAME)
            .toFile())
        .thenReturn(exporterStateFile);
    when(processorStateFile.toPath()).thenReturn(pathToProcessorState);
    when(exporterStateFile.toPath()).thenReturn(pathToExporterState);
  }

  @AfterAll
  static void afterAll() {
    pathToExporterState.toFile().delete();
    pathToProcessorState.toFile().delete();
  }

  @Test
  void shouldPauseAndResumeProcessing() throws IOException {
    // given
    final var partitionProcessingState = new PartitionProcessingState(mockRaftPartition);
    when(processorStateFile.exists()).thenReturn(true);
    partitionProcessingState.pauseProcessing();

    assertTrue(partitionProcessingState.isProcessingPaused());

    // when
    when(processorStateFile.exists()).thenReturn(false);
    partitionProcessingState.resumeProcessing();

    // then
    assertFalse(partitionProcessingState.isProcessingPaused());
  }

  @Test
  void shouldPauseAndResumeExporting() {
    // given
    final var partitionProcessingState = new PartitionProcessingState(mockRaftPartition);
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
    final var partitionProcessingState = new PartitionProcessingState(mockRaftPartition);
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
    final var partitionProcessingState = new PartitionProcessingState(mockRaftPartition);

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
