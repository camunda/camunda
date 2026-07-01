/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.IncidentBulkUpdate;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.TotalHitsRelation;
import org.opensearch.client.opensearch.indices.OpenSearchIndicesAsyncClient;
import org.opensearch.client.opensearch.indices.RefreshResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
public final class OpenSearchIncidentUpdateRepositoryTest {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(OpenSearchIncidentUpdateRepositoryTest.class);

  @Mock private OpenSearchAsyncClient client;

  @RegressionTest("https://github.com/camunda/camunda/pull/53585")
  void shouldSkipBulkCallWhenUpdateIsEmpty() throws Exception {
    // given
    final var repository = createRepository();

    // when
    final var result = repository.bulkUpdate(new IncidentBulkUpdate());

    // then - client.bulk() must not be invoked; previously this sent an empty body and OS threw
    // "[os/bulk] failed: [parse_exception] request body is required"
    assertThat(result).succeedsWithin(Duration.ofSeconds(5)).isEqualTo(List.of());
    Mockito.verify(client, Mockito.never()).bulk(Mockito.any(BulkRequest.class));
  }

  @Test
  void shouldRefreshPostImporterQueueIndexBeforeReadingBatch() throws Exception {
    // given
    final var repository = createRepository();
    final var indicesClient = Mockito.mock(OpenSearchIndicesAsyncClient.class);
    Mockito.when(client.indices()).thenReturn(indicesClient);
    Mockito.when(indicesClient.refresh(Mockito.any(Function.class)))
        .thenReturn(CompletableFuture.completedFuture(buildMinimalRefreshResponse()));
    Mockito.when(client.search(Mockito.any(SearchRequest.class), Mockito.any(Class.class)))
        .thenReturn(CompletableFuture.completedFuture(buildMinimalSearchResponse()));

    // when
    final var result = repository.getPendingIncidentsBatch(-1L, 100);

    // then - the queue write index is refreshed before the batch search runs, so a lagging shard
    // exposes its writes and no pending entry is skipped by the forward-only cursor
    assertThat(result).succeedsWithin(Duration.ofSeconds(5));
    final var inOrder = Mockito.inOrder(client, indicesClient);
    inOrder.verify(indicesClient).refresh(Mockito.any(Function.class));
    inOrder.verify(client).search(Mockito.any(SearchRequest.class), Mockito.any(Class.class));
  }

  @Test
  void shouldFailBatchWithoutSearchingWhenRefreshFails() throws Exception {
    // given
    final var repository = createRepository();
    final var indicesClient = Mockito.mock(OpenSearchIndicesAsyncClient.class);
    Mockito.when(client.indices()).thenReturn(indicesClient);
    Mockito.when(indicesClient.refresh(Mockito.any(Function.class)))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("refresh failed")));

    // when
    final var result = repository.getPendingIncidentsBatch(-1L, 100);

    // then - a failed refresh must abort the batch (and let it retry) rather than search a
    // potentially stale, partially-refreshed index and advance the cursor past unseen entries
    assertThat(result).failsWithin(Duration.ofSeconds(5));
    Mockito.verify(client, Mockito.never())
        .search(Mockito.any(SearchRequest.class), Mockito.any(Class.class));
  }

  @Test
  void shouldDisablePartialResultsOnPendingBatchSearch() throws Exception {
    // given
    final var repository = createRepository();
    final var indicesClient = Mockito.mock(OpenSearchIndicesAsyncClient.class);
    Mockito.when(client.indices()).thenReturn(indicesClient);
    Mockito.when(indicesClient.refresh(Mockito.any(Function.class)))
        .thenReturn(CompletableFuture.completedFuture(buildMinimalRefreshResponse()));
    final var searchCaptor = ArgumentCaptor.forClass(SearchRequest.class);
    Mockito.when(client.search(searchCaptor.capture(), Mockito.any(Class.class)))
        .thenReturn(CompletableFuture.completedFuture(buildMinimalSearchResponse()));

    // when
    repository.getPendingIncidentsBatch(-1L, 100).toCompletableFuture().join();

    // then - an unavailable shard must fail the search (triggering a retry) instead of silently
    // returning partial hits and letting the cursor skip the entries on the missing shard
    assertThat(searchCaptor.getValue().allowPartialSearchResults()).isFalse();
  }

  private RefreshResponse buildMinimalRefreshResponse() {
    return RefreshResponse.of(r -> r.shards(s -> s.total(1).successful(1).failed(0)));
  }

  private SearchResponse<Object> buildMinimalSearchResponse() {
    return new SearchResponse.Builder<Object>()
        .took(1)
        .timedOut(false)
        .shards(s -> s.total(1).successful(1).failed(0))
        .hits(h -> h.total(t -> t.value(0).relation(TotalHitsRelation.Eq)).hits(List.of()))
        .build();
  }

  private OpenSearchIncidentUpdateRepository createRepository() {
    return new OpenSearchIncidentUpdateRepository(
        1,
        "pendingUpdateAlias",
        "pendingUpdateFullQualifiedName",
        "incidentAlias",
        "listViewAlias",
        "listViewFullQualifiedName",
        "flowNodeAlias",
        "operationAlias",
        client,
        Runnable::run,
        LOGGER);
  }
}
