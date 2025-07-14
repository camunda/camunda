/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.elastic.clients.elasticsearch._types.query_dsl.MatchPhraseQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MatchPhraseQueryTransformerTest {

  private SearchTransfomer<SearchQuery, Query> transformer;

  @BeforeEach
  public void setUp() {
    transformer = new ElasticsearchTransformers().getTransformer(SearchQuery.class);
  }

  @Test
  public void shouldTransformMatchPhraseQuery() {
    // given
    final SearchQuery query = SearchQueryBuilders.matchPhrase("foo", "bar");

    // when
    final Query result = transformer.apply(query);

    // then
    final MatchPhraseQuery matchPhrase = (MatchPhraseQuery) result._get();
    assertThat(matchPhrase.field()).isEqualTo("foo");
    assertThat(matchPhrase.query()).isEqualTo("bar");
  }

  @Test
  public void shouldThrowWhenFieldOrQueryIsNull() {
    // expect

    assertThatThrownBy(() -> SearchQueryBuilders.matchPhrase().query("bar").build())
        .hasMessageContaining("Expected a non-null field for the match phrase query.")
        .isInstanceOf(NullPointerException.class);

    assertThatThrownBy(() -> SearchQueryBuilders.matchPhrase().field("foo").build())
        .hasMessageContaining(
            "Expected a non-null query parameter for the match phrase query, with field: 'foo'")
        .isInstanceOf(NullPointerException.class);
  }
}
