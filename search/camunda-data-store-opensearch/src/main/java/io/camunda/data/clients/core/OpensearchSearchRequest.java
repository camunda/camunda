/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.core;

import io.camunda.data.clients.query.DataStoreQuery;
import io.camunda.data.clients.query.DataStoreQueryBuilders;
import io.camunda.data.clients.query.OpensearchQuery;
import io.camunda.data.clients.types.DataStoreSortOptions;
import io.camunda.data.clients.types.OpensearchSortOptions;
import io.camunda.util.DataStoreObjectBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.opensearch.client.opensearch.core.SearchRequest;

public final class OpensearchSearchRequest implements DataStoreSearchRequest {

  private final SearchRequest wrappedSearchRequest;

  public OpensearchSearchRequest(final SearchRequest searchRequest) {
    this.wrappedSearchRequest = searchRequest;
  }

  public Object get() {
    return wrappedSearchRequest;
  }

  public final List<String> index() {
    return wrappedSearchRequest.index();
  }

  public static final class Builder implements DataStoreSearchRequest.Builder {

    private final SearchRequest.Builder wrappedBuilder;

    public Builder() {
      wrappedBuilder = new SearchRequest.Builder();
    }

    @Override
    public Builder index(final List<String> indices) {
      wrappedBuilder.index(indices);
      return this;
    }

    @Override
    public Builder index(final String index, final String... indices) {
      wrappedBuilder.index(index, indices);
      return this;
    }

    @Override
    public Builder query(final DataStoreQuery query) {
      wrappedBuilder.query(((OpensearchQuery) query).query());
      return this;
    }

    @Override
    public Builder query(
        final Function<DataStoreQuery.Builder, DataStoreObjectBuilder<DataStoreQuery>> fn) {
      return query(DataStoreQueryBuilders.query(fn));
    }

    @Override
    public Builder sort(final List<DataStoreSortOptions> list) {
      for (DataStoreSortOptions sortOption : list) {
        if (sortOption != null && sortOption instanceof OpensearchSortOptions wrappedSortOption) {
          wrappedBuilder.sort(wrappedSortOption.sortOptions());
        }
      }
      return this;
    }

    @Override
    public Builder sort(final DataStoreSortOptions sort, final DataStoreSortOptions... options) {
      final var allSorts = new ArrayList<DataStoreSortOptions>();
      allSorts.add(sort);

      if (options != null && options.length > 0) {
        for (DataStoreSortOptions sortOption : options) {
          allSorts.add(sortOption);
        }
      }

      return sort(allSorts);
    }

    @Override
    public Builder sort(
        final Function<DataStoreSortOptions.Builder, DataStoreObjectBuilder<DataStoreSortOptions>>
            fn) {
      return sort(fn.apply(new OpensearchSortOptions.Builder()).build());
    }

    @Override
    public Builder searchAfter(final Object[] searchAfter) {
      final var values =
          Arrays.asList(searchAfter).stream()
              .filter(Objects::nonNull)
              .map(Object::toString)
              .toList();

      wrappedBuilder.searchAfter(values);
      return this;
    }

    @Override
    public Builder from(final Integer from) {
      wrappedBuilder.from(from);
      return this;
    }

    @Override
    public Builder size(final Integer size) {
      wrappedBuilder.size(size);
      return this;
    }

    @Override
    public DataStoreSearchRequest build() {
      final var searchRequest = wrappedBuilder.build();
      return new OpensearchSearchRequest(searchRequest);
    }
  }
}
