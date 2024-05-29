/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.core;

import static io.camunda.util.DataStoreCollectionUtil.listAdd;
import static io.camunda.util.DataStoreCollectionUtil.listAddAll;

import io.camunda.data.clients.query.DataStoreQuery;
import io.camunda.data.clients.query.DataStoreQueryBuilders;
import io.camunda.data.clients.sort.DataStoreSortOptions;
import io.camunda.data.clients.sort.DataStoreSortOptionsBuilders;
import io.camunda.util.DataStoreObjectBuilder;
import java.util.List;
import java.util.function.Function;

public final class DataStoreSearchRequest {

  private final List<String> index;
  private final DataStoreQuery query;
  private final List<DataStoreSortOptions> sort;
  private final Object[] searchAfter;
  private final Integer from;
  private final Integer size;

  private DataStoreSearchRequest(final Builder builder) {
    index = builder.index;
    query = builder.query;
    sort = builder.sort;
    searchAfter = builder.searchAfter;
    from = builder.from;
    size = builder.size;
  }

  public List<String> index() {
    return index;
  }

  public DataStoreQuery query() {
    return query;
  }

  public List<DataStoreSortOptions> sort() {
    return sort;
  }

  public Object[] searchAfter() {
    return searchAfter;
  }

  public Integer from() {
    return from;
  }

  public Integer size() {
    return size;
  }

  public static DataStoreSearchRequest of(
      final Function<Builder, DataStoreObjectBuilder<DataStoreSearchRequest>> fn) {
    return DataStoreRequestBuilders.searchRequest(fn);
  }

  public static final class Builder implements DataStoreObjectBuilder<DataStoreSearchRequest> {

    private List<String> index;
    private DataStoreQuery query;
    private List<DataStoreSortOptions> sort;
    private Object[] searchAfter;
    private Integer from;
    private Integer size;

    public Builder index(final List<String> values) {
      index = listAddAll(index, values);
      return this;
    }

    public Builder index(final String value, final String... values) {
      index = listAdd(index, value, values);
      return this;
    }

    public Builder query(final DataStoreQuery value) {
      query = value;
      return this;
    }

    public Builder query(
        final Function<DataStoreQuery.Builder, DataStoreObjectBuilder<DataStoreQuery>> fn) {
      return query(DataStoreQueryBuilders.query(fn));
    }

    public Builder sort(final List<DataStoreSortOptions> values) {
      sort = listAddAll(sort, values);
      return this;
    }

    public Builder sort(final DataStoreSortOptions value, final DataStoreSortOptions... values) {
      sort = listAdd(sort, value, values);
      return this;
    }

    public Builder sort(
        final Function<DataStoreSortOptions.Builder, DataStoreObjectBuilder<DataStoreSortOptions>>
            fn) {
      return sort(DataStoreSortOptionsBuilders.sort(fn));
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

    @Override
    public DataStoreSearchRequest build() {
      return new DataStoreSearchRequest(this);
    }
  }
}
