/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.transformers;

import io.camunda.data.clients.query.DataStoreQuery;
import io.camunda.data.mappers.DataStoreTransformer;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public abstract class OpensearchTransformer<T, R> implements DataStoreTransformer<T, R> {

  protected final OpensearchTransformers mappers;
  protected final DataStoreTransformer<DataStoreQuery, Query> queryTransformer;

  public OpensearchTransformer(final OpensearchTransformers mappers) {
    this.mappers = mappers;
    queryTransformer = mappers.getMapper(DataStoreQuery.class);
  }
}
