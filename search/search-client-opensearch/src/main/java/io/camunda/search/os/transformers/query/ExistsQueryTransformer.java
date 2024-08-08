/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.query;

import io.camunda.search.clients.query.SearchExistsQuery;
import io.camunda.search.os.transformers.OpensearchTransformers;
import org.opensearch.client.opensearch._types.query_dsl.ExistsQuery;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;

public final class ExistsQueryTransformer
    extends QueryOptionTransformer<SearchExistsQuery, ExistsQuery> {

  public ExistsQueryTransformer(final OpensearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public ExistsQuery apply(final SearchExistsQuery value) {
    final var field = value.field();
    return QueryBuilders.exists().field(field).build();
  }
}
