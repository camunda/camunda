/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.query;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryVariant;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryOption;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.es.transformers.ElasticsearchTransformer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;

public final class QueryTransformer extends ElasticsearchTransformer<SearchQuery, Query> {

  public QueryTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public Query apply(final SearchQuery value) {
    final var queryOption = value.queryOption();

    if (queryOption == null) {
      return null;
    }

    final var queryOptionCls = queryOption.getClass();
    final var transformer = getQueryOptionTransformer(queryOptionCls);
    final var transformedQueryOption = transformer.apply(queryOption);
    final var query = transformedQueryOption._toQuery();
    return query;
  }

  public <T extends SearchQueryOption, R extends QueryVariant>
      SearchTransfomer<T, R> getQueryOptionTransformer(final Class<?> cls) {
    return getTransformer(cls);
  }
}
