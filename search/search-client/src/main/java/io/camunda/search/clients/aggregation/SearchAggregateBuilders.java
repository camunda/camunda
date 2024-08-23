/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.aggregation;

import io.camunda.search.clients.aggregation.SearchAggregate.Builder;
import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

public class SearchAggregateBuilders {

  public static SearchAggregate aggregate(
      final Function<Builder, ObjectBuilder<SearchAggregate>> fn) {
    return fn.apply(aggregate()).build();
  }

  public static SearchAggregate.Builder aggregate() {
    return new SearchAggregate.Builder();
  }

  public static SearchCardinalityAggregate.Builder cardinality() {
    return new SearchCardinalityAggregate.Builder();
  }

  public static SearchCardinalityAggregate cardinality(
      final Function<SearchCardinalityAggregate.Builder, ObjectBuilder<SearchCardinalityAggregate>>
          fn) {
    return fn.apply(cardinality()).build();
  }

  public static SearchCardinalityAggregate cardinality(final Long value) {
    return cardinality(c -> c.value(value));
  }
}
