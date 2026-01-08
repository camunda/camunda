/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.backup;

import static io.camunda.operate.webapp.management.dto.BackupStateDto.INCOMPLETE;
import static io.camunda.operate.webapp.management.dto.BackupStateDto.IN_PROGRESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.property.BackupProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.backup.BackupService.SnapshotRequest;
import io.camunda.operate.webapp.backup.Metadata;
import io.camunda.operate.webapp.management.dto.BackupStateDto;
import io.camunda.operate.webapp.management.dto.GetBackupStateResponseDto;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.awaitility.Awaitility;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.SnapshotClient;
import org.elasticsearch.snapshots.SnapshotId;
import org.elasticsearch.snapshots.SnapshotInfo;
import org.elasticsearch.snapshots.SnapshotState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ElasticsearchBackupRepositoryTest {

  private final String repositoryName = "repo1";
  private final long backupId = 555;
  private final String snapshotName = "camunda_operate_" + backupId + "_8.6_part_1_of_6";

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private RestHighLevelClient esClient;

  private final long incompleteCheckTimeoutLengthSeconds =
      new BackupProperties().getIncompleteCheckTimeoutInSeconds();
  private final long incompleteCheckTimeoutLength = incompleteCheckTimeoutLengthSeconds * 1000;

  @Mock private SnapshotClient snapshotClient;
  @Spy private final ObjectMapper objectMapper = new ObjectMapper();

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
  public void testWaitingForSnapshotTillCompleted() throws IOException {
    final int timeout = 0;
    final int numberOfSnapshots = 6;
    final var snapshotInfos = new ArrayList<SnapshotInfo>();
    for (int i = 0; i < numberOfSnapshots; i++) {
      final SnapshotInfo snapshotInfo = mock(SnapshotInfo.class, RETURNS_DEEP_STUBS);
      when(snapshotInfo.state())
          .thenReturn(SnapshotState.IN_PROGRESS)
          .thenReturn(SnapshotState.IN_PROGRESS)
          .thenReturn(SnapshotState.SUCCESS);
      when(snapshotInfo.snapshotId().getName()).thenReturn(snapshotName);
      snapshotInfos.add(snapshotInfo);
    }

    // mock calls to `findSnapshot` and `operateProperties`
    when(operateProperties.getBackup().getSnapshotTimeout()).thenReturn(timeout);
    final var snapshotResponse = mock(GetSnapshotsResponse.class);
    when(snapshotResponse.getSnapshots()).thenReturn(snapshotInfos);
    when(esClient.snapshot().get(any(), any())).thenReturn(snapshotResponse);
    final var captor = ArgumentCaptor.forClass(ActionListener.class);
    when(esClient.snapshot().createAsync(any(), any(), any())).thenReturn(null);

    for (int i = 0; i < numberOfSnapshots; i++) {
      backupRepository.executeSnapshotting(
          new SnapshotRequest(
              repositoryName,
              snapshotName,
              List.of("index-example"),
              new Metadata()
                  .setBackupId(backupId)
                  .setPartCount(i)
                  .setPartNo(6)
                  .setVersion("8.3.0")),
          () -> {},
          () -> {});
    }
    verify(esClient.snapshot(), times(numberOfSnapshots))
        .createAsync(any(), any(), captor.capture());
    captor.getValue().onFailure(new SocketTimeoutException());
    verify(backupRepository, times(3)).findSnapshots(repositoryName, backupId);

    Awaitility.await("backup is completed")
        .untilAsserted(
            () -> {
              final var response =
                  backupRepository.getBackupState(repositoryName, backupId, id -> false);
              assertThat(response.getState()).isEqualTo(BackupStateDto.COMPLETED);
            });
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
    // given
    final SnapshotInfo snapshotInfo = mock(SnapshotInfo.class, RETURNS_DEEP_STUBS);
    when(snapshotInfo.state()).thenReturn(SnapshotState.IN_PROGRESS);
    when(snapshotInfo.snapshotId().getName()).thenReturn(snapshotName);
    final var response = mock(GetSnapshotsResponse.class, RETURNS_DEEP_STUBS);
    when(response.getSnapshots()).thenReturn(List.of(snapshotInfo));
    when(esClient.snapshot()).thenReturn(snapshotClient);
    when(snapshotClient.get(any(), any())).thenReturn(response);

    // when
    final var responses = backupRepository.getBackups(repositoryName, false, null, id -> false);

    // then
    verify(snapshotClient).get(argThat(req -> !req.verbose()), any());
    assertThat(responses)
        .singleElement()
        .satisfies(
            details -> {
              assertThat(details.getBackupId()).isEqualTo(backupId);
              assertThat(details.getState()).isEqualTo(IN_PROGRESS);
            });
  }

  @Test
  public void shouldForwardThePatternToES() throws IOException {
    final SnapshotInfo snapshotInfo = mock(SnapshotInfo.class, RETURNS_DEEP_STUBS);
    when(snapshotInfo.state()).thenReturn(SnapshotState.IN_PROGRESS);
    when(snapshotInfo.snapshotId().getName()).thenReturn(snapshotName);
    final var response = mock(GetSnapshotsResponse.class, RETURNS_DEEP_STUBS);
    when(response.getSnapshots()).thenReturn(List.of(snapshotInfo));
    when(esClient.snapshot()).thenReturn(snapshotClient);
    when(snapshotClient.get(any(), any())).thenReturn(response);

    // when
    backupRepository.getBackups(repositoryName, true, "2023*", id -> false);

    // then
    verify(snapshotClient)
        .get(
            argThat(req -> Arrays.asList(req.snapshots()).contains("camunda_operate_2023*")),
            any());
  }

  @Test
  void shouldReturnInProgressWhenFewerSnapshotsSuccessAndBackupPendingInMemory()
      throws IOException {
    // given
    final SnapshotInfo snapshotInfo = mock(SnapshotInfo.class, RETURNS_DEEP_STUBS);
    when(snapshotInfo.state()).thenReturn(SnapshotState.SUCCESS);
    when(snapshotInfo.snapshotId().getName()).thenReturn(snapshotName);
    final var response = mock(GetSnapshotsResponse.class, RETURNS_DEEP_STUBS);
    when(response.getSnapshots()).thenReturn(List.of(snapshotInfo));
    when(esClient.snapshot()).thenReturn(snapshotClient);
    when(snapshotClient.get(any(), any())).thenReturn(response);

    // when
    final GetBackupStateResponseDto backupStateResponse =
        backupRepository.getBackupState(repositoryName, backupId, id -> id.equals(backupId));

    // then
    assertThat(backupStateResponse.getState()).isEqualTo(IN_PROGRESS);
  }

  @Test
  void shouldReturnIncompleteWhenFewerSnapshotsSuccessAndBackupNotPendingInMemory()
      throws IOException {
    // given
    final SnapshotInfo snapshotInfo = mock(SnapshotInfo.class, RETURNS_DEEP_STUBS);
    when(snapshotInfo.state()).thenReturn(SnapshotState.SUCCESS);
    when(snapshotInfo.snapshotId().getName()).thenReturn(snapshotName);
    final var response = mock(GetSnapshotsResponse.class, RETURNS_DEEP_STUBS);
    when(response.getSnapshots()).thenReturn(List.of(snapshotInfo));
    when(esClient.snapshot()).thenReturn(snapshotClient);
    when(snapshotClient.get(any(), any())).thenReturn(response);

    // when
    final GetBackupStateResponseDto backupStateResponse =
        backupRepository.getBackupState(repositoryName, backupId, id -> false);

    // given
    assertThat(backupStateResponse.getState()).isEqualTo(INCOMPLETE);
  }

  @Test
  void shouldCreateRepository() {
    assertThat(backupRepository).isNotNull();
  }

  @Test
  void shouldReturnBackupStateCompleted() throws IOException {
    final var snapshotClient = mock(SnapshotClient.class);
    final var firstSnapshotInfo = mock(SnapshotInfo.class);
    final var snapshotResponse = mock(GetSnapshotsResponse.class);

    // Set up Snapshot client
    when(esClient.snapshot()).thenReturn(snapshotClient);
    // Set up Snapshot details
    final Map<String, Object> metadata =
        objectMapper.convertValue(
            new Metadata().setPartCount(1).setPartNo(1).setVersion("8.6"), Map.class);
    when(firstSnapshotInfo.userMetadata()).thenReturn(metadata);
    when(firstSnapshotInfo.snapshotId()).thenReturn(new SnapshotId("snapshot-name", "uuid"));
    when(firstSnapshotInfo.state()).thenReturn(SnapshotState.SUCCESS);

    // Set up Snapshot response
    when(snapshotResponse.getSnapshots()).thenReturn(List.of(firstSnapshotInfo));
    when(snapshotClient.get(any(), any())).thenReturn(snapshotResponse);

    // Test
    final var backupState = backupRepository.getBackupState("repository-name", 5L, id -> false);
    assertThat(backupState.getState()).isEqualTo(BackupStateDto.COMPLETED);
  }

  @Test
  void shouldReturnBackupStateIncomplete() throws IOException {
    final var snapshotClient = mock(SnapshotClient.class);
    final var firstSnapshotInfo = mock(SnapshotInfo.class);
    final var snapshotResponse = mock(GetSnapshotsResponse.class);
    final var lastSnapshotInfo = mock(SnapshotInfo.class);

    // Set up Snapshot client
    when(esClient.snapshot()).thenReturn(snapshotClient);
    // Set up operate properties
    when(operateProperties.getBackup().getIncompleteCheckTimeoutInSeconds())
        .thenReturn(incompleteCheckTimeoutLengthSeconds);
    // Set up first Snapshot details
    final Map<String, Object> metadata =
        objectMapper.convertValue(
            new Metadata().setPartCount(3).setPartNo(1).setVersion("8.6"), Map.class);
    when(firstSnapshotInfo.userMetadata()).thenReturn(metadata);
    when(firstSnapshotInfo.snapshotId()).thenReturn(new SnapshotId("snapshot-name", "uuid"));
    when(firstSnapshotInfo.state()).thenReturn(SnapshotState.SUCCESS);
    when(firstSnapshotInfo.startTime()).thenReturn(23L);

    // Set up last Snapshot details
    when(lastSnapshotInfo.snapshotId()).thenReturn(new SnapshotId("snapshot-name", "uuid"));
    when(lastSnapshotInfo.endTime()).thenReturn(23L + 6 * 60 * 1_000);
    when(lastSnapshotInfo.state()).thenReturn(SnapshotState.SUCCESS);

    // Set up Snapshot response
    when(snapshotResponse.getSnapshots()).thenReturn(List.of(firstSnapshotInfo, lastSnapshotInfo));
    when(snapshotClient.get(any(), any())).thenReturn(snapshotResponse);

    // Test
    final var backupState = backupRepository.getBackupState("repository-name", 5L, id -> false);
    assertThat(backupState.getState()).isEqualTo(BackupStateDto.INCOMPLETE);
  }

  @Test
  void shouldReturnBackupStateIncompleteWhenLastSnapshotEndTimeIsTimedOut() throws IOException {
    final var snapshotClient = mock(SnapshotClient.class);
    final var firstSnapshotInfo = mock(SnapshotInfo.class);
    final var snapshotResponse = mock(GetSnapshotsResponse.class);
    final var lastSnapshotInfo = mock(SnapshotInfo.class);
    final long now = Instant.now().toEpochMilli();

    // Set up Snapshot client
    when(esClient.snapshot()).thenReturn(snapshotClient);
    // Set up operate properties
    when(operateProperties.getBackup().getIncompleteCheckTimeoutInSeconds())
        .thenReturn(incompleteCheckTimeoutLengthSeconds);
    // Set up first Snapshot details
    final Map<String, Object> metadata =
        objectMapper.convertValue(
            new Metadata().setPartCount(3).setPartNo(1).setVersion("8.6"), Map.class);
    when(firstSnapshotInfo.userMetadata()).thenReturn(metadata);
    when(firstSnapshotInfo.snapshotId()).thenReturn(new SnapshotId("snapshot-name", "uuid"));
    when(firstSnapshotInfo.state()).thenReturn(SnapshotState.SUCCESS);
    when(firstSnapshotInfo.startTime()).thenReturn(now - 4_000);

    // Set up last Snapshot details
    when(lastSnapshotInfo.snapshotId()).thenReturn(new SnapshotId("snapshot-name", "uuid"));
    when(lastSnapshotInfo.state()).thenReturn(SnapshotState.SUCCESS);
    when(lastSnapshotInfo.startTime()).thenReturn(now - (incompleteCheckTimeoutLength + 4_000));
    when(lastSnapshotInfo.endTime()).thenReturn(now - (incompleteCheckTimeoutLength + 2_000));

    // Set up Snapshot response
    when(snapshotResponse.getSnapshots()).thenReturn(List.of(firstSnapshotInfo, lastSnapshotInfo));
    when(snapshotClient.get(any(), any())).thenReturn(snapshotResponse);

    // Test
    final var backupState = backupRepository.getBackupState("repository-name", 5L, id -> false);
    assertThat(backupState.getState()).isEqualTo(BackupStateDto.INCOMPLETE);
  }

  @Test
  void shouldReturnBackupStateProgress() throws IOException {
    final var snapshotClient = mock(SnapshotClient.class);
    final var firstSnapshotInfo = mock(SnapshotInfo.class);
    final var lastSnapshotInfo = mock(SnapshotInfo.class);
    final var snapshotResponse = mock(GetSnapshotsResponse.class);
    final long now = Instant.now().toEpochMilli();

    // Set up Snapshot client
    when(esClient.snapshot()).thenReturn(snapshotClient);
    // Set up operate properties
    when(operateProperties.getBackup().getIncompleteCheckTimeoutInSeconds())
        .thenReturn(incompleteCheckTimeoutLengthSeconds);
    // Set up Snapshot details
    final Map<String, Object> metadata =
        objectMapper.convertValue(
            new Metadata().setPartCount(3).setPartNo(1).setVersion("8.6"), Map.class);
    when(firstSnapshotInfo.userMetadata()).thenReturn(metadata);
    when(firstSnapshotInfo.snapshotId())
        .thenReturn(new SnapshotId("first-snapshot-name", "uuid-first"));
    when(lastSnapshotInfo.snapshotId())
        .thenReturn(new SnapshotId("last-snapshot-name", "uuid-last"));
    when(firstSnapshotInfo.state()).thenReturn(SnapshotState.SUCCESS);
    when(lastSnapshotInfo.state()).thenReturn(SnapshotState.SUCCESS);
    when(firstSnapshotInfo.startTime()).thenReturn(now - 4_000);
    when(lastSnapshotInfo.startTime()).thenReturn(now - 200);
    when(lastSnapshotInfo.endTime()).thenReturn(now - 5);
    // Set up Snapshot response
    when(snapshotResponse.getSnapshots()).thenReturn(List.of(firstSnapshotInfo, lastSnapshotInfo));
    when(snapshotClient.get(any(), any())).thenReturn(snapshotResponse);

    // Test
    final var backupState = backupRepository.getBackupState("repository-name", 5L, id -> false);
    assertThat(backupState.getState()).isEqualTo(BackupStateDto.IN_PROGRESS);
  }

  @Test
  public void testOnFailureCallbackInvokedWhenFindSnapshotsThrowsException() throws IOException {
    // given
    final var captor = ArgumentCaptor.forClass(ActionListener.class);
    when(esClient.snapshot().createAsync(any(), any(), any())).thenReturn(null);
    when(operateProperties.getBackup().getSnapshotTimeout()).thenReturn(1);

    // Mock findSnapshots to throw a RuntimeException to simulate the scenario
    // where findSnapshots() fails during isSnapshotFinishedWithinTimeout
    doThrow(new RuntimeException("Simulated findSnapshots failure"))
        .when(backupRepository)
        .findSnapshots(any(), any());

    // Track if onFailure callback was invoked
    final boolean[] onFailureCalled = {false};

    // when
    backupRepository.executeSnapshotting(
        new SnapshotRequest(
            repositoryName,
            snapshotName,
            List.of("index-example"),
            new Metadata().setBackupId(backupId).setPartCount(1).setPartNo(6).setVersion("8.3.0")),
        () -> {},
        () -> {
          onFailureCalled[0] = true;
        });

    verify(esClient.snapshot()).createAsync(any(), any(), captor.capture());

    // Trigger onFailure with SocketTimeoutException, which will call
    // isSnapshotFinishedWithinTimeout
    // where findSnapshots will throw an exception
    captor.getValue().onFailure(new SocketTimeoutException());

    // then - verify that despite the exception in findSnapshots, the onFailure callback was invoked
    Awaitility.await("onFailure callback invoked despite exception in findSnapshots")
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(onFailureCalled[0]).isTrue());
  }
}
