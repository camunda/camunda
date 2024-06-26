/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.query;

import io.camunda.service.search.page.SearchQueryPage;
import io.camunda.service.search.page.SearchQueryPageBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public interface SearchQueryBase {

  SearchQueryPage page();

  public abstract static class AbstractQueryBuilder<T extends AbstractQueryBuilder<T>> {

    private static final SearchQueryPage DEFAULT_PAGE = SearchQueryPage.of((b) -> b);

    private SearchQueryPage page;

    protected abstract T self();

    protected SearchQueryPage page() {
      return Objects.requireNonNullElse(page, DEFAULT_PAGE);
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
