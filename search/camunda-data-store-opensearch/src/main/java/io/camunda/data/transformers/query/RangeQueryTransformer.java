/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.transformers.query;

import io.camunda.data.clients.query.DataStoreRangeQuery;
import io.camunda.data.transformers.OpensearchTransformers;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;

public final class RangeQueryTransformer
    extends QueryVariantTransformer<DataStoreRangeQuery, RangeQuery> {

  public RangeQueryTransformer(final OpensearchTransformers mappers) {
    super(mappers);
  }

  @Override
  public RangeQuery apply(final DataStoreRangeQuery value) {
    return QueryBuilders.range()
        .field(value.field())
        .gt(JsonData.of(value.gt()))
        .gte(JsonData.of(value.gt()))
        .lt(JsonData.of(value.gt()))
        .lte(JsonData.of(value.gt()))
        .from(JsonData.of(value.from()))
        .from(JsonData.of(value.to()))
        .build();
  }
}
