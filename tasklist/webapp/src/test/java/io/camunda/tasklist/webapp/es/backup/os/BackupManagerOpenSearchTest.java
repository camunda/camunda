/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.es.backup.os;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.backup.Prio1Backup;
import io.camunda.tasklist.schema.backup.Prio2Backup;
import io.camunda.tasklist.schema.backup.Prio3Backup;
import io.camunda.tasklist.schema.backup.Prio4Backup;
import io.camunda.tasklist.webapp.es.backup.Metadata;
import io.camunda.tasklist.webapp.es.backup.os.GetCustomSnapshotResponse.Builder;
import io.camunda.tasklist.webapp.es.backup.os.response.SnapshotState;
import io.camunda.tasklist.webapp.management.dto.BackupStateDto;
import io.camunda.tasklist.webapp.management.dto.TakeBackupRequestDto;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.snapshot.CreateSnapshotRequest;
import org.opensearch.client.opensearch.snapshot.CreateSnapshotResponse;
import org.opensearch.client.opensearch.snapshot.GetSnapshotRequest;
import org.opensearch.client.opensearch.snapshot.SnapshotInfo;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.endpoints.SimpleEndpoint;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BackupManagerOpenSearchTest {

  private static final String TASKLIST_VERSION = "8.6.1";

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  OpenSearchTransport openSearchTransport;

  private final long backupId = 2L;
  final Metadata metadata =
      new Metadata()
          .setBackupId(backupId)
          .setPartCount(3)
          .setPartNo(1)
          .setVersion(TASKLIST_VERSION);
  private final String repositoryName = "test-repo";

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private OpenSearchAsyncClient openSearchAsyncClient;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private OpenSearchClient openSearchClient;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private TasklistProperties tasklistProperties;

  @Mock private List<Prio1Backup> prio1BackupIndices;
  @Mock private List<Prio2Backup> prio2BackupTemplates;
  @Mock private List<Prio3Backup> prio3BackupTemplates;
  @Mock private List<Prio4Backup> prio4BackupIndices;

  @InjectMocks private BackupManagerOpenSearch backupManagerOpenSearch;

  @BeforeEach
  public void setUp() {
    when(tasklistProperties.getBackup().getRepositoryName()).thenReturn(repositoryName);
    when(openSearchClient._transport()).thenReturn(openSearchTransport);
    when(openSearchAsyncClient._transport()).thenReturn(openSearchTransport);
  }

  @Test
  void shouldValidateRepositoryExistsDoNotDeserializeOpenSearchResponse() throws IOException {
    when(openSearchTransport.performRequestAsync(any(), any(), any()))
        .thenReturn(mock(CompletableFuture.class));

    backupManagerOpenSearch.validateRepositoryExists();

    final ArgumentCaptor<SimpleEndpoint> endpointArgumentCaptor =
        ArgumentCaptor.forClass(SimpleEndpoint.class);
    verify(openSearchTransport).performRequestAsync(any(), endpointArgumentCaptor.capture(), any());
    assertThat(endpointArgumentCaptor.getValue().responseDeserializer()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUCCESS", "IN_PROGRESS", "PARTIAL", "FAILED"})
  void shouldReturnPartialDataWhenVerboseIsFalse(final String state) throws IOException {
    // given

    final var metadata =
        new Metadata().setBackupId(2L).setPartCount(3).setPartNo(1).setVersion(TASKLIST_VERSION);
    final var snapshots = new ArrayList<SnapshotInfo>(metadata.getPartCount());
    for (int i = 1; i <= metadata.getPartCount(); i++) {
      final var copy = new Metadata(metadata);
      copy.setPartNo(i);
      snapshots.add(
          SnapshotInfo.of(
              sib ->
                  sib.snapshot(copy.buildSnapshotName())
                      .state(state)
                      .uuid(UUID.randomUUID().toString())
                      .dataStreams(List.of())
                      .indices(List.of())));
    }

    final var snapshotResponse = GetCustomSnapshotResponse.of(b -> b.snapshots(snapshots));
    when(openSearchTransport.performRequest(any(), any(), any())).thenReturn(snapshotResponse);
    when(tasklistProperties.getBackup().getRepositoryName()).thenReturn(repositoryName);

    // when
    final var backupResponse = backupManagerOpenSearch.getBackups(false, null);

    // then
    assertThat(backupResponse)
        .singleElement()
        .satisfies(
            snap -> {
              final var expectedState =
                  switch (state) {
                    case "SUCCESS" -> BackupStateDto.COMPLETED;
                    case "IN_PROGRESS" -> BackupStateDto.IN_PROGRESS;
                    case "PARTIAL", "FAILED" -> BackupStateDto.FAILED;
                    default -> null;
                  };
              assertThat(snap.getState()).isEqualTo(expectedState);
              assertThat(snap.getBackupId()).isEqualTo(metadata.getBackupId());
              assertThat(snap.getDetails())
                  .hasSize(metadata.getPartCount())
                  .zipSatisfy(
                      snapshots,
                      (detail, snapshotInfo) -> {
                        assertThat(detail.getState()).isEqualTo(state);
                        assertThat(detail.getSnapshotName()).isEqualTo(snapshotInfo.snapshot());
                      });
            });
  }

  @Test
  public void shouldForwardPatternToOS() throws IOException {
    // given
    final var snapshotResponse = mock(GetCustomSnapshotResponse.class, Answers.RETURNS_DEEP_STUBS);
    when(snapshotResponse.snapshots()).thenReturn(List.of());
    when(openSearchTransport.performRequest(any(), any(), any())).thenReturn(snapshotResponse);

    // when
    final var backupResponse = backupManagerOpenSearch.getBackups(false, "2025*");
    // then

    verify(openSearchTransport)
        .performRequest(
            argThat((GetSnapshotRequest r) -> r.snapshot().contains("camunda_tasklist_2025*")),
            any(),
            any());
  }

  @Test
  public void shouldWaitForSnapshotWithTimeout() throws IOException {
    // given
    final int timeout = 1;
    final SnapshotState snapshotState = SnapshotState.IN_PROGRESS;

    final SnapshotInfo snapshotInfo = mock(SnapshotInfo.class, RETURNS_DEEP_STUBS);
    when(snapshotInfo.state()).thenReturn(snapshotState.name());
    when(snapshotInfo.snapshot()).thenReturn(metadata.buildSnapshotName());
    when(tasklistProperties.getBackup().getSnapshotTimeout()).thenReturn(timeout);
    final GetCustomSnapshotResponse response =
        new GetCustomSnapshotResponse.Builder().snapshots(snapshotInfo).build();
    when(openSearchClient._transport().performRequest(any(), any(), any())).thenReturn(response);

    // when
    final boolean finished =
        backupManagerOpenSearch.isSnapshotFinishedWithinTimeout(snapshotInfo.snapshot());

    // then
    assertFalse(finished);
    verify(openSearchClient._transport(), atLeast(5)).performRequest(any(), any(), any());
  }

  @Test
  public void shouldWaitForSnapshotTillCompleted() throws IOException {
    // given
    final int timeout = 0;
    when(tasklistProperties.getBackup().getSnapshotTimeout()).thenReturn(timeout);
    final int numberOfSnapshots = 3;
    final var snapshotInfos = new ArrayList<SnapshotInfo>();

    final List<Metadata> metadata = new ArrayList<>();
    for (int i = 0; i < numberOfSnapshots; i++) {
      metadata.add(
          new Metadata()
              .setBackupId(backupId)
              .setPartCount(numberOfSnapshots)
              .setPartNo(i)
              .setVersion("8.3.0"));
    }

    for (int i = 0; i < numberOfSnapshots; i++) {
      final SnapshotInfo snapshotInfo = mock(SnapshotInfo.class, RETURNS_DEEP_STUBS);
      when(snapshotInfo.state())
          .thenReturn(SnapshotState.IN_PROGRESS.name())
          .thenReturn(SnapshotState.IN_PROGRESS.name())
          .thenReturn(SnapshotState.SUCCESS.name());
      when(snapshotInfo.snapshot()).thenReturn(metadata.get(i).buildSnapshotName());
      snapshotInfos.add(snapshotInfo);
    }

    final CompletableFuture<CreateSnapshotResponse> failedResponse =
        CompletableFuture.failedFuture(new SocketTimeoutException("Request timed out."));
    when(openSearchAsyncClient.snapshot().create(any(CreateSnapshotRequest.class)))
        .thenReturn(failedResponse);
    when(openSearchClient._transport().performRequest(any(), any(), any()))
        .thenReturn(new Builder().snapshots(snapshotInfos).build());

    // when
    for (int i = 0; i < numberOfSnapshots; i++) {
      final Metadata snapshotMetadata = metadata.get(i);
      final String snapshotName = snapshotMetadata.buildSnapshotName();
      backupManagerOpenSearch.executeSnapshotting(
          CreateSnapshotRequest.of(
              csr ->
                  csr.repository(repositoryName)
                      .snapshot(snapshotName)
                      .indices(List.of("index-1", "index-2"))
                      .ignoreUnavailable(false)
                      .includeGlobalState(true)
                      .metadata(
                          Map.of(
                              "backupId", JsonData.of(snapshotMetadata.getBackupId()),
                              "version", JsonData.of(snapshotMetadata.getVersion()),
                              "partNo", JsonData.of(snapshotMetadata.getPartNo()),
                              "partCount", JsonData.of(snapshotMetadata.getPartCount())))
                      .featureStates("none")
                      .waitForCompletion(true)));
    }

    // then
    // 4 times numberOfSnapshots because there are 3 mocked states plus one time for findSnapshots
    // being called in the
    // exceptionally branch before entering the isSnapshotFinishedWithinTimeout loop to get the name
    verify(openSearchClient._transport(), times(numberOfSnapshots * 4))
        .performRequest(any(), any(), any());
    final var response = backupManagerOpenSearch.getBackupState(backupId);
    assertThat(response.getState()).isEqualTo(BackupStateDto.COMPLETED);
  }

  @Test
  void shouldReturnInProgressStateWhenBackupIsStillRunning() throws IOException {
    // given
    when(tasklistProperties.getVersion()).thenReturn(TASKLIST_VERSION);
    // run takeBackup in parallel to simulate an ongoing backup
    when(openSearchClient.snapshot().create(any(CreateSnapshotRequest.class)))
        .thenAnswer(
            invocation -> {
              Thread.sleep(1000);
              return mock(CompletableFuture.class);
            });
    when(openSearchTransport.performRequest(any(), any(), any()))
        .thenReturn(mock(GetCustomSnapshotResponse.class));
    backupManagerOpenSearch.takeBackup(new TakeBackupRequestDto().setBackupId(backupId));

    final var metadata =
        new Metadata().setBackupId(2L).setPartCount(3).setPartNo(1).setVersion(TASKLIST_VERSION);
    final var snapshots = new ArrayList<SnapshotInfo>(metadata.getPartCount());
    // return (partCount - 1) SUCCESS snapshots, one is remaining
    for (int i = 1; i <= metadata.getPartCount() - 1; i++) {
      final var copy = new Metadata(metadata);
      copy.setPartNo(i);
      snapshots.add(
          SnapshotInfo.of(
              sib ->
                  sib.snapshot(copy.buildSnapshotName())
                      .state("SUCCESS")
                      .uuid(UUID.randomUUID().toString())
                      .dataStreams(List.of())
                      .indices(List.of())));
    }

    final var snapshotResponse = GetCustomSnapshotResponse.of(b -> b.snapshots(snapshots));
    when(openSearchTransport.performRequest(any(), any(), any())).thenReturn(snapshotResponse);
    when(tasklistProperties.getBackup().getRepositoryName()).thenReturn(repositoryName);

    // when
    final var backupResponse = backupManagerOpenSearch.getBackupState(backupId);

    // then
    assertThat(backupResponse.getState()).isEqualTo(BackupStateDto.IN_PROGRESS);
  }

  @ParameterizedTest
  @MethodSource("incompleteBackupStateProvider")
  void shouldReturnBackupStateIncompleteWhenEndIsInTimeout(
      final Supplier<String> lastSnapshotEndTime, final BackupStateDto expectedBackupStatus)
      throws IOException {
    final long now = Instant.now().toEpochMilli();
    when(tasklistProperties.getBackup().getIncompleteCheckTimeoutInSeconds()).thenReturn(1L);
    final var metadata =
        new Metadata().setBackupId(2L).setPartCount(3).setPartNo(1).setVersion(TASKLIST_VERSION);
    final var snapshots = new ArrayList<SnapshotInfo>(metadata.getPartCount());

    snapshots.add(
        SnapshotInfo.of(
            sib ->
                sib.snapshot(metadata.buildSnapshotName())
                    .state("SUCCESS")
                    .uuid(UUID.randomUUID().toString())
                    .dataStreams(List.of())
                    .indices(List.of())
                    .startTimeInMillis(String.valueOf(now - 5000L))
                    .endTimeInMillis(String.valueOf(now - 4000L))));

    snapshots.add(
        SnapshotInfo.of(
            sib ->
                sib.snapshot(new Metadata(metadata).setPartNo(2).buildSnapshotName())
                    .state("SUCCESS")
                    .uuid(UUID.randomUUID().toString())
                    .dataStreams(List.of())
                    .indices(List.of())
                    .startTimeInMillis(String.valueOf(now - 3000L))
                    .endTimeInMillis(lastSnapshotEndTime.get())));

    when(openSearchTransport.performRequest(any(), any(), any()))
        .thenReturn(GetCustomSnapshotResponse.of(b -> b.snapshots(snapshots)));

    // Test
    final var backupState = backupManagerOpenSearch.getBackupState(backupId);
    assertThat(backupState.getState()).isEqualTo(expectedBackupStatus);
  }

  static Stream<Arguments> incompleteBackupStateProvider() {
    return Stream.of(
        Arguments.of(
            (Supplier<String>) () -> String.valueOf(Instant.now().toEpochMilli() - 2000L),
            BackupStateDto.INCOMPLETE),
        Arguments.of(
            (Supplier<String>) () -> String.valueOf(Instant.now().toEpochMilli() - 0L),
            BackupStateDto.IN_PROGRESS),
        Arguments.of((Supplier<String>) () -> null, BackupStateDto.INCOMPLETE));
  }

  @Test
  void shouldThrowNotFoundApiExceptionWhenBackupDoesNotExist() throws IOException {
    // given
    final long nonExistingBackupId = 999L;
    final var snapshotResponse = GetCustomSnapshotResponse.of(b -> b.snapshots(List.of()));
    when(openSearchTransport.performRequest(any(), any(), any())).thenReturn(snapshotResponse);

    // when & then
    assertThatThrownBy(() -> backupManagerOpenSearch.getBackupState(nonExistingBackupId))
        .isInstanceOf(NotFoundApiException.class)
        .hasMessageContaining("No backup with id [999] found.");
  }

  @Test
  void shouldIncludeStartTimeInResponseForSuccessfulBackups() throws IOException {
    // given
    final long backupId = 3L;
    final long startTime = Instant.now().toEpochMilli() - 10_000L;
    final var metadata =
        new Metadata()
            .setBackupId(backupId)
            .setPartCount(1)
            .setPartNo(1)
            .setVersion(TASKLIST_VERSION);
    final var snapshots = new ArrayList<SnapshotInfo>(metadata.getPartCount());
    for (int i = 1; i <= metadata.getPartCount(); i++) {
      final var copy = new Metadata(metadata);
      copy.setPartNo(i);
      snapshots.add(
          SnapshotInfo.of(
              sib ->
                  sib.snapshot(copy.buildSnapshotName())
                      .state("SUCCESS")
                      .uuid(UUID.randomUUID().toString())
                      .dataStreams(List.of())
                      .indices(List.of())
                      .startTimeInMillis(String.valueOf(startTime))));
    }

    final var snapshotResponse = GetCustomSnapshotResponse.of(b -> b.snapshots(snapshots));
    when(openSearchTransport.performRequest(any(), any(), any())).thenReturn(snapshotResponse);
    when(tasklistProperties.getBackup().getRepositoryName()).thenReturn(repositoryName);

    // when
    final var backupResponse = backupManagerOpenSearch.getBackupState(backupId);

    // then
    assertThat(backupResponse.getDetails()).isNotEmpty();
    assertThat(backupResponse.getDetails().getFirst().getStartTime()).isNotNull();
    assertThat(backupResponse.getDetails().getFirst().getStartTime().toInstant().toEpochMilli())
        .isEqualTo(startTime);
  }
}
