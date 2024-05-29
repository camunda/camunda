/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.transformers.query;

import io.camunda.data.clients.query.DataStoreWildcardQuery;
import io.camunda.data.transformers.OpensearchTransformer;
import io.camunda.data.transformers.OpensearchTransformers;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch._types.query_dsl.WildcardQuery;

public final class WildcardQueryTransformer
    extends OpensearchTransformer<DataStoreWildcardQuery, WildcardQuery> {

  public WildcardQueryTransformer(final OpensearchTransformers mappers) {
    super(mappers);
  }

  @Override
  public WildcardQuery apply(final DataStoreWildcardQuery value) {
    return QueryBuilders.wildcard().field(value.field()).value(value.value()).build();
  }
}
