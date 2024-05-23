/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.query;

import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.json.JsonData;

public class ElasticsearchRangeQuery extends ElasticsearchQueryVariant<RangeQuery>
    implements DataStoreRangeQuery {

  public ElasticsearchRangeQuery(final RangeQuery queryVariant) {
    super(queryVariant);
  }

  public static final class Builder implements DataStoreRangeQuery.Builder {

    private RangeQuery.Builder wrappedBuilder;

    public Builder() {
      wrappedBuilder = new RangeQuery.Builder();
    }

    private <V> JsonData toJson(final V value) {
      return JsonData.of(value);
    }

    @Override
    public Builder field(final String field) {
      wrappedBuilder.field(field);
      return this;
    }

    @Override
    public <V> Builder gt(final V value) {
      wrappedBuilder.gt(toJson(value));
      return this;
    }

    @Override
    public <V> Builder gte(final V value) {
      wrappedBuilder.gte(toJson(value));
      return this;
    }

    @Override
    public <V> Builder lt(final V value) {
      wrappedBuilder.lt(toJson(value));
      return this;
    }

    @Override
    public <V> Builder lte(final V value) {
      wrappedBuilder.lte(toJson(value));
      return this;
    }

    @Override
    public Builder from(final String value) {
      wrappedBuilder.from(value);
      return this;
    }

    @Override
    public Builder to(final String to) {
      wrappedBuilder.to(to);
      return this;
    }

    @Override
    public DataStoreRangeQuery build() {
      final var query = wrappedBuilder.build();
      return new ElasticsearchRangeQuery(query);
    }
  }
}
