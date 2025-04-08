/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.store.opensearch.client.sync;

import static io.camunda.operate.store.opensearch.dsl.RequestDSL.getSnapshotRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.repositoryRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.opensearch.ExtendedOpenSearchClient;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchSnapshotOperations;
import io.camunda.operate.store.opensearch.response.OpenSearchSnapshotInfo;
import io.camunda.operate.store.opensearch.response.SnapshotState;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.snapshot.GetRepositoryRequest;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
class OpenSearchSnapshotOperationsTest {

  @Mock ExtendedOpenSearchClient openSearchClient;

  @Mock Logger logger;

  private OpenSearchSnapshotOperations snapshotOperations;

  @BeforeEach
  void setUp() {
    snapshotOperations = new OpenSearchSnapshotOperations(logger, openSearchClient);
    assertThat(snapshotOperations).isNotNull();
  }

  @Test
  void shouldThrowExceptionForNoRepository() {
    final var exception =
        assertThrows(
            OperateRuntimeException.class,
            () -> snapshotOperations.getRepository(new GetRepositoryRequest.Builder()));
    assertThat(exception.getMessage()).isEqualTo("Get repository needs at least one name.");
  }

  @Test
  void shouldGetRepository() throws IOException {
    final var response =
        snapshotOperations.getRepository(repositoryRequestBuilder("test-repository"));
    assertThat(response).isNotNull();
    verify(openSearchClient).arbitraryRequest("GET", "/_snapshot/test-repository", "{}");
  }

  @Test
  void shouldGetOnlyFirstRepository() throws IOException {
    final var response =
        snapshotOperations.getRepository(
            new GetRepositoryRequest.Builder().name("test-repository", "test-repository2"));
    assertThat(response).isNotNull();
    verify(openSearchClient).arbitraryRequest("GET", "/_snapshot/test-repository", "{}");
  }

  @Test
  void shouldThrowExceptionIfCantGetRepository() throws Exception {
    when(openSearchClient.arbitraryRequest(
            "GET", "/_snapshot/test-repository-does-not-exist", "{}"))
        .thenThrow(IOException.class);
    final var exception =
        assertThrows(
            OperateRuntimeException.class,
            () -> snapshotOperations.getRepository(repositoryRequestBuilder("test-repository")));
    assertThat(exception).hasMessage("Failed to get repository test-repository");
  }

  @Test
  void shouldGetSnapshot() throws IOException {
    final Map<String, Object> openSearchResponse =
        Map.of(
            "snapshots",
            List.of(
                Map.of(
                    "snapshot",
                    "snapshot-name",
                    "uuid",
                    "uuid-value",
                    "state",
                    "STARTED",
                    "start_time_in_millis",
                    23L,
                    "metadata",
                    Map.of(),
                    "failures",
                    List.of())));
    when(openSearchClient.arbitraryRequest("GET", "/_snapshot/test-repository/test-snapshot", "{}"))
        .thenReturn(openSearchResponse);
    final var response =
        snapshotOperations.get(
            getSnapshotRequestBuilder("test-repository", "test-snapshot").build());
    assertThat(response.snapshots()).hasSize(1);
    final var snapshotInfo = response.snapshots().getFirst();
    assertSnapshotInfo(snapshotInfo);
  }

  @Test
  void shouldThrowExceptionIfCantGetSnapshot() throws Exception {
    when(openSearchClient.arbitraryRequest("GET", "/_snapshot/test-repository/test-snapshot", "{}"))
        .thenThrow(new IOException("Connection error"));
    final var exception =
        assertThrows(
            OperateRuntimeException.class,
            () ->
                snapshotOperations.get(
                    getSnapshotRequestBuilder("test-repository", "test-snapshot").build()));
    assertThat(exception)
        .hasMessage("Failed to get snapshot test-snapshot in repository test-repository");
    assertThat(exception.getCause()).isInstanceOf(IOException.class);
  }

  private void assertSnapshotInfo(final OpenSearchSnapshotInfo snapshotInfo) {
    assertThat(snapshotInfo.getState()).isEqualTo(SnapshotState.STARTED);
    assertThat(snapshotInfo.getStartTimeInMillis()).isEqualTo(23L);
    assertThat(snapshotInfo.getMetadata()).isEmpty();
    assertThat(snapshotInfo.getFailures()).isEmpty();
    assertThat(snapshotInfo.getUuid()).isEqualTo("uuid-value");
    assertThat(snapshotInfo.getSnapshot()).isEqualTo("snapshot-name");
  }
}
