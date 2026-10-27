/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.raft.partition.RaftPartition;
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
    assertThat(partitionProcessingState.isProcessingPaused()).isTrue();

    partitionProcessingState.resumeProcessing();

    assertThat(persistedProcessorPauseState)
        .describedAs("Processor State file does not exist.")
        .doesNotExist();
    assertThat(partitionProcessingState.isProcessingPaused()).isFalse();
  }

  @Test
  void shouldNotProcessWhilePausedForTransfer() {
    // given
    final var partitionProcessingState = new PartitionProcessingState(MOCK_RAFT_PARTITION);
    partitionProcessingState.setDiskSpaceAvailable(true);
    assertThat(partitionProcessingState.shouldProcess()).isTrue();

    // when
    partitionProcessingState.setPausedForTransfer(true);

    // then
    assertThat(partitionProcessingState.shouldProcess()).isFalse();

    // and when the transfer pause is cleared, processing may run again
    partitionProcessingState.setPausedForTransfer(false);
    assertThat(partitionProcessingState.shouldProcess()).isTrue();
  }

  @Test
  void shouldStayPausedWhenDiskRecoversMidTransfer() {
    // given
    final var partitionProcessingState = new PartitionProcessingState(MOCK_RAFT_PARTITION);
    partitionProcessingState.setPausedForTransfer(true);
    partitionProcessingState.setDiskSpaceAvailable(false);

    // when
    partitionProcessingState.setDiskSpaceAvailable(true);

    // then
    assertThat(partitionProcessingState.shouldProcess()).isFalse();
  }

  @Test
  void shouldStayPausedWhenAdminResumesMidTransfer() throws IOException {
    // given
    final var partitionProcessingState = new PartitionProcessingState(MOCK_RAFT_PARTITION);
    partitionProcessingState.setDiskSpaceAvailable(true);
    partitionProcessingState.setPausedForTransfer(true);
    partitionProcessingState.pauseProcessing();

    // when
    partitionProcessingState.resumeProcessing();

    // then
    assertThat(partitionProcessingState.isProcessingPaused()).isFalse();
    assertThat(partitionProcessingState.shouldProcess()).isFalse();
  }

  @Test
  void shouldNotPersistTransferPause() {
    // given
    final var partitionProcessingState = new PartitionProcessingState(MOCK_RAFT_PARTITION);
    partitionProcessingState.setPausedForTransfer(true);

    // when
    final var reloaded = new PartitionProcessingState(MOCK_RAFT_PARTITION);

    // then
    assertThat(reloaded.isPausedForTransfer()).isFalse();
  }
}
