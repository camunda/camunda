/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.SourceConfig;
import co.elastic.clients.elasticsearch.core.search.SourceFilter;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.service.search.sort.SearchSortOptions;
import io.camunda.search.clients.source.SearchSourceConfig;
import io.camunda.search.clients.source.SearchSourceFilter;
import io.camunda.search.clients.types.TypedValue;
import io.camunda.search.transformers.SearchTransfomer;

public abstract class ElasticsearchTransformer<T, R> implements SearchTransfomer<T, R> {

  protected final ElasticsearchTransformers transformers;

  public ElasticsearchTransformer(final ElasticsearchTransformers transformers) {
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
