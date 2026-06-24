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
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.filter.FilterBuilders;
import org.junit.jupiter.api.Test;

class ProcessDefinitionFilterTransformerTest extends AbstractTransformerTest {

  @Test
  void shouldExcludeDeletedProcessDefinitionsByDefault() {
    // given — empty filter (no explicit criteria)
    final var filter = FilterBuilders.processDefinition(f -> f);

    // when
    final var searchQuery = transformQuery(filter);

    // then — the query must contain a mustNot(isDeleted=true) clause
    assertThat(searchQuery.queryOption())
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            boolQuery -> {
              final var mustNot = boolQuery.mustNot();
              assertThat(mustNot).isNotEmpty();
              final var deletedClause =
                  mustNot.stream()
                      .filter(q -> q.queryOption() instanceof SearchTermQuery)
                      .map(q -> (SearchTermQuery) q.queryOption())
                      .filter(t -> "isDeleted".equals(t.field()))
                      .findFirst();
              assertThat(deletedClause)
                  .isPresent()
                  .get()
                  .satisfies(t -> assertThat(t.value().booleanValue()).isTrue());
            });
  }

  @Test
  void shouldQueryByProcessDefinitionKey() {
    // given
    final var filter = FilterBuilders.processDefinition(f -> f.processDefinitionKeys(123L));

    // when
    final var searchQuery = transformQuery(filter);

    // then — multiple conditions are combined into a must clause; the isDeleted exclusion is
    // one of the must sub-clauses (itself a mustNot bool query)
    assertThat(searchQuery.queryOption()).isInstanceOf(SearchBoolQuery.class);
    final var boolQuery = (SearchBoolQuery) searchQuery.queryOption();
    final var hasDeletedExclusion =
        boolQuery.must().stream()
            .anyMatch(
                q ->
                    q.queryOption() instanceof SearchBoolQuery inner
                        && inner.mustNot().stream()
                            .anyMatch(
                                mn ->
                                    mn.queryOption() instanceof SearchTermQuery t
                                        && "isDeleted".equals(t.field())
                                        && t.value().booleanValue()));
    assertThat(hasDeletedExclusion).isTrue();
  }
}
