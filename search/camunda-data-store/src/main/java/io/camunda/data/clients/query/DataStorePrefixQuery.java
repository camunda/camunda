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

public final record DataStorePrefixQuery(String field, String value)
    implements DataStoreQueryVariant {

  static DataStorePrefixQuery of(
      final Function<Builder, DataStoreObjectBuilder<DataStorePrefixQuery>> fn) {
    return DataStoreQueryBuilders.prefix(fn);
  }

  public static final class Builder implements DataStoreObjectBuilder<DataStorePrefixQuery> {

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
    public DataStorePrefixQuery build() {
      return new DataStorePrefixQuery(field, value);
    }
  }
}
