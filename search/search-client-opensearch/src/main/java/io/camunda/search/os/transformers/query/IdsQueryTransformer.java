/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.query;

import io.camunda.search.clients.query.SearchIdsQuery;
import io.camunda.search.os.transformers.OpensearchTransformers;
import org.opensearch.client.opensearch._types.query_dsl.IdsQuery;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;

public final class IdsQueryTransformer extends QueryOptionTransformer<SearchIdsQuery, IdsQuery> {

  public IdsQueryTransformer(final OpensearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public IdsQuery apply(final SearchIdsQuery value) {
    final var values = value.values();
    return QueryBuilders.ids().values(values).build();
  }
}
