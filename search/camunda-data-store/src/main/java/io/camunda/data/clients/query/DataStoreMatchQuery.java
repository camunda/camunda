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

public final class DataStoreMatchQuery implements DataStoreQueryVariant {

  private final String field;
  private final String query;
  private final String operator;

  private DataStoreMatchQuery(final Builder builder) {
    field = builder.field;
    query = builder.query;
    operator = builder.operator;
  }

  public String field() {
    return field;
  }

  public String query() {
    return query;
  }

  public String operator() {
    return operator;
  }

  static DataStoreMatchQuery of(
      final Function<Builder, DataStoreObjectBuilder<DataStoreMatchQuery>> fn) {
    return DataStoreQueryBuilders.match(fn);
  }

  public static final class Builder implements DataStoreObjectBuilder<DataStoreMatchQuery> {

    private String field;
    private String query;
    private String operator;

    public Builder field(final String value) {
      field = value;
      return this;
    }

    public Builder query(final String value) {
      query = value;
      return this;
    }

    public Builder operator(final String value) {
      operator = value;
      return this;
    }

    @Override
    public DataStoreMatchQuery build() {
      return new DataStoreMatchQuery(this);
    }
  }
}
