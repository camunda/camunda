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

public final record DataStoreConstantScoreQuery(DataStoreQuery query)
    implements DataStoreQueryVariant {

  static DataStoreConstantScoreQuery of(
      final Function<Builder, DataStoreObjectBuilder<DataStoreConstantScoreQuery>> fn) {
    return DataStoreQueryBuilders.constantScore(fn);
  }

  public static final class Builder implements DataStoreObjectBuilder<DataStoreConstantScoreQuery> {

    private DataStoreQuery query;

    public Builder filter(final DataStoreQuery query) {
      this.query = query;
      return this;
    }

    public Builder filter(
        final Function<DataStoreQuery.Builder, DataStoreObjectBuilder<DataStoreQuery>> fn) {
      return filter(DataStoreQueryBuilders.query(fn));
    }

    @Override
    public DataStoreConstantScoreQuery build() {
      return new DataStoreConstantScoreQuery(query);
    }
  }
}
