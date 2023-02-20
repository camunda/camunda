/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.JacksonConfig;
import io.camunda.operate.exceptions.OperateElasticsearchConnectionException;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.BackupProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.es.backup.BackupManager;
import io.camunda.operate.webapp.es.backup.Metadata;
import io.camunda.operate.webapp.management.BackupService;
import io.camunda.operate.webapp.management.dto.GetBackupStateResponseDetailDto;
import io.camunda.operate.webapp.management.dto.GetBackupStateResponseDto;
import io.camunda.operate.webapp.management.dto.TakeBackupRequestDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.cluster.repositories.get.GetRepositoriesResponse;
import org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.SnapshotClient;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.snapshots.SnapshotId;
import org.elasticsearch.snapshots.SnapshotInfo;
import org.elasticsearch.snapshots.SnapshotShardFailure;
import org.elasticsearch.snapshots.SnapshotState;
import org.elasticsearch.transport.TransportException;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
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
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.camunda.operate.util.CollectionUtil.asMap;
import static io.camunda.operate.webapp.es.backup.BackupManager.SNAPSHOT_MISSING_EXCEPTION_TYPE;
import static io.camunda.operate.webapp.management.dto.BackupStateDto.*;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {TestConfig.class, JacksonConfig.class, BackupService.class})
@ActiveProfiles({"test", "backend-test"})
public class BackupServiceTest {

  @SpyBean
  private BackupManager backupManager;

  @Mock
  private SnapshotClient snapshotClient;

  @MockBean
  @Qualifier("esClient")
  private RestHighLevelClient esClient;

  @SpyBean
  private OperateProperties operateProperties;

