/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.transformers.query;

import io.camunda.data.clients.query.DataStoreExistsQuery;
import io.camunda.data.transformers.OpensearchTransformers;
import org.opensearch.client.opensearch._types.query_dsl.ExistsQuery;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;

public final class ExistsQueryTransformer
    extends QueryVariantTransformer<DataStoreExistsQuery, ExistsQuery> {

  public ExistsQueryTransformer(final OpensearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public ExistsQuery apply(final DataStoreExistsQuery value) {
    final var field = value.field();
    return QueryBuilders.exists().field(field).build();
  }
}
