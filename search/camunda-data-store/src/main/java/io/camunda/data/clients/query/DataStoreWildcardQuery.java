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

public final class DataStoreWildcardQuery implements DataStoreQueryVariant {

  private final String field;
  private final String value;

  private DataStoreWildcardQuery(final Builder builder) {
    field = builder.field;
    value = builder.value;
  }

  public String field() {
    return field;
  }

  public String value() {
    return value;
  }

  static DataStoreWildcardQuery of(
      final Function<Builder, DataStoreObjectBuilder<DataStoreWildcardQuery>> fn) {
    return DataStoreQueryBuilders.wildcard(fn);
  }

  public static final class Builder implements DataStoreObjectBuilder<DataStoreWildcardQuery> {

    private String field;
    private String value;

    public Builder field(final String value) {
      field = value;
      return this;
    }

    public Builder value(final String value) {
      this.value = value;
      return this;
    }

    @Override
    public DataStoreWildcardQuery build() {
      return new DataStoreWildcardQuery(this);
    }
  }
}
