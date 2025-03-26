/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.core.ClearScrollRequest;
import co.elastic.clients.elasticsearch.core.ClearScrollResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.operate.IncidentEntity;
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
    Mockito.verify(client, Mockito.never()).clearScroll(Mockito.any(ClearScrollRequest.class));
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

  private ElasticsearchIncidentUpdateRepository createRepository() {
    return new ElasticsearchIncidentUpdateRepository(
        1,
        "pendingUpdateAlias",
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
