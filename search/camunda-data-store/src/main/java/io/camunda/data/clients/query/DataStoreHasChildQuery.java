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

public final class DataStoreHasChildQuery implements DataStoreQueryVariant {

  private final DataStoreQuery query;
  private final String type;

  private DataStoreHasChildQuery(final Builder builder) {
    query = builder.query;
    type = builder.type;
  }

  public DataStoreQuery query() {
    return query;
  }

  public String type() {
    return type;
  }

  static DataStoreHasChildQuery of(
      final Function<Builder, DataStoreObjectBuilder<DataStoreHasChildQuery>> fn) {
    return DataStoreQueryBuilders.hasChild(fn);
  }

  public static final class Builder implements DataStoreObjectBuilder<DataStoreHasChildQuery> {

    private DataStoreQuery query;
    private String type;

    public Builder query(final DataStoreQuery query) {
      this.query = query;
      return this;
    }

    public Builder query(
        final Function<DataStoreQuery.Builder, DataStoreObjectBuilder<DataStoreQuery>> fn) {
      return query(DataStoreQueryBuilders.query(fn));
    }

    public Builder type(final String value) {
      return this;
    }

    @Override
    public DataStoreHasChildQuery build() {
      return new DataStoreHasChildQuery(this);
    }
  }
}
