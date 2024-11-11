/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.filter.FilterBuilders;
import org.junit.jupiter.api.Test;

public class VariableQueryTransformerTest extends AbstractTransformerTest {
  @Test
  public void shouldQueryByVariableKey() {
    // given
    final var filter = FilterBuilders.variable((f) -> f.variableKeys(12345L));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class, // Now expecting SearchTermQuery directly
            (term) -> {
              assertThat(term.field()).isEqualTo("key");
              assertThat(term.value().longValue()).isEqualTo(12345L);
            });
  }

  @Test
  public void shouldQueryByScopeKey() {
    // given
    final var filter = FilterBuilders.variable((f) -> f.scopeKeys(12345L));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class, // Now expecting SearchTermQuery directly
            (term) -> {
              assertThat(term.field()).isEqualTo("scopeKey");
              assertThat(term.value().longValue()).isEqualTo(12345L);
            });
  }

  @Test
  public void shouldQueryByProcessInstanceKey() {
    // given
    final var filter = FilterBuilders.variable((f) -> f.processInstanceKeys(12345L));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class, // Now expecting SearchTermQuery directly
            (term) -> {
              assertThat(term.field()).isEqualTo("processInstanceKey");
              assertThat(term.value().longValue()).isEqualTo(12345L);
            });
  }

  @Test
  public void shouldQueryByTenantId() {
    // given
    final var filter = FilterBuilders.variable((f) -> f.tenantIds("tenantId"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class, // Now expecting SearchTermQuery directly
            (term) -> {
              assertThat(term.field()).isEqualTo("tenantId");
              assertThat(term.value().stringValue()).isEqualTo("tenantId");
            });
  }

  @Test
  public void shouldQueryByVariableNameAndValue() {
    // given
    final var filter = FilterBuilders.variable((f) -> f.names("test").values("testValue"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();

    // Ensure the outer query is a SearchBoolQuery
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            outerBoolQuery -> {
              assertThat(outerBoolQuery.must()).isNotEmpty();

              final SearchQuery nameMustQuery = outerBoolQuery.must().get(0);
              assertThat(nameMustQuery.queryOption()).isInstanceOf(SearchTermQuery.class);

              final SearchQuery valueMustQuery = outerBoolQuery.must().get(1);
              assertThat(valueMustQuery.queryOption()).isInstanceOf(SearchTermQuery.class);

              final SearchTermQuery innerNameTermQuery =
                  (SearchTermQuery) nameMustQuery.queryOption();
              final SearchTermQuery innerValueTermQuery =
                  (SearchTermQuery) valueMustQuery.queryOption();

              // Ensure name query is correct
              assertThat(innerNameTermQuery)
                  .isInstanceOfSatisfying(
                      SearchTermQuery.class,
                      (term) -> {
                        assertThat(term.field()).isEqualTo("name");
                        assertThat(term.value().stringValue()).isEqualTo("test");
                      });

              // Ensure value query is correct
              assertThat(innerValueTermQuery)
                  .isInstanceOfSatisfying(
                      SearchTermQuery.class,
                      (term) -> {
                        assertThat(term.field()).isEqualTo("value");
                        assertThat(term.value().stringValue()).isEqualTo("testValue");
                      });
            });
  }
}
