/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.query;

import io.camunda.search.clients.query.SearchNestedQuery;
import io.camunda.search.os.transformers.OpensearchTransformers;
import org.opensearch.client.opensearch._types.query_dsl.NestedQuery;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;

public final class NestedQueryTransformer
    extends QueryOptionTransformer<SearchNestedQuery, NestedQuery> {

  public NestedQueryTransformer(final OpensearchTransformers transformers) {
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
