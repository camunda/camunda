/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp;

import static io.camunda.tasklist.util.CollectionUtil.asMap;
import static io.camunda.tasklist.webapp.es.backup.es.BackupManagerElasticSearch.SNAPSHOT_MISSING_EXCEPTION_TYPE;
import static io.camunda.tasklist.webapp.management.dto.BackupStateDto.COMPLETED;
import static io.camunda.tasklist.webapp.management.dto.BackupStateDto.FAILED;
import static io.camunda.tasklist.webapp.management.dto.BackupStateDto.INCOMPATIBLE;
import static io.camunda.tasklist.webapp.management.dto.BackupStateDto.INCOMPLETE;
import static io.camunda.tasklist.webapp.management.dto.BackupStateDto.IN_PROGRESS;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.JacksonConfig;
import io.camunda.tasklist.exceptions.TasklistElasticsearchConnectionException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.BackupProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.es.backup.Metadata;
import io.camunda.tasklist.webapp.es.backup.es.BackupManagerElasticSearch;
import io.camunda.tasklist.webapp.management.BackupService;
import io.camunda.tasklist.webapp.management.dto.GetBackupStateResponseDetailDto;
import io.camunda.tasklist.webapp.management.dto.GetBackupStateResponseDto;
import io.camunda.tasklist.webapp.management.dto.TakeBackupRequestDto;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import io.camunda.tasklist.webapp.security.TasklistProfileServiceImpl;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.SnapshotClient;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.snapshots.SnapshotId;
import org.elasticsearch.snapshots.SnapshotInfo;
import org.elasticsearch.snapshots.SnapshotShardFailure;
import org.elasticsearch.snapshots.SnapshotState;
import org.elasticsearch.transport.TransportException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {
      TestConfig.class,
      JacksonConfig.class,
      BackupService.class,
      TasklistProfileServiceImpl.class,
    })
@ActiveProfiles({"test", "backend-test", "standalone"})
public class BackupServiceElasticSearchTest {

  @SpyBean private BackupManagerElasticSearch backupManager;

  @Mock private SnapshotClient snapshotClient;

  @MockBean
  @Qualifier("tasklistEsClient")
  private RestHighLevelClient esClient;

  @SpyBean private TasklistProperties tasklistProperties;

  @Autowired private BackupService backupService;

  @Autowired private ObjectMapper objectMapper;

  @Test
  public void shouldFailCreateBackupOnEmptyBackupId() {
    final Exception exception =
        assertThrows(
            InvalidRequestException.class,
            () -> backupService.takeBackup(new TakeBackupRequestDto()));
    final String expectedMessage = "BackupId must be provided";
    final String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
  }

  @Test
  public void shouldFailOnWrongBackupId() {
    String expectedMessage = "BackupId must be provided";

    Exception exception =
        assertThrows(
            InvalidRequestException.class,
            () -> backupService.takeBackup(new TakeBackupRequestDto().setBackupId(null)));
    assertTrue(exception.getMessage().contains(expectedMessage));

    expectedMessage = "BackupId must be a non-negative Long. Received value:";

    exception =
        assertThrows(
            InvalidRequestException.class,
            () -> backupService.takeBackup(new TakeBackupRequestDto().setBackupId(-1L)));
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception =
        assertThrows(InvalidRequestException.class, () -> backupService.getBackupState(-1L));
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception = assertThrows(InvalidRequestException.class, () -> backupService.deleteBackup(-1L));
    assertTrue(exception.getMessage().contains(expectedMessage));
  }

