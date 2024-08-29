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
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.camunda.search.clients.aggregation.SearchAggregate;
import io.camunda.search.clients.aggregation.SearchAggregation;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.sort.SearchSortOptions;
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

  protected SearchTransfomer<SearchAggregation, Aggregation> getAggregationTransformer() {
    return getTransformer(SearchAggregation.class);
  }

  protected SearchTransfomer<Aggregate, SearchAggregate> getAggregateTransformer() {
    return getTransformer(Aggregate.class);
  }
}
