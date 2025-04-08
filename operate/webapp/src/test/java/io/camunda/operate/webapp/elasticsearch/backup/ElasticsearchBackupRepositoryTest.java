/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.management.dto.BackupStateDto;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.SnapshotClient;
import org.elasticsearch.snapshots.SnapshotInfo;
import org.elasticsearch.snapshots.SnapshotState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ElasticsearchBackupRepositoryTest {

  private final String repositoryName = "repo1";
  private final long backupId = 555;
  private final String snapshotName = "camunda_operate_" + backupId + "_8.6_part_1_of_6";
  @Mock private RestHighLevelClient esClient;
  @Mock private SnapshotClient snapshotClient;
  @Mock private ObjectMapper objectMapper;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private OperateProperties operateProperties;

  @InjectMocks
  private ElasticsearchBackupRepository backupRepository = spy(new ElasticsearchBackupRepository());

  @Test
  public void testWaitingForSnapshotWithTimeout() {
    final int timeout = 1;
    final SnapshotState snapshotState = SnapshotState.IN_PROGRESS;

    // mock calls to `findSnapshot` and `operateProperties`
    final SnapshotInfo snapshotInfo = mock(SnapshotInfo.class, RETURNS_DEEP_STUBS);
    when(snapshotInfo.state()).thenReturn(snapshotState);
    when(snapshotInfo.snapshotId().getName()).thenReturn(snapshotName);
    when(operateProperties.getBackup().getSnapshotTimeout()).thenReturn(timeout);
    doReturn(List.of(snapshotInfo)).when(backupRepository).findSnapshots(any(), any());

    final boolean finished =
        backupRepository.isSnapshotFinishedWithinTimeout(repositoryName, snapshotName);

    assertFalse(finished);
    verify(backupRepository, atLeast(5)).findSnapshots(repositoryName, backupId);
  }

  @Test
  public void testWaitingForSnapshotTillCompleted() {
    final int timeout = 0;

    // mock calls to `findSnapshot` and `operateProperties`
    final SnapshotInfo snapshotInfo = mock(SnapshotInfo.class, RETURNS_DEEP_STUBS);
    when(snapshotInfo.state())
        .thenReturn(SnapshotState.IN_PROGRESS)
        .thenReturn(SnapshotState.IN_PROGRESS)
        .thenReturn(SnapshotState.SUCCESS);
    when(snapshotInfo.snapshotId().getName()).thenReturn(snapshotName);
    when(operateProperties.getBackup().getSnapshotTimeout()).thenReturn(timeout);
    doReturn(List.of(snapshotInfo)).when(backupRepository).findSnapshots(any(), any());

    final boolean finished =
        backupRepository.isSnapshotFinishedWithinTimeout(repositoryName, snapshotName);

    assertTrue(finished);
    verify(backupRepository, times(3)).findSnapshots(repositoryName, backupId);
  }

  @Test
  public void testWaitingForSnapshotWithoutTimeout() {
    final int timeout = 0;
    final SnapshotState snapshotState = SnapshotState.IN_PROGRESS;

    // mock calls to `findSnapshot` and `operateProperties`
    final SnapshotInfo snapshotInfo = mock(SnapshotInfo.class, RETURNS_DEEP_STUBS);
    when(snapshotInfo.state()).thenReturn(snapshotState);
    when(snapshotInfo.snapshotId().getName()).thenReturn(snapshotName);
    when(operateProperties.getBackup().getSnapshotTimeout()).thenReturn(timeout);
    doReturn(List.of(snapshotInfo)).when(backupRepository).findSnapshots(any(), any());

    // we expect infinite loop, so we call snapshotting in separate thread
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    final Future<?> future =
        executor.submit(
            () -> {
              backupRepository.isSnapshotFinishedWithinTimeout(repositoryName, snapshotName);
            });

    try {
      // Set a timeout of 2 seconds for the function to complete
      future.get(2, TimeUnit.SECONDS);
    } catch (final TimeoutException e) {
      // expected
      return;
    } catch (final InterruptedException | ExecutionException e) {
      // ignore
    } finally {
      // Shutdown the executor
      executor.shutdownNow();
    }

    fail("Expected to continue waiting for snapshotting to finish.");
  }

  @Test
  public void shouldForwardVerboseFlagToES() throws IOException {
    // mock calls to `findSnapshot` and `operateProperties`
    final SnapshotInfo snapshotInfo = mock(SnapshotInfo.class, RETURNS_DEEP_STUBS);
    when(snapshotInfo.state()).thenReturn(SnapshotState.IN_PROGRESS);
    when(snapshotInfo.snapshotId().getName()).thenReturn(snapshotName);
    final var response = mock(GetSnapshotsResponse.class, RETURNS_DEEP_STUBS);
    when(response.getSnapshots()).thenReturn(List.of(snapshotInfo));
    when(esClient.snapshot()).thenReturn(snapshotClient);
    when(snapshotClient.get(any(), any())).thenReturn(response);

    final var responses = backupRepository.getBackups(repositoryName, false);
    verify(snapshotClient).get(argThat(req -> !req.verbose()), any());
    assertThat(responses)
        .singleElement()
        .satisfies(
            details -> {
              assertThat(details.getBackupId()).isEqualTo(backupId);
              assertThat(details.getState()).isEqualTo(BackupStateDto.IN_PROGRESS);
            });
  }
}
