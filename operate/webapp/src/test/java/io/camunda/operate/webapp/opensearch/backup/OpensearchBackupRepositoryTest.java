/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.opensearch.backup;

import static io.camunda.operate.webapp.opensearch.backup.OpensearchBackupRepository.REPOSITORY_MISSING_EXCEPTION_TYPE;
import static io.camunda.operate.webapp.opensearch.backup.OpensearchBackupRepository.SNAPSHOT_MISSING_EXCEPTION_TYPE;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.BackupProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.opensearch.client.async.OpenSearchAsyncSnapshotOperations;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchSnapshotOperations;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.response.OpenSearchGetSnapshotResponse;
import io.camunda.operate.store.opensearch.response.OpenSearchSnapshotInfo;
import io.camunda.operate.store.opensearch.response.SnapshotState;
import io.camunda.operate.webapp.backup.BackupService;
import io.camunda.operate.webapp.backup.Metadata;
import io.camunda.operate.webapp.management.dto.BackupStateDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch._types.ErrorCause;
import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.snapshot.*;

@ExtendWith(MockitoExtension.class)
class OpensearchBackupRepositoryTest {
  @Mock private RichOpenSearchClient richOpenSearchClient;

  @Mock private RichOpenSearchClient.Async richOpenSearchClientAsync;

  @Mock private OpenSearchSnapshotOperations openSearchSnapshotOperations;

  @Mock private OpenSearchAsyncSnapshotOperations openSearchAsyncSnapshotOperations;

  @Mock private ObjectMapper objectMapper;

  @Mock private OperateProperties operateProperties;

  private OpensearchBackupRepository repository;
  private final long incompleteCheckTimeoutLength =
      new BackupProperties().getIncompleteCheckTimeoutInSeconds() * 1000;
  private long now;

  @BeforeEach
  public void setUp() {
    repository =
        new OpensearchBackupRepository(richOpenSearchClient, objectMapper, operateProperties);
    now = Instant.now().toEpochMilli();
  }

  private void mockAsynchronSnapshotOperations() {
    when(richOpenSearchClient.async()).thenReturn(richOpenSearchClientAsync);
    when(richOpenSearchClientAsync.snapshot()).thenReturn(openSearchAsyncSnapshotOperations);
  }

  private void mockObjectMapperForMetadata(final Metadata metadata) {
    when(objectMapper.convertValue(any(), eq(Metadata.class))).thenReturn(metadata);
  }

  private void mockSynchronSnapshotOperations() {
    when(richOpenSearchClient.snapshot()).thenReturn(openSearchSnapshotOperations);
  }

  @Test
  void getBackupsReturnsEmptyListOfBackups() {
    mockSynchronSnapshotOperations();

    final var response = new OpenSearchGetSnapshotResponse();
    when(openSearchSnapshotOperations.get(any())).thenReturn(response);

    assertThat(repository.getBackups("repo")).isEmpty();
  }

  @Test
  void getBackupsReturnsNotEmptyListOfBackups() {
    final var metadata =
        new Metadata().setBackupId(5L).setVersion("1").setPartNo(1).setPartCount(3);
    final var snapshotInfos =
        List.of(
            new OpenSearchSnapshotInfo()
                .setSnapshot("test-snapshot")
                .setState(SnapshotState.STARTED)
                .setStartTimeInMillis(23L));
    final var response = new OpenSearchGetSnapshotResponse(snapshotInfos);

    mockObjectMapperForMetadata(metadata);
    when(openSearchSnapshotOperations.get(any())).thenReturn(response);
    mockSynchronSnapshotOperations();

    final var snapshotDtoList = repository.getBackups("repo");
    assertThat(snapshotDtoList).hasSize(1);

    final var snapshotDto = snapshotDtoList.get(0);
    assertThat(snapshotDto.getBackupId()).isEqualTo(5L);
    assertThat(snapshotDto.getState()).isEqualTo(BackupStateDto.IN_PROGRESS);
    assertThat(snapshotDto.getFailureReason()).isNull();
    final var snapshotDtoDetails = snapshotDto.getDetails();
    assertThat(snapshotDtoDetails).hasSize(1);
    final var snapshotDtoDetail = snapshotDtoDetails.get(0);
    assertThat(snapshotDtoDetail.getSnapshotName()).isEqualTo("test-snapshot");
    assertThat(snapshotDtoDetail.getState()).isEqualTo("STARTED");
    assertThat(snapshotDtoDetail.getFailures()).isNull();
    assertThat(snapshotDtoDetail.getStartTime().toInstant().toEpochMilli()).isEqualTo(23L);
  }

