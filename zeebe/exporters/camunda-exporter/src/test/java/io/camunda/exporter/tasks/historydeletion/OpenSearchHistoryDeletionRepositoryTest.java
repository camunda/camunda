/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.historydeletion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.exporter.tasks.utils.TestExporterResourceProvider;
import io.camunda.zeebe.exporter.common.historydeletion.HistoryDeletionConfiguration;
import java.time.InstantSource;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.ErrorCause;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class OpenSearchHistoryDeletionRepositoryTest {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(OpenSearchHistoryDeletionRepositoryTest.class);

  private OpenSearchAsyncClient client;
  private OpenSearchHistoryDeletionRepository repository;

  @BeforeEach
  void setUp() {
    client = mock(OpenSearchAsyncClient.class);
    final var resourceProvider = new TestExporterResourceProvider("test-", false);
    final var config = new HistoryDeletionConfiguration();
    config.setQueueBatchSize(10);

    repository =
        new OpenSearchHistoryDeletionRepository(
            resourceProvider, client, Runnable::run, LOGGER, 1, config, InstantSource.system());
  }

  @Test
  void shouldIgnoreDocumentMissingExceptionErrors() throws Exception {
    // given - mock response with document_missing_exception errors
    final var item1 = mock(BulkResponseItem.class);
    final var error1 = mock(ErrorCause.class);
    when(error1.type()).thenReturn("document_missing_exception");
    when(error1.reason()).thenReturn("Document not found");
    when(item1.error()).thenReturn(error1);
    when(item1.id()).thenReturn("op1");

    final var item2 = mock(BulkResponseItem.class);
    final var error2 = mock(ErrorCause.class);
    when(error2.type()).thenReturn("document_missing_exception");
    when(error2.reason()).thenReturn("Document not found");
    when(item2.error()).thenReturn(error2);
    when(item2.id()).thenReturn("op2");

    final var bulkResponse = mock(BulkResponse.class);
    when(bulkResponse.errors()).thenReturn(true);
    when(bulkResponse.items()).thenReturn(List.of(item1, item2));

    when(client.bulk(any(BulkRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(bulkResponse));

    // when
    final var result = repository.completeOperations(List.of("op1", "op2"));

    // then - should complete successfully ignoring the errors
    assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(5));
    assertThat(result.join()).containsExactly("op1", "op2");
  }

  @Test
  void shouldFailOnActualErrors() throws Exception {
    // given - mock response with actual error (not document_missing_exception)
    final var item1 = mock(BulkResponseItem.class);
    final var error1 = mock(ErrorCause.class);
    when(error1.type()).thenReturn("version_conflict_engine_exception");
    when(error1.reason()).thenReturn("Version conflict");
    when(item1.error()).thenReturn(error1);
    when(item1.id()).thenReturn("op1");

    final var bulkResponse = mock(BulkResponse.class);
    when(bulkResponse.errors()).thenReturn(true);
    when(bulkResponse.items()).thenReturn(List.of(item1));

    when(client.bulk(any(BulkRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(bulkResponse));

    // when
    final var result = repository.completeOperations(List.of("op1"));

    // then - should fail
    assertThatThrownBy(() -> result.join())
        .hasCauseInstanceOf(RuntimeException.class)
        .hasMessageContaining("Bulk updating operations by ids");
  }

  @Test
  void shouldIgnoreSomeErrorsAndFailOnOthers() throws Exception {
    // given - mock response with mixed errors
    final var item1 = mock(BulkResponseItem.class);
    final var error1 = mock(ErrorCause.class);
    when(error1.type()).thenReturn("document_missing_exception");
    when(error1.reason()).thenReturn("Document not found");
    when(item1.error()).thenReturn(error1);
    when(item1.id()).thenReturn("op1");

    final var item2 = mock(BulkResponseItem.class);
    final var error2 = mock(ErrorCause.class);
    when(error2.type()).thenReturn("version_conflict_engine_exception");
    when(error2.reason()).thenReturn("Version conflict");
    when(item2.error()).thenReturn(error2);
    when(item2.id()).thenReturn("op2");

    final var bulkResponse = mock(BulkResponse.class);
    when(bulkResponse.errors()).thenReturn(true);
    when(bulkResponse.items()).thenReturn(List.of(item1, item2));

    when(client.bulk(any(BulkRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(bulkResponse));

    // when
    final var result = repository.completeOperations(List.of("op1", "op2"));

    // then - should fail due to version_conflict_engine_exception
    assertThatThrownBy(() -> result.join())
        .hasCauseInstanceOf(RuntimeException.class)
        .hasMessageContaining("Bulk updating operations by ids");
  }

  @Test
  void shouldSucceedWhenNoErrors() throws Exception {
    // given - mock response with no errors
    final var item1 = mock(BulkResponseItem.class);
    when(item1.error()).thenReturn(null);
    when(item1.id()).thenReturn("op1");

    final var bulkResponse = mock(BulkResponse.class);
    when(bulkResponse.errors()).thenReturn(false);
    when(bulkResponse.items()).thenReturn(List.of(item1));

    when(client.bulk(any(BulkRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(bulkResponse));

    // when
    final var result = repository.completeOperations(List.of("op1"));

    // then - should complete successfully
    assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(5));
    assertThat(result.join()).containsExactly("op1");
  }
}
