/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.query;

import static io.camunda.util.DataStoreCollectionUtil.listAdd;
import static io.camunda.util.DataStoreCollectionUtil.listAddAll;

import io.camunda.util.DataStoreObjectBuilder;
import java.util.List;
import java.util.function.Function;

public final record DataStoreIdsQuery(List<String> values) implements DataStoreQueryVariant {

  static DataStoreIdsQuery of(
      final Function<Builder, DataStoreObjectBuilder<DataStoreIdsQuery>> fn) {
    return DataStoreQueryBuilders.ids(fn);
  }

  public static final class Builder implements DataStoreObjectBuilder<DataStoreIdsQuery> {

    private List<String> values;

    public Builder values(final List<String> values) {
      this.values = listAddAll(this.values, values);
      return this;
    }

    public Builder values(final String value, final String... values) {
      this.values = listAdd(this.values, value, values);
      return this;
    }

    @Override
    public DataStoreIdsQuery build() {
      return new DataStoreIdsQuery(values);
    }
  }
}