  @Autowired
  private BackupService backupService;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  public void shouldFailCreateBackupOnEmptyBackupId() {
    Exception exception = assertThrows(InvalidRequestException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto());
    });
    String expectedMessage = "BackupId must be provided";
    String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
  }

  @Test
  public void shouldFailOnWrongBackupId() {
    String expectedMessage = "BackupId must be provided";

    Exception exception = assertThrows(InvalidRequestException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId(null));
    });
    assertTrue(exception.getMessage().contains(expectedMessage));

    expectedMessage = "BackupId must be a non-negative Integer. Received value:";

    exception = assertThrows(InvalidRequestException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId(-1));
    });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception = assertThrows(InvalidRequestException.class, () -> {
      backupService.getBackupState(-1);
    });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception = assertThrows(InvalidRequestException.class, () -> {
      backupService.deleteBackup(-1);
    });
    assertTrue(exception.getMessage().contains(expectedMessage));
  }

  @Test
  public void shouldFailNoBackupRepositoryConfigured() {
    when(operateProperties.getBackup()).thenReturn(null);
    String expectedMessage = "No backup repository configured.";
    Integer backupId = 100;

    Exception exception = assertThrows(NotFoundException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId(backupId));
    });
    String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));

    exception = assertThrows(NotFoundException.class, () -> {
      backupService.getBackupState(backupId);
    });
    actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));

    exception = assertThrows(NotFoundException.class, () -> {
      backupService.deleteBackup(backupId);
    });
    actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));


    when(operateProperties.getBackup()).thenReturn(new BackupProperties());
    exception = assertThrows(NotFoundException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId(backupId));
    });
    actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));

    exception = assertThrows(NotFoundException.class, () -> {
      backupService.getBackupState(backupId);
    });
    actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));

    exception = assertThrows(NotFoundException.class, () -> {
      backupService.deleteBackup(backupId);
    });
    actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
  }

  @Test
  public void shouldFailCreateBackupOnNonExistingRepository() throws IOException {
    String repoName = "repoName";
    String expectedMessage = String.format("No repository with name [%s] could be found.", repoName);
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    ElasticsearchStatusException elsEx = mock(ElasticsearchStatusException.class);
    when(elsEx.getDetailedMessage()).thenReturn("type=repository_missing_exception");
    when(snapshotClient.getRepository(any(), any())).thenThrow(elsEx);
    when(esClient.snapshot()).thenReturn(snapshotClient);

    Exception exception = assertThrows(OperateRuntimeException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId(1));
    });
    String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));

    exception = assertThrows(OperateRuntimeException.class, () -> {
      backupService.deleteBackup(1);
    });
    actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));

    verify(esClient, times(2)).snapshot();
  }

  @Test
  public void shouldFailCreateBackupOnBackupIdNotFound() throws IOException {
    String repoName = "repoName";
    Integer backupId = 2;
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    SnapshotInfo snapshotInfo = mock(SnapshotInfo.class);
    when(snapshotInfo.snapshotId()).thenReturn(new SnapshotId("snapshotName", "uuid"));
    List<SnapshotInfo> snapshotInfos = asList(new SnapshotInfo[] { snapshotInfo });
    when(snapshotClient.get(any(), any())).thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    Exception exception = assertThrows(InvalidRequestException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId(backupId));
    });
    String expectedMessage = String.format("A backup with ID [%s] already exists. Found snapshots:", backupId);
    String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
    verify(esClient, times(2)).snapshot();
  }

  @Test
  public void shouldFailCreateBackupOn1stRequestFailedWithConnectionError() throws IOException {
    String repoName = "repoName";
    Integer backupId = 2;
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    when(snapshotClient.getRepository(any(), any())).thenThrow(new TransportException("Elastic is not available"));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    Exception exception = assertThrows(OperateElasticsearchConnectionException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId(backupId));
    });
    String expectedMessage = String.format(
        "Encountered an error connecting to Elasticsearch while retrieving repository with name [%s].", repoName);
    String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
  }

  @Test
  public void shouldFailCreateBackupOn2ndRequestFailedWithConnectionError() throws IOException {
    String repoName = "repoName";
    Integer backupId = 2;
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    when(snapshotClient.getRepository(any(), any())).thenReturn(null);
    when(snapshotClient.get(any(), any())).thenThrow(new TransportException("Elastic is not available"));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    Exception exception = assertThrows(OperateElasticsearchConnectionException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId(backupId));
    });
    String expectedMessage = String.format(
        "Encountered an error connecting to Elasticsearch while searching for duplicate backup. Repository name: [%s].",
        repoName);
    String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
  }

  @Test
  public void shouldFailGetStateOnNoBackupFound() throws IOException {
    String repoName = "repoName";
    Integer backupId = 2;
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    when(snapshotClient.get(any(), any())).thenThrow(
        new ElasticsearchStatusException(SNAPSHOT_MISSING_EXCEPTION_TYPE, RestStatus.NOT_FOUND));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    Exception exception = assertThrows(NotFoundException.class, () -> {
      backupService.getBackupState(backupId);
    });
    String expectedMessage = String.format("No backup with id [%s] found.", backupId);
    String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
    verify(esClient, times(1)).snapshot();
  }

  @Test
  public void shouldFailGetStateOnConnectionError() throws IOException {
    String repoName = "repoName";
    Integer backupId = 2;
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    when(esClient.snapshot()).thenThrow(new TransportException("Elastic is not available"));

    Exception exception = assertThrows(OperateElasticsearchConnectionException.class, () -> {
      backupService.getBackupState(backupId);
    });
    String expectedMessage = String.format(
        "Encountered an error connecting to Elasticsearch while searching for snapshots. Repository name: [%s].", repoName);
    String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
  }

  @Test
  public void shouldReturnCompletedState() throws IOException {
    String repoName = "repoName";
    Integer backupId = 2;
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    SnapshotInfo snapshotInfo1 = createSnapshotInfoMock("snapshotName1", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    SnapshotInfo snapshotInfo2 = createSnapshotInfoMock("snapshotName2", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    SnapshotInfo snapshotInfo3 = createSnapshotInfoMock("snapshotName3", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    List<SnapshotInfo> snapshotInfos = asList(
        new SnapshotInfo[] { snapshotInfo1, snapshotInfo2, snapshotInfo3 });
    when(snapshotClient.get(any(), any())).thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    GetBackupStateResponseDto backupState = backupService.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(COMPLETED);
    assertThat(backupState.getBackupId()).isEqualTo(backupId);
    assertThat(backupState.getFailureReason()).isNull();

    assertBackupDetails(snapshotInfos, backupState);
  }
  @Test
  public void shouldReturnFailedState1() throws IOException {
    String repoName = "repoName";
    Integer backupId = 2;
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    SnapshotInfo snapshotInfo1 = createSnapshotInfoMock("snapshotName1", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    SnapshotInfo snapshotInfo2 = createSnapshotInfoMock("snapshotName2", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    SnapshotShardFailure failure1 = new SnapshotShardFailure("someNodeId1",
        new ShardId("someIndex1", UUID.randomUUID().toString(), 1), "Shard is not allocated");
    SnapshotShardFailure failure2 = new SnapshotShardFailure("someNodeId2",
        new ShardId("someIndex2", UUID.randomUUID().toString(), 2), "Shard is not allocated");
    List<SnapshotShardFailure> shardFailures = asList(failure1, failure2);
    SnapshotInfo snapshotInfo3 = createSnapshotInfoMock("snapshotName3", UUID.randomUUID().toString(), SnapshotState.FAILED, shardFailures);
    List<SnapshotInfo> snapshotInfos = asList(
        new SnapshotInfo[] { snapshotInfo1, snapshotInfo2, snapshotInfo3 });
    when(snapshotClient.get(any(), any())).thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    GetBackupStateResponseDto backupState = backupService.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(FAILED);
    assertThat(backupState.getBackupId()).isEqualTo(backupId);
    assertThat(backupState.getFailureReason()).isEqualTo("There were failures with the following snapshots: snapshotName3");

    assertBackupDetails(snapshotInfos, backupState);

    assertThat(backupState.getDetails()).extracting(d -> d.getFailures())
        .containsExactly(null, null,
            snapshotInfos.get(2).shardFailures().stream().map(si -> si.toString()).toArray(String[]::new));
  }

  @Test
  public void shouldReturnFailedState2() throws IOException {
    String repoName = "repoName";
    Integer backupId = 2;
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    SnapshotInfo snapshotInfo1 = createSnapshotInfoMock("snapshotName1", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    SnapshotInfo snapshotInfo2 = createSnapshotInfoMock("snapshotName2", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    SnapshotInfo snapshotInfo3 = createSnapshotInfoMock("snapshotName3", UUID.randomUUID().toString(), SnapshotState.PARTIAL);
    List<SnapshotInfo> snapshotInfos = asList(
        new SnapshotInfo[] { snapshotInfo1, snapshotInfo2, snapshotInfo3 });
    when(snapshotClient.get(any(), any())).thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    GetBackupStateResponseDto backupState = backupService.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(FAILED);
    assertThat(backupState.getBackupId()).isEqualTo(backupId);
    assertThat(backupState.getFailureReason()).isEqualTo("Some of the snapshots are partial: snapshotName3");

    assertBackupDetails(snapshotInfos, backupState);
  }

  @Test
  public void shouldReturnFailedState3WhenMoreSnapshotsThanExpected() throws IOException {
    String repoName = "repoName";
    Integer backupId = 2;
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    SnapshotInfo snapshotInfo1 = createSnapshotInfoMock("snapshotName1", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    SnapshotInfo snapshotInfo2 = createSnapshotInfoMock("snapshotName2", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    SnapshotInfo snapshotInfo3 = createSnapshotInfoMock("snapshotName3", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    SnapshotInfo snapshotInfo4 = createSnapshotInfoMock("snapshotName4", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    List<SnapshotInfo> snapshotInfos = asList(
        new SnapshotInfo[] { snapshotInfo1, snapshotInfo2, snapshotInfo3, snapshotInfo4 });
    when(snapshotClient.get(any(), any())).thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    GetBackupStateResponseDto backupState = backupService.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(FAILED);
    assertThat(backupState.getBackupId()).isEqualTo(backupId);
    assertThat(backupState.getFailureReason()).isEqualTo("More snapshots found than expected.");

    assertBackupDetails(snapshotInfos, backupState);
  }

  @Test
  public void shouldReturnIncompatibleState() throws IOException {
    String repoName = "repoName";
    Integer backupId = 2;
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    SnapshotInfo snapshotInfo1 = createSnapshotInfoMock("snapshotName1", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    SnapshotInfo snapshotInfo2 = createSnapshotInfoMock("snapshotName2", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    SnapshotInfo snapshotInfo3 = createSnapshotInfoMock("snapshotName3", UUID.randomUUID().toString(), SnapshotState.INCOMPATIBLE);
    List<SnapshotInfo> snapshotInfos = asList(
        new SnapshotInfo[] { snapshotInfo1, snapshotInfo2, snapshotInfo3 });
    when(snapshotClient.get(any(), any())).thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    GetBackupStateResponseDto backupState = backupService.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(INCOMPATIBLE);
    assertThat(backupState.getBackupId()).isEqualTo(backupId);
    assertThat(backupState.getFailureReason()).isNull();

    assertBackupDetails(snapshotInfos, backupState);
  }

  @Test
  public void shouldReturnIncompleteState() throws IOException {
    String repoName = "repoName";
    Integer backupId = 2;
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    //we have only 2 out of 3 snapshots
    SnapshotInfo snapshotInfo1 = createSnapshotInfoMock("snapshotName1", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    SnapshotInfo snapshotInfo2 = createSnapshotInfoMock("snapshotName2", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    List<SnapshotInfo> snapshotInfos = asList(
        new SnapshotInfo[] { snapshotInfo1, snapshotInfo2 });
    when(snapshotClient.get(any(), any())).thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    GetBackupStateResponseDto backupState = backupService.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(INCOMPLETE);
    assertThat(backupState.getBackupId()).isEqualTo(backupId);
    assertThat(backupState.getFailureReason()).isNull();

    assertBackupDetails(snapshotInfos, backupState);
  }

  @Test
  public void shouldReturnInProgressState1() throws IOException {
    String repoName = "repoName";
    Integer backupId = 2;
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    //we have only 2 out of 3 snapshots
    SnapshotInfo snapshotInfo1 = createSnapshotInfoMock("snapshotName1", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    SnapshotInfo snapshotInfo2 = createSnapshotInfoMock("snapshotName2", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    SnapshotInfo snapshotInfo3 = createSnapshotInfoMock("snapshotName3", UUID.randomUUID().toString(), SnapshotState.IN_PROGRESS);
    List<SnapshotInfo> snapshotInfos = asList(
        new SnapshotInfo[] { snapshotInfo1, snapshotInfo2, snapshotInfo3 });
    when(snapshotClient.get(any(), any())).thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    GetBackupStateResponseDto backupState = backupService.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(IN_PROGRESS);
    assertThat(backupState.getBackupId()).isEqualTo(backupId);
    assertThat(backupState.getFailureReason()).isNull();

    assertBackupDetails(snapshotInfos, backupState);
  }

  @Test
  public void shouldReturnInProgressState2() throws IOException {
    String repoName = "repoName";
    Integer backupId = 2;
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    //we have only 2 out of 3 snapshots
    SnapshotInfo snapshotInfo1 = createSnapshotInfoMock("snapshotName1", UUID.randomUUID().toString(), SnapshotState.IN_PROGRESS);
    SnapshotInfo snapshotInfo2 = createSnapshotInfoMock("snapshotName2", UUID.randomUUID().toString(), SnapshotState.IN_PROGRESS);
    List<SnapshotInfo> snapshotInfos = asList(
        new SnapshotInfo[] { snapshotInfo1, snapshotInfo2 });
    when(snapshotClient.get(any(), any())).thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    GetBackupStateResponseDto backupState = backupService.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(IN_PROGRESS);
    assertThat(backupState.getBackupId()).isEqualTo(backupId);
    assertThat(backupState.getFailureReason()).isNull();

    assertBackupDetails(snapshotInfos, backupState);
  }

  @Test
  public void shouldFailDeleteBackupOnNonExistingRepository() throws IOException {
    String repoName = "repoName";
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    ElasticsearchStatusException elsEx = mock(ElasticsearchStatusException.class);
    when(elsEx.getDetailedMessage()).thenReturn("type=repository_missing_exception");
    when(snapshotClient.getRepository(any(), any())).thenThrow(elsEx);
    when(esClient.snapshot()).thenReturn(snapshotClient);
    Exception exception = assertThrows(OperateRuntimeException.class, () -> {
      backupService.deleteBackup(3);
    });

    String expectedMessage = String.format("No repository with name [%s] could be found.",
        repoName);
    String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
    verify(esClient, times(1)).snapshot();
  }

  @Test
  public void shouldFailGetBackupOnNonExistingRepository() throws IOException {
    String repoName = "repoName";
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    ElasticsearchStatusException elsEx = mock(ElasticsearchStatusException.class);
    when(elsEx.getDetailedMessage()).thenReturn("type=repository_missing_exception");
    when(esClient.snapshot()).thenThrow(elsEx);
    Exception exception = assertThrows(OperateRuntimeException.class, () -> {
      backupService.getBackupState(3);
    });

    String expectedMessage = String.format("No repository with name [%s] could be found.",
        repoName);
    String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
    verify(esClient, times(1)).snapshot();
  }

  @Test
  public void shouldFailGetBackupsOnNonExistingRepository() throws IOException {
    String repoName = "repoName";
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    ElasticsearchStatusException elsEx = mock(ElasticsearchStatusException.class);
    when(elsEx.getDetailedMessage()).thenReturn("type=repository_missing_exception");
    when(esClient.snapshot()).thenThrow(elsEx);

    Exception exception = assertThrows(OperateRuntimeException.class, () -> {
      backupService.getBackups();
    });

    String expectedMessage = String.format("No repository with name [%s] could be found.",
        repoName);
    String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
    verify(esClient, times(1)).snapshot();
  }

  @Test
  public void shouldReturnEmptyBackupsOnNoBackupFound() throws IOException {
    String repoName = "repoName";
    String backupId = "backupId";
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    when(snapshotClient.get(any(), any())).thenThrow(
        new ElasticsearchStatusException(SNAPSHOT_MISSING_EXCEPTION_TYPE, RestStatus.NOT_FOUND));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    assertThat(backupService.getBackups()).isEmpty();
  }

  @Test
  public void shouldFailGetBackupsOnConnectionError() throws IOException {
    String repoName = "repoName";
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    when(esClient.snapshot()).thenThrow(new TransportException("Elastic is not available"));

    Exception exception = assertThrows(OperateElasticsearchConnectionException.class, () -> {
      backupService.getBackups();
    });
    String expectedMessage = String.format(
        "Encountered an error connecting to Elasticsearch while searching for snapshots. Repository name: [%s].", repoName);
    String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
  }

  @Test
  public void shouldReturnThreeBackups() throws IOException {
    String repoName = "repoName";
    Integer backupId1 = 1;
    Integer backupId2 = 2;
    Integer backupId3 = 3;
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    //COMPLETED
    SnapshotInfo snapshotInfo1_1 = createSnapshotInfoMock(new Metadata().setBackupId(backupId1).setVersion("8.8.8").setPartNo(1).setPartCount(2),
        UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    SnapshotInfo snapshotInfo1_2 = createSnapshotInfoMock(new Metadata().setBackupId(backupId1).setVersion("8.8.8").setPartNo(2).setPartCount(2),
        UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    //we have only 2 out of 3 snapshots -> INCOMPLETE
    SnapshotInfo snapshotInfo2_1 = createSnapshotInfoMock(new Metadata().setBackupId(backupId2).setVersion("8.8.8").setPartNo(1).setPartCount(3),
        UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    SnapshotInfo snapshotInfo2_2 = createSnapshotInfoMock(new Metadata().setBackupId(backupId2).setVersion("8.8.8").setPartNo(2).setPartCount(3),
        UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    //IN_PROGRESS
    SnapshotInfo snapshotInfo3_1 = createSnapshotInfoMock(new Metadata().setBackupId(backupId3).setVersion("8.8.8").setPartNo(1).setPartCount(3),
        UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    SnapshotInfo snapshotInfo3_2 = createSnapshotInfoMock(new Metadata().setBackupId(backupId3).setVersion("8.8.8").setPartNo(2).setPartCount(3),
        UUID.randomUUID().toString(), SnapshotState.IN_PROGRESS);
    List<SnapshotInfo> snapshotInfos = asList(
        new SnapshotInfo[] { snapshotInfo1_1, snapshotInfo1_2, snapshotInfo2_1, snapshotInfo2_2, snapshotInfo3_1, snapshotInfo3_2 });
    when(snapshotClient.get(any(), any())).thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 6, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    List<GetBackupStateResponseDto> backups = backupService.getBackups();
    assertThat(backups).hasSize(3);
    GetBackupStateResponseDto backup3 = backups.get(0);
    assertThat(backup3.getState()).isEqualTo(IN_PROGRESS);
    assertThat(backup3.getBackupId()).isEqualTo(backupId3);
    assertThat(backup3.getFailureReason()).isNull();
    assertBackupDetails(List.of(snapshotInfo3_1, snapshotInfo3_2), backup3);

    GetBackupStateResponseDto backup2 = backups.get(1);
    assertThat(backup2.getState()).isEqualTo(INCOMPLETE);
    assertThat(backup2.getBackupId()).isEqualTo(backupId2);
    assertThat(backup2.getFailureReason()).isNull();
    assertBackupDetails(List.of(snapshotInfo2_1, snapshotInfo2_2), backup2);

    GetBackupStateResponseDto backup1 = backups.get(2);
    assertThat(backup1.getState()).isEqualTo(COMPLETED);
    assertThat(backup1.getBackupId()).isEqualTo(backupId1);
    assertThat(backup1.getFailureReason()).isNull();
    assertBackupDetails(List.of(snapshotInfo1_1, snapshotInfo1_2), backup1);
  }


  private void assertBackupDetails(List<SnapshotInfo> snapshotInfos, GetBackupStateResponseDto backupState) {
    assertThat(backupState.getDetails()).hasSize(snapshotInfos.size());
    assertThat(backupState.getDetails()).extracting(GetBackupStateResponseDetailDto::getSnapshotName)
        .containsExactlyInAnyOrder(
            snapshotInfos.stream().map(si -> si.snapshotId().getName()).toArray(String[]::new));
    assertThat(backupState.getDetails()).extracting(GetBackupStateResponseDetailDto::getState)
        .containsExactlyInAnyOrder(
            snapshotInfos.stream().map(si -> si.state().name()).toArray(String[]::new));
    assertThat(backupState.getDetails()).extracting(d -> d.getStartTime().toInstant().toEpochMilli())
        .containsExactlyInAnyOrder(
            snapshotInfos.stream().map(si -> si.startTime()).toArray(Long[]::new));
  }

  private SnapshotInfo createSnapshotInfoMock(String name, String uuid, SnapshotState state) {
    return createSnapshotInfoMock(null, name, uuid, state, null);
  }
  private SnapshotInfo createSnapshotInfoMock(Metadata metadata, String uuid, SnapshotState state) {
    return createSnapshotInfoMock(metadata, null, uuid, state, null);
  }

  @NotNull private SnapshotInfo createSnapshotInfoMock(String name, String uuid, SnapshotState state, List<SnapshotShardFailure> failures) {
    return createSnapshotInfoMock(null, name, uuid, state, failures);
  }

  @NotNull private SnapshotInfo createSnapshotInfoMock(Metadata metadata, String name, String uuid, SnapshotState state, List<SnapshotShardFailure> failures) {
    SnapshotInfo snapshotInfo = mock(SnapshotInfo.class);
    if (metadata != null) {
      when(snapshotInfo.snapshotId()).thenReturn(new SnapshotId(metadata.buildSnapshotName(), uuid));
      when(snapshotInfo.userMetadata()).thenReturn(objectMapper.convertValue(metadata, new TypeReference<>(){}));
    } else {
      when(snapshotInfo.snapshotId()).thenReturn(new SnapshotId(name, uuid));
      when(snapshotInfo.userMetadata()).thenReturn(asMap("version", "someVersion", "partNo", 1, "partCount", 3));
    }
    when(snapshotInfo.state()).thenReturn(state);
    when(snapshotInfo.shardFailures()).thenReturn(failures);
    when(snapshotInfo.startTime()).thenReturn(OffsetDateTime.now().toInstant().toEpochMilli());
    return snapshotInfo;
  }
}

@Configuration @ComponentScan(basePackages = { "io.camunda.operate.schema.indices",
    "io.camunda.operate.schema.templates", "io.camunda.operate.property" })
@Profile("backend-test")
class TestConfig {

}

