/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query;

import io.camunda.data.clients.core.DataStoreSearchResponse;
import io.camunda.data.clients.core.search.DataStoreSearchHit;
import io.camunda.util.DataStoreObjectBuilder;
import java.util.List;

public class SearchQueryResult<T> {

  private final long total;
  private final List<T> items;
  private final Object[] sortValues;

  public SearchQueryResult(final Builder<T> builder) {
    total = builder.total;
    items = builder.items;
    sortValues = builder.sortValues;
  }

  public long total() {
    return total;
  }

  public List<T> items() {
    return items;
  }

  public Object[] sortValues() {
    return sortValues;
  }

  public static <T> SearchQueryResult<T> from(final DataStoreSearchResponse<T> searchResponse) {
    final var hits = searchResponse.hits();
    final var items = hits.stream().map(DataStoreSearchHit::source).toList();
    final var size = hits.size();
    final Object[] sortValues;

    if (size > 0) {
      final var lastItem = hits.get(size - 1);
      sortValues = lastItem.sortValues();
    } else {
      sortValues = null;
    }

    return new Builder<T>()
        .total(searchResponse.totalHits())
        .sortValues(sortValues)
        .items(items)
        .build();
  }

  public static final class Builder<T> implements DataStoreObjectBuilder<SearchQueryResult<T>> {

    private long total;
    private List<T> items;
    private Object[] sortValues;

    public Builder<T> total(final long total) {
      this.total = total;
      return this;
    }

    public Builder<T> items(final List<T> items) {
      this.items = items;
      return this;
    }

    public Builder<T> sortValues(final Object[] sortValues) {
      this.sortValues = sortValues;
      return this;
    }

    @Override
    public SearchQueryResult<T> build() {
      return new SearchQueryResult<T>(this);
    }
  }
}
