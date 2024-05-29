/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.transformers.query;

import io.camunda.data.clients.query.DataStoreQueryVariant;
import io.camunda.data.mappers.DataStoreTransformer;
import io.camunda.data.transformers.OpensearchTransformer;
import io.camunda.data.transformers.OpensearchTransformers;
import org.opensearch.client.opensearch._types.query_dsl.QueryVariant;

public abstract class QueryVariantTransformer<
        T extends DataStoreQueryVariant, R extends QueryVariant>
    extends OpensearchTransformer<T, R> implements DataStoreTransformer<T, R> {

  public QueryVariantTransformer(final OpensearchTransformers transformers) {
    super(transformers);
  }
}
