/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp;

import static io.camunda.operate.webapp.elasticsearch.backup.ElasticsearchBackupRepository.SNAPSHOT_MISSING_EXCEPTION_TYPE;
import static io.camunda.operate.webapp.management.dto.BackupStateDto.COMPLETED;
import static io.camunda.operate.webapp.management.dto.BackupStateDto.FAILED;
import static io.camunda.operate.webapp.management.dto.BackupStateDto.INCOMPATIBLE;
import static io.camunda.operate.webapp.management.dto.BackupStateDto.INCOMPLETE;
import static io.camunda.operate.webapp.management.dto.BackupStateDto.IN_PROGRESS;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.exceptions.OperateElasticsearchConnectionException;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.backup.BackupService;
import io.camunda.operate.webapp.backup.Metadata;
import io.camunda.operate.webapp.management.BackupController;
import io.camunda.operate.webapp.management.dto.GetBackupStateResponseDetailDto;
import io.camunda.operate.webapp.management.dto.GetBackupStateResponseDto;
import io.camunda.operate.webapp.management.dto.TakeBackupRequestDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.http.HttpStatus;
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {TestApplication.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      "management.endpoints.web.exposure.include = backups",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER"
    })
@ActiveProfiles({"test", "standalone"})
public class BackupControllerIT {
  @SpyBean private BackupService backupService;

  @Mock private SnapshotClient snapshotClient;

  @Qualifier("esClient")
  @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
  private RestHighLevelClient esClient;

  @SpyBean private OperateProperties operateProperties;

  @Autowired private BackupController backupController;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TestRestTemplate testRestTemplate;

  @LocalManagementPort private int managementPort;

  @Test
  public void shouldReturnNotFoundStatusWhenBackupIdNotFound() throws Exception {
    when(esClient.snapshot()).thenReturn(snapshotClient);
    when(snapshotClient.get(any(), any()))
        .thenThrow(
            new ElasticsearchStatusException(
                "type=snapshot_missing_exception", RestStatus.NOT_FOUND));

    final ResponseEntity<Map> result =
        testRestTemplate.getForEntity(
            "http://localhost:" + managementPort + "/actuator/backups/2", Map.class);

    assertThat(result.getStatusCodeValue()).isEqualTo(HttpStatus.SC_NOT_FOUND);

    final Map<String, String> resultBody = (Map<String, String>) result.getBody();

    assertThat(resultBody.get("message")).isEqualTo("No backup with id [2] found.");
  }

