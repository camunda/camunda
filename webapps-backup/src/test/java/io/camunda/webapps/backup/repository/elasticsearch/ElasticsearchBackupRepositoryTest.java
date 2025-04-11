/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.backup.repository.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.GetIndexRequest;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import co.elastic.clients.elasticsearch.snapshot.CreateSnapshotRequest;
import co.elastic.clients.elasticsearch.snapshot.CreateSnapshotResponse;
import co.elastic.clients.elasticsearch.snapshot.GetSnapshotRequest;
import co.elastic.clients.elasticsearch.snapshot.GetSnapshotResponse;
import co.elastic.clients.elasticsearch.snapshot.SnapshotInfo;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.webapps.backup.BackupException.ResourceNotFoundException;
import io.camunda.webapps.backup.BackupService.SnapshotRequest;
import io.camunda.webapps.backup.BackupStateDto;
import io.camunda.webapps.backup.Metadata;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import io.camunda.webapps.backup.repository.SnapshotNameProvider;
import io.camunda.webapps.backup.repository.WebappsSnapshotNameProvider;
import io.camunda.webapps.schema.descriptors.backup.SnapshotIndexCollection;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ElasticsearchBackupRepositoryTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final Executor executor = Runnable::run;
  private final String repositoryName = "repo1";
  private final long backupId = 555;
  private final String snapshotName = "camunda_webapps_" + backupId + "_8.6_part_1_of_6";
  private final long incompleteCheckTimeoutLengthSeconds = 5 * 60L; // 5 minutes
  private final long incompleteCheckTimeoutLength = incompleteCheckTimeoutLengthSeconds * 1000;

  @Mock(answer = RETURNS_DEEP_STUBS)
  private ElasticsearchClient esClient;

  @Mock(answer = RETURNS_DEEP_STUBS)
  private BackupRepositoryProps backupProps;

  private ElasticsearchBackupRepository backupRepository;
  private final SnapshotNameProvider snapshotNameProvider = new WebappsSnapshotNameProvider();

  @BeforeEach
  public void setup() {
    backupRepository =
        Mockito.spy(
            new ElasticsearchBackupRepository(
                esClient, backupProps, snapshotNameProvider, executor));
  }

  @Test
  public void testWaitingForSnapshotWithTimeout() {
    final int timeout = 1;
    final var snapshotState = SnapshotState.IN_PROGRESS.name();

    // mock calls to `findSnapshot` and `operateProperties`
    final var snapshotInfo = mock(SnapshotInfo.class, Mockito.RETURNS_DEEP_STUBS);
    when(snapshotInfo.state()).thenReturn(snapshotState);
    when(snapshotInfo.snapshot()).thenReturn(snapshotName);
    when(backupProps.snapshotTimeout()).thenReturn(timeout);
    doReturn(List.of(snapshotInfo))
        .when(backupRepository)
        .findSnapshots(ArgumentMatchers.any(), ArgumentMatchers.any());

    final boolean finished =
        backupRepository.isSnapshotFinishedWithinTimeout(repositoryName, snapshotName);

    assertThat(finished).isFalse();
    verify(backupRepository, Mockito.atLeast(5)).findSnapshots(repositoryName, backupId);
  }

  @Test
  public void testWaitingForSnapshotTillCompleted() throws IOException {
    final int timeout = 0;
    final int snapshotCount = 6;
    final var snapshotInfos = new ArrayList<SnapshotInfo>();
    final var metadatas = new ArrayList<Metadata>();
    for (int i = 0; i < snapshotCount; i++) {
      final var metadata = new Metadata(backupId, "8.3.0", i + 1, snapshotCount);
      metadatas.add(metadata);
      final SnapshotInfo snapshotInfo = mock(SnapshotInfo.class, RETURNS_DEEP_STUBS);
      when(snapshotInfo.state())
          .thenReturn(SnapshotState.IN_PROGRESS.name())
          .thenReturn(SnapshotState.IN_PROGRESS.name())
          .thenReturn(SnapshotState.SUCCESS.name());
      when(snapshotInfo.metadata())
          .thenReturn(MetadataMarshaller.asJson(metadata, new JacksonJsonpMapper(OBJECT_MAPPER)));
      when(snapshotInfo.snapshot()).thenReturn(snapshotNameProvider.getSnapshotName(metadata));
      snapshotInfos.add(snapshotInfo);
    }

    // mock calls to `findSnapshot` and `operateProperties`
    final var snapshotResponse = mock(GetSnapshotResponse.class);
    when(snapshotResponse.snapshots()).thenReturn(snapshotInfos);
    when(esClient.snapshot().get(any(GetSnapshotRequest.class))).thenReturn(snapshotResponse);
    when(esClient.snapshot().create(any(CreateSnapshotRequest.class)))
        .thenThrow(new SocketTimeoutException());

    for (int i = 0; i < snapshotCount; i++) {
      final var metadata = metadatas.get(i);
      backupRepository.executeSnapshotting(
          new SnapshotRequest(
              repositoryName,
              snapshotNameProvider.getSnapshotName(metadata),
              new SnapshotIndexCollection(List.of("index-example"), List.of()),
              metadata),
          () -> {},
          () -> {});
    }

    verify(backupRepository, times(3 * snapshotCount)).findSnapshots(repositoryName, backupId);

    Awaitility.await("backup is completed")
        .untilAsserted(
            () -> {
              final var response = backupRepository.getBackupState(repositoryName, backupId);
              assertThat(response.getState()).isEqualTo(BackupStateDto.COMPLETED);
            });
  }

  @Test
  public void testWaitingForSnapshotWithoutTimeout() {
    final int timeout = 0;
    final var snapshotState = SnapshotState.IN_PROGRESS.name();

    // mock calls to `findSnapshot` and `operateProperties`
    final SnapshotInfo snapshotInfo = mock(SnapshotInfo.class, Mockito.RETURNS_DEEP_STUBS);
    when(snapshotInfo.state()).thenReturn(snapshotState);
    when(snapshotInfo.snapshot()).thenReturn(snapshotName);
    when(backupProps.snapshotTimeout()).thenReturn(timeout);
    doReturn(List.of(snapshotInfo))
        .when(backupRepository)
        .findSnapshots(ArgumentMatchers.any(), ArgumentMatchers.any());

    // we expect infinite loop, so we call snapshotting in separate thread
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    final Future<?> future =
        executor.submit(
            () -> {
              backupRepository.isSnapshotFinishedWithinTimeout(repositoryName, snapshotName);
            });

    try {
      // Set a timeout of 2 seconds for the function to complete
      future.get(2, TimeUnit.SECONDS);
    } catch (final TimeoutException e) {
      // expected
      return;
    } catch (final InterruptedException | ExecutionException e) {
      // ignore
    } finally {
      // Shutdown the executor
      executor.shutdownNow();
    }

    fail("Expected to continue waiting for snapshotting to finish.");
  }

  @Test
  void shouldCreateRepository() {
    assertThat(backupRepository).isNotNull();
  }

  @Test
  public void shouldTakeSnapshot() throws IOException {
    when(esClient.snapshot().create((CreateSnapshotRequest) any()))
        .then(
            inv -> {
              final var request = (CreateSnapshotRequest) inv.getArgument(0);
              final var snapshotInfo =
                  SnapshotInfo.of(
                      b ->
                          b.snapshot(request.snapshot())
                              .endTimeInMillis(11223232L)
                              .dataStreams(List.of())
                              .uuid("uuuid")
                              .indices(request.indices())
                              .state("SUCCESS"));
              return CreateSnapshotResponse.of(b -> b.snapshot(snapshotInfo).accepted(true));
            });
    final var metadata = new Metadata(1L, "1", 1, 1);
    final var snapshotRequest =
        new SnapshotRequest(
            "repo-name-1",
            snapshotNameProvider.getSnapshotName(metadata),
            new SnapshotIndexCollection(List.of("index-1", "index-2"), List.of("index-3")),
            metadata);
    // 1 element array to bypass closures over final fields
    backupRepository.executeSnapshotting(
        snapshotRequest, () -> {}, () -> fail("Snapshotting failed"));
  }

  @Test
  void shouldReturnBackupStateCompleted() throws IOException {
    final var firstSnapshotInfo = mock(SnapshotInfo.class);
    final var snapshotResponse = mock(GetSnapshotResponse.class);

    // Set up Snapshot details
    final Map<String, JsonData> metadata =
        MetadataMarshaller.asJson(new Metadata(1L, "1", 1, 1), null);
    when(firstSnapshotInfo.metadata()).thenReturn(metadata);
    when(firstSnapshotInfo.snapshot()).thenReturn("snapshot-name");
    //    when(firstSnapshotInfo.uuid()).thenReturn("uuid");
    when(firstSnapshotInfo.state()).thenReturn(SnapshotState.SUCCESS.name());

    // Set up Snapshot response
    when(snapshotResponse.snapshots()).thenReturn(List.of(firstSnapshotInfo));
    when(esClient.snapshot().get((GetSnapshotRequest) ArgumentMatchers.any()))
        .thenReturn(snapshotResponse);

    // Test
    final var backupState = backupRepository.getBackupState("repository-name", 5L);
    assertThat(backupState.getState()).isEqualTo(BackupStateDto.COMPLETED);
  }

  @Test
  void shouldReturnBackupStateIncomplete() throws IOException {
    final var firstSnapshotInfo = mock(SnapshotInfo.class);
    final var snapshotResponse = mock(GetSnapshotResponse.class);
    final var lastSnapshotInfo = mock(SnapshotInfo.class);

    // Set up operate properties
    when(backupProps.incompleteCheckTimeoutInSeconds())
        .thenReturn(incompleteCheckTimeoutLengthSeconds);
    // Set up first Snapshot details
    final Map<String, JsonData> metadata =
        MetadataMarshaller.asJson(new Metadata(1L, "1", 1, 3), null);
    when(firstSnapshotInfo.metadata()).thenReturn(metadata);
    when(firstSnapshotInfo.snapshot()).thenReturn("snapshot-name");
    //    when(firstSnapshotInfo.uuid()).thenReturn("uuid");
    when(firstSnapshotInfo.state()).thenReturn(SnapshotState.SUCCESS.name());
    when(firstSnapshotInfo.startTimeInMillis()).thenReturn(23L);

    // Set up last Snapshot details
    when(lastSnapshotInfo.snapshot()).thenReturn("snapshot-name");
    //    when(lastSnapshotInfo.uuid()).thenReturn("uuid");
    when(lastSnapshotInfo.endTimeInMillis()).thenReturn(23L + 6 * 60 * 1_000);
    when(lastSnapshotInfo.state()).thenReturn(SnapshotState.SUCCESS.name());

    // Set up Snapshot response
    when(snapshotResponse.snapshots()).thenReturn(List.of(firstSnapshotInfo, lastSnapshotInfo));
    when(esClient.snapshot().get((GetSnapshotRequest) ArgumentMatchers.any()))
        .thenReturn(snapshotResponse);

    // Test
    final var backupState = backupRepository.getBackupState("repository-name", 5L);
    assertThat(backupState.getState()).isEqualTo(BackupStateDto.INCOMPLETE);
  }

  @Test
  void shouldReturnBackupStateIncompleteWhenLastSnapshotEndTimeIsTimedOut() throws IOException {
    final var firstSnapshotInfo = mock(SnapshotInfo.class);
    final var snapshotResponse = mock(GetSnapshotResponse.class);
    final var lastSnapshotInfo = mock(SnapshotInfo.class);
    final long now = Instant.now().toEpochMilli();

    // Set up operate properties
    when(backupProps.incompleteCheckTimeoutInSeconds())
        .thenReturn(incompleteCheckTimeoutLengthSeconds);
    // Set up first Snapshot details
    final Map<String, JsonData> metadata =
        MetadataMarshaller.asJson(new Metadata(1L, "1", 1, 3), null);
    when(firstSnapshotInfo.metadata()).thenReturn(metadata);
    when(firstSnapshotInfo.snapshot()).thenReturn("snapshot-name");
    //    when(firstSnapshotInfo.uuid()).thenReturn("uuid");
    when(firstSnapshotInfo.state()).thenReturn(SnapshotState.SUCCESS.name());
    when(firstSnapshotInfo.startTimeInMillis()).thenReturn(now - 4_000);

    // Set up last Snapshot details
    when(lastSnapshotInfo.snapshot()).thenReturn("snapshot-name");
    //    when(lastSnapshotInfo.uuid()).thenReturn("uuid");
    when(lastSnapshotInfo.state()).thenReturn(SnapshotState.SUCCESS.name());
    when(lastSnapshotInfo.startTimeInMillis())
        .thenReturn(now - (incompleteCheckTimeoutLength + 4_000));
    when(lastSnapshotInfo.endTimeInMillis())
        .thenReturn(now - (incompleteCheckTimeoutLength + 2_000));

    // Set up Snapshot response
    when(snapshotResponse.snapshots()).thenReturn(List.of(firstSnapshotInfo, lastSnapshotInfo));
    when(esClient.snapshot().get((GetSnapshotRequest) ArgumentMatchers.any()))
        .thenReturn(snapshotResponse);

    // Test
    final var backupState = backupRepository.getBackupState("repository-name", 5L);
    assertThat(backupState.getState()).isEqualTo(BackupStateDto.INCOMPLETE);
  }

  @Test
  void shouldReturnBackupStateProgress() throws IOException {
    final var firstSnapshotInfo = mock(SnapshotInfo.class);
    final var snapshotResponse = mock(GetSnapshotResponse.class);
    final var lastSnapshotInfo = mock(SnapshotInfo.class);
    final long now = Instant.now().toEpochMilli();

    // Set up operate properties
    when(backupProps.incompleteCheckTimeoutInSeconds())
        .thenReturn(incompleteCheckTimeoutLengthSeconds);
    // Set up Snapshot details
    final Map<String, JsonData> metadata =
        MetadataMarshaller.asJson(new Metadata(1L, "1", 1, 3), null);
    when(firstSnapshotInfo.metadata()).thenReturn(metadata);
    when(firstSnapshotInfo.snapshot()).thenReturn("snapshot-name");
    //    when(firstSnapshotInfo.uuid()).thenReturn("uuid-first");
    when(lastSnapshotInfo.snapshot()).thenReturn("last-snapshot-name");
    //    when(firstSnapshotInfo.uuid()).thenReturn("uuid-last");
    when(firstSnapshotInfo.state()).thenReturn(SnapshotState.SUCCESS.name());
    when(lastSnapshotInfo.state()).thenReturn(SnapshotState.SUCCESS.name());
    when(firstSnapshotInfo.startTimeInMillis()).thenReturn(now - 4_000);
    when(lastSnapshotInfo.startTimeInMillis()).thenReturn(now - 200);
    when(lastSnapshotInfo.endTimeInMillis()).thenReturn(now - 5);
    // Set up Snapshot response
    when(snapshotResponse.snapshots()).thenReturn(List.of(firstSnapshotInfo, lastSnapshotInfo));
    when(esClient.snapshot().get((GetSnapshotRequest) ArgumentMatchers.any()))
        .thenReturn(snapshotResponse);

    // Test
    final var backupState = backupRepository.getBackupState("repository-name", 5L);
    assertThat(backupState.getState()).isEqualTo(BackupStateDto.IN_PROGRESS);
  }

  @Test
  void shouldReturnAvailableIndices() throws IOException {
    // given
    when(esClient.indices().get((GetIndexRequest) Mockito.any()))
        .thenReturn(GetIndexResponse.of(b -> b));

    // when
    final var result = backupRepository.checkAllIndicesExist(List.of("missingIndex"));

    // then
    assertThat(result.size()).isEqualTo(0);
    verify(esClient.indices(), atLeastOnce())
        .get((GetIndexRequest) argThat(r -> ((GetIndexRequest) r).ignoreUnavailable()));
  }

  @Test
  void shouldThrowBackupNotFoundOnEmptySnapshotResponse() throws IOException {
    final var snapshotResponse = mock(GetSnapshotResponse.class);

    // Set up Snapshot response
    when(snapshotResponse.snapshots()).thenReturn(List.of());
    when(esClient.snapshot().get((GetSnapshotRequest) ArgumentMatchers.any()))
        .thenReturn(snapshotResponse);

    // Test
    assertThrows(
        ResourceNotFoundException.class,
        () -> backupRepository.findSnapshots("repository-name", 5L),
        "No backup with id [5] found.");
  }

  @Test
  public void shouldForwardVerboseFlagToES() throws IOException {
    // mock calls to `findSnapshot` and `operateProperties`
    final SnapshotInfo snapshotInfo = mock(SnapshotInfo.class, RETURNS_DEEP_STUBS);
    when(snapshotInfo.state()).thenReturn(SnapshotState.IN_PROGRESS.toString());
    when(snapshotInfo.snapshot()).thenReturn(snapshotName);
    final var response = mock(GetSnapshotResponse.class, RETURNS_DEEP_STUBS);
    when(response.snapshots()).thenReturn(List.of(snapshotInfo));
    when(esClient.snapshot().get(any(GetSnapshotRequest.class))).thenReturn(response);

    final var responses = backupRepository.getBackups(repositoryName, false);
    verify(esClient.snapshot()).get(argThat((GetSnapshotRequest req) -> !req.verbose()));
    assertThat(responses)
        .singleElement()
        .satisfies(
            details -> {
              assertThat(details.getBackupId()).isEqualTo(backupId);
              assertThat(details.getState()).isEqualTo(BackupStateDto.IN_PROGRESS);
              assertThat(details.getDetails())
                  .allSatisfy(
                      detail -> {
                        assertThat(detail.getState())
                            .isEqualTo(BackupStateDto.IN_PROGRESS.toString());
                        assertThat(detail.getStartTime()).isNull();
                      });
            });
  }
}
