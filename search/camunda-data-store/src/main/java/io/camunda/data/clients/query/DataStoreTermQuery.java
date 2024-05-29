/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.query;

import io.camunda.data.clients.types.DataStoreTypedValue;
import io.camunda.util.DataStoreObjectBuilder;
import java.util.function.Function;

public final record DataStoreTermQuery(
    String field, DataStoreTypedValue value, Boolean caseInsensitive)
    implements DataStoreQueryVariant {

  static DataStoreTermQuery of(
      final Function<Builder, DataStoreObjectBuilder<DataStoreTermQuery>> fn) {
    return DataStoreQueryBuilders.term(fn);
  }

  public static final class Builder implements DataStoreObjectBuilder<DataStoreTermQuery> {

    private String field;
    private DataStoreTypedValue value;
    private Boolean caseInsensitive;

    public Builder field(final String value) {
      field = value;
      return this;
    }

    public Builder value(final String value) {
      this.value = DataStoreTypedValue.of(value);
      return this;
    }

    public Builder value(final int value) {
      this.value = DataStoreTypedValue.of(value);
      return this;
    }

    public Builder value(final long value) {
      this.value = DataStoreTypedValue.of(value);
      return this;
    }

    public Builder value(final double value) {
      this.value = DataStoreTypedValue.of(value);
      return this;
    }

    public Builder value(final boolean value) {
      this.value = DataStoreTypedValue.of(value);
      return this;
    }

    public Builder caseInsensitive(final Boolean value) {
      caseInsensitive = value;
      return this;
    }

    @Override
    public DataStoreTermQuery build() {
      return new DataStoreTermQuery(field, value, caseInsensitive);
    }
  }
}
