/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query;

import static io.camunda.data.clients.core.DataStoreRequestBuilders.searchRequest;
import static io.camunda.data.clients.query.DataStoreQueryBuilders.and;

import io.camunda.data.clients.core.DataStoreSearchRequest;
import io.camunda.data.clients.query.DataStoreQuery;
import io.camunda.service.query.filter.FilterBody;
import io.camunda.service.query.types.SearchQueryPage;
import io.camunda.service.query.types.SearchQuerySort;
import io.camunda.util.DataStoreObjectBuilder;
import java.util.function.Function;

public final class SearchQuery<T extends FilterBody> {

  private final T filter;
  private final SearchQuerySort sort;
  private final SearchQueryPage page;

  private SearchQuery(final Builder<T> builder) {
    filter = builder.filter;
    sort = builder.sort;
    page = builder.page;
  }

  public T filter() {
    return filter;
  }

  public SearchQuerySort sort() {
    return sort;
  }

  public SearchQueryPage page() {
    return page;
  }

  public static <T extends FilterBody> SearchQuery<T> of(
      final Function<Builder<T>, DataStoreObjectBuilder<SearchQuery<T>>> fn) {
    return fn.apply(new Builder<T>()).build();
  }

  public DataStoreSearchRequest toSearchRequest() {
    return toSearchRequest(null);
  }

  public DataStoreSearchRequest toSearchRequest(final DataStoreQuery queryToInject) {
    final var indices = filter.index();
    final var query = filter.toSearchQuery();
    final var sorting = sort.toSort(!page.isNextPage());
    final var defaultSorting = page.toSort();
    final var searchAfter = page.getSearchAfter();

    final var builder =
        searchRequest()
            .index(indices)
            .query(and(queryToInject, query))
            .sort(sorting, defaultSorting)
            .from(page.from())
            .size(page.size());

    if (searchAfter != null) {
      builder.searchAfter(searchAfter);
    }

    return builder.build();
  }

  public static final class Builder<T extends FilterBody>
      implements DataStoreObjectBuilder<SearchQuery<T>> {

    private T filter;
    private SearchQuerySort sort;
    private SearchQueryPage page;

    public Builder() {
      page = new SearchQueryPage.Builder().build();
    }

    public Builder<T> filter(final T filter) {
      this.filter = filter;
      return this;
    }

    public Builder<T> sort(final SearchQuerySort sort) {
      this.sort = sort;
      return this;
    }

    public Builder<T> sort(
        final Function<SearchQuerySort.Builder, DataStoreObjectBuilder<SearchQuerySort>> fn) {
      sort(SearchQuerySort.of(fn));
      return this;
    }

    public Builder<T> page(final SearchQueryPage page) {
      this.page = page;
      return this;
    }

    public Builder<T> page(
        final Function<SearchQueryPage.Builder, DataStoreObjectBuilder<SearchQueryPage>> fn) {
      return page(SearchQueryPage.of(fn));
    }

    @Override
    public SearchQuery<T> build() {
      if (filter == null) {
        throw new RuntimeException("no filter provided");
      }

      if (sort == null) {
        sort = new SearchQuerySort.Builder().build();
      }

      if (page == null) {
        page = new SearchQueryPage.Builder().build();
      }

      return new SearchQuery<T>(this);
    }
  }
}
