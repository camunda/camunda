/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp;

import static io.camunda.tasklist.util.CollectionUtil.asMap;
import static io.camunda.tasklist.webapp.es.backup.BackupManager.SNAPSHOT_MISSING_EXCEPTION_TYPE;
import static io.camunda.tasklist.webapp.management.dto.BackupStateDto.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.camunda.tasklist.JacksonConfig;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.BackupProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.es.backup.BackupManager;
import io.camunda.tasklist.webapp.management.BackupService;
import io.camunda.tasklist.webapp.management.dto.GetBackupStateResponseDto;
import io.camunda.tasklist.webapp.management.dto.TakeBackupRequestDto;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
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

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {TestConfig.class, JacksonConfig.class})
@ActiveProfiles({"test", "backend-test"})
public class BackupServiceTest {

  @SpyBean private BackupManager backupManager;

  @Mock private SnapshotClient snapshotClient;

  @MockBean
  @Qualifier("esClient")
  private RestHighLevelClient esClient;

  @SpyBean private TasklistProperties tasklistProperties;

  @InjectMocks private BackupService backupService;

  @Test
  public void shouldFailCreateBackupOnEmptyBackupId() {
    final Exception exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupService.takeBackup(new TakeBackupRequestDto());
            });
    final String expectedMessage = "BackupId must be provided";
    final String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
  }

  @Test
  public void shouldFailCreateBackupOnWrongBackupId() {
    final String expectedMessage =
        "BackupId must not contain any uppercase letters or any of [ , \", *, \\, <, |, ,, >, /, ?].";

    Exception exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupService.takeBackup(new TakeBackupRequestDto().setBackupId("UPPERCASEID"));
            });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith "));
            });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith\""));
            });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith*"));
            });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith\\"));
            });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith<"));
            });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith|"));
            });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith,"));
            });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith>"));
            });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith/"));
            });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith?"));
            });
    assertTrue(exception.getMessage().contains(expectedMessage));
  }

  @Test
  public void shouldFailCreateBackupOnAbsentRepositoryName() {
    when(tasklistProperties.getBackup()).thenReturn(new BackupProperties());
    final Exception exception =
        assertThrows(
            TasklistRuntimeException.class,
            () -> {
              backupManager.takeBackup(new TakeBackupRequestDto().setBackupId("backupid"));
            });
    final String expectedMessage =
        "Cannot trigger backup because no Elasticsearch snapshot repository name found in Tasklist configuration.";
    final String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
  }

  @Test
  public void shouldFailCreateBackupOnNonExistingRepository() throws IOException {
    final String repoName = "repoName";
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    final ElasticsearchStatusException elsEx = mock(ElasticsearchStatusException.class);
    when(elsEx.getDetailedMessage()).thenReturn("type=repository_missing_exception");
    when(snapshotClient.getRepository(any(), any())).thenThrow(elsEx);
    when(esClient.snapshot()).thenReturn(snapshotClient);
    final Exception exception =
        assertThrows(
            TasklistRuntimeException.class,
            () -> {
              backupManager.takeBackup(new TakeBackupRequestDto().setBackupId("backupid"));
            });

    final String expectedMessage =
        String.format(
            "Cannot trigger backup because no repository with name [%s] could be found.", repoName);
    final String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
    verify(esClient, times(1)).snapshot();
  }

  @Test
  public void shouldFailCreateBackupOnBackupIdAlreadyExists() throws IOException {
    final String repoName = "repoName";
    final String backupid = "backupid";
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    final SnapshotInfo snapshotInfo = mock(SnapshotInfo.class);
    when(snapshotInfo.snapshotId()).thenReturn(new SnapshotId("snapshotName", "uuid"));
    final List<SnapshotInfo> snapshotInfos = Arrays.asList(new SnapshotInfo[] {snapshotInfo});
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final Exception exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupManager.takeBackup(new TakeBackupRequestDto().setBackupId(backupid));
            });
    final String expectedMessage =
        String.format("A backup with ID [%s] already exists. Found snapshots:", backupid);
    final String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
    verify(esClient, times(2)).snapshot();
  }

  @Test
  public void shouldFailGetStateOnBackupIdNotFound() throws IOException {
    final String repoName = "repoName";
    final String backupId = "backupId";
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    when(snapshotClient.get(any(), any()))
        .thenThrow(
            new ElasticsearchStatusException(
                SNAPSHOT_MISSING_EXCEPTION_TYPE, RestStatus.NOT_FOUND));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final Exception exception =
        assertThrows(
            NotFoundException.class,
            () -> {
              backupManager.getBackupState(backupId);
            });
    final String expectedMessage = String.format("No backup with id [%s] found.", backupId);
    final String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
    verify(esClient, times(1)).snapshot();
  }

  @Test
  public void shouldReturnCompletedState() throws IOException {
    final String repoName = "repoName";
    final String backupId = "backupId";
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    final SnapshotInfo snapshotInfo1 =
        createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo3 =
        createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final List<SnapshotInfo> snapshotInfos =
        Arrays.asList(new SnapshotInfo[] {snapshotInfo1, snapshotInfo2, snapshotInfo3});
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final GetBackupStateResponseDto backupState = backupManager.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(COMPLETED);
  }

  @Test
  public void shouldReturnFailedState1() throws IOException {
    final String repoName = "repoName";
    final String backupId = "backupId";
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    final SnapshotInfo snapshotInfo1 =
        createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo3 =
        createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.FAILED);
    final List<SnapshotInfo> snapshotInfos =
        Arrays.asList(new SnapshotInfo[] {snapshotInfo1, snapshotInfo2, snapshotInfo3});
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final GetBackupStateResponseDto backupState = backupManager.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(FAILED);
  }

  @Test
  public void shouldReturnFailedState2() throws IOException {
    final String repoName = "repoName";
    final String backupId = "backupId";
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    final SnapshotInfo snapshotInfo1 =
        createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo3 =
        createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.PARTIAL);
    final List<SnapshotInfo> snapshotInfos =
        Arrays.asList(new SnapshotInfo[] {snapshotInfo1, snapshotInfo2, snapshotInfo3});
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final GetBackupStateResponseDto backupState = backupManager.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(FAILED);
  }

  @Test
  public void shouldReturnIncompatibleState() throws IOException {
    final String repoName = "repoName";
    final String backupId = "backupId";
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    final SnapshotInfo snapshotInfo1 =
        createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo3 =
        createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.INCOMPATIBLE);
    final List<SnapshotInfo> snapshotInfos =
        Arrays.asList(new SnapshotInfo[] {snapshotInfo1, snapshotInfo2, snapshotInfo3});
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final GetBackupStateResponseDto backupState = backupManager.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(INCOMPATIBLE);
  }

  @Test
  public void shouldReturnIncompleteState() throws IOException {
    final String repoName = "repoName";
    final String backupId = "backupId";
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    // we have only 2 out of 3 snapshots
    final SnapshotInfo snapshotInfo1 =
        createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final List<SnapshotInfo> snapshotInfos =
        Arrays.asList(new SnapshotInfo[] {snapshotInfo1, snapshotInfo2});
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final GetBackupStateResponseDto backupState = backupManager.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(INCOMPLETE);
  }

  @Test
  public void shouldReturnInProgressState1() throws IOException {
    final String repoName = "repoName";
    final String backupId = "backupId";
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    // we have only 2 out of 3 snapshots
    final SnapshotInfo snapshotInfo1 =
        createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo3 =
        createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.IN_PROGRESS);
    final List<SnapshotInfo> snapshotInfos =
        Arrays.asList(new SnapshotInfo[] {snapshotInfo1, snapshotInfo2, snapshotInfo3});
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final GetBackupStateResponseDto backupState = backupManager.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(IN_PROGRESS);
  }

  @Test
  public void shouldReturnInProgressState2() throws IOException {
    final String repoName = "repoName";
    final String backupId = "backupId";
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    // we have only 2 out of 3 snapshots
    final SnapshotInfo snapshotInfo1 =
        createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.IN_PROGRESS);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock(UUID.randomUUID().toString(), SnapshotState.IN_PROGRESS);
    final List<SnapshotInfo> snapshotInfos =
        Arrays.asList(new SnapshotInfo[] {snapshotInfo1, snapshotInfo2});
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final GetBackupStateResponseDto backupState = backupManager.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(IN_PROGRESS);
  }

  @NotNull
  private SnapshotInfo createSnapshotInfoMock(String uuid, SnapshotState state) {
    final SnapshotInfo snapshotInfo = mock(SnapshotInfo.class);
    when(snapshotInfo.snapshotId()).thenReturn(new SnapshotId("snapshotName", uuid));
    when(snapshotInfo.userMetadata())
        .thenReturn(asMap("version", "someVersion", "partNo", 1, "partCount", 3));
    when(snapshotInfo.state()).thenReturn(state);
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
