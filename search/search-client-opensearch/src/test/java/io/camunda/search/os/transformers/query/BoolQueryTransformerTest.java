/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.query;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.os.transformers.OpensearchTransformers;
import io.camunda.search.os.util.OSQuerySerializer;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public class BoolQueryTransformerTest {

  private final OpensearchTransformers transformers = new OpensearchTransformers();
  private SearchTransfomer<SearchQuery, Query> transformer;
  private OSQuerySerializer osQuerySerializer;

  @BeforeEach
  public void before() throws IOException {
    transformer = transformers.getTransformer(SearchQuery.class);

    // To serialize OS queries to json
    osQuerySerializer = new OSQuerySerializer();
  }

  @AfterEach
  public void close() throws Exception {
    osQuerySerializer.close();
  }

  private static Stream<Arguments> provideQueries() {
    return Stream.of(
        Arguments.arguments(
            SearchQueryBuilders.bool()
                .should(
                    List.of(SearchQueryBuilders.exists("foo"), SearchQueryBuilders.exists("bar")))
                .build()
                .toSearchQuery(),
            "{'bool':{'should':[{'exists':{'field':'foo'}},{'exists':{'field':'bar'}}]}}"),
        Arguments.arguments(
            SearchQueryBuilders.or(
                List.of(SearchQueryBuilders.exists("foo"), SearchQueryBuilders.exists("bar"))),
            "{'bool':{'should':[{'exists':{'field':'foo'}},{'exists':{'field':'bar'}}]}}"),
        Arguments.arguments(
            SearchQueryBuilders.or(
                SearchQueryBuilders.exists("foo"), SearchQueryBuilders.exists("bar")),
            "{'bool':{'should':[{'exists':{'field':'foo'}},{'exists':{'field':'bar'}}]}}"),
        Arguments.arguments(
            SearchQueryBuilders.bool()
                .must(List.of(SearchQueryBuilders.exists("foo"), SearchQueryBuilders.exists("bar")))
                .build()
                .toSearchQuery(),
            "{'bool':{'must':[{'exists':{'field':'foo'}},{'exists':{'field':'bar'}}]}}"),
        Arguments.arguments(
            SearchQueryBuilders.and(
                List.of(SearchQueryBuilders.exists("foo"), SearchQueryBuilders.exists("bar"))),
            "{'bool':{'must':[{'exists':{'field':'foo'}},{'exists':{'field':'bar'}}]}}"),
        Arguments.arguments(
            SearchQueryBuilders.and(
                SearchQueryBuilders.exists("foo"), SearchQueryBuilders.exists("bar")),
            "{'bool':{'must':[{'exists':{'field':'foo'}},{'exists':{'field':'bar'}}]}}"),
        Arguments.arguments(
            SearchQueryBuilders.bool()
                .mustNot(
                    List.of(SearchQueryBuilders.exists("foo"), SearchQueryBuilders.exists("bar")))
                .build()
                .toSearchQuery(),
            "{'bool':{'must_not':[{'exists':{'field':'foo'}},{'exists':{'field':'bar'}}]}}"),
        Arguments.arguments(
            SearchQueryBuilders.not(
                List.of(SearchQueryBuilders.exists("foo"), SearchQueryBuilders.exists("bar"))),
            "{'bool':{'must_not':[{'exists':{'field':'foo'}},{'exists':{'field':'bar'}}]}}"),
        Arguments.arguments(
            SearchQueryBuilders.not(
                SearchQueryBuilders.exists("foo"), SearchQueryBuilders.exists("bar")),
            "{'bool':{'must_not':[{'exists':{'field':'foo'}},{'exists':{'field':'bar'}}]}}"),
        Arguments.arguments(
            SearchQueryBuilders.bool()
                .filter(
                    List.of(SearchQueryBuilders.exists("foo"), SearchQueryBuilders.exists("bar")))
                .build()
                .toSearchQuery(),
            "{'bool':{'filter':[{'exists':{'field':'foo'}},{'exists':{'field':'bar'}}]}}"),
        Arguments.arguments(
            SearchQueryBuilders.constantScore()
                .filter(SearchQueryBuilders.exists("foo"))
                .build()
                .toSearchQuery(),
            "{'constant_score':{'filter':{'exists':{'field':'foo'}}}}"));
  }

  @ParameterizedTest
  @MethodSource("provideQueries")
  public void shouldApplyTransformer(final SearchQuery query, final String expectedResultQuery)
      throws IOException {
    // given
    final var expectedQuery = expectedResultQuery.replace("'", "\"");

    // when
    final var result = transformer.apply(query);

    // then
    assertThat(result).isNotNull();
    Assertions.assertThat(osQuerySerializer.serialize(result)).isEqualTo(expectedQuery);
  }
}
