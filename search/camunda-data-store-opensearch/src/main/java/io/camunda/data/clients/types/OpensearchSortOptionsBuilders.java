/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.types;

import io.camunda.data.clients.util.DataStoreSortOptionsBuildersDelegate;
import io.camunda.util.DataStoreObjectBuilder;
import java.util.function.Function;

public class OpensearchSortOptionsBuilders implements DataStoreSortOptionsBuildersDelegate {

  @Override
  public DataStoreSortOptions.Builder sort() {
    return new OpensearchSortOptions.Builder();
  }

  @Override
  public DataStoreSortOptions sort(
      final Function<DataStoreSortOptions.Builder, DataStoreObjectBuilder<DataStoreSortOptions>>
          fn) {
    return fn.apply(sort()).build();
  }

  @Override
  public DataStoreFieldSort.Builder field() {
    return new OpensearchFieldSort.Builder();
  }

  @Override
  public DataStoreFieldSort field(
      final Function<DataStoreFieldSort.Builder, DataStoreObjectBuilder<DataStoreFieldSort>> fn) {
    return fn.apply(field()).build();
  }
}
