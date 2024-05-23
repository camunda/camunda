/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.query;

import co.elastic.clients.elasticsearch._types.query_dsl.WildcardQuery;

public class ElasticsearchWildcardQuery extends ElasticsearchQueryVariant<WildcardQuery>
    implements DataStoreWildcardQuery {

  public ElasticsearchWildcardQuery(final WildcardQuery query) {
    super(query);
  }

  public static final class Builder implements DataStoreWildcardQuery.Builder {

    private WildcardQuery.Builder wrappedBuilder;

    public Builder() {
      wrappedBuilder = new WildcardQuery.Builder();
    }

    @Override
    public DataStoreWildcardQuery.Builder field(final String field) {
      wrappedBuilder.field(field);
      return this;
    }

    @Override
    public DataStoreWildcardQuery.Builder value(final String value) {
      wrappedBuilder.value(value);
      return this;
    }

    @Override
    public DataStoreWildcardQuery build() {
      final var query = wrappedBuilder.build();
      return new ElasticsearchWildcardQuery(query);
    }
  }
}
