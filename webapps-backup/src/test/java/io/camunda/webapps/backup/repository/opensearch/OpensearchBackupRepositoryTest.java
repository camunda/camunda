/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.backup.repository.opensearch;

import static io.camunda.webapps.backup.repository.opensearch.OpensearchBackupRepository.REPOSITORY_MISSING_EXCEPTION_TYPE;
import static io.camunda.webapps.backup.repository.opensearch.OpensearchBackupRepository.SNAPSHOT_MISSING_EXCEPTION_TYPE;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.AssertionsForClassTypes.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.webapps.backup.BackupException;
import io.camunda.webapps.backup.BackupException.InvalidRequestException;
import io.camunda.webapps.backup.BackupService;
import io.camunda.webapps.backup.BackupService.SnapshotRequest;
import io.camunda.webapps.backup.BackupStateDto;
import io.camunda.webapps.backup.Metadata;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import io.camunda.webapps.backup.repository.TestSnapshotProvider;
import io.camunda.webapps.schema.descriptors.backup.SnapshotIndexCollection;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.ErrorCause;
import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.indices.GetIndexRequest;
import org.opensearch.client.opensearch.indices.GetIndexResponse;
import org.opensearch.client.opensearch.snapshot.*;

@ExtendWith(MockitoExtension.class)
class OpensearchBackupRepositoryTest {
  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private OpenSearchClient openSearchClient;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private OpenSearchAsyncClient openSearchAsyncClient;

  @Mock private BackupRepositoryProps backupProps;

  private OpensearchBackupRepository repository;
  private final long incompleteCheckTimeoutLength =
      BackupRepositoryProps.defaultIncompleteCheckTimeoutInSeconds() * 1000;
  private long now;
  private final TestSnapshotProvider snapshotNameProvider = new TestSnapshotProvider();

  @BeforeEach
  public void setUp() {
    repository =
        new OpensearchBackupRepository(
            openSearchClient, openSearchAsyncClient, backupProps, new TestSnapshotProvider());
    now = Instant.now().toEpochMilli();
  }

  @Test
  void getBackupsReturnsEmptyListOfBackups() throws IOException {

    final var response = emptyResponse();
    when(openSearchClient.snapshot().get((GetSnapshotRequest) any())).thenReturn(response);

    assertThat(repository.getBackups("repo")).isEmpty();
  }

