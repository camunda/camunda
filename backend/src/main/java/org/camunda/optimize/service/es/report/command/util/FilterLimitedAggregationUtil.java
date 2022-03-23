/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.ParsedSingleBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilter;

import java.util.Optional;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FilterLimitedAggregationUtil {

  public static final String FILTER_LIMITED_AGGREGATION = "filterLimitedAggregation";

  public static FilterAggregationBuilder wrapWithFilterLimitedParentAggregation(
    final BoolQueryBuilder limitFilterQuery,
    final AggregationBuilder subAggregationToLimit) {
    return wrapWithFilterLimitedParentAggregation(FILTER_LIMITED_AGGREGATION, limitFilterQuery, subAggregationToLimit);
  }

  public static FilterAggregationBuilder wrapWithFilterLimitedParentAggregation(final String filterParentAggregationName,
                                                                                final QueryBuilder limitFilterQuery,
                                                                                final AggregationBuilder subAggregationToLimit) {
    return AggregationBuilders
      .filter(filterParentAggregationName, limitFilterQuery)
      .subAggregation(subAggregationToLimit);
  }

  public static Optional<Aggregations> unwrapFilterLimitedAggregations(final Aggregations aggregations) {
    return unwrapFilterLimitedAggregations(FILTER_LIMITED_AGGREGATION, aggregations);
  }

  public static Optional<Aggregations> unwrapFilterLimitedAggregations(final String filterParentAggregationName,
                                                                       final Aggregations aggregations) {
    return Optional.ofNullable((ParsedFilter) aggregations.get(filterParentAggregationName))
      .map(ParsedSingleBucketAggregation::getAggregations);
  }
}