  @Test
  public void shouldFailCreateBackupOnEmptyBackupId() {
    final Exception exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupController.takeBackup(new TakeBackupRequestDto());
            });
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
            () -> {
              backupController.takeBackup(new TakeBackupRequestDto().setBackupId(null));
            });
    assertTrue(exception.getMessage().contains(expectedMessage));

    expectedMessage = "BackupId must be a non-negative Integer. Received value:";

    exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupController.takeBackup(new TakeBackupRequestDto().setBackupId(-1L));
            });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupController.getBackupState(-1L);
            });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupController.deleteBackup(-1L);
            });
    assertTrue(exception.getMessage().contains(expectedMessage));
  }

  @Test
  public void shouldFailNoBackupRepositoryConfigured() {
    when(operateProperties.getBackup()).thenReturn(null);
    final String expectedMessage = "No backup repository configured.";
    final Long backupId = 100L;

    Exception exception =
        assertThrows(
            NotFoundException.class,
            () -> {
              backupController.takeBackup(new TakeBackupRequestDto().setBackupId(backupId));
            });
    String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));

    exception =
        assertThrows(
            NotFoundException.class,
            () -> {
              backupController.getBackupState(backupId);
            });
    actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));

    exception =
        assertThrows(
            NotFoundException.class,
            () -> {
              backupController.deleteBackup(backupId);
            });
    actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));

    exception =
        assertThrows(
            NotFoundException.class,
            () -> {
              backupController.takeBackup(new TakeBackupRequestDto().setBackupId(backupId));
            });
    actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));

    exception =
        assertThrows(
            NotFoundException.class,
            () -> {
              backupController.getBackupState(backupId);
            });
    actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));

    exception =
        assertThrows(
            NotFoundException.class,
            () -> {
              backupController.deleteBackup(backupId);
            });
    actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
  }

  @Test
  public void shouldFailCreateBackupOnNonExistingRepository() throws IOException {
    final String expectedMessage =
        String.format(
            "No repository with name [%s] could be found.",
            operateProperties.getBackup().getRepositoryName());
    final ElasticsearchStatusException elsEx = mock(ElasticsearchStatusException.class);
    when(elsEx.getDetailedMessage()).thenReturn("type=repository_missing_exception");
    when(snapshotClient.getRepository(any(), any())).thenThrow(elsEx);
    when(esClient.snapshot()).thenReturn(snapshotClient);

    Exception exception =
        assertThrows(
            OperateRuntimeException.class,
            () -> {
              backupController.takeBackup(new TakeBackupRequestDto().setBackupId(1L));
            });
    String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));

    exception =
        assertThrows(
            OperateRuntimeException.class,
            () -> {
              backupController.deleteBackup(1L);
            });
    actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));

    verify(esClient, times(2)).snapshot();
  }

  @Test
  public void shouldFailCreateBackupOnBackupIdNotFound() throws IOException {
    final Long backupId = 2L;
    final SnapshotInfo snapshotInfo = mock(SnapshotInfo.class, Answers.RETURNS_DEEP_STUBS);
    when(snapshotInfo.snapshotId()).thenReturn(new SnapshotId("snapshotName", "uuid"));
    final List<SnapshotInfo> snapshotInfos = asList(new SnapshotInfo[] {snapshotInfo});
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final Exception exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupController.takeBackup(new TakeBackupRequestDto().setBackupId(backupId));
            });
    final String expectedMessage =
        String.format("A backup with ID [%s] already exists. Found snapshots:", backupId);
    final String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
    verify(esClient, times(2)).snapshot();
  }

  @Test
  public void shouldFailCreateBackupOn1stRequestFailedWithConnectionError() throws IOException {
    final Long backupId = 2L;
    when(snapshotClient.getRepository(any(), any()))
        .thenThrow(new TransportException("Elastic is not available"));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final Exception exception =
        assertThrows(
            OperateElasticsearchConnectionException.class,
            () -> {
              backupController.takeBackup(new TakeBackupRequestDto().setBackupId(backupId));
            });
    final String expectedMessage =
        String.format(
            "Encountered an error connecting to Elasticsearch while retrieving repository with name [%s].",
            operateProperties.getBackup().getRepositoryName());
    final String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
  }

  @Test
  public void shouldFailCreateBackupOn2ndRequestFailedWithConnectionError() throws IOException {
    final Long backupId = 2L;
    when(snapshotClient.getRepository(any(), any())).thenReturn(null);
    when(snapshotClient.get(any(), any()))
        .thenThrow(new TransportException("Elastic is not available"));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final Exception exception =
        assertThrows(
            OperateElasticsearchConnectionException.class,
            () -> {
              backupController.takeBackup(new TakeBackupRequestDto().setBackupId(backupId));
            });
    final String expectedMessage =
        String.format(
            "Encountered an error connecting to Elasticsearch while searching for duplicate backup. Repository name: [%s].",
            operateProperties.getBackup().getRepositoryName());
    final String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
  }

  @Test
  public void shouldFailGetStateOnNoBackupFound() throws IOException {
    final Long backupId = 2L;
    when(snapshotClient.get(any(), any()))
        .thenThrow(
            new ElasticsearchStatusException(
                SNAPSHOT_MISSING_EXCEPTION_TYPE, RestStatus.NOT_FOUND));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final Exception exception =
        assertThrows(
            ResourceNotFoundException.class,
            () -> {
              backupController.getBackupState(backupId);
            });
    final String expectedMessage = String.format("No backup with id [%s] found.", backupId);
    final String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
    verify(esClient, times(1)).snapshot();
  }

  @Test
  public void shouldFailGetStateOnConnectionError() throws IOException {
    final Long backupId = 2L;
    when(esClient.snapshot()).thenThrow(new TransportException("Elastic is not available"));

    final Exception exception =
        assertThrows(
            OperateElasticsearchConnectionException.class,
            () -> {
              backupController.getBackupState(backupId);
            });
    final String expectedMessage =
        String.format(
            "Encountered an error connecting to Elasticsearch while searching for snapshots. Repository name: [%s].",
            operateProperties.getBackup().getRepositoryName());
    final String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
  }

  @Test
  public void shouldReturnCompletedState() throws IOException {
    final Long backupId = 2L;
    final SnapshotInfo snapshotInfo1 =
        createSnapshotInfoMock(
            backupId, 1, 3, UUID.randomUUID().toString(), SnapshotState.SUCCESS, null);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock(
            backupId, 2, 3, UUID.randomUUID().toString(), SnapshotState.SUCCESS, null);
    final SnapshotInfo snapshotInfo3 =
        createSnapshotInfoMock(
            backupId, 3, 3, UUID.randomUUID().toString(), SnapshotState.SUCCESS, null);
    final List<SnapshotInfo> snapshotInfos =
        asList(new SnapshotInfo[] {snapshotInfo1, snapshotInfo2, snapshotInfo3});
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final GetBackupStateResponseDto backupState = backupController.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(COMPLETED);
    assertThat(backupState.getBackupId()).isEqualTo(backupId);
    assertThat(backupState.getFailureReason()).isNull();

    assertBackupDetails(snapshotInfos, backupState);
  }

  @Test
  public void shouldReturnFailedState1() throws IOException {
    final Long backupId = 2L;
    final SnapshotInfo snapshotInfo1 =
        createSnapshotInfoMock(
            backupId, 1, 3, UUID.randomUUID().toString(), SnapshotState.SUCCESS, null);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock(
            backupId, 2, 3, UUID.randomUUID().toString(), SnapshotState.SUCCESS, null);
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
            backupId, 3, 3, UUID.randomUUID().toString(), SnapshotState.FAILED, shardFailures);
    final List<SnapshotInfo> snapshotInfos =
        asList(new SnapshotInfo[] {snapshotInfo1, snapshotInfo2, snapshotInfo3});
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final GetBackupStateResponseDto backupState = backupController.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(FAILED);
    assertThat(backupState.getBackupId()).isEqualTo(backupId);
    assertThat(backupState.getFailureReason())
        .isEqualTo(
            "There were failures with the following snapshots: camunda_operate_2_8.7.0_part_3_of_3");

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
    final Long backupId = 2L;
    final SnapshotInfo snapshotInfo1 =
        createSnapshotInfoMock(
            backupId, 1, 3, UUID.randomUUID().toString(), SnapshotState.SUCCESS, null);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock(
            backupId, 2, 3, UUID.randomUUID().toString(), SnapshotState.SUCCESS, null);
    final SnapshotInfo snapshotInfo3 =
        createSnapshotInfoMock(
            backupId, 3, 3, UUID.randomUUID().toString(), SnapshotState.PARTIAL, null);
    final List<SnapshotInfo> snapshotInfos =
        asList(new SnapshotInfo[] {snapshotInfo1, snapshotInfo2, snapshotInfo3});
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final GetBackupStateResponseDto backupState = backupController.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(FAILED);
    assertThat(backupState.getBackupId()).isEqualTo(backupId);
    assertThat(backupState.getFailureReason())
        .isEqualTo("Some of the snapshots are partial: camunda_operate_2_8.7.0_part_3_of_3");

    assertBackupDetails(snapshotInfos, backupState);
  }

  @Test
  public void shouldReturnFailedState3WhenMoreSnapshotsThanExpected() throws IOException {
    final Long backupId = 2L;
    final SnapshotInfo snapshotInfo1 =
        createSnapshotInfoMock(
            backupId, 1, 3, UUID.randomUUID().toString(), SnapshotState.SUCCESS, null);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock(
            backupId, 2, 3, UUID.randomUUID().toString(), SnapshotState.SUCCESS, null);
    final SnapshotInfo snapshotInfo3 =
        createSnapshotInfoMock(
            backupId, 3, 3, UUID.randomUUID().toString(), SnapshotState.SUCCESS, null);
    final SnapshotInfo snapshotInfo4 =
        createSnapshotInfoMock(
            backupId, 4, 3, UUID.randomUUID().toString(), SnapshotState.SUCCESS, null);
    final List<SnapshotInfo> snapshotInfos =
        asList(new SnapshotInfo[] {snapshotInfo1, snapshotInfo2, snapshotInfo3, snapshotInfo4});
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final GetBackupStateResponseDto backupState = backupController.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(FAILED);
    assertThat(backupState.getBackupId()).isEqualTo(backupId);
    assertThat(backupState.getFailureReason()).isEqualTo("More snapshots found than expected.");

    assertBackupDetails(snapshotInfos, backupState);
  }

  @Test
  public void shouldReturnIncompatibleState() throws IOException {
    final Long backupId = 2L;
    final SnapshotInfo snapshotInfo1 =
        createSnapshotInfoMock(
            backupId, 1, 3, UUID.randomUUID().toString(), SnapshotState.SUCCESS, null);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock(
            backupId, 2, 3, UUID.randomUUID().toString(), SnapshotState.SUCCESS, null);
    final SnapshotInfo snapshotInfo3 =
        createSnapshotInfoMock(
            backupId, 3, 3, UUID.randomUUID().toString(), SnapshotState.INCOMPATIBLE, null);
    final List<SnapshotInfo> snapshotInfos =
        asList(new SnapshotInfo[] {snapshotInfo1, snapshotInfo2, snapshotInfo3});
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final GetBackupStateResponseDto backupState = backupController.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(INCOMPATIBLE);
    assertThat(backupState.getBackupId()).isEqualTo(backupId);
    assertThat(backupState.getFailureReason()).isNull();

    assertBackupDetails(snapshotInfos, backupState);
  }

  @Test
  public void shouldReturnIncompleteState() throws IOException {
    final Long backupId = 2L;
    // we have only 2 out of 3 snapshots
    final SnapshotInfo snapshotInfo1 =
        createSnapshotInfoMock(
            backupId, 1, 3, UUID.randomUUID().toString(), SnapshotState.SUCCESS, null);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock(
            backupId, 2, 3, UUID.randomUUID().toString(), SnapshotState.SUCCESS, null);
    final List<SnapshotInfo> snapshotInfos =
        asList(new SnapshotInfo[] {snapshotInfo1, snapshotInfo2});
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final GetBackupStateResponseDto backupState = backupController.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(INCOMPLETE);
    assertThat(backupState.getBackupId()).isEqualTo(backupId);
    assertThat(backupState.getFailureReason()).isNull();

    assertBackupDetails(snapshotInfos, backupState);
  }

  @Test
  public void shouldReturnInProgressState1() throws IOException {
    final Long backupId = 2L;
    // we have only 2 out of 3 snapshots
    final SnapshotInfo snapshotInfo1 =
        createSnapshotInfoMock(
            backupId, 1, 3, UUID.randomUUID().toString(), SnapshotState.SUCCESS, null);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock(
            backupId, 2, 3, UUID.randomUUID().toString(), SnapshotState.SUCCESS, null);
    final SnapshotInfo snapshotInfo3 =
        createSnapshotInfoMock(
            backupId, 3, 3, UUID.randomUUID().toString(), SnapshotState.IN_PROGRESS, null);
    final List<SnapshotInfo> snapshotInfos =
        asList(new SnapshotInfo[] {snapshotInfo1, snapshotInfo2, snapshotInfo3});
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final GetBackupStateResponseDto backupState = backupController.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(IN_PROGRESS);
    assertThat(backupState.getBackupId()).isEqualTo(backupId);
    assertThat(backupState.getFailureReason()).isNull();

    assertBackupDetails(snapshotInfos, backupState);
  }

  @Test
  public void shouldReturnInProgressState2() throws IOException {
    final Long backupId = 2L;
    // we have only 2 out of 3 snapshots
    final SnapshotInfo snapshotInfo1 =
        createSnapshotInfoMock(
            backupId, 1, 3, UUID.randomUUID().toString(), SnapshotState.IN_PROGRESS, null);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock(
            backupId, 1, 3, UUID.randomUUID().toString(), SnapshotState.IN_PROGRESS, null);
    final List<SnapshotInfo> snapshotInfos =
        asList(new SnapshotInfo[] {snapshotInfo1, snapshotInfo2});
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final GetBackupStateResponseDto backupState = backupController.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(IN_PROGRESS);
    assertThat(backupState.getBackupId()).isEqualTo(backupId);
    assertThat(backupState.getFailureReason()).isNull();

    assertBackupDetails(snapshotInfos, backupState);
  }

  @Test
  public void shouldFailDeleteBackupOnNonExistingRepository() throws IOException {
    final ElasticsearchStatusException elsEx = mock(ElasticsearchStatusException.class);
    when(elsEx.getDetailedMessage()).thenReturn("type=repository_missing_exception");
    when(snapshotClient.getRepository(any(), any())).thenThrow(elsEx);
    when(esClient.snapshot()).thenReturn(snapshotClient);
    final Exception exception =
        assertThrows(
            OperateRuntimeException.class,
            () -> {
              backupController.deleteBackup(3L);
            });

    final String expectedMessage =
        String.format(
            "No repository with name [%s] could be found.",
            operateProperties.getBackup().getRepositoryName());
    final String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
    verify(esClient, times(1)).snapshot();
  }

  @Test
  public void shouldFailGetBackupOnNonExistingRepository() throws IOException {
    final ElasticsearchStatusException elsEx = mock(ElasticsearchStatusException.class);
    when(elsEx.getDetailedMessage()).thenReturn("type=repository_missing_exception");
    when(esClient.snapshot()).thenThrow(elsEx);
    final Exception exception =
        assertThrows(
            OperateRuntimeException.class,
            () -> {
              backupController.getBackupState(3L);
            });

    final String expectedMessage =
        String.format(
            "No repository with name [%s] could be found.",
            operateProperties.getBackup().getRepositoryName());
    final String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
    verify(esClient, times(1)).snapshot();
  }

  @Test
  public void shouldFailGetBackupsOnNonExistingRepository() throws IOException {
    final ElasticsearchStatusException elsEx = mock(ElasticsearchStatusException.class);
    when(elsEx.getDetailedMessage()).thenReturn("type=repository_missing_exception");
    when(esClient.snapshot()).thenThrow(elsEx);

    final Exception exception =
        assertThrows(
            OperateRuntimeException.class,
            () -> {
              backupController.getBackups(true);
            });

    final String expectedMessage =
        String.format(
            "No repository with name [%s] could be found.",
            operateProperties.getBackup().getRepositoryName());
    final String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
    verify(esClient, times(1)).snapshot();
  }

  @Test
  public void shouldReturnEmptyBackupsOnNoBackupFound() throws IOException {
    when(snapshotClient.get(any(), any()))
        .thenThrow(
            new ElasticsearchStatusException(
                SNAPSHOT_MISSING_EXCEPTION_TYPE, RestStatus.NOT_FOUND));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    assertThat(backupController.getBackups(true)).isEmpty();
  }

  @Test
  public void shouldFailGetBackupsOnConnectionError() throws IOException {
    when(esClient.snapshot()).thenThrow(new TransportException("Elastic is not available"));

    final Exception exception =
        assertThrows(
            OperateElasticsearchConnectionException.class,
            () -> {
              backupController.getBackups(true);
            });
    final String expectedMessage =
        String.format(
            "Encountered an error connecting to Elasticsearch while searching for snapshots. Repository name: [%s].",
            operateProperties.getBackup().getRepositoryName());
    final String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
  }

  @Test
  public void shouldReturnThreeBackups() throws IOException {
    final Long backupId1 = 1L;
    final Long backupId2 = 2L;
    final Long backupId3 = 3L;
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
            new SnapshotInfo[] {
              snapshotInfo11,
              snapshotInfo12,
              snapshotInfo21,
              snapshotInfo22,
              snapshotInfo31,
              snapshotInfo32
            });
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 6, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final List<GetBackupStateResponseDto> backups = backupController.getBackups(true);
    assertThat(backups).hasSize(3);

    final GetBackupStateResponseDto backup3 =
        backups.stream()
            .filter(response -> backupId3.equals(response.getBackupId()))
            .findAny()
            .orElse(null);
    assertThat(backup3).isNotNull();
    assertThat(backup3.getState()).isEqualTo(IN_PROGRESS);
    assertThat(backup3.getBackupId()).isEqualTo(backupId3);
    assertThat(backup3.getFailureReason()).isNull();
    assertBackupDetails(List.of(snapshotInfo31, snapshotInfo32), backup3);

    final GetBackupStateResponseDto backup2 =
        backups.stream()
            .filter(response -> backupId2.equals(response.getBackupId()))
            .findAny()
            .orElse(null);
    assertThat(backup2).isNotNull();
    assertThat(backup2.getState()).isEqualTo(INCOMPLETE);
    assertThat(backup2.getBackupId()).isEqualTo(backupId2);
    assertThat(backup2.getFailureReason()).isNull();
    assertBackupDetails(List.of(snapshotInfo21, snapshotInfo22), backup2);

    final GetBackupStateResponseDto backup1 =
        backups.stream()
            .filter(response -> backupId1.equals(response.getBackupId()))
            .findAny()
            .orElse(null);
    assertThat(backup1).isNotNull();
    assertThat(backup1.getState()).isEqualTo(COMPLETED);
    assertThat(backup1.getBackupId()).isEqualTo(backupId1);
    assertThat(backup1.getFailureReason()).isNull();
    assertBackupDetails(List.of(snapshotInfo11, snapshotInfo12), backup1);
  }

  @Test
  public void shouldReturnVersion81Backup() throws IOException {
    final Long backupId1 = 123L;
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
    final List<SnapshotInfo> snapshotInfos =
        asList(new SnapshotInfo[] {snapshotInfo11, snapshotInfo12});
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 6, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final List<GetBackupStateResponseDto> backups = backupController.getBackups(true);
    assertThat(backups).hasSize(1);
    final GetBackupStateResponseDto backup1 = backups.get(0);
    assertThat(backup1.getState()).isEqualTo(COMPLETED);
    assertThat(backup1.getBackupId()).isEqualTo(backupId1);
    assertThat(backup1.getFailureReason()).isNull();
    assertBackupDetails(List.of(snapshotInfo11, snapshotInfo12), backup1);
  }

  @Test
  public void shouldRespectVerboseFlag() throws IOException {
    final Long backupId = 2L;
    // when using verbose=false, ES/OS will return something like this:
    //    {
    //      "snapshot": "camunda_operate_20250320000001_8.6.9_part_1_of_6",
    //        "uuid": "3V4JXZ5GRE2Yy5VnDKTF5w",
    //        "indices": [
    //      "operate-import-position-8.3.0_"
    //        ],
    //      "data_streams": [],
    //      "state": "SUCCESS"
    //    }

    final Metadata metadata =
        new Metadata().setBackupId(backupId).setVersion("8.8.8").setPartNo(1).setPartCount(1);

    final SnapshotInfo snapshotInfo = mock(SnapshotInfo.class, Answers.RETURNS_DEEP_STUBS);
    when(snapshotInfo.snapshotId())
        .thenReturn(new SnapshotId(metadata.buildSnapshotName(), "snapshot-uuid"));
    when(snapshotInfo.state()).thenReturn(SnapshotState.SUCCESS);
    final List<SnapshotInfo> snapshotInfos = asList(new SnapshotInfo[] {snapshotInfo});
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final var backups = backupController.getBackups(false);
    assertThat(backups)
        .allSatisfy(
            backupState -> {
              assertThat(backupState.getState()).isEqualTo(COMPLETED);
              assertThat(backupState.getBackupId()).isEqualTo(backupId);
              assertThat(backupState.getDetails())
                  .singleElement()
                  .satisfies(
                      info -> {
                        System.out.println(info);
                        assertThat(info.getState()).isEqualTo("SUCCESS");
                      });
            });
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
            snapshotInfos.stream().map(si -> si.startTime()).toArray(Long[]::new));
  }

  private SnapshotInfo createSnapshotInfoMock(
      final long backupId,
      final int part,
      final int count,
      final String uuid,
      final SnapshotState state,
      final List<SnapshotShardFailure> failures) {
    final var metadata =
        new Metadata()
            .setPartNo(part)
            .setPartCount(count)
            .setVersion("8.7.0")
            .setBackupId(backupId);
    return createSnapshotInfoMock(metadata, uuid, state, failures);
  }

  private SnapshotInfo createSnapshotInfoMock(
      final Metadata metadata, final String uuid, final SnapshotState state) {
    return createSnapshotInfoMock(metadata, uuid, state, null);
  }

  @NotNull
  private SnapshotInfo createSnapshotInfoMock(
      final Metadata metadata,
      final String uuid,
      final SnapshotState state,
      final List<SnapshotShardFailure> failures) {
    final SnapshotInfo snapshotInfo = mock(SnapshotInfo.class, Answers.RETURNS_DEEP_STUBS);
    when(snapshotInfo.snapshotId()).thenReturn(new SnapshotId(metadata.buildSnapshotName(), uuid));
    when(snapshotInfo.userMetadata())
        .thenReturn(objectMapper.convertValue(metadata, new TypeReference<>() {}));
    when(snapshotInfo.state()).thenReturn(state);
    when(snapshotInfo.shardFailures()).thenReturn(failures);
    when(snapshotInfo.startTime()).thenReturn(OffsetDateTime.now().toInstant().toEpochMilli());
    return snapshotInfo;
  }
}
