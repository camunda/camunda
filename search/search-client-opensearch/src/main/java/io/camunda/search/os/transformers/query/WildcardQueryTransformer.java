/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.query;

import io.camunda.search.clients.query.SearchWildcardQuery;
import io.camunda.search.os.transformers.OpensearchTransformer;
import io.camunda.search.os.transformers.OpensearchTransformers;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch._types.query_dsl.WildcardQuery;

public final class WildcardQueryTransformer
    extends OpensearchTransformer<SearchWildcardQuery, WildcardQuery> {

  public WildcardQueryTransformer(final OpensearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public WildcardQuery apply(final SearchWildcardQuery value) {
    final var field = value.field();
    final var fieldValue = value.value();
    return QueryBuilders.wildcard().field(field).value(fieldValue).build();
  }
}