  @Test
  void successForExecuteSnapshotting() {
    mockAsynchronSnapshotOperations();

    final var snapshotRequest =
        new BackupService.SnapshotRequest(
            "repo", "camunda_operate_1_2", List.of("index-1", "index-2"), new Metadata());
    final Runnable onSuccess = () -> {};
    final Runnable onFailure = () -> fail("Should execute snapshot successfully.");

    final var createSnapShotResponse =
        new CreateSnapshotResponse.Builder()
            .snapshot(
                new SnapshotInfo.Builder()
                    .snapshot("snapshot")
                    .dataStreams(List.of())
                    .indices(List.of("index-1", "index-2"))
                    .uuid("uuid")
                    .state(SnapshotState.SUCCESS.toString())
                    .build())
            .build();
    when(openSearchAsyncSnapshotOperations.create(any()))
        .thenReturn(CompletableFuture.completedFuture(createSnapShotResponse));

    repository.executeSnapshotting(snapshotRequest, onSuccess, onFailure);
  }

  @Test
  void failedForExecuteSnapshotting() {
    final var snapshotRequest =
        new BackupService.SnapshotRequest(
            "repo", "camunda_operate_1_2", List.of("index-1", "index-2"), new Metadata());
    final Runnable onSuccess = () -> fail("Should execute snapshot with failures.");
    final Runnable onFailure = () -> {};

    mockAsynchronSnapshotOperations();
    when(openSearchAsyncSnapshotOperations.create(any()))
        .thenReturn(CompletableFuture.failedFuture(new SocketTimeoutException("no internet")));

    repository.executeSnapshotting(snapshotRequest, onSuccess, onFailure);
  }

  @Test
  void deleteSnapshotSucceed() {
    mockAsynchronSnapshotOperations();

    when(openSearchAsyncSnapshotOperations.delete(any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new DeleteSnapshotResponse.Builder().acknowledged(true).build()));
    repository.deleteSnapshot("repo", "snapshot");
  }

  @Test
  void deleteSnapshotFails() {
    mockAsynchronSnapshotOperations();

    when(openSearchAsyncSnapshotOperations.delete(any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new OpenSearchException(
                    new ErrorResponse.Builder()
                        .status(5)
                        .error(
                            new ErrorCause.Builder()
                                .type(SNAPSHOT_MISSING_EXCEPTION_TYPE)
                                .reason("test reason")
                                .build())
                        .build())));

    repository.deleteSnapshot("repo", "snapshot");
  }

  @Test
  void getBackupStateShouldBeInProgress() {
    when(operateProperties.getBackup()).thenReturn(new BackupProperties());
    mockSynchronSnapshotOperations();
    mockObjectMapperForMetadata(new Metadata().setPartCount(3));

    when(openSearchSnapshotOperations.get(any()))
        .thenReturn(
            new OpenSearchGetSnapshotResponse(
                List.of(
                    new OpenSearchSnapshotInfo()
                        .setSnapshot("snapshot")
                        .setState(SnapshotState.SUCCESS)
                        .setStartTimeInMillis(now - (incompleteCheckTimeoutLength / 2))
                        .setEndTimeInMillis(now))));

    final var response = repository.getBackupState("repo", 5L);

    assertThat(response).isNotNull();
    assertThat(response.getState()).isEqualTo(BackupStateDto.IN_PROGRESS);
    assertThat(response.getBackupId()).isEqualTo(5L);
    final var snapshotDetails = response.getDetails();
    assertThat(snapshotDetails).hasSize(1);
    final var snapshotDetail = snapshotDetails.get(0);
    assertThat(snapshotDetail.getState()).isEqualTo(SnapshotState.SUCCESS.toString());
    assertThat(snapshotDetail.getSnapshotName()).isEqualTo("snapshot");
    assertThat(snapshotDetail.getFailures()).isNull();
  }

