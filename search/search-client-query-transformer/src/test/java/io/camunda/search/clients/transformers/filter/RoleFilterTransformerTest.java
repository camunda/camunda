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
import java.util.List;
import org.junit.jupiter.api.Test;

class RoleFilterTransformerTest extends AbstractTransformerTest {

  @Test
  void shouldCombineOrFiltersWithOrLogic() {
    final var filter =
        FilterBuilders.role(
            f ->
                f.orFilters(
                    List.of(
                        FilterBuilders.role(f1 -> f1.roleId("role-1")),
                        FilterBuilders.role(f2 -> f2.roleId("role-2")))));

    final var searchQuery = transformQuery(filter);

    assertThat(searchQuery.queryOption())
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            bool -> {
              // must contains the unconditional JOIN term plus the appended OR group
              assertThat(bool.must()).hasSize(2);
              assertThat(bool.must().getLast().queryOption())
                  .isInstanceOfSatisfying(
                      SearchBoolQuery.class,
                      or -> {
                        assertThat(or.should()).hasSize(2);
                        assertThat(termValue(or.should().get(0))).isEqualTo("role-1");
                        assertThat(termValue(or.should().get(1))).isEqualTo("role-2");
                      });
            });
  }

  /**
   * Extracts the string value of the first {@link SearchTermQuery} found in {@code q} — either
   * {@code q} itself is a term query, or it's a bool query and the term is one of its {@code must}
   * clauses. and(queries) unwraps single-element lists instead of nesting a redundant one-element
   * "must" bool around them (see SearchQueryBuilders.map()), so which shape shows up depends on how
   * many conditions the given filter contributes — this helper handles both.
   */
  private static String termValue(final io.camunda.search.clients.query.SearchQuery q) {
    if (q.queryOption() instanceof SearchTermQuery term) {
      return term.value().stringValue();
    }
    final var bool = (SearchBoolQuery) q.queryOption();
    return bool.must().stream()
        .map(io.camunda.search.clients.query.SearchQuery::queryOption)
        .filter(SearchTermQuery.class::isInstance)
        .map(SearchTermQuery.class::cast)
        .findFirst()
        .orElseThrow()
        .value()
        .stringValue();
  }
}
