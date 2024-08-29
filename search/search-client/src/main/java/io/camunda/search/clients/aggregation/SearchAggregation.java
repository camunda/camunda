/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.aggregation;

import static io.camunda.search.clients.aggregation.SearchAggregationBuilders.aggregation;

import io.camunda.search.clients.query.SearchQuery.Builder;
import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

public record SearchAggregation(SearchAggregationOption aggregationOption) {

  public static SearchAggregation of(final Function<Builder, ObjectBuilder<SearchAggregation>> fn) {
    return aggregation(fn);
  }

  public static final class Builder implements ObjectBuilder<SearchAggregation> {

    private SearchAggregationOption aggregationOption;

    public Builder cardinality(final SearchCardinalityAggregation cardinality) {
      aggregationOption = cardinality;
      return this;
    }

    public Builder cardinality(
        final Function<
                SearchCardinalityAggregation.Builder, ObjectBuilder<SearchCardinalityAggregation>>
            fn) {
      return cardinality(SearchAggregationBuilders.cardinality(fn));
    }

    @Override
    public SearchAggregation build() {
      return new SearchAggregation(aggregationOption);
    }
  }
}
