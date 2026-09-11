/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.ClearScrollRequest;
import co.elastic.clients.elasticsearch.core.ClearScrollResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.OperationType;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesAsyncClient;
import co.elastic.clients.elasticsearch.indices.RefreshResponse;
import co.elastic.clients.transport.TransportException;
import co.elastic.clients.transport.http.TransportHttpClient;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.IncidentBulkUpdate;
import io.camunda.exporter.tasks.util.BulkRequestTooLargeException;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.incident.IncidentState;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * There is no way to test in integration if the scroll context was cleared, because there is no API
 * on the ES side for it. So the closest we can get is using mocks to ensure we are clearing scroll
 * contexts.
 */
@ExtendWith(MockitoExtension.class)
public final class ElasticsearchIncidentUpdateRepositoryTest {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchIncidentUpdateRepositoryTest.class);

  @Mock private ElasticsearchAsyncClient client;

  @ParameterizedTest
  @MethodSource("scrollTestCases")
  void shouldClearScroll(final ScrollTestCase testCase) {
    // given
    final var repository = createRepository();
    Mockito.when(client.search(Mockito.any(SearchRequest.class), Mockito.any(Class.class)))
        .thenReturn(CompletableFuture.completedFuture(buildMinimalSearchResponse()));
    Mockito.when(client.clearScroll(Mockito.any(ClearScrollRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(buildMinimalClearScrollResponse()));

    // when
    final var result = testCase.executeScrollingMethod(repository);

    // then
    final var inOrder = Mockito.inOrder(client);
    assertThat(result).succeedsWithin(Duration.ofSeconds(5));
    inOrder.verify(client, Mockito.times(1)).clearScroll(Mockito.any(ClearScrollRequest.class));
    inOrder.verifyNoMoreInteractions();
  }

  @ParameterizedTest
  @MethodSource("scrollTestCases")
  void shouldNotClearScrollOnSearchFailure(final ScrollTestCase testCase) {
    // given
    final var repository = createRepository();
    Mockito.when(client.search(Mockito.any(SearchRequest.class), Mockito.any(Class.class)))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("failure")));

    // when
    final var result = testCase.executeScrollingMethod(repository);

    // then
    assertThat(result).failsWithin(Duration.ofSeconds(5));
    verify(client, Mockito.never()).clearScroll(Mockito.any(ClearScrollRequest.class));
  }

  @ParameterizedTest
  @MethodSource("scrollTestCases")
  <T> void shouldClearScrollOnScrollFailure(final ScrollTestCase<T> testCase) {
    // given
    final var repository = createRepository();
    final var hit = new Hit.Builder<T>().id("1").index("index").source(testCase.document()).build();
    final SearchResponse<T> searchResponse =
        buildMinimalSearchResponse(b -> b.hits(h -> h.hits(hit)));
    Mockito.when(client.search(Mockito.any(SearchRequest.class), Mockito.any(Class.class)))
        .thenReturn(CompletableFuture.completedFuture(searchResponse));
    Mockito.when(client.scroll(Mockito.any(Function.class), Mockito.any()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("failure")));
    Mockito.when(client.clearScroll(Mockito.any(ClearScrollRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(buildMinimalClearScrollResponse()));

    // when
    final var result = testCase.executeScrollingMethod(repository);

    // then
    final var inOrder = Mockito.inOrder(client);
    assertThat(result).failsWithin(Duration.ofSeconds(5));
    inOrder.verify(client, Mockito.times(1)).clearScroll(Mockito.any(ClearScrollRequest.class));
    inOrder.verifyNoMoreInteractions();
  }

  @RegressionTest("https://github.com/camunda/camunda/pull/53585")
  void shouldSkipBulkCallWhenUpdateIsEmpty() {
    // given
    final var repository = createRepository();

    // when
    final var result = repository.bulkUpdate(new IncidentBulkUpdate());

    // then - client.bulk() must not be invoked; previously this sent an empty body and ES threw
    // "[es/bulk] failed: [parse_exception] request body is required"
    assertThat(result).succeedsWithin(Duration.ofSeconds(5)).isEqualTo(List.of());
    verify(client, Mockito.never()).bulk(Mockito.any(BulkRequest.class));
  }

  @Test
  void shouldRecognizeARequestLevelCircuitBreakerTripAsTooLarge() {
    // given - the breaker rejects the whole request, which is how Elasticsearch refuses work that
    // would otherwise exhaust its heap
    final var repository = createRepository();
    Mockito.when(client.bulk(Mockito.any(BulkRequest.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ElasticsearchException(
                    "es/bulk",
                    ErrorResponse.of(
                        r ->
                            r.status(429)
                                .error(
                                    e ->
                                        e.type("circuit_breaking_exception")
                                            .reason("[parent] Data too large"))))));

    // when
    final var result = repository.bulkUpdate(bulkUpdateOf(1));

    // then
    assertThat(result)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableThat()
        .havingCause()
        .isInstanceOf(BulkRequestTooLargeException.class)
        .withMessageContaining("Data too large")
        // the original rejection is kept, so the operator still sees what the cluster said
        .havingCause()
        .isInstanceOf(ElasticsearchException.class);
  }

  @Test
  void shouldRecognizeAContentTooLargeRejectionAsTooLarge() {
    // given - a request over http.max_content_length is refused at the HTTP layer, so there is no
    // error body to read a type from, only the status code
    final var repository = createRepository();
    final var response = Mockito.mock(TransportHttpClient.Response.class);
    Mockito.when(response.statusCode()).thenReturn(413);
    final var rejection = new TransportException(response, "Content too long", "es/bulk");
    Mockito.when(client.bulk(Mockito.any(BulkRequest.class)))
        .thenReturn(CompletableFuture.failedFuture(rejection));

    // when
    final var result = repository.bulkUpdate(bulkUpdateOf(1));

    // then
    assertThat(result)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableThat()
        .havingCause()
        .isInstanceOf(BulkRequestTooLargeException.class)
        .withMessageContaining("http.max_content_length");
  }

  @Test
  void shouldNotTreatAnItemLevelCircuitBreakerTripAsTooLarge() {
    // given - a breaker that trips once the shards are already processing an accepted request
    // reports itself per item inside a successful response
    final var repository = createRepository();
    Mockito.when(client.bulk(Mockito.any(BulkRequest.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                buildFailedBulkResponse("circuit_breaking_exception", "[parent] Data too large")));

    // when
    final var result = repository.bulkUpdate(bulkUpdateOf(1));

    // then - the request was accepted, so its size is not what tripped the breaker; the node's
    // overall heap is, which is back pressure to retry rather than a reason to write less
    assertThat(result)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableThat()
        .havingRootCause()
        .isInstanceOf(ExporterException.class)
        .isNotInstanceOf(BulkRequestTooLargeException.class);
  }

  @Test
  void shouldNotTreatQueueRejectionsAsTooLarge() {
    // given - a full bulk queue is back pressure, not a size problem; writing less would not help
    final var repository = createRepository();
    Mockito.when(client.bulk(Mockito.any(BulkRequest.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                buildFailedBulkResponse("es_rejected_execution_exception", "queue full")));

    // when
    final var result = repository.bulkUpdate(bulkUpdateOf(1));

    // then
    assertThat(result)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableThat()
        .havingRootCause()
        .isInstanceOf(ExporterException.class)
        .isNotInstanceOf(BulkRequestTooLargeException.class);
  }

  @Test
  void shouldNotTreatItemFailuresAsTooLarge() {
    // given
    final var repository = createRepository();
    Mockito.when(client.bulk(Mockito.any(BulkRequest.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                buildFailedBulkResponse("version_conflict_engine_exception", "conflict")));

    // when
    final var result = repository.bulkUpdate(bulkUpdateOf(1));

    // then
    assertThat(result)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableThat()
        .havingRootCause()
        .isNotInstanceOf(BulkRequestTooLargeException.class);
  }

  @Test
  void shouldLeaveUnrelatedFailuresUntranslated() {
    // given
    final var repository = createRepository();
    Mockito.when(client.bulk(Mockito.any(BulkRequest.class)))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("connection reset")));

    // when
    final var result = repository.bulkUpdate(bulkUpdateOf(1));

    // then
    assertThat(result)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableThat()
        .withRootCauseExactlyInstanceOf(RuntimeException.class)
        .withMessageContaining("connection reset");
  }

  @Test
  void shouldClearScrollOnTransformerFailure() {
    // given - the transformer here will fail because there is no incident source to get the tree
    // path from, but we should still clear the scroll anyway
    final var repository = createRepository();
    final var hit = new Hit.Builder<>().id("1").index("index").source(null).build();
    final SearchResponse<?> searchResponse =
        buildMinimalSearchResponse(b -> b.hits(h -> h.hits(hit)));
    Mockito.when(client.search(Mockito.any(SearchRequest.class), Mockito.any(Class.class)))
        .thenReturn(CompletableFuture.completedFuture(searchResponse));
    Mockito.when(client.clearScroll(Mockito.any(ClearScrollRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(buildMinimalClearScrollResponse()));

    // when
    final var result = repository.getActiveIncidentsByTreePaths(List.of("1"));

    // then
    final var inOrder = Mockito.inOrder(client);
    assertThat(result).failsWithin(Duration.ofSeconds(5));
    inOrder.verify(client, Mockito.times(1)).clearScroll(Mockito.any(ClearScrollRequest.class));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldRefreshPostImporterQueueIndexBeforeReadingBatch() {
    // given
    final var repository = createRepository();
    final var indicesClient = Mockito.mock(ElasticsearchIndicesAsyncClient.class);
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
  void shouldFailBatchWithoutSearchingWhenRefreshFails() {
    // given
    final var repository = createRepository();
    final var indicesClient = Mockito.mock(ElasticsearchIndicesAsyncClient.class);
    Mockito.when(client.indices()).thenReturn(indicesClient);
    Mockito.when(indicesClient.refresh(Mockito.any(Function.class)))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("refresh failed")));

    // when
    final var result = repository.getPendingIncidentsBatch(-1L, 100);

    // then - a failed refresh must abort the batch (and let it retry) rather than search a
    // potentially stale, partially-refreshed index and advance the cursor past unseen entries
    assertThat(result).failsWithin(Duration.ofSeconds(5));
    verify(client, Mockito.never())
        .search(Mockito.any(SearchRequest.class), Mockito.any(Class.class));
  }

  @Test
  void shouldDisablePartialResultsOnPendingBatchSearch() {
    // given
    final var repository = createRepository();
    final var indicesClient = Mockito.mock(ElasticsearchIndicesAsyncClient.class);
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

  @Test
  void shouldUseExactLongBoundsWhenReadingBatch() {
    // given
    final var repository = createRepository();
    final var indicesClient = Mockito.mock(ElasticsearchIndicesAsyncClient.class);
    Mockito.when(client.indices()).thenReturn(indicesClient);
    Mockito.when(indicesClient.refresh(Mockito.any(Function.class)))
        .thenReturn(CompletableFuture.completedFuture(buildMinimalRefreshResponse()));
    Mockito.when(client.search(Mockito.any(SearchRequest.class), Mockito.any(Class.class)))
        .thenReturn(CompletableFuture.completedFuture(buildMinimalSearchResponse()));

    // larger than double can accurately represent, so the repository must use exact long bounds in
    // the search query to avoid skipping entries
    final long fromPosition = (2L << 54) + 1;

    // when
    final var result = repository.getPendingIncidentsBatch(fromPosition, 100);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(5));

    final var searchCaptor = ArgumentCaptor.forClass(SearchRequest.class);
    verify(client).search(searchCaptor.capture(), Mockito.any(Class.class));

    final var searchRequest = searchCaptor.getValue();

    final var positionRange =
        searchRequest.query().bool().must().stream()
            .filter(Query::isRange)
            .map(q -> q.range().longNumber())
            .findFirst()
            .orElseThrow();

    assertThat(positionRange.gt()).isEqualTo(fromPosition);
    assertThat(positionRange.lt()).isNull();
    assertThat(positionRange.lte()).isNull();
    assertThat(positionRange.gte()).isNull();
  }

  private RefreshResponse buildMinimalRefreshResponse() {
    return RefreshResponse.of(r -> r.shards(s -> s.total(1).successful(1).failed(0)));
  }

  private ClearScrollResponse buildMinimalClearScrollResponse() {
    return new ClearScrollResponse.Builder().succeeded(true).numFreed(1).build();
  }

  private SearchResponse<Object> buildMinimalSearchResponse() {
    return buildMinimalSearchResponse(ignored -> {});
  }

  private <T> SearchResponse<T> buildMinimalSearchResponse(
      final Consumer<SearchResponse.Builder<T>> modifier) {
    // need to specify all the required fields
    final var response =
        new SearchResponse.Builder<T>()
            .scrollId("foo")
            .hits(h -> h.hits(List.of()))
            .took(1)
            .timedOut(false)
            .shards(s -> s.total(0).failed(0).successful(0));

    modifier.accept(response);
    return response.build();
  }

  private IncidentBulkUpdate bulkUpdateOf(final int updateCount) {
    final var bulk = new IncidentBulkUpdate();
    for (int i = 0; i < updateCount; i++) {
      bulk.incidentRequests()
          .add(
              IncidentUpdate.id(String.valueOf(i))
                  .index("incidentIndex")
                  .state(IncidentState.ACTIVE)
                  .build());
    }
    return bulk;
  }

  private BulkResponse buildFailedBulkResponse(final String errorType, final String reason) {
    final var item =
        new BulkResponseItem.Builder()
            .operationType(OperationType.Update)
            .status(429)
            .index("incidentIndex")
            .id("0")
            .error(e -> e.type(errorType).reason(reason))
            .build();
    return new BulkResponse.Builder().took(1).errors(true).items(List.of(item)).build();
  }

  private ElasticsearchIncidentUpdateRepository createRepository() {
    return new ElasticsearchIncidentUpdateRepository(
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

  private static Stream<Named<ScrollTestCase>> scrollTestCases() {
    return Stream.of(
        Named.of(
            "getFlowNodeInstances",
            new ScrollTestCase<>(null, r -> r.getFlowNodeInstances(List.of("1", "2")))),
        Named.of(
            "getFlowNodesInListView",
            new ScrollTestCase<>(null, r -> r.getFlowNodesInListView(List.of("1", "2")))),
        Named.of(
            "getActiveIncidentsByTreePaths",
            new ScrollTestCase<>(
                new IncidentEntity(), r -> r.getActiveIncidentsByTreePaths(List.of("1", "2")))),
        Named.of(
            "getProcessInstances",
            new ScrollTestCase<>(
                new ProcessInstanceForListViewEntity(),
                r -> r.getProcessInstances(List.of("1", "2")))));
  }

  record ScrollTestCase<T>(
      T document, Function<IncidentUpdateRepository, CompletionStage<?>> scrollMethod) {
    CompletionStage<?> executeScrollingMethod(final IncidentUpdateRepository repository) {
      return scrollMethod.apply(repository);
    }
  }
}