  @Test
  public void shouldFailNoBackupRepositoryConfigured() {
    when(tasklistProperties.getBackup()).thenReturn(null);
    final String expectedMessage = "No backup repository configured.";
    final Long backupId = 100L;

    Exception exception =
        assertThrows(
            NotFoundApiException.class,
            () -> backupService.takeBackup(new TakeBackupRequestDto().setBackupId(backupId)));
    String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));

    exception =
        assertThrows(NotFoundApiException.class, () -> backupService.getBackupState(backupId));
    actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));

    exception =
        assertThrows(NotFoundApiException.class, () -> backupService.deleteBackup(backupId));
    actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));

    when(tasklistProperties.getBackup()).thenReturn(new BackupProperties());
    exception =
        assertThrows(
            NotFoundApiException.class,
            () -> backupService.takeBackup(new TakeBackupRequestDto().setBackupId(backupId)));
    actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));

    exception =
        assertThrows(NotFoundApiException.class, () -> backupService.getBackupState(backupId));
    actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));

    exception =
        assertThrows(NotFoundApiException.class, () -> backupService.deleteBackup(backupId));
    actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
  }

  @Test
  public void shouldFailCreateBackupOnNonExistingRepository() throws IOException {
    final String repoName = "repoName";
    final String expectedMessage =
        String.format("No repository with name [%s] could be found.", repoName);
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    final ElasticsearchStatusException elsEx = mock(ElasticsearchStatusException.class);
    when(elsEx.getDetailedMessage()).thenReturn("type=repository_missing_exception");
    when(snapshotClient.getRepository(any(), any())).thenThrow(elsEx);
    when(esClient.snapshot()).thenReturn(snapshotClient);
    Exception exception =
        assertThrows(
            TasklistRuntimeException.class,
            () -> backupService.takeBackup(new TakeBackupRequestDto().setBackupId(1L)));

    String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));

    exception = assertThrows(TasklistRuntimeException.class, () -> backupService.deleteBackup(1L));
    actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));

    verify(esClient, times(2)).snapshot();
  }

  @Test
  public void shouldFailCreateBackupOn1stRequestFailedWithConnectionError() throws IOException {
    final String repoName = "repoName";
    final Long backupId = 2L;
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    when(snapshotClient.getRepository(any(), any()))
        .thenThrow(new TransportException("Elastic is not available"));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final Exception exception =
        assertThrows(
            TasklistElasticsearchConnectionException.class,
            () -> backupService.takeBackup(new TakeBackupRequestDto().setBackupId(backupId)));
    final String expectedMessage =
        String.format(
            "Encountered an error connecting to Elasticsearch while retrieving repository with name [%s].",
            repoName);
    final String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
  }

  @Test
  public void shouldFailCreateBackupOn2ndRequestFailedWithConnectionError() throws IOException {
    final String repoName = "repoName";
    final Long backupId = 3L;
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    when(snapshotClient.getRepository(any(), any())).thenReturn(null);
    when(snapshotClient.get(any(), any()))
        .thenThrow(new TransportException("Elastic is not available"));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final Exception exception =
        assertThrows(
            TasklistElasticsearchConnectionException.class,
            () -> backupService.takeBackup(new TakeBackupRequestDto().setBackupId(backupId)));
    final String expectedMessage =
        String.format(
            "Encountered an error connecting to Elasticsearch while searching for duplicate backup. Repository name: [%s].",
            repoName);
    final String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
  }

  @Test
  public void shouldFailCreateBackupOnBackupIdAlreadyExists() throws IOException {
    final String repoName = "repoName";
    final Long backupid = 4L;
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    final SnapshotInfo snapshotInfo = mock(SnapshotInfo.class);
    when(snapshotInfo.snapshotId()).thenReturn(new SnapshotId("snapshotName", "uuid"));
    final List<SnapshotInfo> snapshotInfos = asList(new SnapshotInfo[] {snapshotInfo});
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final Exception exception =
        assertThrows(
            InvalidRequestException.class,
            () -> backupService.takeBackup(new TakeBackupRequestDto().setBackupId(backupid)));
    final String expectedMessage =
        String.format("A backup with ID [%s] already exists. Found snapshots:", backupid);
    final String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
    verify(esClient, times(2)).snapshot();
  }

  @Test
  public void shouldFailGetStateOnBackupIdNotFound() throws IOException {
    final String repoName = "repoName";
    final Long backupId = 5L;
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    when(snapshotClient.get(any(), any()))
        .thenThrow(
            new ElasticsearchStatusException(
                SNAPSHOT_MISSING_EXCEPTION_TYPE, RestStatus.NOT_FOUND));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final Exception exception =
        assertThrows(NotFoundApiException.class, () -> backupService.getBackupState(backupId));
    final String expectedMessage = String.format("No backup with id [%s] found.", backupId);
    final String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
    verify(esClient, times(1)).snapshot();
  }

  @Test
  public void shouldFailGetStateOnConnectionError() {
    final String repoName = "repoName";
    final Long backupId = 6L;
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    when(esClient.snapshot()).thenThrow(new TransportException("Elastic is not available"));

    final Exception exception =
        assertThrows(
            TasklistElasticsearchConnectionException.class,
            () -> backupService.getBackupState(backupId));
    final String expectedMessage =
        String.format(
            "Encountered an error connecting to Elasticsearch while searching for snapshots. Repository name: [%s].",
            repoName);
    final String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
  }

  @Test
  public void shouldReturnCompletedState() throws IOException {
    final String repoName = "repoName";
    final Long backupId = 7L;
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    final SnapshotInfo snapshotInfo1 =
        createSnapshotInfoMock(
            "snapshotName1", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock(
            "snapshotName2", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo3 =
        createSnapshotInfoMock(
            "snapshotName3", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final List<SnapshotInfo> snapshotInfos = asList(snapshotInfo1, snapshotInfo2, snapshotInfo3);
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final GetBackupStateResponseDto backupState = backupService.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(COMPLETED);
    assertThat(backupState.getBackupId()).isEqualTo(backupId);
    assertThat(backupState.getFailureReason()).isNull();

    assertBackupDetails(snapshotInfos, backupState);
  }

  @Test
  public void shouldReturnFailedState1() throws IOException {
    final String repoName = "repoName";
    final Long backupId = 8L;
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    final SnapshotInfo snapshotInfo1 =
        createSnapshotInfoMock(
            "snapshotName1", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock(
            "snapshotName2", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final SnapshotShardFailure failure1 =
        new SnapshotShardFailure(
            "someNodeId1",
            new ShardId("someIndex1", UUID.randomUUID().toString(), 1),
            "Shard is not allocated");
    final SnapshotShardFailure failure2 =
        new SnapshotShardFailure(
            "someNodeId2",
            new ShardId("someIndex2", UUID.randomUUID().toString(), 2),
            "Shard is not allocated");
    final List<SnapshotShardFailure> shardFailures = asList(failure1, failure2);
    final SnapshotInfo snapshotInfo3 =
        createSnapshotInfoMock(
            "snapshotName3", UUID.randomUUID().toString(), SnapshotState.FAILED, shardFailures);
    final List<SnapshotInfo> snapshotInfos = asList(snapshotInfo1, snapshotInfo2, snapshotInfo3);
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final GetBackupStateResponseDto backupState = backupService.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(FAILED);
    assertThat(backupState.getBackupId()).isEqualTo(backupId);
    assertThat(backupState.getFailureReason())
        .isEqualTo("There were failures with the following snapshots: snapshotName3");

    assertBackupDetails(snapshotInfos, backupState);

    assertThat(backupState.getDetails())
        .extracting(d -> d.getFailures())
        .containsExactly(
            null,
            null,
            snapshotInfos.get(2).shardFailures().stream()
                .map(SnapshotShardFailure::toString)
                .toArray(String[]::new));
  }

  @Test
  public void shouldReturnFailedState2() throws IOException {
    final String repoName = "repoName";
    final Long backupId = 9L;
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    final SnapshotInfo snapshotInfo1 =
        createSnapshotInfoMock(
            "snapshotName1", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock(
            "snapshotName2", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo3 =
        createSnapshotInfoMock(
            "snapshotName3", UUID.randomUUID().toString(), SnapshotState.PARTIAL);
    final List<SnapshotInfo> snapshotInfos = asList(snapshotInfo1, snapshotInfo2, snapshotInfo3);
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final GetBackupStateResponseDto backupState = backupService.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(FAILED);
    assertThat(backupState.getBackupId()).isEqualTo(backupId);
    assertThat(backupState.getFailureReason())
        .isEqualTo("Some of the snapshots are partial: snapshotName3");

    assertBackupDetails(snapshotInfos, backupState);
  }

  @Test
  public void shouldReturnFailedState3WhenMoreSnapshotsThanExpected() throws IOException {
    final String repoName = "repoName";
    final Long backupId = 10L;
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    final SnapshotInfo snapshotInfo1 =
        createSnapshotInfoMock(
            "snapshotName1", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock(
            "snapshotName2", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo3 =
        createSnapshotInfoMock(
            "snapshotName3", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo4 =
        createSnapshotInfoMock(
            "snapshotName4", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final List<SnapshotInfo> snapshotInfos =
        asList(snapshotInfo1, snapshotInfo2, snapshotInfo3, snapshotInfo4);
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final GetBackupStateResponseDto backupState = backupService.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(FAILED);
    assertThat(backupState.getBackupId()).isEqualTo(backupId);
    assertThat(backupState.getFailureReason()).isEqualTo("More snapshots found than expected.");

    assertBackupDetails(snapshotInfos, backupState);
  }

  @Test
  public void shouldReturnIncompatibleState() throws IOException {
    final String repoName = "repoName";
    final Long backupId = 11L;
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    final SnapshotInfo snapshotInfo1 =
        createSnapshotInfoMock(
            "snapshotName1", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock(
            "snapshotName2", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo3 =
        createSnapshotInfoMock(
            "snapshotName3", UUID.randomUUID().toString(), SnapshotState.INCOMPATIBLE);
    final List<SnapshotInfo> snapshotInfos = asList(snapshotInfo1, snapshotInfo2, snapshotInfo3);
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final GetBackupStateResponseDto backupState = backupService.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(INCOMPATIBLE);
    assertThat(backupState.getBackupId()).isEqualTo(backupId);
    assertThat(backupState.getFailureReason()).isNull();

    assertBackupDetails(snapshotInfos, backupState);
  }

  @Test
  public void shouldReturnIncompleteState() throws IOException {
    final String repoName = "repoName";
    final Long backupId = 12L;
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    // we have only 2 out of 3 snapshots
    final SnapshotInfo snapshotInfo1 =
        createSnapshotInfoMock(
            "snapshotName1", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock(
            "snapshotName2", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final List<SnapshotInfo> snapshotInfos = asList(snapshotInfo1, snapshotInfo2);
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final GetBackupStateResponseDto backupState = backupService.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(INCOMPLETE);
    assertThat(backupState.getBackupId()).isEqualTo(backupId);
    assertThat(backupState.getFailureReason()).isNull();

    assertBackupDetails(snapshotInfos, backupState);
  }

  @Test
  public void shouldReturnInProgressState1() throws IOException {
    final String repoName = "repoName";
    final Long backupId = 13L;
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    final SnapshotInfo snapshotInfo1 =
        createSnapshotInfoMock(
            "snapshotName1", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock(
            "snapshotName2", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo3 =
        createSnapshotInfoMock(
            "snapshotName3", UUID.randomUUID().toString(), SnapshotState.IN_PROGRESS);
    final List<SnapshotInfo> snapshotInfos = asList(snapshotInfo1, snapshotInfo2, snapshotInfo3);
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final GetBackupStateResponseDto backupState = backupService.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(IN_PROGRESS);
    assertThat(backupState.getBackupId()).isEqualTo(backupId);
    assertThat(backupState.getFailureReason()).isNull();

    assertBackupDetails(snapshotInfos, backupState);
  }

  @Test
  public void shouldReturnInProgressState2() throws IOException {
    final String repoName = "repoName";
    final Long backupId = 14L;
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    // we have only 2 out of 3 snapshots
    final SnapshotInfo snapshotInfo1 =
        createSnapshotInfoMock(
            "snapshotName1", UUID.randomUUID().toString(), SnapshotState.IN_PROGRESS);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock(
            "snapshotName2", UUID.randomUUID().toString(), SnapshotState.IN_PROGRESS);
    final List<SnapshotInfo> snapshotInfos = asList(snapshotInfo1, snapshotInfo2);
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final GetBackupStateResponseDto backupState = backupService.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(IN_PROGRESS);
    assertThat(backupState.getBackupId()).isEqualTo(backupId);
    assertThat(backupState.getFailureReason()).isNull();

    assertBackupDetails(snapshotInfos, backupState);
  }

  @Test
  public void shouldFailDeleteBackupOnNonExistingRepository() throws IOException {
    final String repoName = "repoName";
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    final ElasticsearchStatusException elsEx = mock(ElasticsearchStatusException.class);
    when(elsEx.getDetailedMessage()).thenReturn("type=repository_missing_exception");
    when(snapshotClient.getRepository(any(), any())).thenThrow(elsEx);
    when(esClient.snapshot()).thenReturn(snapshotClient);
    final Exception exception =
        assertThrows(TasklistRuntimeException.class, () -> backupService.deleteBackup(3L));

    final String expectedMessage =
        String.format("No repository with name [%s] could be found.", repoName);
    final String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
    verify(esClient, times(1)).snapshot();
  }

  @Test
  public void shouldFailGetBackupOnNonExistingRepository() {
    final String repoName = "repoName";
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    final ElasticsearchStatusException elsEx = mock(ElasticsearchStatusException.class);
    when(elsEx.getDetailedMessage()).thenReturn("type=repository_missing_exception");
    when(esClient.snapshot()).thenThrow(elsEx);
    final Exception exception =
        assertThrows(TasklistRuntimeException.class, () -> backupService.getBackupState(4L));

    final String expectedMessage =
        String.format("No repository with name [%s] could be found.", repoName);
    final String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
    verify(esClient, times(1)).snapshot();
  }

  @Test
  public void shouldFailGetBackupsOnNonExistingRepository() {
    final String repoName = "repoName";
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    final ElasticsearchStatusException elsEx = mock(ElasticsearchStatusException.class);
    when(elsEx.getDetailedMessage()).thenReturn("type=repository_missing_exception");
    when(esClient.snapshot()).thenThrow(elsEx);

    final Exception exception =
        assertThrows(TasklistRuntimeException.class, () -> backupService.getBackups(true, null));

    final String expectedMessage =
        String.format("No repository with name [%s] could be found.", repoName);
    final String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
    verify(esClient, times(1)).snapshot();
  }

  @Test
  public void shouldReturnEmptyBackupsOnNoBackupFound() throws IOException {
    final String repoName = "repoName";
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    when(snapshotClient.get(any(), any()))
        .thenThrow(
            new ElasticsearchStatusException(
                SNAPSHOT_MISSING_EXCEPTION_TYPE, RestStatus.NOT_FOUND));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    assertThat(backupService.getBackups(true, null)).isEmpty();
  }

  @Test
  public void shouldFailGetBackupsOnConnectionError() {
    final String repoName = "repoName";
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    when(esClient.snapshot()).thenThrow(new TransportException("Elastic is not available"));

    final Exception exception =
        assertThrows(
            TasklistElasticsearchConnectionException.class,
            () -> backupService.getBackups(true, null));
    final String expectedMessage =
        String.format(
            "Encountered an error connecting to Elasticsearch while searching for snapshots. Repository name: [%s].",
            repoName);
    final String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
  }

  @Test
  public void shouldReturnThreeBackups() throws IOException {
    final String repoName = "repoName";
    final Long backupId1 = 1L;
    final Long backupId2 = 2L;
    final Long backupId3 = 3L;
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    // COMPLETED
    final SnapshotInfo snapshotInfo11 =
        createSnapshotInfoMock(
            new Metadata().setBackupId(backupId1).setVersion("8.8.8").setPartNo(1).setPartCount(2),
            UUID.randomUUID().toString(),
            SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo12 =
        createSnapshotInfoMock(
            new Metadata().setBackupId(backupId1).setVersion("8.8.8").setPartNo(2).setPartCount(2),
            UUID.randomUUID().toString(),
            SnapshotState.SUCCESS);
    // we have only 2 out of 3 snapshots -> INCOMPLETE
    final SnapshotInfo snapshotInfo21 =
        createSnapshotInfoMock(
            new Metadata().setBackupId(backupId2).setVersion("8.8.8").setPartNo(1).setPartCount(3),
            UUID.randomUUID().toString(),
            SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo22 =
        createSnapshotInfoMock(
            new Metadata().setBackupId(backupId2).setVersion("8.8.8").setPartNo(2).setPartCount(3),
            UUID.randomUUID().toString(),
            SnapshotState.SUCCESS);
    // IN_PROGRESS
    final SnapshotInfo snapshotInfo31 =
        createSnapshotInfoMock(
            new Metadata().setBackupId(backupId3).setVersion("8.8.8").setPartNo(1).setPartCount(3),
            UUID.randomUUID().toString(),
            SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo32 =
        createSnapshotInfoMock(
            new Metadata().setBackupId(backupId3).setVersion("8.8.8").setPartNo(2).setPartCount(3),
            UUID.randomUUID().toString(),
            SnapshotState.IN_PROGRESS);
    final List<SnapshotInfo> snapshotInfos =
        asList(
            snapshotInfo11,
            snapshotInfo12,
            snapshotInfo21,
            snapshotInfo22,
            snapshotInfo31,
            snapshotInfo32);
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 6, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final List<GetBackupStateResponseDto> backups = backupService.getBackups(true, null);
    assertThat(backups).hasSize(3);
    final GetBackupStateResponseDto backup3 = backups.get(0);
    assertThat(backup3.getState()).isEqualTo(IN_PROGRESS);
    assertThat(backup3.getBackupId()).isEqualTo(backupId3);
    assertThat(backup3.getFailureReason()).isNull();
    assertBackupDetails(List.of(snapshotInfo31, snapshotInfo32), backup3);

    final GetBackupStateResponseDto backup2 = backups.get(1);
    assertThat(backup2.getState()).isEqualTo(INCOMPLETE);
    assertThat(backup2.getBackupId()).isEqualTo(backupId2);
    assertThat(backup2.getFailureReason()).isNull();
    assertBackupDetails(List.of(snapshotInfo21, snapshotInfo22), backup2);

    final GetBackupStateResponseDto backup1 = backups.get(2);
    assertThat(backup1.getState()).isEqualTo(COMPLETED);
    assertThat(backup1.getBackupId()).isEqualTo(backupId1);
    assertThat(backup1.getFailureReason()).isNull();
    assertBackupDetails(List.of(snapshotInfo11, snapshotInfo12), backup1);
  }

  @Test
  public void shouldReturnVersion81BackupByLongBackupId() throws IOException {
    final String repoName = "repoName";
    final Long backupId1 = 1692175636514555512L;
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    // COMPLETED
    final Metadata metadata1 =
        new Metadata().setBackupId(backupId1).setVersion("8.8.8").setPartNo(1).setPartCount(2);
    final SnapshotInfo snapshotInfo11 =
        createSnapshotInfoMock(metadata1, UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    // remove backupId from metadata
    metadata1.setBackupId(null);
    when(snapshotInfo11.userMetadata())
        .thenReturn(objectMapper.convertValue(metadata1, new TypeReference<>() {}));

    final Metadata metadata2 =
        new Metadata().setBackupId(backupId1).setVersion("8.8.8").setPartNo(2).setPartCount(2);
    final SnapshotInfo snapshotInfo12 =
        createSnapshotInfoMock(metadata2, UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    metadata2.setBackupId(null);
    when(snapshotInfo12.userMetadata())
        .thenReturn(objectMapper.convertValue(metadata2, new TypeReference<>() {}));
    final List<SnapshotInfo> snapshotInfos = asList(snapshotInfo11, snapshotInfo12);
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 6, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final List<GetBackupStateResponseDto> backups = backupService.getBackups(true, null);
    assertThat(backups).hasSize(1);
    final GetBackupStateResponseDto backup1 = backups.get(0);
    assertThat(backup1.getState()).isEqualTo(COMPLETED);
    assertThat(backup1.getBackupId()).isEqualTo(backupId1);
    assertThat(backup1.getFailureReason()).isNull();
    assertBackupDetails(List.of(snapshotInfo11, snapshotInfo12), backup1);
  }

  private void assertBackupDetails(
      final List<SnapshotInfo> snapshotInfos, final GetBackupStateResponseDto backupState) {
    assertThat(backupState.getDetails()).hasSize(snapshotInfos.size());
    assertThat(backupState.getDetails())
        .extracting(GetBackupStateResponseDetailDto::getSnapshotName)
        .containsExactlyInAnyOrder(
            snapshotInfos.stream().map(si -> si.snapshotId().getName()).toArray(String[]::new));
    assertThat(backupState.getDetails())
        .extracting(GetBackupStateResponseDetailDto::getState)
        .containsExactlyInAnyOrder(
            snapshotInfos.stream().map(si -> si.state().name()).toArray(String[]::new));
    assertThat(backupState.getDetails())
        .extracting(d -> d.getStartTime().toInstant().toEpochMilli())
        .containsExactlyInAnyOrder(
            snapshotInfos.stream().map(SnapshotInfo::startTime).toArray(Long[]::new));
  }

  private SnapshotInfo createSnapshotInfoMock(
      final String name, final String uuid, final SnapshotState state) {
    return createSnapshotInfoMock(null, name, uuid, state, null);
  }

  private SnapshotInfo createSnapshotInfoMock(
      final Metadata metadata, final String uuid, final SnapshotState state) {
    return createSnapshotInfoMock(metadata, null, uuid, state, null);
  }

  private SnapshotInfo createSnapshotInfoMock(
      final String name,
      final String uuid,
      final SnapshotState state,
      final List<SnapshotShardFailure> failures) {
    return createSnapshotInfoMock(null, name, uuid, state, failures);
  }

  @NotNull
  private SnapshotInfo createSnapshotInfoMock(
      final Metadata metadata,
      final String name,
      final String uuid,
      final SnapshotState state,
      final List<SnapshotShardFailure> failures) {
    final SnapshotInfo snapshotInfo = mock(SnapshotInfo.class);
    if (metadata != null) {
      when(snapshotInfo.snapshotId())
          .thenReturn(new SnapshotId(metadata.buildSnapshotName(), uuid));
      when(snapshotInfo.userMetadata())
          .thenReturn(objectMapper.convertValue(metadata, new TypeReference<>() {}));
    } else {
      when(snapshotInfo.snapshotId()).thenReturn(new SnapshotId(name, uuid));
      when(snapshotInfo.userMetadata())
          .thenReturn(asMap("version", "someVersion", "partNo", 1, "partCount", 3));
    }
    when(snapshotInfo.state()).thenReturn(state);
    when(snapshotInfo.shardFailures()).thenReturn(failures);
    when(snapshotInfo.startTime()).thenReturn(OffsetDateTime.now().toInstant().toEpochMilli());
    return snapshotInfo;
  }
}

@Configuration
@ComponentScan(
    basePackages = {
      "io.camunda.tasklist.schema.indices",
      "io.camunda.tasklist.schema.templates",
      "io.camunda.tasklist.property"
    })
@Profile("backend-test")
class TestConfig {}
