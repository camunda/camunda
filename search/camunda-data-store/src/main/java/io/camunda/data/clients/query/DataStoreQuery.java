/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.query;

import io.camunda.util.DataStoreObjectBuilder;
import java.util.function.Function;

public final record DataStoreQuery(DataStoreQueryVariant queryVariant) {

  public static DataStoreQuery of(
      final Function<Builder, DataStoreObjectBuilder<DataStoreQuery>> fn) {
    return DataStoreQueryBuilders.query(fn);
  }

  public static final class Builder implements DataStoreObjectBuilder<DataStoreQuery> {

    private DataStoreQueryVariant queryVariant;

    public Builder bool(final DataStoreBoolQuery query) {
      queryVariant = query;
      return this;
    }

    public Builder bool(
        final Function<DataStoreBoolQuery.Builder, DataStoreObjectBuilder<DataStoreBoolQuery>> fn) {
      return bool(DataStoreQueryBuilders.bool(fn));
    }

    public Builder constantScore(final DataStoreConstantScoreQuery query) {
      queryVariant = query;
      return this;
    }

    public Builder constantScore(
        final Function<
                DataStoreConstantScoreQuery.Builder,
                DataStoreObjectBuilder<DataStoreConstantScoreQuery>>
            fn) {
      return constantScore(DataStoreQueryBuilders.constantScore(fn));
    }

    public Builder exists(final DataStoreExistsQuery query) {
      queryVariant = query;
      return this;
    }

    public Builder exists(
        final Function<DataStoreExistsQuery.Builder, DataStoreObjectBuilder<DataStoreExistsQuery>>
            fn) {
      return exists(DataStoreQueryBuilders.exists(fn));
    }

    public Builder hasChild(final DataStoreHasChildQuery query) {
      queryVariant = query;
      return this;
    }

    public Builder hasChild(
        final Function<
                DataStoreHasChildQuery.Builder, DataStoreObjectBuilder<DataStoreHasChildQuery>>
            fn) {
      return hasChild(DataStoreQueryBuilders.hasChild(fn));
    }

    public Builder ids(final DataStoreIdsQuery query) {
      queryVariant = query;
      return this;
    }

    public Builder ids(
        final Function<DataStoreIdsQuery.Builder, DataStoreObjectBuilder<DataStoreIdsQuery>> fn) {
      return ids(DataStoreQueryBuilders.ids(fn));
    }

    public Builder match(final DataStoreMatchQuery query) {
      queryVariant = query;
      return this;
    }

    public Builder match(
        final Function<DataStoreMatchQuery.Builder, DataStoreObjectBuilder<DataStoreMatchQuery>>
            fn) {
      return match(DataStoreQueryBuilders.match(fn));
    }

    public Builder matchAll() {
      queryVariant = new DataStoreMatchAllQuery.Builder().build();
      return this;
    }

    public Builder matchNone(final DataStoreMatchNoneQuery query) {
      queryVariant = new DataStoreMatchNoneQuery.Builder().build();
      return this;
    }

    public Builder prefix(final DataStorePrefixQuery query) {
      queryVariant = query;
      return this;
    }

    public Builder prefix(
        final Function<DataStorePrefixQuery.Builder, DataStoreObjectBuilder<DataStorePrefixQuery>>
            fn) {
      return prefix(DataStoreQueryBuilders.prefix(fn));
    }

    public Builder range(final DataStoreRangeQuery query) {
      queryVariant = query;
      return this;
    }

    public Builder range(
        final Function<DataStoreRangeQuery.Builder, DataStoreObjectBuilder<DataStoreRangeQuery>>
            fn) {
      return range(DataStoreQueryBuilders.range(fn));
    }

    public Builder term(final DataStoreTermQuery query) {
      queryVariant = query;
      return this;
    }

    public Builder term(
        final Function<DataStoreTermQuery.Builder, DataStoreObjectBuilder<DataStoreTermQuery>> fn) {
      return term(DataStoreQueryBuilders.term(fn));
    }

    public Builder terms(final DataStoreTermsQuery query) {
      queryVariant = query;
      return this;
    }

    public Builder terms(
        final Function<DataStoreTermsQuery.Builder, DataStoreObjectBuilder<DataStoreTermsQuery>>
            fn) {
      return terms(DataStoreQueryBuilders.terms(fn));
    }

    public Builder wildcard(final DataStoreWildcardQuery query) {
      queryVariant = query;
      return this;
    }

    public Builder wildcard(
        final Function<
                DataStoreWildcardQuery.Builder, DataStoreObjectBuilder<DataStoreWildcardQuery>>
            fn) {
      return wildcard(DataStoreQueryBuilders.wildcard(fn));
    }

    @Override
    public DataStoreQuery build() {
      return new DataStoreQuery(queryVariant);
    }
  }
}
