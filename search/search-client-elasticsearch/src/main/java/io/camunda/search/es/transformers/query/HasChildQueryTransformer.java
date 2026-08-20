/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.query;

import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.HasChildQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import io.camunda.search.clients.query.SearchHasChildQuery;
import io.camunda.search.es.transformers.ElasticsearchTransformers;

public final class HasChildQueryTransformer
    extends QueryOptionTransformer<SearchHasChildQuery, HasChildQuery> {

  public HasChildQueryTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public HasChildQuery apply(final SearchHasChildQuery value) {
    final var transformer = getQueryTransformer();
    final var searchQuery = value.query();
    final var query = transformer.apply(searchQuery);
    final var type = value.type();
    return QueryBuilders.hasChild().type(type).query(query).scoreMode(ChildScoreMode.None).build();
  }
}
