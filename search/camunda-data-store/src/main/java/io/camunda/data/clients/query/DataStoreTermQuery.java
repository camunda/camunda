/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.query;

import io.camunda.data.clients.types.DataStoreFieldValue;
import io.camunda.util.DataStoreObjectBuilder;
import java.util.function.Function;

public final class DataStoreTermQuery implements DataStoreQueryVariant {

  private final String field;
  private final DataStoreFieldValue value;
  private final Boolean caseInsensitive;

  private DataStoreTermQuery(final Builder builder) {
    field = builder.field;
    value = builder.value;
    caseInsensitive = builder.caseInsensitive;
  }

  public String field() {
    return field;
  }

  public DataStoreFieldValue value() {
    return value;
  }

  public Boolean caseSensitive() {
    return caseInsensitive;
  }

  static DataStoreTermQuery of(
      final Function<Builder, DataStoreObjectBuilder<DataStoreTermQuery>> fn) {
    return DataStoreQueryBuilders.term(fn);
  }

  public static final class Builder implements DataStoreObjectBuilder<DataStoreTermQuery> {

    private String field;
    private DataStoreFieldValue value;
    private Boolean caseInsensitive;

    public Builder field(final String value) {
      field = value;
      return this;
    }

    public Builder value(final String value) {
      this.value = DataStoreFieldValue.of(value);
      return this;
    }

    public Builder value(final int value) {
      this.value = DataStoreFieldValue.of(value);
      return this;
    }

    public Builder value(final long value) {
      this.value = DataStoreFieldValue.of(value);
      return this;
    }

    public Builder value(final double value) {
      this.value = DataStoreFieldValue.of(value);
      return this;
    }

    public Builder value(final boolean value) {
      this.value = DataStoreFieldValue.of(value);
      return this;
    }

    public Builder caseInsensitive(final Boolean value) {
      caseInsensitive = value;
      return this;
    }

    @Override
    public DataStoreTermQuery build() {
      return new DataStoreTermQuery(this);
    }
  }
}