  @Test
  void getBackupStateShouldBeIncompleteDueToTimeout() {
    when(operateProperties.getBackup()).thenReturn(new BackupProperties());
    mockSynchronSnapshotOperations();
    mockObjectMapperForMetadata(new Metadata().setPartCount(3));

    final long endtime = now - (incompleteCheckTimeoutLength * 2);

    when(openSearchSnapshotOperations.get(any()))
        .thenReturn(
            new OpenSearchGetSnapshotResponse(
                List.of(
                    new OpenSearchSnapshotInfo()
                        .setSnapshot("snapshot")
                        .setState(SnapshotState.SUCCESS)
                        .setStartTimeInMillis(endtime - 20)
                        // end time was double the timeout from now
                        .setEndTimeInMillis(endtime))));

    final var response = repository.getBackupState("repo", 5L);

    assertThat(response).isNotNull();
    assertThat(response.getState()).isEqualTo(BackupStateDto.INCOMPLETE);
    assertThat(response.getBackupId()).isEqualTo(5L);
    final var snapshotDetails = response.getDetails();
    assertThat(snapshotDetails).hasSize(1);
    final var snapshotDetail = snapshotDetails.get(0);
    assertThat(snapshotDetail.getState()).isEqualTo(SnapshotState.SUCCESS.toString());
    assertThat(snapshotDetail.getSnapshotName()).isEqualTo("snapshot");
    assertThat(snapshotDetail.getFailures()).isNull();
  }

  @Test
  void validateRepositoryExistsSuccess() {
    mockSynchronSnapshotOperations();
    when(openSearchSnapshotOperations.getRepository(any())).thenReturn(Map.of());

    repository.validateRepositoryExists("repo");
  }

  @Test
  void validateRepositoryExistsFailed() {
    mockSynchronSnapshotOperations();
    when(openSearchSnapshotOperations.getRepository(any()))
        .thenThrow(
            new OpenSearchException(
                new ErrorResponse.Builder()
                    .status(5)
                    .error(
                        new ErrorCause.Builder()
                            .type(REPOSITORY_MISSING_EXCEPTION_TYPE)
                            .reason("test")
                            .build())
                    .build()));

    final var exception =
        assertThrows(
            OperateRuntimeException.class, () -> repository.validateRepositoryExists("repo"));
    assertThat(exception.getMessage()).isEqualTo("No repository with name [repo] could be found.");
  }

  @Test
  void validateNoDuplicateBackupIdSuccess() {
    mockSynchronSnapshotOperations();
    when(openSearchSnapshotOperations.get(any())).thenReturn(new OpenSearchGetSnapshotResponse());

    repository.validateNoDuplicateBackupId("repo", 42L);
  }

  @Test
  void validateNoDuplicateBackupIdFailed() {
    mockSynchronSnapshotOperations();
    when(openSearchSnapshotOperations.get(any()))
        .thenReturn(
            new OpenSearchGetSnapshotResponse(
                List.of(new OpenSearchSnapshotInfo().setUuid("test"))));

    final var exception =
        assertThrows(
            InvalidRequestException.class,
            () -> repository.validateNoDuplicateBackupId("repo", 42L));
    assertThat(exception.getMessage())
        .isEqualTo("A backup with ID [42] already exists. Found snapshots: [test]");
  }

  @Test
  void shouldReturnBackupStateIncompleteWhenEndIsInTimeout() throws IOException {
    when(operateProperties.getBackup()).thenReturn(new BackupProperties());
    mockSynchronSnapshotOperations();
    mockObjectMapperForMetadata(new Metadata().setPartCount(3));
    final long timeoutTime = now - incompleteCheckTimeoutLength * 2;
    final var firstSnapshotInfo =
        new OpenSearchSnapshotInfo()
            .setUuid("uuid")
            .setState(SnapshotState.SUCCESS)
            .setStartTimeInMillis(timeoutTime - 50)
            .setEndTimeInMillis(timeoutTime - 40);
    final var lastSnapshotInfo =
        new OpenSearchSnapshotInfo()
            .setUuid("uuid")
            .setState(SnapshotState.SUCCESS)
            .setStartTimeInMillis(timeoutTime - 30)
            .setEndTimeInMillis(timeoutTime - 20);
    when(openSearchSnapshotOperations.get(any()))
        .thenReturn(
            new OpenSearchGetSnapshotResponse(List.of(firstSnapshotInfo, lastSnapshotInfo)));
    // Test
    final var backupState = repository.getBackupState("repository-name", 5L);
    assertThat(backupState.getState()).isEqualTo(BackupStateDto.INCOMPLETE);
  }

