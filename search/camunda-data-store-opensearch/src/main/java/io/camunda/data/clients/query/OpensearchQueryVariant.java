/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.query;

import org.opensearch.client.opensearch._types.query_dsl.QueryVariant;

public class OpensearchQueryVariant<T extends QueryVariant> implements DataStoreQueryVariant {

  private final T queryVariant;

  public OpensearchQueryVariant(final T queryVariant) {
    this.queryVariant = queryVariant;
  }

  public T queryVariant() {
    return queryVariant;
  }

  @Override
  public DataStoreQuery toQuery() {
    return new OpensearchQuery(this);
  }
}
