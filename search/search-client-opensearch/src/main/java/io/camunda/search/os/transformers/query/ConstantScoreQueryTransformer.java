/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.query;

import io.camunda.search.clients.query.SearchConstantScoreQuery;
import io.camunda.search.os.transformers.OpensearchTransformers;
import org.opensearch.client.opensearch._types.query_dsl.ConstantScoreQuery;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;

public final class ConstantScoreQueryTransformer
    extends QueryOptionTransformer<SearchConstantScoreQuery, ConstantScoreQuery> {

  public ConstantScoreQueryTransformer(final OpensearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public ConstantScoreQuery apply(final SearchConstantScoreQuery value) {
    final var transformer = getQueryTransformer();
    final var searchQuery = value.query();
    final var query = transformer.apply(searchQuery);
    return QueryBuilders.constantScore().filter(query).build();
  }
}
