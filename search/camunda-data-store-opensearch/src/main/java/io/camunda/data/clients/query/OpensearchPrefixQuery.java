/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.query;

import org.opensearch.client.opensearch._types.query_dsl.PrefixQuery;

public class OpensearchPrefixQuery extends OpensearchQueryVariant<PrefixQuery>
    implements DataStorePrefixQuery {

  public OpensearchPrefixQuery(final PrefixQuery query) {
    super(query);
  }

  public static final class Builder implements DataStorePrefixQuery.Builder {

    private PrefixQuery.Builder wrappedBuilder;

    public Builder() {
      wrappedBuilder = new PrefixQuery.Builder();
    }

    @Override
    public Builder field(final String value) {
      wrappedBuilder.field(value);
      return this;
    }

    @Override
    public Builder value(final String value) {
      wrappedBuilder.value(value);
      return this;
    }

    @Override
    public DataStorePrefixQuery build() {
      final var query = wrappedBuilder.build();
      return new OpensearchPrefixQuery(query);
    }
  }
}
