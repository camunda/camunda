/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.query;

import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import io.camunda.search.clients.query.SearchMatchAllQuery;
import io.camunda.search.es.transformers.ElasticsearchTransformers;

public final class MatchAllQueryTransformer
    extends QueryOptionTransformer<SearchMatchAllQuery, MatchAllQuery> {

  public MatchAllQueryTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public MatchAllQuery apply(final SearchMatchAllQuery value) {
    return QueryBuilders.matchAll().build();
  }
}
