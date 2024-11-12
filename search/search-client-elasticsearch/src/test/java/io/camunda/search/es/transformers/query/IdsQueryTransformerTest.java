/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.query;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class IdsQueryTransformerTest {

  private final ElasticsearchTransformers transformers = new ElasticsearchTransformers();
  private SearchTransfomer<SearchQuery, Query> transformer;

  @BeforeEach
  public void before() {
    transformer = transformers.getTransformer(SearchQuery.class);
  }

  private static Stream<Arguments> provideQueries() {
    return Stream.of(
        Arguments.arguments(SearchQueryBuilders.ids(List.of()), "Query: {'ids':{'values':[]}}"),
        Arguments.arguments(
            SearchQueryBuilders.ids().values(List.of()).build().toSearchQuery(),
            "Query: {'ids':{'values':[]}}"),
        Arguments.arguments(
            SearchQueryBuilders.ids().build().toSearchQuery(), "Query: {'ids':{'values':[]}}"),
        Arguments.arguments(
            SearchQueryBuilders.ids((List<String>) null), "Query: {'ids':{'values':[]}}"),
        Arguments.arguments(
            SearchQueryBuilders.ids((Collection<String>) null), "Query: {'ids':{'values':[]}}"),
        Arguments.arguments(
            SearchQueryBuilders.ids((String[]) null), "Query: {'ids':{'values':[]}}"),
        Arguments.arguments(
            SearchQueryBuilders.ids().values((List<String>) null).build().toSearchQuery(),
            "Query: {'ids':{'values':[]}}"),
        Arguments.arguments(
            SearchQueryBuilders.ids().values((String[]) null).build().toSearchQuery(),
            "Query: {'ids':{'values':[]}}"),
        Arguments.arguments(
            SearchQueryBuilders.ids(List.of("value")), "Query: {'ids':{'values':['value']}}"),
        Arguments.arguments(
            SearchQueryBuilders.ids("value1", "value2"),
            "Query: {'ids':{'values':['value1','value2']}}"),
        Arguments.arguments(
            SearchQueryBuilders.ids((Collection<String>) List.of("value1", "value2")),
            "Query: {'ids':{'values':['value1','value2']}}"),
        Arguments.arguments(
            SearchQueryBuilders.ids(List.of("value1", "value2")),
            "Query: {'ids':{'values':['value1','value2']}}"),
        Arguments.arguments(
            SearchQueryBuilders.ids().values(List.of("value1", "value2")).build().toSearchQuery(),
            "Query: {'ids':{'values':['value1','value2']}}"),
        Arguments.arguments(
            SearchQueryBuilders.ids()
                .values(List.of("value1"))
                .values(List.of("value2"))
                .build()
                .toSearchQuery(),
            "Query: {'ids':{'values':['value1','value2']}}"),
        Arguments.arguments(
            SearchQueryBuilders.ids().values("value1").values("value2").build().toSearchQuery(),
            "Query: {'ids':{'values':['value1','value2']}}"),
        Arguments.arguments(
            SearchQueryBuilders.ids().values("value1", "value2").build().toSearchQuery(),
            "Query: {'ids':{'values':['value1','value2']}}"));
  }

  @ParameterizedTest
  @MethodSource("provideQueries")
  public void shouldApplyTransformer(final SearchQuery query, final String expectedResultQuery) {
    // given
    final var expectedQuery = expectedResultQuery.replace("'", "\"");

    // when
    final var result = transformer.apply(query);

    // then
    assertThat(result).isNotNull();
    assertThat(result.toString()).isEqualTo(expectedQuery);
  }
}
