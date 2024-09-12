/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.search;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.service.search.sort.SearchSortOptions;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import io.camunda.search.es.util.ESQuerySerializer;
import io.camunda.search.transformers.SearchTransfomer;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class SearchRequestTransformerTest {

  private final ElasticsearchTransformers transformers = new ElasticsearchTransformers();
  private SearchTransfomer<SearchQueryRequest, SearchRequest> transformer;

  private ESQuerySerializer esQuerySerializer;

  @BeforeEach
  public void before() throws IOException {
    transformer = transformers.getTransformer(SearchQueryRequest.class);

    // To serialize OS queries to json
    esQuerySerializer = new ESQuerySerializer();
  }

  @AfterEach
  public void close() throws Exception {
    esQuerySerializer.close();
  }

  private static Stream<Arguments> provideQueryRequests() {
    return Stream.of(
        // NO QUERY
        Arguments.arguments(SearchQueryRequest.of(b -> b.index("operate-list-view-8.3.0_")), "{}"),
        // WITH QUERY
        Arguments.arguments(
            SearchQueryRequest.of(
                b ->
                    b.index("operate-list-view-8.3.0_")
                        .query(
                            SearchQueryBuilders.or(
                                List.of(
                                    SearchQueryBuilders.exists("foo"),
                                    SearchQueryBuilders.exists("bar"))))),
            "{'query':{'bool':{'should':[{'exists':{'field':'foo'}},{'exists':{'field':'bar'}}]}}}"),
        // WITH FROM
        Arguments.arguments(
            SearchQueryRequest.of(b -> b.index("operate-list-view-8.3.0_").from(1)), "{'from':1}"),
        // WITH QUERY AND FROM
        Arguments.arguments(
            SearchQueryRequest.of(
                b ->
                    b.index("operate-list-view-8.3.0_")
                        .query(
                            SearchQueryBuilders.or(
                                List.of(
                                    SearchQueryBuilders.exists("foo"),
                                    SearchQueryBuilders.exists("bar"))))
                        .from(1)),
            "{'from':1,'query':{'bool':{'should':[{'exists':{'field':'foo'}},{'exists':{'field':'bar'}}]}}}"),
        // WITH SIZE
        Arguments.arguments(
            SearchQueryRequest.of(b -> b.index("operate-list-view-8.3.0_").size(10)),
            "{'size':10}"),
        // WITH QUERY AND SIZE
        Arguments.arguments(
            SearchQueryRequest.of(
                b ->
                    b.index("operate-list-view-8.3.0_")
                        .query(
                            SearchQueryBuilders.or(
                                List.of(
                                    SearchQueryBuilders.exists("foo"),
                                    SearchQueryBuilders.exists("bar"))))
                        .size(10)),
            "{'query':{'bool':{'should':[{'exists':{'field':'foo'}},{'exists':{'field':'bar'}}]}},'size':10}"),
        // WITH QUERY, SIZE AND FROM
        Arguments.arguments(
            SearchQueryRequest.of(
                b ->
                    b.index("operate-list-view-8.3.0_")
                        .query(
                            SearchQueryBuilders.or(
                                List.of(
                                    SearchQueryBuilders.exists("foo"),
                                    SearchQueryBuilders.exists("bar"))))
                        .from(1)
                        .size(10)),
            "{'from':1,'query':{'bool':{'should':[{'exists':{'field':'foo'}},{'exists':{'field':'bar'}}]}},'size':10}"),
        // WITH SIZE AND FROM
        Arguments.arguments(
            SearchQueryRequest.of(b -> b.index("operate-list-view-8.3.0_").from(1).size(10)),
            "{'from':1,'size':10}"),
        // WITH SORT
        Arguments.arguments(
            SearchQueryRequest.of(
                b ->
                    b.index("operate-list-view-8.3.0_")
                        .sort(
                            SearchSortOptions.of(
                                builder -> builder.field(f -> f.field("test").asc())))),
            "{'sort':[{'test':{'order':'asc'}}]}"),
        // WITH SIZE, SORT
        Arguments.arguments(
            SearchQueryRequest.of(
                b ->
                    b.index("operate-list-view-8.3.0_")
                        .size(10)
                        .sort(
                            SearchSortOptions.of(
                                builder -> builder.field(f -> f.field("test").asc())))),
            "{'size':10,'sort':[{'test':{'order':'asc'}}]}"),
        // WITH FROM, SORT
        Arguments.arguments(
            SearchQueryRequest.of(
                b ->
                    b.index("operate-list-view-8.3.0_")
                        .from(1)
                        .sort(
                            SearchSortOptions.of(
                                builder -> builder.field(f -> f.field("test").asc())))),
            "{'from':1,'sort':[{'test':{'order':'asc'}}]}"),
        // WITH SIZE, FROM AND SORT
        Arguments.arguments(
            SearchQueryRequest.of(
                b ->
                    b.index("operate-list-view-8.3.0_")
                        .sort(
                            SearchSortOptions.of(
                                builder -> builder.field(f -> f.field("test").asc())))
                        .from(1)
                        .size(10)),
            "{'from':1,'size':10,'sort':[{'test':{'order':'asc'}}]}"),
        // WITH QUERY AND SORT
        Arguments.arguments(
            SearchQueryRequest.of(
                b ->
                    b.index("operate-list-view-8.3.0_")
                        .query(
                            SearchQueryBuilders.or(
                                List.of(
                                    SearchQueryBuilders.exists("foo"),
                                    SearchQueryBuilders.exists("bar"))))
                        .sort(
                            SearchSortOptions.of(
                                builder -> builder.field(f -> f.field("test").asc())))),
            "{'query':{'bool':{'should':[{'exists':{'field':'foo'}},{'exists':{'field':'bar'}}]}},'sort':[{'test':{'order':'asc'}}]}"),
        // WITH QUERY, SIZE, FROM AND SORT
        Arguments.arguments(
            SearchQueryRequest.of(
                b ->
                    b.index("operate-list-view-8.3.0_")
                        .query(
                            SearchQueryBuilders.or(
                                List.of(
                                    SearchQueryBuilders.exists("foo"),
                                    SearchQueryBuilders.exists("bar"))))
                        .sort(
                            SearchSortOptions.of(
                                builder -> builder.field(f -> f.field("test").asc())))
                        .from(1)
                        .size(10)),
            "{'from':1,'query':{'bool':{'should':[{'exists':{'field':'foo'}},{'exists':{'field':'bar'}}]}},'size':10,'sort':[{'test':{'order':'asc'}}]}"));
  }

  @ParameterizedTest
  @MethodSource("provideQueryRequests")
  public void shouldApplyTransformer(
      final SearchQueryRequest queryRequest, final String expectedResultQuery) {
    // given
    final var expectedQuery = expectedResultQuery.replace("'", "\"");

    // when
    final var result = transformer.apply(queryRequest);

    // then
    assertThat(result).isNotNull();
    assertThat(result.index()).hasSize(1).contains("operate-list-view-8.3.0_");
    Assertions.assertThat(esQuerySerializer.serialize(result)).isEqualTo(expectedQuery);
  }

  @Test
  public void shouldTransformSearchRequestWithIndex() {
    // given
    final SearchQueryRequest request =
        SearchQueryRequest.of(b -> b.index("operate-list-view-8.3.0_"));

    // when
    final SearchRequest actual = transformer.apply(request);

    // then
    assertThat(actual.index()).hasSize(1).contains("operate-list-view-8.3.0_");
  }

  @Test
  public void shouldTransformSort() {
    // given
    final SearchQueryRequest request =
        SearchQueryRequest.of(
            b ->
                b.index("operate-list-view-8.3.0_")
                    .sort((s) -> s.field((f) -> f.field("abc").asc())));

    // when
    final SearchRequest actual = transformer.apply(request);

    // then
    assertThat(actual.index()).hasSize(1).contains("operate-list-view-8.3.0_");
    assertThat(actual.sort()).hasSize(1);
    assertThat(actual.sort().get(0).field().field()).isEqualTo("abc");
    assertThat(actual.sort().get(0).field().order()).isEqualTo(SortOrder.Asc);
  }
}
