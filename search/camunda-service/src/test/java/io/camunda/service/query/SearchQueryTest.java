/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query;

import io.camunda.service.query.filter.ProcessInstanceFilter;
import io.camunda.service.query.filter.ProcessInstanceFilterTest;
import io.camunda.service.query.types.SearchQueryPage;
import io.camunda.service.query.types.SearchQuerySort;
import org.junit.jupiter.api.Test;

public class SearchQueryTest {

  @Test
  public void shouldCreateQuery() {
    // given


    // test search
    final var searchQueryBuilder = new SearchQuery.Builder<ProcessInstanceFilter>();

    // test paging
    final var searchQueryPage = SearchQueryPage.ofSize(50);


    // test sort -> validate mandatory fields like "field"
    final var searchQuerySort = SearchQuerySort.of(builder -> builder.field("field").asc());


    // test filter - mandatory -
    final ProcessInstanceFilter filter = new ProcessInstanceFilter.Builder().build();// all


    // test query
    final SearchQuery<ProcessInstanceFilter> query = searchQueryBuilder.page(
            searchQueryPage)
        .sort(searchQuerySort)
        .filter(filter).build();

    // when

    query.filter()

  }
}
