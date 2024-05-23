/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.types;

import co.elastic.clients.elasticsearch._types.SortOptions;
import io.camunda.util.DataStoreObjectBuilder;
import java.util.function.Function;

public final class ElasticsearchSortOptions implements DataStoreSortOptions {

  private final SortOptions wrappedSortOptions;

  public ElasticsearchSortOptions(final SortOptions sortOptions) {
    wrappedSortOptions = sortOptions;
  }

  @Override
  public DataStoreFieldSort field() {
    return new ElasticsearchFieldSort(wrappedSortOptions.field());
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
      if (sort instanceof ElasticsearchFieldSort wrappedFieldSort) {
        wrappedBuilder.field(wrappedFieldSort.fieldSort());
      }
      return this;
    }

    @Override
    public Builder field(
        final Function<DataStoreFieldSort.Builder, DataStoreObjectBuilder<DataStoreFieldSort>> fn) {
      return field(fn.apply(new ElasticsearchFieldSort.Builder()).build());
    }

    @Override
    public DataStoreSortOptions build() {
      final var sortOptions = wrappedBuilder.build();
      return new ElasticsearchSortOptions(sortOptions);
    }
  }
}
