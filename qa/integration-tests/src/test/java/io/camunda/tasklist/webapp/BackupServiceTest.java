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
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.JacksonConfig;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.BackupProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.es.backup.BackupManager;
import io.camunda.tasklist.webapp.management.BackupService;
import io.camunda.tasklist.webapp.management.dto.GetBackupStateResponseDetailDto;
import io.camunda.tasklist.webapp.management.dto.GetBackupStateResponseDto;
import io.camunda.tasklist.webapp.management.dto.TakeBackupRequestDto;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundException;
import io.camunda.tasklist.webapp.security.TasklistProfileService;
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

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
      TestConfig.class,
      JacksonConfig.class,
      BackupService.class,
      TasklistProfileService.class
    })
@ActiveProfiles({"test", "backend-test"})
public class BackupServiceTest {

  @SpyBean private BackupManager backupManager;

  @Mock private SnapshotClient snapshotClient;

  @MockBean
  @Qualifier("esClient")
  private RestHighLevelClient esClient;

  @SpyBean private TasklistProperties tasklistProperties;

  @Autowired private BackupService backupService;

  @Autowired private ObjectMapper objectMapper;

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
        "BackupId must not contain any uppercase letters or any of [ , \", *, \\, <, |, ,, >, /, ?, _].";

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

    exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith_"));
            });
    assertTrue(exception.getMessage().contains(expectedMessage));
  }

  @Test
  public void shouldFailNoBackupRepositoryConfigured() {
    when(tasklistProperties.getBackup()).thenReturn(null);
    final String expectedMessage = "No backup repository configured.";

    Exception exception =
        assertThrows(
            NotFoundException.class,
            () -> {
              backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupid"));
            });
    String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));

    exception =
        assertThrows(
            NotFoundException.class,
            () -> {
              backupService.getBackupState("backupid");
            });
    actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));

    exception =
        assertThrows(
            NotFoundException.class,
            () -> {
              backupService.deleteBackup("backupid");
            });
    actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));

    when(tasklistProperties.getBackup()).thenReturn(new BackupProperties());
    exception =
        assertThrows(
            NotFoundException.class,
            () -> {
              backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupid"));
            });
    actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));

    exception =
        assertThrows(
            NotFoundException.class,
            () -> {
              backupService.getBackupState("backupid");
            });
    actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));

    exception =
        assertThrows(
            NotFoundException.class,
            () -> {
              backupService.deleteBackup("backupid");
            });
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
            () -> {
              backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupid"));
            });

    String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));

    exception =
        assertThrows(
            TasklistRuntimeException.class,
            () -> {
              backupService.deleteBackup("backupid");
            });
    actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));

    verify(esClient, times(2)).snapshot();
  }

  @Test
  public void shouldFailCreateBackupOnBackupIdAlreadyExists() throws IOException {
    final String repoName = "repoName";
    final String backupid = "backupid";
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
            () -> {
              backupService.takeBackup(new TakeBackupRequestDto().setBackupId(backupid));
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
              backupService.getBackupState(backupId);
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
        createSnapshotInfoMock(
            "snapshotName1", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock(
            "snapshotName2", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo3 =
        createSnapshotInfoMock(
            "snapshotName3", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final List<SnapshotInfo> snapshotInfos =
        asList(new SnapshotInfo[] {snapshotInfo1, snapshotInfo2, snapshotInfo3});
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
    final String backupId = "backupId";
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
    final List<SnapshotInfo> snapshotInfos =
        asList(new SnapshotInfo[] {snapshotInfo1, snapshotInfo2, snapshotInfo3});
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
                .map(si -> si.toString())
                .toArray(String[]::new));
  }

  @Test
  public void shouldReturnFailedState2() throws IOException {
    final String repoName = "repoName";
    final String backupId = "backupId";
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
    final List<SnapshotInfo> snapshotInfos =
        asList(new SnapshotInfo[] {snapshotInfo1, snapshotInfo2, snapshotInfo3});
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
    final String backupId = "backupId";
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
        asList(new SnapshotInfo[] {snapshotInfo1, snapshotInfo2, snapshotInfo3, snapshotInfo4});
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
    final String backupId = "backupId";
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
    final List<SnapshotInfo> snapshotInfos =
        asList(new SnapshotInfo[] {snapshotInfo1, snapshotInfo2, snapshotInfo3});
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
    final String backupId = "backupId";
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    // we have only 2 out of 3 snapshots
    final SnapshotInfo snapshotInfo1 =
        createSnapshotInfoMock(
            "snapshotName1", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock(
            "snapshotName2", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final List<SnapshotInfo> snapshotInfos =
        asList(new SnapshotInfo[] {snapshotInfo1, snapshotInfo2});
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
    final String backupId = "backupId";
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
    final List<SnapshotInfo> snapshotInfos =
        asList(new SnapshotInfo[] {snapshotInfo1, snapshotInfo2, snapshotInfo3});
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
    final String backupId = "backupId";
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    // we have only 2 out of 3 snapshots
    final SnapshotInfo snapshotInfo1 =
        createSnapshotInfoMock(
            "snapshotName1", UUID.randomUUID().toString(), SnapshotState.IN_PROGRESS);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock(
            "snapshotName2", UUID.randomUUID().toString(), SnapshotState.IN_PROGRESS);
    final List<SnapshotInfo> snapshotInfos =
        asList(new SnapshotInfo[] {snapshotInfo1, snapshotInfo2});
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
        assertThrows(
            TasklistRuntimeException.class,
            () -> {
              backupService.deleteBackup("backupid");
            });

    final String expectedMessage =
        String.format("No repository with name [%s] could be found.", repoName);
    final String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
    verify(esClient, times(1)).snapshot();
  }

  private void assertBackupDetails(
      List<SnapshotInfo> snapshotInfos, GetBackupStateResponseDto backupState) {
    assertThat(backupState.getDetails()).hasSize(snapshotInfos.size());
    assertThat(backupState.getDetails())
        .extracting(GetBackupStateResponseDetailDto::getSnapshotName)
        .containsExactly(
            snapshotInfos.stream().map(si -> si.snapshotId().getName()).toArray(String[]::new));
    assertThat(backupState.getDetails())
        .extracting(GetBackupStateResponseDetailDto::getState)
        .containsExactly(
            snapshotInfos.stream().map(si -> si.state().name()).toArray(String[]::new));
    assertThat(backupState.getDetails())
        .extracting(d -> d.getStartTime().toInstant().toEpochMilli())
        .containsExactly(snapshotInfos.stream().map(si -> si.startTime()).toArray(Long[]::new));
  }

  private SnapshotInfo createSnapshotInfoMock(String name, String uuid, SnapshotState state) {
    return createSnapshotInfoMock(name, uuid, state, null);
  }

  @NotNull
  private SnapshotInfo createSnapshotInfoMock(
      String name, String uuid, SnapshotState state, List<SnapshotShardFailure> failures) {
    final SnapshotInfo snapshotInfo = mock(SnapshotInfo.class);
    when(snapshotInfo.snapshotId()).thenReturn(new SnapshotId(name, uuid));
    when(snapshotInfo.userMetadata())
        .thenReturn(asMap("version", "someVersion", "partNo", 1, "partCount", 3));
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
