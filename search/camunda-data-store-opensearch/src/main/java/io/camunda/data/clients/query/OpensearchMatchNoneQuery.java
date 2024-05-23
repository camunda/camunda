/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.query;

import org.opensearch.client.opensearch._types.query_dsl.MatchNoneQuery;

public class OpensearchMatchNoneQuery extends OpensearchQueryVariant<MatchNoneQuery>
    implements DataStoreMatchNoneQuery {

  public OpensearchMatchNoneQuery(final MatchNoneQuery query) {
    super(query);
  }

  public static final class Builder implements DataStoreMatchNoneQuery.Builder {

    private MatchNoneQuery.Builder wrappedBuilder;

    public Builder() {
      wrappedBuilder = new MatchNoneQuery.Builder();
    }

    @Override
    public DataStoreMatchNoneQuery build() {
      final var query = wrappedBuilder.build();
      return new OpensearchMatchNoneQuery(query);
    }
  }
}
