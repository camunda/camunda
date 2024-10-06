/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.query;

import io.camunda.search.clients.query.SearchHasParentQuery;
import io.camunda.search.os.transformers.OpensearchTransformers;
import org.opensearch.client.opensearch._types.query_dsl.HasParentQuery;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;

public final class HasParentQueryTransformer
    extends QueryOptionTransformer<SearchHasParentQuery, HasParentQuery> {

  public HasParentQueryTransformer(final OpensearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public HasParentQuery apply(final SearchHasParentQuery value) {
    final var transformer = getQueryTransformer();
    final var searchQuery = value.query();
    final var query = transformer.apply(searchQuery);
    final var parentType = value.parentType();
    return QueryBuilders.hasParent().parentType(parentType).query(query).build();
  }
}
