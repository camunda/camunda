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

public interface DataStoreQuery {

  public interface Builder extends DataStoreObjectBuilder<DataStoreQuery> {

    Builder bool(final DataStoreBoolQuery query);

    Builder bool(
        final Function<DataStoreBoolQuery.Builder, DataStoreObjectBuilder<DataStoreBoolQuery>> fn);

    Builder constantScore(final DataStoreConstantScoreQuery query);

    Builder constantScore(
        final Function<
                DataStoreConstantScoreQuery.Builder,
                DataStoreObjectBuilder<DataStoreConstantScoreQuery>>
            fn);

    Builder exists(final DataStoreExistsQuery query);

    Builder exists(
        final Function<DataStoreExistsQuery.Builder, DataStoreObjectBuilder<DataStoreExistsQuery>>
            fn);

    Builder hasChild(final DataStoreHasChildQuery query);

    Builder hasChild(
        final Function<
                DataStoreHasChildQuery.Builder, DataStoreObjectBuilder<DataStoreHasChildQuery>>
            fn);

    Builder ids(final DataStoreIdsQuery query);

    Builder ids(
        final Function<DataStoreIdsQuery.Builder, DataStoreObjectBuilder<DataStoreIdsQuery>> fn);

    Builder match(final DataStoreMatchQuery query);

    Builder match(
        final Function<DataStoreMatchQuery.Builder, DataStoreObjectBuilder<DataStoreMatchQuery>>
            fn);

    Builder matchAll(final DataStoreMatchAllQuery query);

    Builder matchAll(
        final Function<
                DataStoreMatchAllQuery.Builder, DataStoreObjectBuilder<DataStoreMatchAllQuery>>
            fn);

    Builder matchNone(final DataStoreMatchNoneQuery query);

    Builder matchNone(
        final Function<
                DataStoreMatchNoneQuery.Builder, DataStoreObjectBuilder<DataStoreMatchNoneQuery>>
            fn);

    Builder prefix(final DataStorePrefixQuery query);

    Builder prefix(
        final Function<DataStorePrefixQuery.Builder, DataStoreObjectBuilder<DataStorePrefixQuery>>
            fn);

    Builder range(final DataStoreRangeQuery query);

    Builder range(
        final Function<DataStoreRangeQuery.Builder, DataStoreObjectBuilder<DataStoreRangeQuery>>
            fn);

    Builder term(final DataStoreTermQuery query);

    Builder term(
        final Function<DataStoreTermQuery.Builder, DataStoreObjectBuilder<DataStoreTermQuery>> fn);

    Builder terms(final DataStoreTermsQuery query);

    Builder terms(
        final Function<DataStoreTermsQuery.Builder, DataStoreObjectBuilder<DataStoreTermsQuery>>
            fn);

    Builder wildcard(final DataStoreWildcardQuery query);

    Builder wildcard(
        final Function<
                DataStoreWildcardQuery.Builder, DataStoreObjectBuilder<DataStoreWildcardQuery>>
            fn);
  }
}
