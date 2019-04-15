/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.util;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.ParsedSingleBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilter;

import java.util.Optional;

public class FilterLimitedAggregationUtil {

  private static final String FILTER_LIMITED_AGGREGATION = "filterLimitedAggregation";

  private FilterLimitedAggregationUtil() {
  }

  public static FilterAggregationBuilder wrapWithFilterLimitedParentAggregation(
    final BoolQueryBuilder limitFilterQuery,
    final AggregationBuilder subAggregationToLimit) {
    return AggregationBuilders
      .filter(FILTER_LIMITED_AGGREGATION, limitFilterQuery)
      .subAggregation(subAggregationToLimit);
  }

  public static Optional<Aggregations> unwrapFilterLimitedAggregations(final Aggregations aggregations) {
    return Optional.ofNullable((ParsedFilter) aggregations.get(FILTER_LIMITED_AGGREGATION))
      .map(ParsedSingleBucketAggregation::getAggregations);
  }

  public static boolean isResultComplete(final SearchResponse response) {
    boolean complete = true;
    final Aggregations aggregations = response.getAggregations();
    if (aggregations.getAsMap().containsKey(FILTER_LIMITED_AGGREGATION)) {
      final ParsedFilter limitingAggregation = aggregations.get(FILTER_LIMITED_AGGREGATION);
      complete = limitingAggregation.getDocCount() == response.getHits().getTotalHits();
    }
    return complete;
  }
}
