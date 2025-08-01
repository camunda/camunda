/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp;

import static io.camunda.management.backups.HistoryStateCode.*;
import static io.camunda.webapps.backup.repository.elasticsearch.ElasticsearchBackupRepository.SNAPSHOT_MISSING_EXCEPTION_TYPE;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.snapshot.ElasticsearchSnapshotClient;
import co.elastic.clients.elasticsearch.snapshot.GetRepositoryRequest;
import co.elastic.clients.elasticsearch.snapshot.GetRepositoryResponse;
import co.elastic.clients.elasticsearch.snapshot.GetSnapshotRequest;
import co.elastic.clients.elasticsearch.snapshot.GetSnapshotResponse;
import co.elastic.clients.elasticsearch.snapshot.Repository;
import co.elastic.clients.elasticsearch.snapshot.SnapshotInfo;
import co.elastic.clients.elasticsearch.snapshot.SnapshotShardFailure;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.TransportException;
import co.elastic.clients.util.ObjectBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.management.backups.Error;
import io.camunda.management.backups.HistoryBackupDetail;
import io.camunda.management.backups.HistoryBackupInfo;
import io.camunda.management.backups.HistoryStateCode;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.TestApplication;
import io.camunda.webapps.backup.Metadata;
import io.camunda.webapps.backup.repository.SnapshotNameProvider;
import io.camunda.webapps.backup.repository.WebappsSnapshotNameProvider;
import io.camunda.webapps.backup.repository.elasticsearch.MetadataMarshaller;
import io.camunda.webapps.controllers.BackupController;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.snapshots.SnapshotState;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
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
    classes = {
      TestApplication.class,
      UnifiedConfiguration.class,
      UnifiedConfigurationHelper.class,
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      OperateProperties.PREFIX + ".zeebe.compatibility.enabled = true",
      "management.endpoints.web.exposure.include = backupHistory",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER"
    })
@ActiveProfiles({"test", "operate", "standalone"})
public class BackupControllerIT {
  private ElasticsearchSnapshotClient snapshotClient;

  @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
  private ElasticsearchClient elasticsearchClient;

  private final JsonpMapper jsonpMapper = new JacksonJsonpMapper();
  private final SnapshotNameProvider snapshotNameProvider = new WebappsSnapshotNameProvider();

  @MockBean(name = "esClient", answer = Answers.RETURNS_DEEP_STUBS)
  private RestHighLevelClient esClient;

