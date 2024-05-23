/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.query;

import org.opensearch.client.opensearch._types.query_dsl.MatchAllQuery;

public class OpensearchMatchAllQuery extends OpensearchQueryVariant<MatchAllQuery>
    implements DataStoreMatchAllQuery {

  public OpensearchMatchAllQuery(final MatchAllQuery query) {
    super(query);
  }

  public static final class Builder implements DataStoreMatchAllQuery.Builder {

    private MatchAllQuery.Builder wrappedBuilder;

    public Builder() {
      wrappedBuilder = new MatchAllQuery.Builder();
    }

    @Override
    public DataStoreMatchAllQuery build() {
      final var query = wrappedBuilder.build();
      return new OpensearchMatchAllQuery(query);
    }
  }
}
