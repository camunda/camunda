/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.query;

import co.elastic.clients.elasticsearch._types.query_dsl.NestedQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import io.camunda.search.clients.query.SearchNestedQuery;
import io.camunda.search.es.transformers.ElasticsearchTransformers;

public final class NestedQueryTransformer
    extends QueryOptionTransformer<SearchNestedQuery, NestedQuery> {

  public NestedQueryTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public NestedQuery apply(final SearchNestedQuery value) {
    final var transformer = getQueryTransformer();
    final var searchQuery = value.query();
    final var query = transformer.apply(searchQuery);
    final var path = value.path();
    return QueryBuilders.nested().path(path).query(query).build();
  }
}
