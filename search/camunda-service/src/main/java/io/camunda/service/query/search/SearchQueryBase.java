/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.search;

import static io.camunda.data.clients.core.DataStoreRequestBuilders.searchRequest;
import static io.camunda.data.clients.query.DataStoreQueryBuilders.and;

import io.camunda.data.clients.core.DataStoreSearchRequest;
import io.camunda.data.clients.query.DataStoreQuery;
import io.camunda.service.query.filter.FilterBody;
import io.camunda.service.query.page.SearchQueryPage;
import io.camunda.service.query.page.SearchQueryPageBuilders;
import io.camunda.service.query.sort.SortOption;
import io.camunda.util.DataStoreObjectBuilder;
import java.util.function.Function;

public abstract class SearchQueryBase<T extends FilterBody, S extends SortOption> {

  private final T filter;
  private final S sort;
  private final SearchQueryPage page;

  protected SearchQueryBase(final T filter, S sort, SearchQueryPage page) {
    this.filter = filter;
    this.sort = sort;
    this.page = page;
  }

  public T filter() {
    return filter;
  }

  public S sort() {
    return sort;
  }

  public SearchQueryPage page() {
    return page;
  }

  public DataStoreSearchRequest toSearchRequest() {
    return toSearchRequest(null);
  }

  public DataStoreSearchRequest toSearchRequest(final DataStoreQuery queryToInject) {
    final var indices = filter.index();
    final var builder = searchRequest().index(indices).from(page.from()).size(page.size());

    final var query = filter.toSearchQuery();
    if (query != null) {
      builder.query(and(queryToInject, query));
    }

    final var page = getPageOrDefault();
    if (sort != null) {
      final var sorting = sort.toSortOptions(!page.isNextPage());
      builder.sort(sorting);
    }

    final var additionalSorting = page.toSortOptions();
    if (additionalSorting != null) {
      builder.sort(additionalSorting);
    }

    final var searchAfter = page.getSearchAfter();
    if (searchAfter != null) {
      builder.searchAfter(searchAfter);
    }

    return builder.build();
  }

  private SearchQueryPage getPageOrDefault() {
    if (page != null) {
      return page;
    } else {
      return SearchQueryPage.DEFAULT_PAGE;
    }
  }

  public abstract static class Builder<
          T extends FilterBody,
          S extends SortOption,
          Q extends SearchQueryBase<T, S>,
          BuilderT extends Builder<T, S, Q, BuilderT>>
      implements DataStoreObjectBuilder<Q> {

    protected S sort;
    protected SearchQueryPage page;

    public Builder() {
      page = new SearchQueryPage.Builder().build();
    }

    protected abstract BuilderT self();

    public abstract BuilderT filter(final T filter);

    public BuilderT sort(final S sort) {
      this.sort = sort;
      return self();
    }

    public BuilderT page(final SearchQueryPage page) {
      this.page = page;
      return self();
    }

    public BuilderT page(
        final Function<SearchQueryPage.Builder, DataStoreObjectBuilder<SearchQueryPage>> fn) {
      return page(SearchQueryPageBuilders.page(fn));
    }
  }
}
