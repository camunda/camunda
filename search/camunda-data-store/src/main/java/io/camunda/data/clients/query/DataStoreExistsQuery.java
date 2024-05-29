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

public final class DataStoreExistsQuery implements DataStoreQueryVariant {

  private final String field;

  private DataStoreExistsQuery(final Builder builder) {
    field = builder.field;
  }

  public String field() {
    return field;
  }

  static DataStoreExistsQuery of(
      final Function<Builder, DataStoreObjectBuilder<DataStoreExistsQuery>> fn) {
    return DataStoreQueryBuilders.exists(fn);
  }

  public static final class Builder implements DataStoreObjectBuilder<DataStoreExistsQuery> {

    private String field;

    public Builder field(final String value) {
      field = value;
      return this;
    }

    @Override
    public DataStoreExistsQuery build() {
      return new DataStoreExistsQuery(this);
    }
  }
}
