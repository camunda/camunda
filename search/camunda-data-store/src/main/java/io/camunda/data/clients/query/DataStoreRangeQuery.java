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

public interface DataStoreRangeQuery extends DataStoreQueryVariant {

  static DataStoreRangeQuery of(
      final Function<Builder, DataStoreObjectBuilder<DataStoreRangeQuery>> fn) {
    return DataStoreQueryBuilders.range(fn);
  }

  public interface Builder extends DataStoreObjectBuilder<DataStoreRangeQuery> {

    Builder field(final String field);

    <V> Builder gt(final V value);

    <V> Builder gte(final V value);

    <V> Builder lt(final V value);

    <V> Builder lte(final V value);

    Builder from(final String value);

    Builder to(final String to);
  }
}
