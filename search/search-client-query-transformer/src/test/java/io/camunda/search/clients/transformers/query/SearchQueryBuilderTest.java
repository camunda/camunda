/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.query;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.result.ProcessInstanceQueryResultConfig;
import io.camunda.search.sort.ProcessInstanceSort;
import org.junit.jupiter.api.Test;

public class SearchQueryBuilderTest {

  @Test
  public void shouldCreateQuery() {
    // given
    final var searchQueryBuilder = new ProcessInstanceQuery.Builder();
    final var searchQueryPage = new SearchQueryPage.Builder().size(50).build();
    final var searchQuerySort = ProcessInstanceSort.of(builder -> builder.startDate().asc());
    final var searchQueryResultConfig =
        ProcessInstanceQueryResultConfig.of(builder -> builder.onlyKeys(true));
    final var filter = new ProcessInstanceFilter.Builder().build(); // all

    // when
    final ProcessInstanceQuery query =
        searchQueryBuilder
            .page(searchQueryPage)
            .sort(searchQuerySort)
            .filter(filter)
            .resultConfig(searchQueryResultConfig)
            .build();

    // when
    assertThat(query.filter()).isEqualTo(filter);
    assertThat(query.sort()).isEqualTo(searchQuerySort);
    assertThat(query.page()).isEqualTo(searchQueryPage);
    assertThat(query.resultConfig()).isEqualTo(searchQueryResultConfig);
  }
}
