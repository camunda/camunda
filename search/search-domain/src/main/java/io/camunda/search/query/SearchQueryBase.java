/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import static io.camunda.search.page.SearchQueryPage.SearchQueryResultType.SINGLE_RESULT;
import static io.camunda.search.page.SearchQueryPage.SearchQueryResultType.UNLIMITED;

import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.page.SearchQueryPageBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public interface SearchQueryBase {

  SearchQueryPage page();

  abstract class AbstractQueryBuilder<T extends AbstractQueryBuilder<T>> {

    private static final SearchQueryPage DEFAULT_PAGE = SearchQueryPage.of((b) -> b);

    private SearchQueryPage page;

    protected abstract T self();

    protected SearchQueryPage page() {
      return Objects.requireNonNullElse(page, DEFAULT_PAGE);
    }

    public T singleResult() {
      page(new SearchQueryPage(0, 2, null, null, SINGLE_RESULT));
      return self();
    }

    public T unlimited() {
      page(new SearchQueryPage(0, 0, null, null, UNLIMITED));
      return self();
    }

    public T page(final SearchQueryPage value) {
      page = value;
      return self();
    }

    public T page(final Function<SearchQueryPage.Builder, ObjectBuilder<SearchQueryPage>> fn) {
      return page(SearchQueryPageBuilders.page(fn));
    }
  }
}
