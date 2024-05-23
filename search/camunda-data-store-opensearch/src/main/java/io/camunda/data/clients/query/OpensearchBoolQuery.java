/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.query;

import io.camunda.util.DataStoreObjectBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public class OpensearchBoolQuery extends OpensearchQueryVariant<BoolQuery>
    implements DataStoreBoolQuery {

  public OpensearchBoolQuery(final BoolQuery query) {
    super(query);
  }

  public static final class Builder implements DataStoreBoolQuery.Builder {

    private final BoolQuery.Builder wrappedBuilder;

    public Builder() {
      wrappedBuilder = new BoolQuery.Builder();
    }

    private List<Query> collectQueries(final List<DataStoreQuery> queries) {
      return queries.stream()
          .filter(OpensearchQuery.class::isInstance)
          .map(OpensearchQuery.class::cast)
          .map(OpensearchQuery::query)
          .toList();
    }

    private List<DataStoreQuery> toList(
        final DataStoreQuery query, final DataStoreQuery... queries) {
      final var result = new ArrayList<DataStoreQuery>();
      result.add(query);
      if (queries != null && queries.length > 0) {
        for (final DataStoreQuery searchQuery : queries) {
          result.add(searchQuery);
        }
      }
      return result;
    }

    private DataStoreQuery apply(
        final Function<DataStoreQuery.Builder, DataStoreObjectBuilder<DataStoreQuery>> fn) {
      return DataStoreQueryBuilders.query(fn);
    }

    @Override
    public Builder filter(final List<DataStoreQuery> queries) {
      final var actualQueries = collectQueries(queries);
      wrappedBuilder.filter(actualQueries);
      return this;
    }

    @Override
    public Builder filter(final DataStoreQuery query, final DataStoreQuery... queries) {
      return filter(toList(query, queries));
    }

    @Override
    public Builder filter(
        final Function<DataStoreQuery.Builder, DataStoreObjectBuilder<DataStoreQuery>> fn) {
      return filter(apply(fn));
    }

    @Override
    public Builder must(final List<DataStoreQuery> queries) {
      final var actualQueries = collectQueries(queries);
      wrappedBuilder.must(actualQueries);
      return this;
    }

    @Override
    public Builder must(final DataStoreQuery query, final DataStoreQuery... queries) {
      return must(toList(query, queries));
    }

    @Override
    public Builder must(
        final Function<DataStoreQuery.Builder, DataStoreObjectBuilder<DataStoreQuery>> fn) {
      return must(apply(fn));
    }

    @Override
    public Builder mustNot(final List<DataStoreQuery> queries) {
      final var actualQueries = collectQueries(queries);
      wrappedBuilder.mustNot(actualQueries);
      return this;
    }

    @Override
    public Builder mustNot(final DataStoreQuery query, final DataStoreQuery... queries) {
      return mustNot(toList(query, queries));
    }

    @Override
    public Builder mustNot(
        final Function<DataStoreQuery.Builder, DataStoreObjectBuilder<DataStoreQuery>> fn) {
      return mustNot(apply(fn));
    }

    @Override
    public Builder should(final List<DataStoreQuery> queries) {
      final var actualQueries = collectQueries(queries);
      wrappedBuilder.should(actualQueries);
      return this;
    }

    @Override
    public Builder should(final DataStoreQuery query, final DataStoreQuery... queries) {
      return should(toList(query, queries));
    }

    @Override
    public Builder should(
        final Function<DataStoreQuery.Builder, DataStoreObjectBuilder<DataStoreQuery>> fn) {
      return should(apply(fn));
    }

    @Override
    public DataStoreBoolQuery build() {
      final var boolQuery = wrappedBuilder.build();
      return new OpensearchBoolQuery(boolQuery);
    }
  }
}
