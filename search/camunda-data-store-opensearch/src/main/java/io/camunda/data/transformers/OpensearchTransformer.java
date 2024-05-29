/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.transformers;

import io.camunda.data.clients.query.DataStoreQuery;
import io.camunda.data.clients.sort.DataStoreSortOptions;
import io.camunda.data.clients.types.DataStoreTypedValue;
import io.camunda.data.mappers.DataStoreTransformer;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public abstract class OpensearchTransformer<T, R> implements DataStoreTransformer<T, R> {

  protected final OpensearchTransformers transformers;

  public OpensearchTransformer(final OpensearchTransformers transformers) {
    this.transformers = transformers;
  }

  protected <T, R> DataStoreTransformer<T, R> getTransformer(final Class<?> cls) {
    return (DataStoreTransformer<T, R>) transformers.getTransformer(cls);
  }

  protected DataStoreTransformer<DataStoreQuery, Query> getQueryTransformer() {
    return getTransformer(DataStoreQuery.class);
  }

  protected DataStoreTransformer<DataStoreTypedValue, FieldValue> getFieldValueTransformer() {
    return getTransformer(DataStoreTypedValue.class);
  }

  protected DataStoreTransformer<DataStoreSortOptions, SortOptions> getSortOptionsTransformer() {
    return getTransformer(DataStoreSortOptions.class);
  }
}
