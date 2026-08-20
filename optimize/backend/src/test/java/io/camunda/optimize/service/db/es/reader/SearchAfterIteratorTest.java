/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchAfterIteratorTest {

  @Mock private OptimizeElasticsearchClient esClient;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private SearchResponse<String> emptyResponse;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private SearchResponse<String> firstPageResponse;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private SearchResponse<String> secondPageResponse;

  @Test
  void shouldReturnFalseWhenFirstPageIsEmpty() throws IOException {
    // given
    final SearchRequest request = SearchRequest.of(r -> r.index("test-index"));
    when(emptyResponse.hits().hits()).thenReturn(List.of());
    when(esClient.search(any(SearchRequest.class), eq(String.class))).thenReturn(emptyResponse);

    final SearchAfterIterator<String> iterator =
        new SearchAfterIterator<>(esClient, request, String.class);

    // when / then
    assertThat(iterator).isExhausted();
  }

  @Test
  void shouldThrowNoSuchElementExceptionWhenFirstPageIsEmpty() throws IOException {
    // given
    final SearchRequest request = SearchRequest.of(r -> r.index("test-index"));
    when(emptyResponse.hits().hits()).thenReturn(List.of());
    when(esClient.search(any(SearchRequest.class), eq(String.class))).thenReturn(emptyResponse);

    final SearchAfterIterator<String> iterator =
        new SearchAfterIterator<>(esClient, request, String.class);

    // when / then
    assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void shouldReturnSinglePageOfResults() throws IOException {
    // given
    final SearchRequest request = SearchRequest.of(r -> r.index("test-index"));
    final Hit<String> hit = buildHit("item-1", List.of(FieldValue.of("sort-1")));
    when(firstPageResponse.hits().hits()).thenReturn(List.of(hit)).thenReturn(List.of());

    when(esClient.search(any(SearchRequest.class), eq(String.class)))
        .thenReturn(firstPageResponse)
        .thenReturn(emptyResponse);
    when(emptyResponse.hits().hits()).thenReturn(List.of());

    final SearchAfterIterator<String> iterator =
        new SearchAfterIterator<>(esClient, request, String.class);

    // when / then
    assertThat(iterator).hasNext();
    final List<String> page = iterator.next();
    assertThat(page).containsExactly("item-1");
    assertThat(iterator).isExhausted();
  }

  @Test
  void shouldOnlyPerformSearchOnceForRepeatedHasNextCalls() throws IOException {
    // given
    final SearchRequest request = SearchRequest.of(r -> r.index("test-index"));
    final Hit<String> hit = buildHit("item-1", List.of(FieldValue.of("sort-1")));
    when(firstPageResponse.hits().hits()).thenReturn(List.of(hit)).thenReturn(List.of());

    when(esClient.search(any(SearchRequest.class), eq(String.class)))
        .thenReturn(firstPageResponse)
        .thenReturn(emptyResponse);
    when(emptyResponse.hits().hits()).thenReturn(List.of());

    final SearchAfterIterator<String> iterator =
        new SearchAfterIterator<>(esClient, request, String.class);

    // when
    assertThat(iterator).hasNext();

    // then
    verify(esClient).search(any(SearchRequest.class), eq(String.class));

    assertThat(iterator).hasNext();

    verify(esClient).search(any(SearchRequest.class), eq(String.class));
  }

  @Test
  void shouldIterateOverMultiplePages() throws IOException {
    // given
    final SearchRequest request = SearchRequest.of(r -> r.index("test-index"));

    final Hit<String> firstHit = buildHit("item-1", List.of(FieldValue.of("sort-1")));
    final Hit<String> secondHit = buildHit("item-2", List.of(FieldValue.of("sort-2")));

    when(firstPageResponse.hits().hits()).thenReturn(List.of(firstHit));
    when(secondPageResponse.hits().hits()).thenReturn(List.of(secondHit));
    when(emptyResponse.hits().hits()).thenReturn(List.of());

    when(esClient.search(any(SearchRequest.class), eq(String.class)))
        .thenReturn(firstPageResponse)
        .thenReturn(secondPageResponse)
        .thenReturn(emptyResponse);

    final SearchAfterIterator<String> iterator =
        new SearchAfterIterator<>(esClient, request, String.class);

    // when
    final List<List<String>> pages = new ArrayList<>();
    while (iterator.hasNext()) {
      pages.add(iterator.next());
    }

    // then
    assertThat(pages).hasSize(2);
    assertThat(pages.get(0)).containsExactly("item-1");
    assertThat(pages.get(1)).containsExactly("item-2");
  }

  @Test
  void shouldExcludeNullSourcesFromPage() throws IOException {
    // given
    final SearchRequest request = SearchRequest.of(r -> r.index("test-index"));
    final Hit<String> hitWithSource = buildHit("item-1", List.of(FieldValue.of("sort-1")));
    final Hit<String> hitWithNullSource = buildHit(null, List.of(FieldValue.of("sort-2")));

    when(firstPageResponse.hits().hits()).thenReturn(List.of(hitWithSource, hitWithNullSource));
    when(esClient.search(any(SearchRequest.class), eq(String.class))).thenReturn(firstPageResponse);

    final SearchAfterIterator<String> iterator =
        new SearchAfterIterator<>(esClient, request, String.class);

    // when
    assertThat(iterator.hasNext()).isTrue();
    final List<String> page = iterator.next();

    // then
    assertThat(page).containsExactly("item-1");
  }

  @Test
  void shouldReturnFalseOnSubsequentCallsOnceFinished() throws IOException {
    // given
    final SearchRequest request = SearchRequest.of(r -> r.index("test-index"));
    when(emptyResponse.hits().hits()).thenReturn(List.of());
    when(esClient.search(any(SearchRequest.class), eq(String.class))).thenReturn(emptyResponse);

    final SearchAfterIterator<String> iterator =
        new SearchAfterIterator<>(esClient, request, String.class);

    // when
    assertThat(iterator).isExhausted();

    // then — no further ES calls needed, finished flag is set
    assertThat(iterator).isExhausted();
  }

  @Test
  void shouldWrapIOExceptionAsUncheckedIOException() throws IOException {
    // given
    final SearchRequest request = SearchRequest.of(r -> r.index("test-index"));
    when(esClient.search(any(SearchRequest.class), eq(String.class)))
        .thenThrow(new IOException("ES unavailable"));

    final SearchAfterIterator<String> iterator =
        new SearchAfterIterator<>(esClient, request, String.class);

    // when / then
    assertThatThrownBy(iterator::hasNext)
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining("ES unavailable");
  }

  @SuppressWarnings("unchecked")
  private Hit<String> buildHit(final String source, final List<FieldValue> sortValues) {
    final Hit<String> hit = (Hit<String>) mock(Hit.class);
    when(hit.source()).thenReturn(source);
    // lenient: sort() is only called on the last hit in each page, not every hit
    lenient().when(hit.sort()).thenReturn(sortValues);
    return hit;
  }
}
