/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.source.SearchSourceConfig;
import io.camunda.search.clients.source.SearchSourceFilter;
import io.camunda.search.clients.types.TypedValue;
import io.camunda.search.transformers.SearchTransfomer;
import io.camunda.service.search.sort.SearchSortOptions;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.search.SourceConfig;
import org.opensearch.client.opensearch.core.search.SourceFilter;

public abstract class OpensearchTransformer<T, R> implements SearchTransfomer<T, R> {

  protected final OpensearchTransformers transformers;

  public OpensearchTransformer(final OpensearchTransformers transformers) {
    this.transformers = transformers;
  }

  protected <T, R> SearchTransfomer<T, R> getTransformer(final Class<?> cls) {
    return (SearchTransfomer<T, R>) transformers.getTransformer(cls);
  }

  protected SearchTransfomer<SearchQuery, Query> getQueryTransformer() {
    return getTransformer(SearchQuery.class);
  }

  protected SearchTransfomer<TypedValue, FieldValue> getFieldValueTransformer() {
    return getTransformer(TypedValue.class);
  }

  protected SearchTransfomer<SearchSortOptions, SortOptions> getSortOptionsTransformer() {
    return getTransformer(SearchSortOptions.class);
  }

  protected SearchTransfomer<SearchSourceConfig, SourceConfig> getSourceConfigTransformer() {
    return getTransformer(SearchSourceConfig.class);
  }

  protected SearchTransfomer<SearchSourceFilter, SourceFilter> getSourceFilterTransformer() {
    return getTransformer(SearchSourceFilter.class);
  }
}
