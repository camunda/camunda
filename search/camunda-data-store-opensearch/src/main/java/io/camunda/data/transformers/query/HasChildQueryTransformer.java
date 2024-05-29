/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.transformers.query;

import io.camunda.data.clients.query.DataStoreHasChildQuery;
import io.camunda.data.transformers.OpensearchTransformers;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.HasChildQuery;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;

public final class HasChildQueryTransformer
    extends QueryVariantTransformer<DataStoreHasChildQuery, HasChildQuery> {

  public HasChildQueryTransformer(final OpensearchTransformers mappers) {
    super(mappers);
  }

  @Override
  public HasChildQuery apply(final DataStoreHasChildQuery value) {
    final var query = queryTransformer.apply(value.query());
    return QueryBuilders.hasChild()
        .type(value.type())
        .query(query)
        .scoreMode(ChildScoreMode.None)
        .build();
  }
}
