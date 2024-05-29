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

  public SortOptionsTransformer(final OpensearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public SortOptions apply(final DataStoreSortOptions value) {
    final var transformer = getFieldSortTransformer();
    final var field = value.field();
    final var transformedField = transformer.apply(field);

    return new SortOptions.Builder().field(transformedField).build();
  }

  protected DataStoreTransformer<DataStoreFieldSort, FieldSort> getFieldSortTransformer() {
    return transformers.getTransformer(DataStoreFieldSort.class);
  }
}
