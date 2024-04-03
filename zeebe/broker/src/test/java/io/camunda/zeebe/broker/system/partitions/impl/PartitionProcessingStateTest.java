/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.broker.system.partitions.impl.PartitionProcessingState.ExporterState;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PartitionProcessingStateTest {
  private static final RaftPartition MOCK_RAFT_PARTITION =
      mock(RaftPartition.class, RETURNS_DEEP_STUBS);
  private static final String PERSISTED_PROCESSOR_PAUSE_STATE_FILENAME = ".processorPaused";
  private static final String PERSISTED_EXPORTER_PAUSE_STATE_FILENAME = ".exporterPaused";
  @TempDir private Path testDir;

  @BeforeEach
  void setUp() {
    when(MOCK_RAFT_PARTITION.dataDirectory().toPath()).thenReturn(testDir);
  }

  @Test
  void shouldPauseAndResumeProcessing() throws IOException {
    final var partitionProcessingState = new PartitionProcessingState(MOCK_RAFT_PARTITION);
    final File persistedProcessorPauseState =
        testDir.resolve(PERSISTED_PROCESSOR_PAUSE_STATE_FILENAME).toFile();

    // when
    partitionProcessingState.pauseProcessing();

    // then
    assertThat(persistedProcessorPauseState).describedAs("Processor State file exists.").exists();
    assertTrue(partitionProcessingState.isProcessingPaused());

    partitionProcessingState.resumeProcessing();

    assertThat(persistedProcessorPauseState)
        .describedAs("Processor State file does not exist.")
        .doesNotExist();
    assertFalse(partitionProcessingState.isProcessingPaused());
  }

  @Test
  void shouldPauseAndResumeExporting() {
    // given
    final var partitionProcessingState = new PartitionProcessingState(MOCK_RAFT_PARTITION);
    partitionProcessingState.pauseExporting();

    assertThat(partitionProcessingState.getExporterState())
        .describedAs("Exporting must be paused.")
        .isEqualTo(ExporterState.PAUSED);

    // when
    partitionProcessingState.resumeExporting();

    // then
    assertThat(partitionProcessingState.getExporterState())
        .describedAs("Exporting must be resumed.")
        .isEqualTo(ExporterState.EXPORTING);
  }

  @Test
  void shouldSoftPauseAndResumeExporter() {
    // given
    final var partitionProcessingState = new PartitionProcessingState(MOCK_RAFT_PARTITION);
    partitionProcessingState.softPauseExporting();

    assertThat(partitionProcessingState.getExporterState())
        .describedAs("Exporting must be soft paused.")
        .isEqualTo(ExporterState.SOFT_PAUSED);

    // when
    partitionProcessingState.resumeExporting();

    // then
    assertThat(partitionProcessingState.getExporterState())
        .describedAs("Exporting must be resumed.")
        .isEqualTo(ExporterState.EXPORTING);
  }

  @Test
  void shouldOverwriteExporterStates() {
    // given
    final var partitionProcessingState = new PartitionProcessingState(MOCK_RAFT_PARTITION);

    partitionProcessingState.pauseExporting();

    assertThat(partitionProcessingState.getExporterState())
        .describedAs("Exporting must be paused.")
        .isEqualTo(ExporterState.PAUSED);

    // we overwrite the pause state
    partitionProcessingState.softPauseExporting();

    assertThat(partitionProcessingState.getExporterState())
        .describedAs("Exporting must be soft paused.")
        .isEqualTo(ExporterState.SOFT_PAUSED);

    // then we resume again
    partitionProcessingState.resumeExporting();

    assertThat(partitionProcessingState.getExporterState())
        .describedAs("Exporting must be resumed.")
        .isEqualTo(ExporterState.EXPORTING);
  }

  @Test
  void shouldAssureBackwardCompatibility() throws IOException {
    // Before the functionality to soft pause the exporter, the previous implementation did not
    // have the exporter state saved onto to the file. It determined the exporter state based on
    // the existence of the file (if it exists, then the exporter is paused).
    // see the changes in: https://github.com/camunda/zeebe/pull/16869

    // Create an empty file for pauseState
    final File persistedExporterPauseState =
        testDir.resolve(PERSISTED_EXPORTER_PAUSE_STATE_FILENAME).toFile();

    // when
    persistedExporterPauseState.createNewFile();

    // then
    final var partitionProcessingState = new PartitionProcessingState(MOCK_RAFT_PARTITION);
    // the exporter state should be paused

    assertThat(partitionProcessingState.getExporterState())
        .describedAs("Exporting must be paused.")
        .isEqualTo(ExporterState.PAUSED);
  }
}
