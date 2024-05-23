/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.query;

import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.HasChildQuery;
import io.camunda.util.DataStoreObjectBuilder;
import java.util.function.Function;

public class ElasticsearchHasChildQuery extends ElasticsearchQueryVariant<HasChildQuery>
    implements DataStoreHasChildQuery {

  public ElasticsearchHasChildQuery(final HasChildQuery query) {
    super(query);
  }

  public static final class Builder implements DataStoreHasChildQuery.Builder {

    private HasChildQuery.Builder wrappedBuilder;

    public Builder() {
      wrappedBuilder = new HasChildQuery.Builder();
    }

    @Override
    public Builder query(final DataStoreQuery query) {
      wrappedBuilder.query(((ElasticsearchQuery) query).query());
      return this;
    }

    @Override
    public Builder query(
        final Function<DataStoreQuery.Builder, DataStoreObjectBuilder<DataStoreQuery>> fn) {
      return query(DataStoreQueryBuilders.query(fn));
    }

    @Override
    public Builder type(final String value) {
      wrappedBuilder.type(value);
      return this;
    }

    @Override
    public DataStoreHasChildQuery build() {
      final var query = wrappedBuilder.scoreMode(ChildScoreMode.None).build();
      return new ElasticsearchHasChildQuery(query);
    }
  }
}
