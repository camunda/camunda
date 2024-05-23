/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.types;

import io.camunda.util.DataStoreObjectBuilder;
import java.util.function.Function;
import org.opensearch.client.opensearch._types.SortOptions;

public final class OpensearchSortOptions implements DataStoreSortOptions {

  private final SortOptions wrappedSortOptions;

  public OpensearchSortOptions(final SortOptions sortOptions) {
    wrappedSortOptions = sortOptions;
  }

  @Override
  public DataStoreFieldSort field() {
    return new OpensearchFieldSort(wrappedSortOptions.field());
  }

  public SortOptions sortOptions() {
    return wrappedSortOptions;
  }

  public static final class Builder implements DataStoreSortOptions.Builder {

    private final SortOptions.Builder wrappedBuilder;

    public Builder() {
      wrappedBuilder = new SortOptions.Builder();
    }

    @Override
    public Builder field(final DataStoreFieldSort sort) {
      if (sort instanceof OpensearchFieldSort wrappedFieldSort) {
        wrappedBuilder.field(wrappedFieldSort.fieldSort());
      }
      return this;
    }

    @Override
    public Builder field(
        final Function<DataStoreFieldSort.Builder, DataStoreObjectBuilder<DataStoreFieldSort>> fn) {
      return field(fn.apply(new OpensearchFieldSort.Builder()).build());
    }

    @Override
    public DataStoreSortOptions build() {
      final var sortOptions = wrappedBuilder.build();
      return new OpensearchSortOptions(sortOptions);
    }
  }
}
