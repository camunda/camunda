/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.query;

import co.elastic.clients.elasticsearch._types.query_dsl.MatchNoneQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import io.camunda.search.clients.query.SearchMatchNoneQuery;
import io.camunda.search.es.transformers.ElasticsearchTransformers;

public final class MatchNoneQueryTransformer
    extends QueryOptionTransformer<SearchMatchNoneQuery, MatchNoneQuery> {

  public MatchNoneQueryTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public MatchNoneQuery apply(final SearchMatchNoneQuery value) {
    return QueryBuilders.matchNone().build();
  }
}
