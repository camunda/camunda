/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.util;

import java.util.Optional;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.ParsedSingleBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilter;

public class FilterLimitedAggregationUtil {

  public static final String FILTER_LIMITED_AGGREGATION = "filterLimitedAggregation";

  private FilterLimitedAggregationUtil() {}

  public static FilterAggregationBuilder wrapWithFilterLimitedParentAggregation(
      final BoolQueryBuilder limitFilterQuery, final AggregationBuilder subAggregationToLimit) {
    return wrapWithFilterLimitedParentAggregation(
        FILTER_LIMITED_AGGREGATION, limitFilterQuery, subAggregationToLimit);
  }

  public static FilterAggregationBuilder wrapWithFilterLimitedParentAggregation(
      final String filterParentAggregationName,
      final QueryBuilder limitFilterQuery,
      final AggregationBuilder subAggregationToLimit) {
    return AggregationBuilders.filter(filterParentAggregationName, limitFilterQuery)
        .subAggregation(subAggregationToLimit);
  }

  public static Optional<Aggregations> unwrapFilterLimitedAggregations(
      final Aggregations aggregations) {
    return unwrapFilterLimitedAggregations(FILTER_LIMITED_AGGREGATION, aggregations);
  }

  public static Optional<Aggregations> unwrapFilterLimitedAggregations(
      final String filterParentAggregationName, final Aggregations aggregations) {
    return Optional.ofNullable((ParsedFilter) aggregations.get(filterParentAggregationName))
        .map(ParsedSingleBucketAggregation::getAggregations);
  }
}
