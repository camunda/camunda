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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.exceptions.OperateRuntimeException;
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
import java.net.SocketTimeoutException;
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

  private OpensearchBackupRepository repository;

  @BeforeEach
  public void setUp() {
    repository = new OpensearchBackupRepository(richOpenSearchClient, objectMapper);
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
  void shouldForwardVerboseFlagToOpensearch() {
    final var metadata =
        new Metadata().setBackupId(5L).setVersion("1").setPartNo(1).setPartCount(3);
    final var snapshotInfos =
        List.of(
            new OpenSearchSnapshotInfo()
                .setSnapshot("test-snapshot")
                .setState(SnapshotState.STARTED));
    final var response = new OpenSearchGetSnapshotResponse(snapshotInfos);
    mockObjectMapperForMetadata(metadata);
    when(openSearchSnapshotOperations.get(any())).thenReturn(response);
    mockSynchronSnapshotOperations();
    final var snapshotDtoList = repository.getBackups("repo", false, null);
    verify(openSearchSnapshotOperations).get(argThat(req -> !req.verbose()));

    assertThat(snapshotDtoList)
        .singleElement()
        .satisfies(
            backup -> {
              assertThat(backup.getBackupId()).isEqualTo(5L);
              assertThat(backup.getState()).isEqualTo(BackupStateDto.IN_PROGRESS);
            });
  }

  @Test
  void shouldForwardSnapshotPatternFlagToOpensearch() {
    final var metadata =
        new Metadata().setBackupId(5L).setVersion("1").setPartNo(1).setPartCount(3);
    final var snapshotInfos =
        List.of(
            new OpenSearchSnapshotInfo()
                .setSnapshot("test-snapshot")
                .setState(SnapshotState.STARTED));
    final var response = new OpenSearchGetSnapshotResponse(snapshotInfos);
    mockObjectMapperForMetadata(metadata);
    when(openSearchSnapshotOperations.get(any())).thenReturn(response);
    mockSynchronSnapshotOperations();
    repository.getBackups("repo", false, "2023*");
    verify(openSearchSnapshotOperations)
        .get(argThat(req -> req.snapshot().contains("camunda_operate_2023*")));
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
  void getBackupState() {
    mockSynchronSnapshotOperations();
    mockObjectMapperForMetadata(new Metadata().setPartNo(1).setVersion("8.7.0").setPartCount(3));

    when(openSearchSnapshotOperations.get(any()))
        .thenReturn(
            new OpenSearchGetSnapshotResponse(
                List.of(
                    new OpenSearchSnapshotInfo()
                        .setSnapshot("snapshot")
                        .setState(SnapshotState.SUCCESS)
                        .setStartTimeInMillis(23L))));

    final var response = repository.getBackupState("repo", 5L);

    assertThat(response).isNotNull();
    assertThat(response.getState()).isEqualTo(BackupStateDto.INCOMPLETE);
    assertThat(response.getBackupId()).isEqualTo(5L);
    final var snapshotDetails = response.getDetails();
    assertThat(snapshotDetails).hasSize(1);
    final var snapshotDetail = snapshotDetails.get(0);
    assertThat(snapshotDetail.getState()).isEqualTo(SnapshotState.SUCCESS.toString());
    assertThat(snapshotDetail.getStartTime().toInstant().toEpochMilli()).isEqualTo(23L);
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
}
