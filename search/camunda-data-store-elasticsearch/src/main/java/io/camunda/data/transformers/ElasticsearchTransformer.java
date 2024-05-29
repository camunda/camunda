/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.transformers;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.camunda.data.clients.query.DataStoreQuery;
import io.camunda.data.clients.sort.DataStoreSortOptions;
import io.camunda.data.clients.types.DataStoreTypedValue;
import io.camunda.data.mappers.DataStoreTransformer;

public abstract class ElasticsearchTransformer<T, R> implements DataStoreTransformer<T, R> {

  protected final ElasticsearchTransformers transformers;

  public ElasticsearchTransformer(final ElasticsearchTransformers transformers) {
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
