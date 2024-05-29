/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.transformers.query;

import io.camunda.data.clients.query.DataStoreTermQuery;
import io.camunda.data.transformers.OpensearchTransformers;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;

public class TermQueryTransformer extends QueryVariantTransformer<DataStoreTermQuery, TermQuery> {

  public TermQueryTransformer(final OpensearchTransformers mappers) {
    super(mappers);
  }

  @Override
  public TermQuery apply(final DataStoreTermQuery value) {
    return QueryBuilders.term()
        .field(value.field())
        .value(fieldValueTransformer.apply(value.value()))
        .build();
  }
}
