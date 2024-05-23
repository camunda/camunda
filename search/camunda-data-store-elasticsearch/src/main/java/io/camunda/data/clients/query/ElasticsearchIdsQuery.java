/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.query;

import co.elastic.clients.elasticsearch._types.query_dsl.IdsQuery;
import java.util.List;

public final class ElasticsearchIdsQuery extends ElasticsearchQueryVariant<IdsQuery>
    implements DataStoreIdsQuery {

  private ElasticsearchIdsQuery(final IdsQuery idsQuery) {
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
      return new ElasticsearchIdsQuery(idsQuery);
    }
  }
}
