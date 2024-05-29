/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.transformers.query;

import io.camunda.data.clients.query.DataStoreMatchQuery;
import io.camunda.data.transformers.OpensearchTransformers;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.MatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.Operator;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;

public final class MatchQueryTransformer
    extends QueryVariantTransformer<DataStoreMatchQuery, MatchQuery> {

  public MatchQueryTransformer(final OpensearchTransformers mappers) {
    super(mappers);
  }

  @Override
  public MatchQuery apply(final DataStoreMatchQuery value) {
    return QueryBuilders.match()
        .field(value.field())
        .query(FieldValue.of(value.query()))
        .operator(Operator.valueOf(value.operator()))
        .build();
  }
}
