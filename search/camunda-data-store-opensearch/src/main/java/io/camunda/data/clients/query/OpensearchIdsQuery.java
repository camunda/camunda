/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.query;

import java.util.List;
import org.opensearch.client.opensearch._types.query_dsl.IdsQuery;

public final class OpensearchIdsQuery extends OpensearchQueryVariant<IdsQuery>
    implements DataStoreIdsQuery {

  private OpensearchIdsQuery(final IdsQuery idsQuery) {
    super(idsQuery);
  }

  public static final class Builder implements DataStoreIdsQuery.Builder {

    private final IdsQuery.Builder wrappedBuilder;

    public Builder() {
      this.wrappedBuilder = new IdsQuery.Builder();
    }

    @Override
    public Builder values(final List<String> list) {
      wrappedBuilder.values(list);
      return this;
    }

    @Override
    public Builder values(final String value, final String... values) {
      wrappedBuilder.values(value, values);
      return this;
    }

    @Override
    public DataStoreIdsQuery build() {
      final var idsQuery = wrappedBuilder.build();
      return new OpensearchIdsQuery(idsQuery);
    }
  }
}
