/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.query;

import io.camunda.util.DataStoreObjectBuilder;
import java.util.List;
import java.util.function.Function;

public interface DataStoreBoolQuery extends DataStoreQueryVariant {

  static DataStoreBoolQuery of(
      final Function<Builder, DataStoreObjectBuilder<DataStoreBoolQuery>> fn) {
    return DataStoreQueryBuilders.bool(fn);
  }

  public interface Builder extends DataStoreObjectBuilder<DataStoreBoolQuery> {

    Builder filter(final List<DataStoreQuery> queries);

    Builder filter(final DataStoreQuery query, final DataStoreQuery... queries);

    Builder filter(
        final Function<DataStoreQuery.Builder, DataStoreObjectBuilder<DataStoreQuery>> fn);

    Builder must(final List<DataStoreQuery> queries);

    Builder must(final DataStoreQuery query, final DataStoreQuery... queries);

    Builder must(final Function<DataStoreQuery.Builder, DataStoreObjectBuilder<DataStoreQuery>> fn);

    Builder mustNot(final List<DataStoreQuery> queries);

    Builder mustNot(final DataStoreQuery query, final DataStoreQuery... queries);

    Builder mustNot(
        final Function<DataStoreQuery.Builder, DataStoreObjectBuilder<DataStoreQuery>> fn);

    Builder should(final List<DataStoreQuery> queries);

    Builder should(final DataStoreQuery query, final DataStoreQuery... queries);

    Builder should(
        final Function<DataStoreQuery.Builder, DataStoreObjectBuilder<DataStoreQuery>> fn);
  }
}
