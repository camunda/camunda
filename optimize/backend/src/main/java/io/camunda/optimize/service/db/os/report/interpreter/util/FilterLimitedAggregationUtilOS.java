/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.util;

import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public final class FilterLimitedAggregationUtilOS {

  public static final String FILTER_LIMITED_AGGREGATION = "filterLimitedAggregation";

  private FilterLimitedAggregationUtilOS() {}

  public static Pair<String, Aggregation> wrapWithFilterLimitedParentAggregation(
      final Query limitFilterQuery, final Pair<String, Aggregation> subAggregationToLimit) {
    return wrapWithFilterLimitedParentAggregation(
        FILTER_LIMITED_AGGREGATION, limitFilterQuery, subAggregationToLimit);
  }

  public static Pair<String, Aggregation> wrapWithFilterLimitedParentAggregation(
      final String filterParentAggregationName,
      final Query limitFilterQuery,
      final Pair<String, Aggregation> subAggregation) {
    return Pair.of(
        filterParentAggregationName,
        new Aggregation.Builder()
            .filter(limitFilterQuery)
            .aggregations(subAggregation.getKey(), subAggregation.getValue())
            .build());
  }

  public static Optional<Map<String, Aggregate>> unwrapFilterLimitedAggregations(
      final Map<String, Aggregate> aggregations) {
    return unwrapFilterLimitedAggregations(FILTER_LIMITED_AGGREGATION, aggregations);
  }

  public static Optional<Map<String, Aggregate>> unwrapFilterLimitedAggregations(
      final String filterParentAggregationName, final Map<String, Aggregate> aggregations) {
    return aggregations.containsKey(filterParentAggregationName)
        ? Optional.ofNullable(aggregations.get(filterParentAggregationName).filter().aggregations())
        : Optional.empty();
  }
}
