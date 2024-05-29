/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.sort;

import io.camunda.util.DataStoreObjectBuilder;
import java.util.function.Function;

public final record DataStoreFieldSort(String field, SortOrder order, String missing) {

  public boolean asc() {
    return SortOrder.ASC == order;
  }

  public boolean desc() {
    return SortOrder.DESC == order;
  }

  public static DataStoreFieldSort of(
      final Function<Builder, DataStoreObjectBuilder<DataStoreFieldSort>> fn) {
    return DataStoreSortOptionsBuilders.field(fn);
  }

  public static final class Builder implements DataStoreObjectBuilder<DataStoreFieldSort> {

    private String field;
    private SortOrder order;
    private String missing;

    public Builder field(final String value) {
      field = value;
      return this;
    }

    public Builder asc() {
      order = SortOrder.ASC;
      return this;
    }

    public Builder desc() {
      order = SortOrder.DESC;
      return this;
    }

    public Builder order(SortOrder order) {
      if (order == SortOrder.ASC) {
        return asc();
      } else if (order == SortOrder.DESC) {
        return desc();
      }
      throw new RuntimeException("something went wrong");
    }

    public Builder missing(String value) {
      missing = value;
      return this;
    }

    @Override
    public DataStoreFieldSort build() {
      return new DataStoreFieldSort(field, order, missing);
    }
  }
}