  @Test
  void getBackupsReturnsNotEmptyListOfBackups() throws IOException {
    final var metadata = new Metadata(5L, "1", 1, 3);
    final var snapshotInfos =
        List.of(
            SnapshotInfo.of(
                bi ->
                    defaultFields(bi, metadata)
                        .snapshot("test-snapshot")
                        .state(SnapshotState.STARTED.name())
                        .startTimeInMillis("23")));
    final var response = GetSnapshotResponse.of(b -> defaultFields(b).snapshots(snapshotInfos));

    when(openSearchClient.snapshot().get((GetSnapshotRequest) any())).thenReturn(response);

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
  void successForExecuteSnapshotting() throws IOException {

    final var snapshotRequest =
        new BackupService.SnapshotRequest(
            "repo",
            "camunda_operate_1_2",
            new SnapshotIndexCollection(List.of("index-1", "index-2")),
            new Metadata(1L, "1", 1, 1));
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
    when(openSearchAsyncClient.snapshot().create((CreateSnapshotRequest) any()))
        .thenReturn(CompletableFuture.completedFuture(createSnapShotResponse));

    repository.executeSnapshotting(snapshotRequest, onSuccess, onFailure);
  }

  @Test
  void failedForExecuteSnapshotting() throws IOException {
    final var snapshotRequest =
        new SnapshotRequest(
            "repo",
            "camunda_operate_1_2",
            new SnapshotIndexCollection(List.of("index-1", "index-2")),
            new Metadata(1L, "1", 1, 1));
    final Runnable onSuccess = () -> fail("Should execute snapshot with failures.");
    final Runnable onFailure = () -> {};

    when(openSearchAsyncClient.snapshot().create((CreateSnapshotRequest) any()))
        .thenReturn(CompletableFuture.failedFuture(new SocketTimeoutException("no internet")));

    repository.executeSnapshotting(snapshotRequest, onSuccess, onFailure);
  }

  @Test
  void deleteSnapshotSucceed() throws IOException {
    when(openSearchAsyncClient.snapshot().delete((DeleteSnapshotRequest) any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new DeleteSnapshotResponse.Builder().acknowledged(true).build()));
    repository.deleteSnapshot("repo", "snapshot");
  }

  @Test
  void deleteSnapshotFails() throws IOException {

    when(openSearchAsyncClient.snapshot().delete((DeleteSnapshotRequest) any()))
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
  void getBackupStateShouldBeInProgress() throws IOException {
    when(backupProps.incompleteCheckTimeoutInSeconds())
        .thenReturn(BackupRepositoryProps.defaultIncompleteCheckTimeoutInSeconds());
    when(openSearchClient.snapshot().get((GetSnapshotRequest) any()))
        .thenReturn(
            GetSnapshotResponse.of(
                b ->
                    defaultFields(b)
                        .snapshots(
                            List.of(
                                SnapshotInfo.of(
                                    bi ->
                                        defaultFields(bi, new Metadata(5L, "1", 1, 3))
                                            .snapshot("snapshot")
                                            .state(SnapshotState.SUCCESS.name())
                                            .startTimeInMillis(
                                                Long.toString(
                                                    now - (incompleteCheckTimeoutLength / 2)))
                                            .endTimeInMillis(Long.toString(now)))))));

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
  void getBackupStateShouldBeIncompleteDueToTimeout() throws IOException {

    final long endtime = now - (incompleteCheckTimeoutLength * 2);

    final var snapshotInfos =
        List.of(
            SnapshotInfo.of(
                bi ->
                    defaultFields(bi, new Metadata(5L, "1", 1, 3))
                        .snapshot("snapshot")
                        .state(SnapshotState.SUCCESS.name())
                        .startTimeInMillis(Long.toString(endtime - 20))
                        // end time was double the timeout from now
                        .endTimeInMillis(Long.toString(endtime))));
    when(openSearchClient.snapshot().get((GetSnapshotRequest) any()))
        .thenReturn(GetSnapshotResponse.of(b -> defaultFields(b).snapshots(snapshotInfos)));

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
  void validateRepositoryExistsSuccess() throws IOException {
    when(openSearchClient.snapshot().getRepository((GetRepositoryRequest) any()))
        .thenReturn(GetRepositoryResponse.of(b -> b));

    repository.validateRepositoryExists("repo");
  }

  @Test
  void validateRepositoryExistsFailed() throws IOException {
    when(openSearchClient.snapshot().getRepository((GetRepositoryRequest) any()))
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
        assertThrows(BackupException.class, () -> repository.validateRepositoryExists("repo"));
    assertThat(exception.getMessage()).isEqualTo("No repository with name [repo] could be found.");
  }

  @Test
  void validateNoDuplicateBackupIdSuccess() throws IOException {
    when(openSearchClient.snapshot().get((GetSnapshotRequest) any())).thenReturn(emptyResponse());

    repository.validateNoDuplicateBackupId("repo", 42L);
  }

  @Test
  void validateNoDuplicateBackupIdFailed() throws IOException {
    when(openSearchClient.snapshot().get((GetSnapshotRequest) any()))
        .thenReturn(
            GetSnapshotResponse.of(
                b ->
                    defaultFields(b)
                        .snapshots(
                            List.of(
                                SnapshotInfo.of(
                                    bi ->
                                        defaultFields(bi, new Metadata(42L, "1", 1, 3))
                                            .uuid("test"))))));

    final var exception =
        assertThrows(
            InvalidRequestException.class,
            () -> repository.validateNoDuplicateBackupId("repo", 42L));
    assertThat(exception.getMessage())
        .isEqualTo("A backup with ID [42] already exists. Found snapshots: [test]");
  }

  @Test
  void shouldReturnBackupStateIncompleteWhenEndIsInTimeout() throws IOException {
    final long timeoutTime = now - incompleteCheckTimeoutLength * 2;
    final var firstSnapshotInfo =
        SnapshotInfo.of(
            bi ->
                defaultFields(bi, new Metadata(5L, "1", 1, 3))
                    .uuid("uuid")
                    .state(SnapshotState.SUCCESS.name())
                    .startTimeInMillis(Long.toString(timeoutTime - 50))
                    .endTimeInMillis(Long.toString(timeoutTime - 40)));
    final var lastSnapshotInfo =
        SnapshotInfo.of(
            bi ->
                defaultFields(bi, new Metadata(5L, "1", 2, 3))
                    .uuid("uuid")
                    .state(SnapshotState.SUCCESS.name())
                    .startTimeInMillis(Long.toString(timeoutTime - 30))
                    .endTimeInMillis(Long.toString(timeoutTime - 20)));
    when(openSearchClient.snapshot().get((GetSnapshotRequest) any()))
        .thenReturn(
            GetSnapshotResponse.of(
                b -> defaultFields(b).snapshots(List.of(firstSnapshotInfo, lastSnapshotInfo))));
    // Test
    final var backupState = repository.getBackupState("repository-name", 5L);
    assertThat(backupState.getState()).isEqualTo(BackupStateDto.INCOMPLETE);
  }

  @Test
  void shouldReturnBackupStateInProgressWhenStartIsInTimeoutButEndIsNot() throws IOException {
    when(backupProps.incompleteCheckTimeoutInSeconds())
        .thenReturn(BackupRepositoryProps.defaultIncompleteCheckTimeoutInSeconds());
    final var firstSnapshotInfo =
        SnapshotInfo.of(
            bi ->
                defaultFields(bi, new Metadata(5L, "1", 1, 3))
                    .uuid("uuid")
                    .state(SnapshotState.SUCCESS.name())
                    .startTimeInMillis(Long.toString(now - incompleteCheckTimeoutLength))
                    .endTimeInMillis(Long.toString(now - 20)));
    when(openSearchClient.snapshot().get((GetSnapshotRequest) any()))
        .thenReturn(
            GetSnapshotResponse.of(b -> defaultFields(b).snapshots(List.of(firstSnapshotInfo))));
    // Test
    final var backupState = repository.getBackupState("repository-name", 5L);
    assertThat(backupState.getState()).isEqualTo(BackupStateDto.IN_PROGRESS);
  }

  @Test
  void shouldReturnBackupStateFailedWhenSnapshotIsPartialCompleted() throws IOException {
    final var firstSnapshotInfo =
        SnapshotInfo.of(
            bi ->
                defaultFields(bi)
                    .uuid("uuid")
                    .state(SnapshotState.PARTIAL.name())
                    .startTimeInMillis(Long.toString(Instant.now().toEpochMilli())));
    when(openSearchClient.snapshot().get((GetSnapshotRequest) any()))
        .thenReturn(
            GetSnapshotResponse.of(b -> defaultFields(b).snapshots(List.of(firstSnapshotInfo))));
    // Test
    final var backupState = repository.getBackupState("repository-name", 5L);
    assertThat(backupState.getState()).isEqualTo(BackupStateDto.FAILED);
  }

  @Test
  void shouldReturnBackupStateFailedCompleted() throws IOException {
    final var firstSnapshotInfo =
        SnapshotInfo.of(
            bi ->
                defaultFields(bi, new Metadata(5L, "1", 1, 2))
                    .uuid("uuid")
                    .state(SnapshotState.SUCCESS.name())
                    .startTimeInMillis(Long.toString(Instant.now().toEpochMilli())));
    final var lastSnapshotInfo =
        SnapshotInfo.of(
            bi ->
                defaultFields(bi, new Metadata(5L, "1", 2, 2))
                    .uuid("uuid")
                    .state(SnapshotState.SUCCESS.name())
                    .startTimeInMillis(Long.toString(Instant.now().toEpochMilli())));
    when(openSearchClient.snapshot().get((GetSnapshotRequest) any()))
        .thenReturn(
            GetSnapshotResponse.of(
                b -> defaultFields(b).snapshots(List.of(firstSnapshotInfo, lastSnapshotInfo))));
    // Test
    final var backupState = repository.getBackupState("repository-name", 5L);
    assertThat(backupState.getState()).isEqualTo(BackupStateDto.COMPLETED);
  }

  @Test
  void shouldReturnBackupStateFailedWhenSnapshotIsFailed() throws IOException {
    final var firstSnapshotInfo =
        SnapshotInfo.of(
            bi ->
                defaultFields(bi)
                    .uuid("uuid")
                    .state(SnapshotState.FAILED.name())
                    .startTimeInMillis(Long.toString(Instant.now().toEpochMilli())));
    when(openSearchClient.snapshot().get((GetSnapshotRequest) any()))
        .thenReturn(
            GetSnapshotResponse.of(b -> defaultFields(b).snapshots(List.of(firstSnapshotInfo))));
    // Test
    final var backupState = repository.getBackupState("repository-name", 5L);
    assertThat(backupState.getState()).isEqualTo(BackupStateDto.FAILED);
  }

  @Test
  void shouldReturnAvailableIndices() throws IOException {
    // given
    when(openSearchClient.indices().get((GetIndexRequest) any()))
        .thenReturn(GetIndexResponse.of(b -> b));

    // when
    final var result = repository.checkAllIndicesExist(List.of("missingIndex"));

    // then
    assertThat(result.size()).isEqualTo(0);
    verify(openSearchClient.indices(), atLeastOnce())
        .get((GetIndexRequest) argThat(r -> ((GetIndexRequest) r).ignoreUnavailable()));
  }

  private SnapshotInfo.Builder defaultFields(final SnapshotInfo.Builder b) {
    return defaultFields(b, new Metadata(1L, "1", 1, 1));
  }

  private SnapshotInfo.Builder defaultFields(
      final SnapshotInfo.Builder b, final Metadata metadata) {
    return b.dataStreams(List.of())
        .indices(List.of())
        .snapshot("snapshot")
        .uuid("uuid")
        .metadata(MetadataMarshaller.asJson(metadata, new JacksonJsonpMapper()));
  }

  private GetSnapshotResponse.Builder defaultFields(final GetSnapshotResponse.Builder b) {
    return b.snapshots(List.of());
  }

  private GetSnapshotResponse emptyResponse() {
    return GetSnapshotResponse.of(this::defaultFields);
  }
}
