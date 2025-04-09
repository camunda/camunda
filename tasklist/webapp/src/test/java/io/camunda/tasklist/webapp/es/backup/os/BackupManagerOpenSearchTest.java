/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.es.backup.os;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.es.backup.Metadata;
import io.camunda.tasklist.webapp.management.dto.BackupStateDto;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.snapshot.GetSnapshotRequest;
import org.opensearch.client.opensearch.snapshot.SnapshotInfo;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.endpoints.SimpleEndpoint;

@ExtendWith(MockitoExtension.class)
class BackupManagerOpenSearchTest {

  @Mock OpenSearchTransport openSearchTransport;

  @Mock(strictness = Strictness.LENIENT)
  private OpenSearchAsyncClient openSearchAsyncClient;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private OpenSearchClient openSearchClient;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private TasklistProperties tasklistProperties;

  @InjectMocks private BackupManagerOpenSearch backupManagerOpenSearch;

  @BeforeEach
  public void setUp() {
    when(tasklistProperties.getBackup().getRepositoryName()).thenReturn("test-repo");
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
        new Metadata().setBackupId(2L).setPartCount(3).setPartNo(1).setVersion("8.6.1");
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
}
