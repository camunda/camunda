/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.aggregation;

import static io.camunda.search.clients.aggregation.SearchAggregateBuilders.aggregate;

import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

public record SearchAggregate(SearchAggregateOption aggregateOption) {

  public static SearchAggregate of(final Function<Builder, ObjectBuilder<SearchAggregate>> fn) {
    return aggregate(fn);
  }

  public static final class Builder implements ObjectBuilder<SearchAggregate> {

    private SearchAggregateOption aggregateOption;

    public Builder cardinality(final SearchCardinalityAggregate cardinality) {
      aggregateOption = cardinality;
      return this;
    }

    public Builder cardinality(
        final Function<
                SearchCardinalityAggregate.Builder, ObjectBuilder<SearchCardinalityAggregate>>
            fn) {
      return cardinality(SearchAggregateBuilders.cardinality(fn));
    }

    @Override
    public SearchAggregate build() {
      return new SearchAggregate(aggregateOption);
    }
  }
}
