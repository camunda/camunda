/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.query;

import static io.camunda.util.DataStoreCollectionUtil.listAdd;
import static io.camunda.util.DataStoreCollectionUtil.listAddAll;

import io.camunda.util.DataStoreObjectBuilder;
import java.util.List;
import java.util.function.Function;

public final record DataStoreBoolQuery(
    List<DataStoreQuery> filter,
    List<DataStoreQuery> must,
    List<DataStoreQuery> mustNot,
    List<DataStoreQuery> should)
    implements DataStoreQueryVariant {

  static DataStoreBoolQuery of(
      final Function<Builder, DataStoreObjectBuilder<DataStoreBoolQuery>> fn) {
    return DataStoreQueryBuilders.bool(fn);
  }

  public static final class Builder implements DataStoreObjectBuilder<DataStoreBoolQuery> {

    private List<DataStoreQuery> filter;
    private List<DataStoreQuery> must;
    private List<DataStoreQuery> mustNot;
    private List<DataStoreQuery> should;

    public Builder filter(final List<DataStoreQuery> queries) {
      filter = listAddAll(filter, queries);
      return this;
    }

    public Builder filter(final DataStoreQuery query, final DataStoreQuery... queries) {
      filter = listAdd(filter, query, queries);
      return this;
    }

    public Builder filter(
        final Function<DataStoreQuery.Builder, DataStoreObjectBuilder<DataStoreQuery>> fn) {
      return filter(DataStoreQueryBuilders.query(fn));
    }

    public Builder must(final List<DataStoreQuery> queries) {
      must = listAddAll(must, queries);
      return this;
    }

    public Builder must(final DataStoreQuery query, final DataStoreQuery... queries) {
      filter = listAdd(must, query, queries);
      return this;
    }

    public Builder must(
        final Function<DataStoreQuery.Builder, DataStoreObjectBuilder<DataStoreQuery>> fn) {
      return must(DataStoreQueryBuilders.query(fn));
    }

    public Builder mustNot(final List<DataStoreQuery> queries) {
      mustNot = listAddAll(mustNot, queries);
      return this;
    }

    public Builder mustNot(final DataStoreQuery query, final DataStoreQuery... queries) {
      mustNot = listAdd(mustNot, query, queries);
      return this;
    }

    public Builder mustNot(
        final Function<DataStoreQuery.Builder, DataStoreObjectBuilder<DataStoreQuery>> fn) {
      return mustNot(DataStoreQueryBuilders.query(fn));
    }

    public Builder should(final List<DataStoreQuery> queries) {
      should = listAddAll(should, queries);
      return this;
    }

    public Builder should(final DataStoreQuery query, final DataStoreQuery... queries) {
      should = listAdd(should, query, queries);
      return this;
    }

    public Builder should(
        final Function<DataStoreQuery.Builder, DataStoreObjectBuilder<DataStoreQuery>> fn) {
      return should(DataStoreQueryBuilders.query(fn));
    }

    @Override
    public DataStoreBoolQuery build() {
      return new DataStoreBoolQuery(filter, must, mustNot, should);
    }
  }
}
