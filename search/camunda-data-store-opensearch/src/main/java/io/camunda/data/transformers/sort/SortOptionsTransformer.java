/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.transformers.sort;

import io.camunda.data.clients.sort.DataStoreFieldSort;
import io.camunda.data.clients.sort.DataStoreSortOptions;
import io.camunda.data.mappers.DataStoreTransformer;
import io.camunda.data.transformers.OpensearchTransformer;
import io.camunda.data.transformers.OpensearchTransformers;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.SortOptions;

public final class SortOptionsTransformer
    extends OpensearchTransformer<DataStoreSortOptions, SortOptions> {

  protected final DataStoreTransformer<DataStoreFieldSort, FieldSort> fieldSortTransformer;

  public SortOptionsTransformer(final OpensearchTransformers mappers) {
    super(mappers);
    fieldSortTransformer = mappers.getMapper(DataStoreFieldSort.class);
  }

  @Override
  public SortOptions apply(final DataStoreSortOptions value) {
    return new SortOptions.Builder().field(fieldSortTransformer.apply(value.field())).build();
  }
}
