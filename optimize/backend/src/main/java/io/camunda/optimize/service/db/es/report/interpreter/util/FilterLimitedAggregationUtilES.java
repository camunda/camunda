/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.util;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import java.util.Map;
import java.util.Optional;

public final class FilterLimitedAggregationUtilES {

  public static final String FILTER_LIMITED_AGGREGATION = "filterLimitedAggregation";

  private FilterLimitedAggregationUtilES() {}

  public static Map<String, Aggregation.Builder.ContainerBuilder>
      wrapWithFilterLimitedParentAggregation(
          final Query limitFilterQuery,
          final Map<String, Aggregation.Builder.ContainerBuilder> subAggregationsToLimit) {
    return wrapWithFilterLimitedParentAggregation(
        FILTER_LIMITED_AGGREGATION, limitFilterQuery, subAggregationsToLimit);
  }

  public static Map<String, Aggregation.Builder.ContainerBuilder>
      wrapWithFilterLimitedParentAggregation(
          final String filterParentAggregationName,
          final Query limitFilterQuery,
          final Map<String, Aggregation.Builder.ContainerBuilder> subAggregationsToLimit) {
    final Aggregation.Builder.ContainerBuilder builder =
        new Aggregation.Builder().filter(limitFilterQuery);
    subAggregationsToLimit.forEach((k, v) -> builder.aggregations(k, a -> v));
    return Map.of(filterParentAggregationName, builder);
  }

  public static Optional<Map<String, Aggregate>> unwrapFilterLimitedAggregations(
      final Map<String, Aggregate> aggregations) {
    return unwrapFilterLimitedAggregations(FILTER_LIMITED_AGGREGATION, aggregations);
  }

  public static Optional<Map<String, Aggregate>> unwrapFilterLimitedAggregations(
      final String filterParentAggregationName, final Map<String, Aggregate> aggregations) {
    return Optional.ofNullable(aggregations.get(filterParentAggregationName))
        .map(a -> a.filter().aggregations());
  }
}
