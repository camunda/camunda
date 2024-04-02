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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PartitionProcessingStateTest {
  private static final String PERSISTED_PAUSE_STATE_FILENAME = ".processorPaused";
  private static final String PERSISTED_EXPORTER_PAUSE_STATE_FILENAME = ".exporterPaused";
  private static @TempDir Path TEST_DIR;
  private static final RaftPartition MOCK_RAFT_PARTITION =
      mock(RaftPartition.class, RETURNS_DEEP_STUBS);

  @BeforeAll
  static void setUp() {
    when(MOCK_RAFT_PARTITION.dataDirectory().toPath()).thenReturn(TEST_DIR);
  }

  @Test
  void shouldPauseAndResumeProcessing() throws IOException {
    final var partitionProcessingState = new PartitionProcessingState(MOCK_RAFT_PARTITION);

    // when
    partitionProcessingState.pauseProcessing();

    // then
    // assert that file exists
    assertTrue(partitionProcessingState.isProcessingPaused());

    partitionProcessingState.resumeProcessing();

    // assert that file does not exist
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
}
