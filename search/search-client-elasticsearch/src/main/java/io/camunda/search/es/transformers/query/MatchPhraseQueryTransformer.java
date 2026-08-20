/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.query;

import co.elastic.clients.elasticsearch._types.query_dsl.MatchPhraseQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import io.camunda.search.clients.query.SearchMatchPhraseQuery;
import io.camunda.search.es.transformers.ElasticsearchTransformers;

public final class MatchPhraseQueryTransformer
    extends QueryOptionTransformer<SearchMatchPhraseQuery, MatchPhraseQuery> {

  public MatchPhraseQueryTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public MatchPhraseQuery apply(final SearchMatchPhraseQuery value) {
    return QueryBuilders.matchPhrase().query(value.query()).field(value.field()).build();
  }
}
