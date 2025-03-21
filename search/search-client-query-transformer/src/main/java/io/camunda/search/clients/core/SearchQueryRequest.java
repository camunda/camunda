/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.core;

import static io.camunda.search.clients.core.RequestBuilders.searchRequest;
import static io.camunda.util.CollectionUtil.addValuesToList;
import static io.camunda.util.CollectionUtil.collectValues;

import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.clients.source.SearchSourceConfig;
import io.camunda.search.clients.source.SourceConfigBuilders;
import io.camunda.search.sort.SearchSortOptions;
import io.camunda.search.sort.SortOptionsBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record SearchQueryRequest(
    List<String> index,
    SearchQuery query,
    List<SearchAggregator> aggregations,
    List<SearchSortOptions> sort,
    Object[] searchAfter,
    Integer from,
    Integer size,
    SearchSourceConfig source) {

  public static SearchQueryRequest of(
      final Function<Builder, ObjectBuilder<SearchQueryRequest>> fn) {
    return searchRequest(fn);
  }

  public Builder toBuilder() {
    return new Builder()
        .index(index)
        .query(query)
        .aggregations(aggregations)
        .sort(sort)
        .searchAfter(searchAfter)
        .from(from)
        .size(size)
        .source(source);
  }

  public static final class Builder implements ObjectBuilder<SearchQueryRequest> {

    private List<String> index;
    private SearchQuery query;
    private List<SearchAggregator> aggregations;
    private List<SearchSortOptions> sort;
    private Object[] searchAfter;
    private Integer from;
    private Integer size;
    private SearchSourceConfig source;

    public Builder index(final List<String> values) {
      index = addValuesToList(index, values);
      return this;
    }

    public Builder index(final String value, final String... values) {
      return index(collectValues(value, values));
    }

    public Builder query(final SearchQuery value) {
      query = value;
      return this;
    }

    public Builder query(final Function<SearchQuery.Builder, ObjectBuilder<SearchQuery>> fn) {
      return query(SearchQueryBuilders.query(fn));
    }

    public Builder sort(final List<SearchSortOptions> values) {
      sort = addValuesToList(sort, values);
      return this;
    }

    public Builder sort(final SearchSortOptions value, final SearchSortOptions... values) {
      return sort(collectValues(value, values));
    }

    public Builder sort(
        final Function<SearchSortOptions.Builder, ObjectBuilder<SearchSortOptions>> fn) {
      return sort(SortOptionsBuilders.sort(fn));
    }

    public Builder searchAfter(final Object[] value) {
      searchAfter = value;
      return this;
    }

    public Builder size(final Integer value) {
      size = value;
      return this;
    }

    public Builder from(final Integer value) {
      from = value;
      return this;
    }

    public Builder source(final SearchSourceConfig value) {
      source = value;
      return this;
    }

    public Builder aggregations(final List<SearchAggregator> values) {
      aggregations = addValuesToList(aggregations, values);
      return this;
    }

    public Builder aggregations(final SearchAggregator value, final SearchAggregator... values) {
      return aggregations(collectValues(value, values));
    }

    public Builder source(
        final Function<SearchSourceConfig.Builder, ObjectBuilder<SearchSourceConfig>> fn) {
      return source(SourceConfigBuilders.sourceConfig(fn));
    }

    @Override
    public SearchQueryRequest build() {
      return new SearchQueryRequest(
          Objects.requireNonNull(
              index, "Expected to create request for index, but given index was null."),
          query,
          aggregations,
          sort,
          searchAfter,
          from,
          size,
          source);
    }
  }
}
