/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.deletion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.util.AsyncDocumentPipeline.DocumentBatch;
import io.camunda.exporter.tasks.util.FilterByKeysQuery;
import io.camunda.exporter.tasks.util.SearchAfter;
import io.camunda.search.schema.config.DeleterConfiguration;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.slf4j.Logger;

class DeleteByIdPipelineTest {
  private final DocumentBatch<DocToDelete, SearchAfter> batch =
      DocumentBatch.from(
          List.of(new DocToDelete("123", null, "test-index")), new TestSearchAfter());

  private final Logger logger = mock(Logger.class);
  private final DeleteByIdSupport support = mock(DeleteByIdSupport.class);
  private final CamundaExporterMetrics metrics = mock(CamundaExporterMetrics.class);

  private DeleterConfiguration config;
  private DeleteByIdPipeline pipeline;

  @BeforeEach
  void setup() {
    config = new DeleterConfiguration();
    config.setRetryDelayMs(1);
    pipeline = new DeleteByIdPipeline(config, support, metrics, logger);
  }

  @Test
  void shouldSearchThenDelete() {
    // given
    final var filter = FilterByKeysQuery.of(Map.of("processInstanceKey", List.of("1", "2", "3")));
    when(support.searchDocumentsToDelete(any(), any(), any(), anyInt()))
        .thenReturn(CompletableFuture.completedFuture(batch));
    when(support.deleteDocBatch(any())).thenReturn(CompletableFuture.completedFuture(1));

    // when
    final var result = pipeline.deleteDocumentsById("test-index", filter, Runnable::run);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(5));

    final var inOrder = Mockito.inOrder(support);
    inOrder.verify(support).searchDocumentsToDelete("test-index", filter, null, 2500);
    inOrder.verify(support).deleteDocBatch(batch);
    verifyNoMoreInteractions(support);
  }

  @ParameterizedTest
  @MethodSource("retryableErrors")
  void shouldRetryBatchIfProblemDeleting(
      final Throwable throwable, final int firstBatchSize, final int secondBatchSize) {
    // given
    final var filter = FilterByKeysQuery.of(Map.of("processInstanceKey", List.of("1", "2", "3")));
    when(support.searchDocumentsToDelete(any(), any(), any(), anyInt()))
        .thenReturn(CompletableFuture.completedFuture(batch));
    when(support.deleteDocBatch(any()))
        .thenReturn(CompletableFuture.failedFuture(throwable))
        .thenReturn(CompletableFuture.completedFuture(1));

    // when
    final var result = pipeline.deleteDocumentsById("test-index", filter, Runnable::run);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(5));

    final var inOrder = Mockito.inOrder(support, metrics);
    inOrder.verify(support).searchDocumentsToDelete("test-index", filter, null, firstBatchSize);
    inOrder.verify(support).deleteDocBatch(batch);
    inOrder.verify(metrics).recordDeletionBatchRetry();
    inOrder.verify(support).searchDocumentsToDelete("test-index", filter, null, secondBatchSize);
    inOrder.verify(support).deleteDocBatch(batch);
    verifyNoMoreInteractions(support);
  }

  @Test
  void shouldFailIfNonRetryableExceptionThrown() {
    // given
    final var filter = FilterByKeysQuery.of(Map.of("processInstanceKey", List.of("1", "2", "3")));
    when(support.searchDocumentsToDelete(any(), any(), any(), anyInt()))
        .thenReturn(CompletableFuture.completedFuture(batch));
    when(support.deleteDocBatch(any()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("failed")));

    // when
    final var result = pipeline.deleteDocumentsById("test-index", filter, Runnable::run);

    // then
    assertThat(result)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableThat()
        .havingRootCause()
        .withMessage("failed");

    final var inOrder = Mockito.inOrder(support);
    inOrder.verify(support).searchDocumentsToDelete("test-index", filter, null, 2500);
    inOrder.verify(support).deleteDocBatch(batch);
    verifyNoMoreInteractions(support);
  }

  @Test
  void shouldRetryBatchIfNotAllDocsDeleted() {
    // given
    final var filter = FilterByKeysQuery.of(Map.of("processInstanceKey", List.of("1", "2", "3")));
    when(support.searchDocumentsToDelete(any(), any(), any(), anyInt()))
        .thenReturn(CompletableFuture.completedFuture(batch));
    when(support.deleteDocBatch(any()))
        .thenReturn(CompletableFuture.completedFuture(0))
        .thenReturn(CompletableFuture.completedFuture(1));

    // when
    final var result = pipeline.deleteDocumentsById("test-index", filter, Runnable::run);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(5));

    final var inOrder = Mockito.inOrder(support, metrics);
    inOrder.verify(support).searchDocumentsToDelete("test-index", filter, null, 2500);
    inOrder.verify(support).deleteDocBatch(batch);
    inOrder.verify(metrics).recordDeletionBatchRetry();
    inOrder.verify(support).searchDocumentsToDelete("test-index", filter, null, 2500);
    inOrder.verify(support).deleteDocBatch(batch);
    verifyNoMoreInteractions(support);
  }

  static Stream<Arguments> retryableErrors() {
    return Stream.of(
        Arguments.of(
            new CompletionException(new IncompleteDocumentDeletionException("conflict")),
            2500,
            2500),
        Arguments.of(new CompletionException(mock(ElasticsearchException.class)), 2500, 2500),
        Arguments.of(new CompletionException(mock(OpenSearchException.class)), 2500, 2500),
        Arguments.of(new IncompleteDocumentDeletionException("conflict"), 2500, 2500),
        Arguments.of(mock(ElasticsearchException.class), 2500, 2500),
        Arguments.of(mock(OpenSearchException.class), 2500, 2500),
        // causes batch to be reduced in size on second retry
        Arguments.of(new CompletionException(new SocketTimeoutException()), 2500, 1250),
        Arguments.of(new RuntimeException(new SocketTimeoutException()), 2500, 1250),
        Arguments.of(new SocketTimeoutException(), 2500, 1250));
  }

  private record TestSearchAfter() implements SearchAfter {}
}
