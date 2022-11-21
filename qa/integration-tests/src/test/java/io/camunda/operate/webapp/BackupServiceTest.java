/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp;

import io.camunda.operate.JacksonConfig;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.BackupProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.es.backup.BackupManager;
import io.camunda.operate.webapp.management.BackupService;
import io.camunda.operate.webapp.management.dto.GetBackupStateResponseDto;
import io.camunda.operate.webapp.management.dto.TakeBackupRequestDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import java.util.UUID;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.SnapshotClient;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.snapshots.SnapshotId;
import org.elasticsearch.snapshots.SnapshotInfo;
import org.elasticsearch.snapshots.SnapshotState;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
import java.util.Arrays;
import java.util.List;

import static io.camunda.operate.util.CollectionUtil.asMap;
import static io.camunda.operate.webapp.es.backup.BackupManager.SNAPSHOT_MISSING_EXCEPTION_TYPE;
import static io.camunda.operate.webapp.management.dto.BackupStateDto.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {TestConfig.class, JacksonConfig.class})
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

  @InjectMocks
  private BackupService backupService;

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
  public void shouldFailCreateBackupOnWrongBackupId() {
    String expectedMessage = "BackupId must not contain any uppercase letters or any of [ , \", *, \\, <, |, ,, >, /, ?, _].";

    Exception exception = assertThrows(InvalidRequestException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId("UPPERCASEID"));
    });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception = assertThrows(InvalidRequestException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith "));
    });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception = assertThrows(InvalidRequestException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith\""));
    });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception = assertThrows(InvalidRequestException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith*"));
    });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception = assertThrows(InvalidRequestException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith\\"));
    });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception = assertThrows(InvalidRequestException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith<"));
    });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception = assertThrows(InvalidRequestException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith|"));
    });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception = assertThrows(InvalidRequestException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith,"));
    });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception = assertThrows(InvalidRequestException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith>"));
    });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception = assertThrows(InvalidRequestException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith/"));
    });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception = assertThrows(InvalidRequestException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith?"));
    });

    exception = assertThrows(InvalidRequestException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith_"));
    });
    assertTrue(exception.getMessage().contains(expectedMessage));
  }

  @Test
  public void shouldFailCreateBackupOnAbsentRepositoryName() {
    when(operateProperties.getBackup()).thenReturn(new BackupProperties());
    Exception exception = assertThrows(InvalidRequestException.class, () -> {
      backupManager.takeBackup(new TakeBackupRequestDto().setBackupId("backupid"));
    });
    String expectedMessage = "Cannot trigger backup because no Elasticsearch snapshot repository name found in Operate configuration.";
    String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
  }

  @Test
  public void shouldFailCreateBackupOnNonExistingRepository() throws IOException {
    String repoName = "repoName";
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    ElasticsearchStatusException elsEx = mock(ElasticsearchStatusException.class);
    when(elsEx.getDetailedMessage()).thenReturn("type=repository_missing_exception");
    when(snapshotClient.getRepository(any(), any())).thenThrow(elsEx);
    when(esClient.snapshot()).thenReturn(snapshotClient);
    Exception exception = assertThrows(OperateRuntimeException.class, () -> {
      backupManager.takeBackup(new TakeBackupRequestDto().setBackupId("backupid"));
    });

    String expectedMessage = String.format("Cannot trigger backup because no repository with name [%s] could be found.",
        repoName);
    String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
    verify(esClient, times(1)).snapshot();
  }

  @Test
  public void shouldFailCreateBackupOnBackupIdNotFound() throws IOException {
    String repoName = "repoName";
    String backupId = "backupId";
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    SnapshotInfo snapshotInfo = mock(SnapshotInfo.class);
    when(snapshotInfo.snapshotId()).thenReturn(new SnapshotId("snapshotName", "uuid"));
    List<SnapshotInfo> snapshotInfos = Arrays.asList(new SnapshotInfo[] { snapshotInfo });
    when(snapshotClient.get(any(), any())).thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    Exception exception = assertThrows(InvalidRequestException.class, () -> {
      backupManager.takeBackup(new TakeBackupRequestDto().setBackupId(backupId));
    });
    String expectedMessage = String.format("A backup with ID [%s] already exists. Found snapshots:", backupId);
    String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
    verify(esClient, times(2)).snapshot();
  }

  @Test
  public void shouldFailGetStateOnBackupIdAlreadyExists() throws IOException {
    String repoName = "repoName";
    String backupId = "backupId";
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    when(snapshotClient.get(any(), any())).thenThrow(
        new ElasticsearchStatusException(SNAPSHOT_MISSING_EXCEPTION_TYPE, RestStatus.NOT_FOUND));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    Exception exception = assertThrows(NotFoundException.class, () -> {
      backupManager.getBackupState(backupId);
    });
    String expectedMessage = String.format("No backup with id [%s] found.", backupId);
    String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
    verify(esClient, times(1)).snapshot();
  }

  @Test
  public void shouldReturnCompletedState() throws IOException {
    String repoName = "repoName";
    String backupId = "backupId";
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    SnapshotInfo snapshotInfo1 = createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    SnapshotInfo snapshotInfo2 = createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    SnapshotInfo snapshotInfo3 = createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    List<SnapshotInfo> snapshotInfos = Arrays.asList(
        new SnapshotInfo[] { snapshotInfo1, snapshotInfo2, snapshotInfo3 });
    when(snapshotClient.get(any(), any())).thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    GetBackupStateResponseDto backupState = backupManager.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(COMPLETED);
  }
  @Test
  public void shouldReturnFailedState1() throws IOException {
    String repoName = "repoName";
    String backupId = "backupId";
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    SnapshotInfo snapshotInfo1 = createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    SnapshotInfo snapshotInfo2 = createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    SnapshotInfo snapshotInfo3 = createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.FAILED);
    List<SnapshotInfo> snapshotInfos = Arrays.asList(
        new SnapshotInfo[] { snapshotInfo1, snapshotInfo2, snapshotInfo3 });
    when(snapshotClient.get(any(), any())).thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    GetBackupStateResponseDto backupState = backupManager.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(FAILED);
  }

  @Test
  public void shouldReturnFailedState2() throws IOException {
    String repoName = "repoName";
    String backupId = "backupId";
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    SnapshotInfo snapshotInfo1 = createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    SnapshotInfo snapshotInfo2 = createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    SnapshotInfo snapshotInfo3 = createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.PARTIAL);
    List<SnapshotInfo> snapshotInfos = Arrays.asList(
        new SnapshotInfo[] { snapshotInfo1, snapshotInfo2, snapshotInfo3 });
    when(snapshotClient.get(any(), any())).thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    GetBackupStateResponseDto backupState = backupManager.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(FAILED);
  }
  @Test
  public void shouldReturnIncompatibleState() throws IOException {
    String repoName = "repoName";
    String backupId = "backupId";
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    SnapshotInfo snapshotInfo1 = createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    SnapshotInfo snapshotInfo2 = createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    SnapshotInfo snapshotInfo3 = createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.INCOMPATIBLE);
    List<SnapshotInfo> snapshotInfos = Arrays.asList(
        new SnapshotInfo[] { snapshotInfo1, snapshotInfo2, snapshotInfo3 });
    when(snapshotClient.get(any(), any())).thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    GetBackupStateResponseDto backupState = backupManager.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(INCOMPATIBLE);
  }

  @Test
  public void shouldReturnIncompleteState() throws IOException {
    String repoName = "repoName";
    String backupId = "backupId";
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    //we have only 2 out of 3 snapshots
    SnapshotInfo snapshotInfo1 = createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    SnapshotInfo snapshotInfo2 = createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    List<SnapshotInfo> snapshotInfos = Arrays.asList(
        new SnapshotInfo[] { snapshotInfo1, snapshotInfo2 });
    when(snapshotClient.get(any(), any())).thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    GetBackupStateResponseDto backupState = backupManager.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(INCOMPLETE);
  }

  @Test
  public void shouldReturnInProgressState1() throws IOException {
    String repoName = "repoName";
    String backupId = "backupId";
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    //we have only 2 out of 3 snapshots
    SnapshotInfo snapshotInfo1 = createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    SnapshotInfo snapshotInfo2 = createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    SnapshotInfo snapshotInfo3 = createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.IN_PROGRESS);
    List<SnapshotInfo> snapshotInfos = Arrays.asList(
        new SnapshotInfo[] { snapshotInfo1, snapshotInfo2, snapshotInfo3 });
    when(snapshotClient.get(any(), any())).thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    GetBackupStateResponseDto backupState = backupManager.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(IN_PROGRESS);
  }

  @Test
  public void shouldReturnInProgressState2() throws IOException {
    String repoName = "repoName";
    String backupId = "backupId";
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    //we have only 2 out of 3 snapshots
    SnapshotInfo snapshotInfo1 = createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.IN_PROGRESS);
    SnapshotInfo snapshotInfo2 = createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.IN_PROGRESS);
    List<SnapshotInfo> snapshotInfos = Arrays.asList(
        new SnapshotInfo[] { snapshotInfo1, snapshotInfo2 });
    when(snapshotClient.get(any(), any())).thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    GetBackupStateResponseDto backupState = backupManager.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(IN_PROGRESS);
  }

  @Test
  public void shouldFailDeleteBackupOnAbsentRepositoryName() {
    when(operateProperties.getBackup()).thenReturn(new BackupProperties());
    Exception exception = assertThrows(InvalidRequestException.class, () -> {
      backupManager.deleteBackup("backupid");
    });
    String expectedMessage = "Cannot trigger backup because no Elasticsearch snapshot repository name found in Operate configuration.";
    String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
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
      backupManager.deleteBackup("backupid");
    });

    String expectedMessage = String.format("Cannot trigger backup because no repository with name [%s] could be found.",
        repoName);
    String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
    verify(esClient, times(1)).snapshot();
  }

  @NotNull private SnapshotInfo createSnapshotInfoMock(String uuid, SnapshotState state) {
    SnapshotInfo snapshotInfo = mock(SnapshotInfo.class);
    when(snapshotInfo.snapshotId()).thenReturn(new SnapshotId("snapshotName", uuid));
    when(snapshotInfo.userMetadata()).thenReturn(asMap("version", "someVersion", "partNo", 1, "partCount", 3));
    when(snapshotInfo.state()).thenReturn(state);
    return snapshotInfo;
  }
}

@Configuration @ComponentScan(basePackages = { "io.camunda.operate.schema.indices",
    "io.camunda.operate.schema.templates", "io.camunda.operate.property" })
@Profile("backend-test")
class TestConfig {

}

