/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp;

import static io.camunda.application.commons.backup.ElasticsearchBackupRepository.SNAPSHOT_MISSING_EXCEPTION_TYPE;
import static io.camunda.management.backups.HistoryStateCode.*;
import static io.camunda.operate.util.CollectionUtil.asMap;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.management.backups.Error;
import io.camunda.management.backups.HistoryBackupDetail;
import io.camunda.management.backups.HistoryBackupInfo;
import io.camunda.management.backups.HistoryStateCode;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.webapp.backup.OperateSnapshotNameProvider;
import io.camunda.webapps.backup.Metadata;
import io.camunda.webapps.controllers.BackupController;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
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
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

/* This class should be moved to the dist/ module as it does not really test anything operate specific
anymore, it will be done in a follow-up PR
*/
@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {TestApplication.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      "management.endpoints.web.exposure.include = backup-history",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER"
    })
@ActiveProfiles({"test", "operate", "standalone"})
public class BackupControllerIT {
  @Mock private SnapshotClient snapshotClient;

  @MockBean
  @Qualifier("esClient")
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
            "http://localhost:" + managementPort + "/actuator/backup-history/2", Map.class);

    assertThat(result.getStatusCode().value()).isEqualTo(HttpStatus.SC_NOT_FOUND);

    final Map<String, String> resultBody = (Map<String, String>) result.getBody();
    assertThat(resultBody.get("message")).isEqualTo("No backup with id [2] found.");
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

    assertReturnsError(() -> backupController.takeBackup(1L), 404, expectedMessage);

    assertReturnsError(() -> backupController.takeBackup(1L), 404, expectedMessage);

    verify(esClient, times(2)).snapshot();
  }

  @Test
  public void shouldFailCreateBackupOnBackupIdNotFound() throws IOException {
    final long backupId = 2L;
    final SnapshotInfo snapshotInfo = mock(SnapshotInfo.class);
    when(snapshotInfo.snapshotId()).thenReturn(new SnapshotId("snapshotName", "uuid"));
    final List<SnapshotInfo> snapshotInfos = asList(new SnapshotInfo[] {snapshotInfo});
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final String expectedMessage =
        String.format("A backup with ID [%s] already exists. Found snapshots:", backupId);
    assertReturnsError(() -> backupController.takeBackup(backupId), 400, expectedMessage);
    verify(esClient, times(2)).snapshot();
  }

  @Test
  public void shouldFailCreateBackupOn1stRequestFailedWithConnectionError() throws IOException {
    final long backupId = 2L;
    when(snapshotClient.getRepository(any(), any()))
        .thenThrow(new TransportException("Elastic is not available"));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final String expectedMessage =
        String.format(
            "Encountered an error connecting to Elasticsearch while retrieving repository with name [%s].",
            operateProperties.getBackup().getRepositoryName());
    assertReturnsError(() -> backupController.takeBackup(backupId), 502, expectedMessage);
  }

  @Test
  public void shouldFailCreateBackupOn2ndRequestFailedWithConnectionError() throws IOException {
    final long backupId = 2L;
    when(snapshotClient.getRepository(any(), any())).thenReturn(null);
    when(snapshotClient.get(any(), any()))
        .thenThrow(new TransportException("Elastic is not available"));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final String expectedMessage =
        String.format(
            "Encountered an error connecting to Elasticsearch while searching for duplicate backup. Repository name: [%s].",
            operateProperties.getBackup().getRepositoryName());
    assertReturnsError(() -> backupController.takeBackup(backupId), 502, expectedMessage);
  }

  @Test
  public void shouldFailGetStateOnNoBackupFound() throws IOException {
    final long backupId = 2L;
    when(snapshotClient.get(any(), any()))
        .thenThrow(
            new ElasticsearchStatusException(
                SNAPSHOT_MISSING_EXCEPTION_TYPE, RestStatus.NOT_FOUND));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final String expectedMessage = String.format("No backup with id [%s] found.", backupId);
    assertReturnsError(() -> backupController.getBackupState(backupId), 404, expectedMessage);
    verify(esClient, times(1)).snapshot();
  }

  @Test
  public void shouldFailGetStateOnConnectionError() throws IOException {
    final long backupId = 2L;
    when(esClient.snapshot()).thenThrow(new TransportException("Elastic is not available"));

    final String expectedMessage =
        String.format(
            "Encountered an error connecting to Elasticsearch while searching for snapshots. Repository name: [%s].",
            operateProperties.getBackup().getRepositoryName());
    assertReturnsError(() -> backupController.getBackupState(backupId), 502, expectedMessage);
  }

  @Test
  public void shouldReturnCompletedState() throws IOException {
    final long backupId = 2L;
    final var snapshotInfo1 =
        createSnapshotInfoMock(
            "snapshotName1", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final var snapshotInfo2 =
        createSnapshotInfoMock(
            "snapshotName2", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final var snapshotInfo3 =
        createSnapshotInfoMock(
            "snapshotName3", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final List<SnapshotInfo> snapshotInfos = asList(snapshotInfo1, snapshotInfo2, snapshotInfo3);
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final var response = backupController.getBackupState(backupId);
    final var backupState = (HistoryBackupInfo) response.getBody();
    assertThat(backupState.getState()).isEqualTo(COMPLETED);
    assertThat(backupState.getBackupId()).isEqualTo(new BigDecimal(backupId));
    assertThat(backupState.getFailureReason()).isNull();

    assertBackupDetails(snapshotInfos, backupState);
  }

  @Test
  public void shouldReturnFailedState1() throws IOException {
    final long backupId = 2L;
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

    final var backupState = (HistoryBackupInfo) backupController.getBackupState(backupId).getBody();
    assertThat(backupState.getState()).isEqualTo(FAILED);
    assertThat(backupState.getBackupId()).isEqualTo(new BigDecimal(backupId));
    assertThat(backupState.getFailureReason())
        .isEqualTo("There were failures with the following snapshots: snapshotName3");

    assertBackupDetails(snapshotInfos, backupState);

    assertThat(backupState.getDetails())
        .extracting(d -> d.getFailures())
        .containsExactly(
            null,
            null,
            snapshotInfos.get(2).shardFailures().stream().map(si -> si.toString()).toList());
  }

  @Test
  public void shouldReturnFailedState2() throws IOException {
    final long backupId = 2L;
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

    final var backupState = (HistoryBackupInfo) backupController.getBackupState(backupId).getBody();
    assertThat(backupState.getState()).isEqualTo(FAILED);
    assertThat(backupState.getBackupId()).isEqualTo(new BigDecimal(backupId));
    assertThat(backupState.getFailureReason())
        .isEqualTo("Some of the snapshots are partial: snapshotName3");

    assertBackupDetails(snapshotInfos, backupState);
  }

  @Test
  public void shouldReturnFailedState3WhenMoreSnapshotsThanExpected() throws IOException {
    final Long backupId = 2L;
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

    final var backupState = (HistoryBackupInfo) backupController.getBackupState(backupId).getBody();
    assertThat(backupState.getState()).isEqualTo(FAILED);
    assertThat(backupState.getBackupId()).isEqualTo(new BigDecimal(backupId));
    assertThat(backupState.getFailureReason()).isEqualTo("More snapshots found than expected.");

    assertBackupDetails(snapshotInfos, backupState);
  }

  @Test
  public void shouldReturnIncompatibleState() throws IOException {
    final long backupId = 2L;
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

    final var backupState = (HistoryBackupInfo) backupController.getBackupState(backupId).getBody();
    assertThat(backupState.getState()).isEqualTo(INCOMPATIBLE);
    assertThat(backupState.getBackupId()).isEqualTo(new BigDecimal(backupId));
    assertThat(backupState.getFailureReason()).isNull();

    assertBackupDetails(snapshotInfos, backupState);
  }

  @Test
  public void shouldReturnIncompleteState() throws IOException {
    final long backupId = 2L;
    // we have only 2 out of 3 snapshots + timeout
    final SnapshotInfo snapshotInfo1 =
        createSnapshotInfoMock(
            "snapshotName1", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    when(snapshotInfo1.startTime()).thenReturn(0L);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock(
            "snapshotName2", UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final List<SnapshotInfo> snapshotInfos =
        asList(new SnapshotInfo[] {snapshotInfo1, snapshotInfo2});
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final var backupState = (HistoryBackupInfo) backupController.getBackupState(backupId).getBody();
    assertThat(backupState.getState()).isEqualTo(HistoryStateCode.INCOMPLETE);
    assertThat(backupState.getBackupId()).isEqualTo(new BigDecimal(backupId));
    assertThat(backupState.getFailureReason()).isNull();

    assertBackupDetails(snapshotInfos, backupState);
  }

  @Test
  public void shouldReturnInProgressState1() throws IOException {
    final long backupId = 2L;
    // we have only 2 out of 3 snapshots
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

    final var backupState = (HistoryBackupInfo) backupController.getBackupState(backupId).getBody();
    assertThat(backupState.getState()).isEqualTo(IN_PROGRESS);
    assertThat(backupState.getBackupId()).isEqualTo(new BigDecimal(backupId));
    assertThat(backupState.getFailureReason()).isNull();

    assertBackupDetails(snapshotInfos, backupState);
  }

  @Test
  public void shouldReturnInProgressState2() throws IOException {
    final Long backupId = 2L;
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

    final HistoryBackupInfo backupState =
        (HistoryBackupInfo) backupController.getBackupState(backupId).getBody();
    assertThat(backupState.getState()).isEqualTo(IN_PROGRESS);
    assertThat(backupState.getBackupId()).isEqualTo(new BigDecimal(backupId));
    assertThat(backupState.getFailureReason()).isNull();

    assertBackupDetails(snapshotInfos, backupState);
  }

  @Test
  public void shouldFailDeleteBackupOnNonExistingRepository() throws IOException {
    final ElasticsearchStatusException elsEx = mock(ElasticsearchStatusException.class);
    when(elsEx.getDetailedMessage()).thenReturn("type=repository_missing_exception");
    when(snapshotClient.getRepository(any(), any())).thenThrow(elsEx);
    when(esClient.snapshot()).thenReturn(snapshotClient);
    final var webResponse = backupController.deleteBackup(3L);
    assertThat(webResponse.getStatus()).isEqualTo(404);

    final var error = (Error) webResponse.getBody();

    final String expectedMessage =
        String.format(
            "No repository with name [%s] could be found.",
            operateProperties.getBackup().getRepositoryName());
    final String actualMessage = error.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
    verify(esClient, times(1)).snapshot();
  }

  @Test
  public void shouldFailGetBackupOnNonExistingRepository() throws IOException {
    final ElasticsearchStatusException elsEx = mock(ElasticsearchStatusException.class);
    when(elsEx.getDetailedMessage()).thenReturn("type=repository_missing_exception");
    when(esClient.snapshot()).thenThrow(elsEx);
    final var webResponse = backupController.getBackupState(3L);
    assertThat(webResponse.getStatus()).isEqualTo(WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR);
    final var error = (Error) webResponse.getBody();

    final String expectedMessage =
        String.format(
            "No repository with name [%s] could be found.",
            operateProperties.getBackup().getRepositoryName());
    final String actualMessage = error.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
    verify(esClient, times(1)).snapshot();
  }

  @Test
  public void shouldFailGetBackupsOnNonExistingRepository() throws IOException {
    final ElasticsearchStatusException elsEx = mock(ElasticsearchStatusException.class);
    when(elsEx.getDetailedMessage()).thenReturn("type=repository_missing_exception");
    when(esClient.snapshot()).thenThrow(elsEx);

    final String expectedMessage =
        String.format(
            "No repository with name [%s] could be found.",
            operateProperties.getBackup().getRepositoryName());
    assertReturnsError(() -> backupController.getBackups(), 404, expectedMessage);

    verify(esClient, times(1)).snapshot();
  }

  @Test
  public void shouldReturnEmptyBackupsOnNoBackupFound() throws IOException {
    when(snapshotClient.get(any(), any()))
        .thenThrow(
            new ElasticsearchStatusException(
                SNAPSHOT_MISSING_EXCEPTION_TYPE, RestStatus.NOT_FOUND));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    assertThat((List<HistoryBackupInfo>) backupController.getBackups().getBody()).isEmpty();
  }

  @Test
  public void shouldFailGetBackupsOnConnectionError() throws IOException {
    when(esClient.snapshot()).thenThrow(new TransportException("Elastic is not available"));

    final String expectedMessage =
        String.format(
            "Encountered an error connecting to Elasticsearch while searching for snapshots. Repository name: [%s].",
            operateProperties.getBackup().getRepositoryName());
    assertReturnsError(() -> backupController.getBackups(), 502, expectedMessage);
  }

  @Test
  public void shouldReturnThreeBackups() throws IOException {
    final long backupId1 = 1L;
    final long backupId2 = 2L;
    final long backupId3 = 3L;
    // COMPLETED
    final SnapshotInfo snapshotInfo11 =
        createSnapshotInfoMock(
            new Metadata(backupId1, "8.8.8", 1, 2),
            UUID.randomUUID().toString(),
            SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo12 =
        createSnapshotInfoMock(
            new Metadata(backupId1, "8.8.8", 2, 2),
            UUID.randomUUID().toString(),
            SnapshotState.SUCCESS);
    // we have only 2 out of 3 snapshots + TIMEOUT -> INCOMPLETE
    final SnapshotInfo snapshotInfo21 =
        createSnapshotInfoMock(
            new Metadata(backupId2, "8.8.8", 1, 3),
            UUID.randomUUID().toString(),
            SnapshotState.SUCCESS);
    when(snapshotInfo21.startTime()).thenReturn(0L);
    final SnapshotInfo snapshotInfo22 =
        createSnapshotInfoMock(
            new Metadata(backupId2, "8.8.8", 2, 3),
            UUID.randomUUID().toString(),
            SnapshotState.SUCCESS);
    when(snapshotInfo22.startTime()).thenReturn(0L);
    // IN_PROGRESS
    final SnapshotInfo snapshotInfo31 =
        createSnapshotInfoMock(
            new Metadata(backupId3, "8.8.8", 1, 3),
            UUID.randomUUID().toString(),
            SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo32 =
        createSnapshotInfoMock(
            new Metadata(backupId3, "8.8.8", 2, 3),
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

    final var backups = (List<HistoryBackupInfo>) backupController.getBackups().getBody();
    assertThat(backups).hasSize(3);

    final var backup3 =
        backups.stream()
            .filter(response -> backupId3 == response.getBackupId().longValue())
            .findAny()
            .orElse(null);
    assertThat(backup3).isNotNull();
    assertThat(backup3.getState()).isEqualTo(IN_PROGRESS);
    assertThat(backup3.getBackupId()).isEqualTo(new BigDecimal(backupId3));
    assertThat(backup3.getFailureReason()).isNull();
    assertBackupDetails(List.of(snapshotInfo31, snapshotInfo32), backup3);

    final var backup2 =
        backups.stream()
            .filter(response -> backupId2 == response.getBackupId().longValue())
            .findAny()
            .orElse(null);
    assertThat(backup2).isNotNull();
    assertThat(backup2.getState()).isEqualTo(INCOMPLETE);
    assertThat(backup2.getBackupId()).isEqualTo(new BigDecimal(backupId2));
    assertThat(backup2.getFailureReason()).isNull();
    assertBackupDetails(List.of(snapshotInfo21, snapshotInfo22), backup2);

    final var backup1 =
        backups.stream()
            .filter(response -> backupId1 == response.getBackupId().longValue())
            .findAny()
            .orElse(null);
    assertThat(backup1).isNotNull();
    assertThat(backup1.getState()).isEqualTo(COMPLETED);
    assertThat(backup1.getBackupId()).isEqualTo(new BigDecimal(backupId1));
    assertThat(backup1.getFailureReason()).isNull();
    assertBackupDetails(List.of(snapshotInfo11, snapshotInfo12), backup1);
  }

  @Test
  public void shouldReturnVersion81Backup() throws IOException {
    final Long backupId1 = 123L;
    // COMPLETED
    final Metadata metadata1 = new Metadata(backupId1, "8.8.8", 1, 2);
    final SnapshotInfo snapshotInfo11 =
        createSnapshotInfoMock(metadata1, UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    // remove backupId from metadata
    final Metadata metadata1WithNoId = new Metadata(null, "8.8.8", 1, 2);
    when(snapshotInfo11.userMetadata())
        .thenReturn(objectMapper.convertValue(metadata1WithNoId, new TypeReference<>() {}));

    final Metadata metadata2 = new Metadata(backupId1, "8.8.8", 2, 2);
    final SnapshotInfo snapshotInfo12 =
        createSnapshotInfoMock(metadata2, UUID.randomUUID().toString(), SnapshotState.SUCCESS);
    final Metadata metadata2WithNoId = new Metadata(backupId1, "8.8.8", 2, 2);
    when(snapshotInfo12.userMetadata())
        .thenReturn(objectMapper.convertValue(metadata2WithNoId, new TypeReference<>() {}));
    final List<SnapshotInfo> snapshotInfos =
        asList(new SnapshotInfo[] {snapshotInfo11, snapshotInfo12});
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 6, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final var backups = (List<HistoryBackupInfo>) backupController.getBackups().getBody();
    assertThat(backups).hasSize(1);
    final HistoryBackupInfo backup1 = backups.get(0);
    assertThat(backup1.getState()).isEqualTo(COMPLETED);
    assertThat(backup1.getBackupId()).isEqualTo(new BigDecimal(backupId1));
    assertThat(backup1.getFailureReason()).isNull();
    assertBackupDetails(List.of(snapshotInfo11, snapshotInfo12), backup1);
  }

  private void assertBackupDetails(
      final List<SnapshotInfo> snapshotInfos, final HistoryBackupInfo backupState) {
    assertThat(backupState.getDetails()).hasSize(snapshotInfos.size());
    assertThat(backupState.getDetails())
        .extracting(HistoryBackupDetail::getSnapshotName)
        .containsExactlyInAnyOrder(
            snapshotInfos.stream().map(si -> si.snapshotId().getName()).toArray(String[]::new));
    assertThat(backupState.getDetails())
        .extracting(d -> d.getState())
        .containsExactlyInAnyOrder(
            snapshotInfos.stream().map(si -> si.state().name()).toArray(String[]::new));
    assertThat(backupState.getDetails())
        .extracting(d -> d.getStartTime().toInstant().toEpochMilli())
        .containsExactlyInAnyOrder(
            snapshotInfos.stream().map(si -> si.startTime()).toArray(Long[]::new));
  }

  private SnapshotInfo createSnapshotInfoMock(
      final String name, final String uuid, final SnapshotState state) {
    return createSnapshotInfoMock(null, name, uuid, state, null);
  }

  private SnapshotInfo createSnapshotInfoMock(
      final Metadata metadata, final String uuid, final SnapshotState state) {
    return createSnapshotInfoMock(metadata, null, uuid, state, null);
  }

  @NotNull
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
          .thenReturn(
              new SnapshotId(new OperateSnapshotNameProvider().getSnapshotName(metadata), uuid));
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

  private void assertReturnsError(
      final Supplier<WebEndpointResponse<?>> runnable,
      final int httpStatusCode,
      final String expectedMessage) {
    final var webResponse = runnable.get();

    assertThat(webResponse.getStatus()).isEqualTo(httpStatusCode);

    final var error = (Error) webResponse.getBody();
    assertThat(error.getMessage()).contains(expectedMessage);
  }
}