  @MockBean(name = "zeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

  @SpyBean private OperateProperties operateProperties;
  @Autowired private BackupController backupController;
  @Autowired private TestRestTemplate testRestTemplate;

  @LocalManagementPort private int managementPort;

  @Before
  public void setup() throws IOException {
    snapshotClient = mock(ElasticsearchSnapshotClient.class);
    when(elasticsearchClient._transport().jsonpMapper()).thenReturn(jsonpMapper);
    when(elasticsearchClient.snapshot()).thenReturn(snapshotClient);
    final var repoResponse =
        GetRepositoryResponse.of(
            b ->
                b.result(
                    Map.of(
                        operateProperties.getBackup().getRepositoryName(),
                        mock(Repository.class))));
    mockGetRepoWithReturn(repoResponse);
  }

  private void mockGetRepoWithReturn(final GetRepositoryResponse response) throws IOException {
    when(snapshotClient.getRepository((GetRepositoryRequest) any())).thenReturn(response);
    when(snapshotClient.getRepository(
            (Function<GetRepositoryRequest.Builder, ObjectBuilder<GetRepositoryRequest>>) any()))
        .thenReturn(response);
  }

  private void mockGetRepoWithException(final Exception ex) throws IOException {
    when(snapshotClient.getRepository((GetRepositoryRequest) any())).thenThrow(ex);
    when(snapshotClient.getRepository(
            (Function<GetRepositoryRequest.Builder, ObjectBuilder<GetRepositoryRequest>>) any()))
        .thenThrow(ex);
  }

  private void mockGetWithReturn(final GetSnapshotResponse response) throws IOException {
    when(snapshotClient.get((GetSnapshotRequest) any())).thenReturn(response);
    when(snapshotClient.get(
            (Function<GetSnapshotRequest.Builder, ObjectBuilder<GetSnapshotRequest>>) any()))
        .thenReturn(response);
  }

  private void mockGetWithException(final Exception ex) throws IOException {
    when(snapshotClient.get((GetSnapshotRequest) any())).thenThrow(ex);
    when(snapshotClient.get(
            (Function<GetSnapshotRequest.Builder, ObjectBuilder<GetSnapshotRequest>>) any()))
        .thenThrow(ex);
  }

  @Test
  public void shouldReturnNotFoundStatusWhenBackupIdNotFound() throws Exception {
    final ElasticsearchException ex =
        mock(ElasticsearchException.class, Answers.RETURNS_DEEP_STUBS);
    when(ex.error().type()).thenReturn("snapshot_missing_exception");
    mockGetWithException(ex);

    final ResponseEntity<Map> result =
        testRestTemplate.getForEntity(
            "http://localhost:" + managementPort + "/actuator/backupHistory/2", Map.class);

    assertThat(result.getStatusCode().value()).isEqualTo(404);

    final Map<String, String> resultBody = (Map<String, String>) result.getBody();
    assertThat(resultBody.get("message")).isEqualTo("No backup with id [2] found.");
  }

  @Test
  public void shouldFailCreateBackupOnNonExistingRepository() throws IOException {
    final String expectedMessage =
        String.format(
            "No repository with name [%s] could be found.",
            operateProperties.getBackup().getRepositoryName());
    final ElasticsearchException elsEx =
        mock(ElasticsearchException.class, Answers.RETURNS_DEEP_STUBS);
    when(elsEx.error().type()).thenReturn("repository_missing_exception");
    mockGetRepoWithException(elsEx);

    assertReturnsError(() -> backupController.takeBackup(1L), 404, expectedMessage);

    assertReturnsError(() -> backupController.takeBackup(1L), 404, expectedMessage);

    verify(elasticsearchClient, times(2)).snapshot();
  }

  @Test
  public void shouldFailCreateBackupOnBackupIdNotFound() throws IOException {
    final long backupId = 2L;
    final SnapshotInfo snapshotInfo = mock(SnapshotInfo.class, Answers.RETURNS_DEEP_STUBS);
    when(snapshotInfo.snapshot()).thenReturn("snapshotName");
    final List<SnapshotInfo> snapshotInfos = List.of(snapshotInfo);
    mockGetWithReturn(
        GetSnapshotResponse.of(b -> b.snapshots(snapshotInfos).total(1).remaining(1)));

    final String expectedMessage =
        String.format("A backup with ID [%s] already exists. Found snapshots:", backupId);
    assertReturnsError(() -> backupController.takeBackup(backupId), 400, expectedMessage);
    verify(elasticsearchClient, times(2)).snapshot();
  }

  @Test
  public void shouldFailCreateBackupOn1stRequestFailedWithConnectionError() throws IOException {
    final long backupId = 2L;
    final TransportException ex = mock(TransportException.class);
    mockGetRepoWithException(ex);

    final String expectedMessage =
        String.format(
            "Encountered an error connecting to Elasticsearch while retrieving repository with name [%s].",
            operateProperties.getBackup().getRepositoryName());
    assertReturnsError(() -> backupController.takeBackup(backupId), 502, expectedMessage);
  }

  @Test
  public void shouldFailCreateBackupOn2ndRequestFailedWithConnectionError() throws IOException {
    final long backupId = 2L;
    final TransportException ex = mock(TransportException.class);

    mockGetWithException(ex);

    final String expectedMessage =
        String.format(
            "Encountered an error connecting to Elasticsearch while searching for duplicate backup. Repository name: [%s].",
            operateProperties.getBackup().getRepositoryName());
    assertReturnsError(() -> backupController.takeBackup(backupId), 502, expectedMessage);
  }

  @Test
  public void shouldFailGetStateOnNoBackupFound() throws IOException {
    final long backupId = 2L;
    final ElasticsearchException ex =
        mock(ElasticsearchException.class, Answers.RETURNS_DEEP_STUBS);
    when(ex.error().type()).thenReturn(SNAPSHOT_MISSING_EXCEPTION_TYPE);
    mockGetWithException(ex);

    final String expectedMessage = String.format("No backup with id [%s] found.", backupId);
    assertReturnsError(() -> backupController.getBackupState(backupId), 404, expectedMessage);
    verify(elasticsearchClient, times(1)).snapshot();
  }

  @Test
  public void shouldFailGetStateOnConnectionError() throws IOException {
    final long backupId = 2L;
    final TransportException ex = mock(TransportException.class);
    mockGetRepoWithException(ex);
    mockGetWithException(ex);

    final String expectedMessage =
        String.format(
            "Encountered an error connecting to Elasticsearch while searching for snapshots. Repository name: [%s].",
            operateProperties.getBackup().getRepositoryName());
    assertReturnsError(() -> backupController.getBackupState(backupId), 502, expectedMessage);
  }

  @Test
  public void shouldReturnCompletedState() throws IOException {
    final long backupId = 2L;
    final var snapshotInfo1 = createSnapshotInfoMock("snapshotName1", SnapshotState.SUCCESS);
    final var snapshotInfo2 = createSnapshotInfoMock("snapshotName2", SnapshotState.SUCCESS);
    final var snapshotInfo3 = createSnapshotInfoMock("snapshotName3", SnapshotState.SUCCESS);
    final List<SnapshotInfo> snapshotInfos = asList(snapshotInfo1, snapshotInfo2, snapshotInfo3);
    final var snapshotResponse =
        GetSnapshotResponse.of(b -> b.remaining(1).total(1).snapshots(snapshotInfos));
    mockGetWithReturn(snapshotResponse);

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
        createSnapshotInfoMock("snapshotName1", SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock("snapshotName2", SnapshotState.SUCCESS);
    final SnapshotShardFailure failure1 =
        SnapshotShardFailure.of(
            b ->
                b.index("1")
                    .indexUuid("uuid")
                    .nodeId("someNodeId1")
                    .shardId("someIndex1" + UUID.randomUUID() + 1)
                    .status("FAILURE")
                    .reason("Shard 1 is not allocated"));
    final SnapshotShardFailure failure2 =
        SnapshotShardFailure.of(
            b ->
                b.index("2")
                    .indexUuid("uuid2")
                    .nodeId("someNodeId2")
                    .shardId("someIndex2" + UUID.randomUUID() + 2)
                    .status("FAILURE")
                    .reason("Shard 2 is not allocated"));
    final List<SnapshotShardFailure> shardFailures = asList(failure1, failure2);
    final SnapshotInfo snapshotInfo3 =
        createSnapshotInfoMock("snapshotName3", SnapshotState.FAILED, shardFailures);
    final List<SnapshotInfo> snapshotInfos = asList(snapshotInfo1, snapshotInfo2, snapshotInfo3);

    mockGetWithReturn(
        GetSnapshotResponse.of(b -> b.snapshots(snapshotInfos).total(1).remaining(1)));

    final var backupState = (HistoryBackupInfo) backupController.getBackupState(backupId).getBody();
    assertThat(backupState.getState()).isEqualTo(FAILED);
    assertThat(backupState.getBackupId()).isEqualTo(new BigDecimal(backupId));
    assertThat(backupState.getFailureReason())
        .isEqualTo("There were failures with the following snapshots: snapshotName3");

    assertBackupDetails(snapshotInfos, backupState);

    assertThat(backupState.getDetails())
        .extracting(d -> d.getFailures())
        .containsExactly(
            null, null, snapshotInfos.get(2).failures().stream().map(si -> si.reason()).toList());
  }

  @Test
  public void shouldReturnFailedState2() throws IOException {
    final long backupId = 2L;
    final SnapshotInfo snapshotInfo1 =
        createSnapshotInfoMock("snapshotName1", SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock("snapshotName2", SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo3 =
        createSnapshotInfoMock("snapshotName3", SnapshotState.PARTIAL);
    final List<SnapshotInfo> snapshotInfos = asList(snapshotInfo1, snapshotInfo2, snapshotInfo3);
    mockGetWithReturn(
        GetSnapshotResponse.of(b -> b.snapshots(snapshotInfos).remaining(1).total(1)));

    final var backupState = (HistoryBackupInfo) backupController.getBackupState(backupId).getBody();
    assertThat(backupState.getState()).isEqualTo(FAILED);
    assertThat(backupState.getBackupId()).isEqualTo(new BigDecimal(backupId));
    assertThat(backupState.getFailureReason())
        .isEqualTo("Some of the snapshots are partial: snapshotName3");

    assertBackupDetails(snapshotInfos, backupState);
  }

  @Test
  public void shouldReturnFailedState3WhenMoreSnapshotsThanExpected() throws IOException {
    final long backupId = 2L;
    final SnapshotInfo snapshotInfo1 =
        createSnapshotInfoMock("snapshotName1", SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock("snapshotName2", SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo3 =
        createSnapshotInfoMock("snapshotName3", SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo4 =
        createSnapshotInfoMock("snapshotName4", SnapshotState.SUCCESS);
    final List<SnapshotInfo> snapshotInfos =
        asList(snapshotInfo1, snapshotInfo2, snapshotInfo3, snapshotInfo4);
    mockGetWithReturn(
        GetSnapshotResponse.of(b -> b.snapshots(snapshotInfos).remaining(1).total(1)));

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
        createSnapshotInfoMock("snapshotName1", SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock("snapshotName2", SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo3 =
        createSnapshotInfoMock("snapshotName3", SnapshotState.INCOMPATIBLE);
    final List<SnapshotInfo> snapshotInfos = asList(snapshotInfo1, snapshotInfo2, snapshotInfo3);
    mockGetWithReturn(
        GetSnapshotResponse.of(b -> b.snapshots(snapshotInfos).remaining(1).total(1)));

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
        createSnapshotInfoMock("snapshotName1", SnapshotState.SUCCESS);
    when(snapshotInfo1.startTimeInMillis()).thenReturn(0L);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock("snapshotName2", SnapshotState.SUCCESS);
    final List<SnapshotInfo> snapshotInfos = asList(snapshotInfo1, snapshotInfo2);
    mockGetWithReturn(
        GetSnapshotResponse.of(b -> b.snapshots(snapshotInfos).remaining(1).total(1)));

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
        createSnapshotInfoMock("snapshotName1", SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock("snapshotName2", SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo3 =
        createSnapshotInfoMock("snapshotName3", SnapshotState.IN_PROGRESS);
    final List<SnapshotInfo> snapshotInfos = asList(snapshotInfo1, snapshotInfo2, snapshotInfo3);
    mockGetWithReturn(
        GetSnapshotResponse.of(b -> b.snapshots(snapshotInfos).remaining(1).total(1)));

    final var backupState = (HistoryBackupInfo) backupController.getBackupState(backupId).getBody();
    assertThat(backupState.getState()).isEqualTo(IN_PROGRESS);
    assertThat(backupState.getBackupId()).isEqualTo(new BigDecimal(backupId));
    assertThat(backupState.getFailureReason()).isNull();

    assertBackupDetails(snapshotInfos, backupState);
  }

  @Test
  public void shouldReturnInProgressState2() throws IOException {
    final long backupId = 2L;
    // we have only 2 out of 3 snapshots
    final SnapshotInfo snapshotInfo1 =
        createSnapshotInfoMock("snapshotName1", SnapshotState.IN_PROGRESS);
    final SnapshotInfo snapshotInfo2 =
        createSnapshotInfoMock("snapshotName2", SnapshotState.IN_PROGRESS);
    final List<SnapshotInfo> snapshotInfos =
        asList(new SnapshotInfo[] {snapshotInfo1, snapshotInfo2});
    mockGetWithReturn(
        GetSnapshotResponse.of(b -> b.snapshots(snapshotInfos).remaining(1).total(1)));

    final HistoryBackupInfo backupState =
        (HistoryBackupInfo) backupController.getBackupState(backupId).getBody();
    assertThat(backupState.getState()).isEqualTo(IN_PROGRESS);
    assertThat(backupState.getBackupId()).isEqualTo(new BigDecimal(backupId));
    assertThat(backupState.getFailureReason()).isNull();

    assertBackupDetails(snapshotInfos, backupState);
  }

  @Test
  public void shouldFailDeleteBackupOnNonExistingRepository() throws IOException {
    final ElasticsearchException elsEx =
        mock(ElasticsearchException.class, Answers.RETURNS_DEEP_STUBS);
    when(elsEx.error().type()).thenReturn("repository_missing_exception");
    mockGetRepoWithException(elsEx);
    final var webResponse = backupController.deleteBackup(3L);
    assertThat(webResponse.getStatus()).isEqualTo(404);

    final var error = (Error) webResponse.getBody();

    final String expectedMessage =
        String.format(
            "No repository with name [%s] could be found.",
            operateProperties.getBackup().getRepositoryName());
    final String actualMessage = error.getMessage();
    assertThat(actualMessage.contains(expectedMessage)).isTrue();
    verify(elasticsearchClient, times(1)).snapshot();
  }

  @Test
  public void shouldFailGetBackupOnNonExistingRepository() throws IOException {
    final ElasticsearchException elsEx =
        mock(ElasticsearchException.class, Answers.RETURNS_DEEP_STUBS);
    when(elsEx.error().type()).thenReturn("repository_missing_exception");
    mockGetWithException(elsEx);
    final var webResponse = backupController.getBackupState(3L);
    assertThat(webResponse.getStatus()).isEqualTo(WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR);
    final var error = (Error) webResponse.getBody();

    final String expectedMessage =
        String.format(
            "No repository with name [%s] could be found.",
            operateProperties.getBackup().getRepositoryName());
    final String actualMessage = error.getMessage();
    assertThat(actualMessage.contains(expectedMessage)).isTrue();
    verify(elasticsearchClient, times(1)).snapshot();
  }

  @Test
  public void shouldFailGetBackupsOnNonExistingRepository() throws IOException {
    final ElasticsearchException elsEx =
        mock(ElasticsearchException.class, Answers.RETURNS_DEEP_STUBS);
    when(elsEx.error().type()).thenReturn("repository_missing_exception");
    mockGetRepoWithException(elsEx);
    mockGetWithException(elsEx);

    final String expectedMessage =
        String.format(
            "No repository with name [%s] could be found.",
            operateProperties.getBackup().getRepositoryName());
    assertReturnsError(() -> backupController.getBackups(true, null), 404, expectedMessage);

    verify(elasticsearchClient, times(1)).snapshot();
  }

  @Test
  public void shouldReturnEmptyBackupsOnNoBackupFound() throws IOException {
    final ElasticsearchException elsEx =
        mock(ElasticsearchException.class, Answers.RETURNS_DEEP_STUBS);
    when(elsEx.error().type()).thenReturn(SNAPSHOT_MISSING_EXCEPTION_TYPE);
    mockGetWithException(elsEx);
    final var response = backupController.getBackups(true, null);

    assertThat((List<HistoryBackupInfo>) response.getBody()).isEmpty();
  }

  @Test
  public void shouldFailGetBackupsOnConnectionError() throws IOException {
    final TransportException elsEx = mock(TransportException.class);
    mockGetRepoWithException(elsEx);
    mockGetWithException(elsEx);

    final String expectedMessage =
        String.format(
            "Encountered an error connecting to Elasticsearch while searching for snapshots. Repository name: [%s].",
            operateProperties.getBackup().getRepositoryName());
    assertReturnsError(() -> backupController.getBackups(true, null), 502, expectedMessage);
  }

  @Test
  public void shouldReturnThreeBackups() throws IOException {
    final long backupId1 = 1L;
    final long backupId2 = 2L;
    final long backupId3 = 3L;
    // COMPLETED
    final SnapshotInfo snapshotInfo11 =
        createSnapshotInfoMock(new Metadata(backupId1, "8.8.8", 1, 2), SnapshotState.SUCCESS);

    final SnapshotInfo snapshotInfo12 =
        createSnapshotInfoMock(new Metadata(backupId1, "8.8.8", 2, 2), SnapshotState.SUCCESS);

    // we have only 2 out of 3 snapshots + TIMEOUT -> INCOMPLETE
    final SnapshotInfo snapshotInfo21 =
        createSnapshotInfoMock(new Metadata(backupId2, "8.8.8", 1, 3), SnapshotState.SUCCESS);
    when(snapshotInfo21.startTimeInMillis()).thenReturn(0L);
    final SnapshotInfo snapshotInfo22 =
        createSnapshotInfoMock(new Metadata(backupId2, "8.8.8", 2, 3), SnapshotState.SUCCESS);
    when(snapshotInfo22.startTimeInMillis()).thenReturn(0L);
    // IN_PROGRESS
    final SnapshotInfo snapshotInfo31 =
        createSnapshotInfoMock(new Metadata(backupId3, "8.8.8", 1, 3), SnapshotState.SUCCESS);
    final SnapshotInfo snapshotInfo32 =
        createSnapshotInfoMock(new Metadata(backupId3, "8.8.8", 2, 3), SnapshotState.IN_PROGRESS);
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

    mockGetWithReturn(
        GetSnapshotResponse.of(b -> b.snapshots(snapshotInfos).remaining(1).total(1)));

    final var backups = (List<HistoryBackupInfo>) backupController.getBackups(true, null).getBody();
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
    final SnapshotInfo snapshotInfo11 = createSnapshotInfoMock(metadata1, SnapshotState.SUCCESS);
    // remove backupId from metadata
    final Metadata metadata1WithNoId = new Metadata(null, "8.8.8", 1, 2);
    when(snapshotInfo11.metadata())
        .thenReturn(MetadataMarshaller.asJson(metadata1WithNoId, jsonpMapper));

    final Metadata metadata2 = new Metadata(backupId1, "8.8.8", 2, 2);
    final SnapshotInfo snapshotInfo12 = createSnapshotInfoMock(metadata2, SnapshotState.SUCCESS);
    final Metadata metadata2WithNoId = new Metadata(backupId1, "8.8.8", 2, 2);
    when(snapshotInfo12.metadata())
        .thenReturn(MetadataMarshaller.asJson(metadata2WithNoId, jsonpMapper));
    final List<SnapshotInfo> snapshotInfos = asList(snapshotInfo11, snapshotInfo12);
    final var returnedResponse =
        GetSnapshotResponse.of(b -> b.snapshots(snapshotInfos).remaining(1).total(1));
    mockGetWithReturn(returnedResponse);

    final var backups = (List<HistoryBackupInfo>) backupController.getBackups(true, null).getBody();
    assertThat(backups).hasSize(1);
    final HistoryBackupInfo backup1 = backups.get(0);
    assertThat(backup1.getState()).isEqualTo(COMPLETED);
    assertThat(backup1.getBackupId()).isEqualTo(new BigDecimal(backupId1));
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

    final Metadata metadata = new Metadata(backupId, "8.8.8", 1, 1);

    final SnapshotInfo snapshotInfo = mock(SnapshotInfo.class);
    when(snapshotInfo.snapshot()).thenReturn(snapshotNameProvider.getSnapshotName(metadata));
    when(snapshotInfo.state()).thenReturn(SnapshotState.SUCCESS.toString());
    final List<SnapshotInfo> snapshotInfos = asList(new SnapshotInfo[] {snapshotInfo});
    when(snapshotClient.get(any(GetSnapshotRequest.class)))
        .thenReturn(GetSnapshotResponse.of(b -> b.snapshots(snapshotInfos).total(1).remaining(0)));

    final var backups = backupController.getBackups(false, null);
    assertThat((List<HistoryBackupInfo>) backups.getBody())
        .allSatisfy(
            backupState -> {
              assertThat(backupState.getState()).isEqualTo(COMPLETED);
              assertThat(backupState.getBackupId().longValue()).isEqualTo(backupId);
              assertThat(backupState.getDetails())
                  .singleElement()
                  .satisfies(
                      info -> {
                        assertThat(info.getState()).isEqualTo("SUCCESS");
                        assertThat(info.getStartTime()).isNull();
                      });
            });
  }

  @Test
  public void shouldReturnBackupsWhenNoParameters() throws IOException {
    final long backupId1 = 1L;
    // COMPLETED
    final SnapshotInfo snapshotInfo11 =
        createSnapshotInfoMock(new Metadata(backupId1, "8.8.8", 1, 2), SnapshotState.SUCCESS);

    final SnapshotInfo snapshotInfo12 =
        createSnapshotInfoMock(new Metadata(backupId1, "8.8.8", 2, 2), SnapshotState.SUCCESS);

    final List<SnapshotInfo> snapshotInfos = asList(snapshotInfo11, snapshotInfo12);

    mockGetWithReturn(
        GetSnapshotResponse.of(b -> b.snapshots(snapshotInfos).remaining(1).total(1)));

    final var res =
        testRestTemplate.getForEntity(
            "http://localhost:" + managementPort + "/actuator/backupHistory", String.class);
    assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();

    final ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    final List<HistoryBackupInfo> backups =
        objectMapper.readValue(
            res.getBody(),
            objectMapper
                .getTypeFactory()
                .constructCollectionType(List.class, HistoryBackupInfo.class));

    assertThat(backups).hasSize(1);

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

  private void assertBackupDetails(
      final List<SnapshotInfo> snapshotInfos, final HistoryBackupInfo backupState) {
    assertThat(backupState.getDetails()).hasSize(snapshotInfos.size());
    assertThat(backupState.getDetails())
        .extracting(HistoryBackupDetail::getSnapshotName)
        .containsExactlyInAnyOrder(
            snapshotInfos.stream().map(si -> si.snapshot()).toArray(String[]::new));
    assertThat(backupState.getDetails())
        .extracting(d -> d.getState())
        .containsExactlyInAnyOrder(
            snapshotInfos.stream().map(si -> si.state()).toArray(String[]::new));
    assertThat(backupState.getDetails())
        .extracting(
            d -> d.getStartTime() != null ? d.getStartTime().toInstant().toEpochMilli() : 0L)
        .containsExactlyInAnyOrder(
            snapshotInfos.stream().map(si -> si.startTimeInMillis()).toArray(Long[]::new));
  }

  private SnapshotInfo createSnapshotInfoMock(final String name, final SnapshotState state) {
    return createSnapshotInfoMock(null, name, state, null);
  }

  private SnapshotInfo createSnapshotInfoMock(final Metadata metadata, final SnapshotState state) {
    return createSnapshotInfoMock(metadata, null, state, null);
  }

  @NotNull
  private SnapshotInfo createSnapshotInfoMock(
      final String name, final SnapshotState state, final List<SnapshotShardFailure> failures) {
    return createSnapshotInfoMock(null, name, state, failures);
  }

  @NotNull
  private SnapshotInfo createSnapshotInfoMock(
      final Metadata metadata,
      final String name,
      final SnapshotState state,
      final List<SnapshotShardFailure> failures) {
    final SnapshotInfo snapshotInfo = mock(SnapshotInfo.class);
    if (metadata != null) {
      when(snapshotInfo.snapshot())
          .thenReturn(new WebappsSnapshotNameProvider().getSnapshotName(metadata));
      when(snapshotInfo.metadata()).thenReturn(MetadataMarshaller.asJson(metadata, jsonpMapper));
    } else {
      when(snapshotInfo.snapshot()).thenReturn(name);
      when(snapshotInfo.metadata())
          .thenReturn(
              Map.of(
                  "backupId",
                  JsonData.of("1"),
                  "version",
                  JsonData.of("someVersion"),
                  "partNo",
                  JsonData.of(1),
                  "partCount",
                  JsonData.of(3)));
    }
    when(snapshotInfo.state()).thenReturn(state.toString());
    when(snapshotInfo.failures()).thenReturn(failures);
    when(snapshotInfo.startTimeInMillis())
        .thenReturn(OffsetDateTime.now().toInstant().toEpochMilli());
    return snapshotInfo;
  }

  private void assertReturnsError(
      final Supplier<WebEndpointResponse<?>> runnable,
      final int httpStatusCode,
      final String expectedMessage) {
    final var webResponse = runnable.get();

    try {
      assertThat(webResponse.getStatus()).isEqualTo(httpStatusCode);

      final var error = (Error) webResponse.getBody();
      assertThat(error.getMessage()).contains(expectedMessage);
    } catch (final Throwable e) {
      System.out.println("web response is " + webResponse.getBody());
      throw e;
    }
  }
}
