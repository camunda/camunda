/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.service.exceptions.OptimizeByQueryFailureException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.BulkByScrollFailure;
import org.opensearch.client.opensearch.snapshot.GetRepositoryRequest;
import org.opensearch.client.opensearch.tasks.GetTasksResponse;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.endpoints.SimpleEndpoint;

@ExtendWith(MockitoExtension.class)
public class OptimizeOpenSearchClientTest {
  @Mock private OpenSearchAsyncClient openSearchAsyncClient;

  @InjectMocks private OptimizeOpenSearchClient optimizeOpenSearchClient;

  @Test
  void shouldValidateRepositoryExistsDoNotDeserializeOpenSearchResponse() throws IOException {
    final OpenSearchTransport openSearchTransport = mock(OpenSearchTransport.class);
    when(openSearchAsyncClient._transport()).thenReturn(openSearchTransport);
    when(openSearchTransport.performRequestAsync(any(), any(), any()))
        .thenReturn(mock(CompletableFuture.class));

    optimizeOpenSearchClient.verifyRepositoryExists(
        GetRepositoryRequest.of(grr -> grr.name("test-repo")));

    final ArgumentCaptor<SimpleEndpoint> endpointArgumentCaptor =
        ArgumentCaptor.forClass(SimpleEndpoint.class);
    verify(openSearchTransport).performRequestAsync(any(), endpointArgumentCaptor.capture(), any());
    assertThat(endpointArgumentCaptor.getValue().responseDeserializer()).isNull();
  }

  @Test
  void shouldThrowWhenTaskResponseContainsFailures() {
    // given -- e.g. a version conflict surfaced via Conflicts.Abort on a delete-by-query task
    final BulkByScrollFailure conflictFailure =
        BulkByScrollFailure.of(
            f ->
                f.id("instance-1")
                    .index("test-index")
                    .cause(c -> c.type("version_conflict_engine_exception").reason("conflict"))
                    .status(409));
    final GetTasksResponse taskResponse =
        GetTasksResponse.of(
            b ->
                b.completed(true)
                    .task(
                        t ->
                            t.id(1L)
                                .node("node-1")
                                .action("indices:data/write/delete/byquery")
                                .startTimeInMillis(0L)
                                .runningTimeInNanos(0L)
                                .cancellable(false)
                                .headers(Map.of())
                                .type("transport"))
                    .response(
                        r ->
                            r.total(1L)
                                .updated(0L)
                                .created(0L)
                                .deleted(0L)
                                .batches(1)
                                .took(0L)
                                .timedOut(false)
                                .noops(0L)
                                .retries(rt -> rt.bulk(0L).search(0L))
                                .requestsPerSecond(0f)
                                .throttledMillis(0L)
                                .throttledUntilMillis(0L)
                                .versionConflicts(0L)
                                .failures(conflictFailure)));

    // when / then
    assertThatThrownBy(() -> OptimizeOpenSearchClient.validateTaskResponse(taskResponse))
        .isInstanceOf(OptimizeByQueryFailureException.class);
  }

  @Test
  void shouldNotThrowWhenTaskResponseHasNoFailures() {
    // given
    final GetTasksResponse taskResponse =
        GetTasksResponse.of(
            b ->
                b.completed(true)
                    .task(
                        t ->
                            t.id(1L)
                                .node("node-1")
                                .action("indices:data/write/delete/byquery")
                                .startTimeInMillis(0L)
                                .runningTimeInNanos(0L)
                                .cancellable(false)
                                .headers(Map.of())
                                .type("transport"))
                    .response(
                        r ->
                            r.total(1L)
                                .updated(1L)
                                .created(0L)
                                .deleted(0L)
                                .batches(1)
                                .took(0L)
                                .timedOut(false)
                                .noops(0L)
                                .retries(rt -> rt.bulk(0L).search(0L))
                                .requestsPerSecond(0f)
                                .throttledMillis(0L)
                                .throttledUntilMillis(0L)
                                .versionConflicts(0L)
                                .failures(List.of())));

    // when / then
    assertThatCode(() -> OptimizeOpenSearchClient.validateTaskResponse(taskResponse))
        .doesNotThrowAnyException();
  }
}