  @Test
  void shouldReturnBackupStateInProgressWhenStartIsInTimeoutButEndIsNot() throws IOException {
    when(operateProperties.getBackup()).thenReturn(new BackupProperties());
    mockSynchronSnapshotOperations();
    mockObjectMapperForMetadata(new Metadata().setPartCount(3));

    final var firstSnapshotInfo =
        new OpenSearchSnapshotInfo()
            .setUuid("uuid")
            .setState(SnapshotState.SUCCESS)
            .setStartTimeInMillis(now - incompleteCheckTimeoutLength)
            .setEndTimeInMillis(now - 20);
    when(openSearchSnapshotOperations.get(any()))
        .thenReturn(new OpenSearchGetSnapshotResponse(List.of(firstSnapshotInfo)));
    // Test
    final var backupState = repository.getBackupState("repository-name", 5L);
    assertThat(backupState.getState()).isEqualTo(BackupStateDto.IN_PROGRESS);
  }

  @Test
  void shouldReturnBackupStateFailedWhenSnapshotIsPartialCompleted() throws IOException {
    mockSynchronSnapshotOperations();
    mockObjectMapperForMetadata(new Metadata().setPartCount(1));
    final var firstSnapshotInfo =
        new OpenSearchSnapshotInfo()
            .setUuid("uuid")
            .setState(SnapshotState.PARTIAL)
            .setStartTimeInMillis(Instant.now().toEpochMilli());
    when(openSearchSnapshotOperations.get(any()))
        .thenReturn(new OpenSearchGetSnapshotResponse(List.of(firstSnapshotInfo)));
    // Test
    final var backupState = repository.getBackupState("repository-name", 5L);
    assertThat(backupState.getState()).isEqualTo(BackupStateDto.FAILED);
  }

  @Test
  void shouldReturnBackupStateFailedCompleted() throws IOException {
    mockSynchronSnapshotOperations();
    mockObjectMapperForMetadata(new Metadata().setPartCount(2));
    final var firstSnapshotInfo =
        new OpenSearchSnapshotInfo()
            .setUuid("uuid")
            .setState(SnapshotState.SUCCESS)
            .setStartTimeInMillis(Instant.now().toEpochMilli());
    final var lastSnapshotInfo =
        new OpenSearchSnapshotInfo()
            .setUuid("uuid")
            .setState(SnapshotState.SUCCESS)
            .setStartTimeInMillis(Instant.now().toEpochMilli());
    when(openSearchSnapshotOperations.get(any()))
        .thenReturn(
            new OpenSearchGetSnapshotResponse(List.of(firstSnapshotInfo, lastSnapshotInfo)));
    // Test
    final var backupState = repository.getBackupState("repository-name", 5L);
    assertThat(backupState.getState()).isEqualTo(BackupStateDto.COMPLETED);
  }

  @Test
  void shouldReturnBackupStateFailedWhenSnapshotIsFailed() throws IOException {
    mockSynchronSnapshotOperations();
    mockObjectMapperForMetadata(new Metadata().setPartCount(1));
    final var firstSnapshotInfo =
        new OpenSearchSnapshotInfo()
            .setUuid("uuid")
            .setState(SnapshotState.FAILED)
            .setStartTimeInMillis(Instant.now().toEpochMilli());
    when(openSearchSnapshotOperations.get(any()))
        .thenReturn(new OpenSearchGetSnapshotResponse(List.of(firstSnapshotInfo)));
    // Test
    final var backupState = repository.getBackupState("repository-name", 5L);
    assertThat(backupState.getState()).isEqualTo(BackupStateDto.FAILED);
  }
}
