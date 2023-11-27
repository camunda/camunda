/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.opensearch;

import io.camunda.operate.store.opensearch.dsl.AggregationDSL;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.BucketSortAggregation;
import org.opensearch.client.opensearch._types.aggregations.CalendarInterval;
import org.opensearch.client.opensearch._types.aggregations.CardinalityAggregation;
import org.opensearch.client.opensearch._types.aggregations.ChildrenAggregation;
import org.opensearch.client.opensearch._types.aggregations.DateHistogramAggregation;
import org.opensearch.client.opensearch._types.aggregations.FiltersAggregation;
import org.opensearch.client.opensearch._types.aggregations.ParentAggregation;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;
import org.opensearch.client.opensearch._types.aggregations.TopHitsAggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Wrapper class around the static AggregationDSL interface. Enhances testability by allowing classes to utilize the
 * AggregationDSL class without static calls, enabling unit tests to mock this out and reduce test complexity
 */
@Component
public class OpensearchAggregationDSLWrapper {

  public BucketSortAggregation bucketSortAggregation(@Nullable Integer size, SortOptions... sortOptions) {
    return AggregationDSL.bucketSortAggregation(size, sortOptions);
  }

  public CardinalityAggregation cardinalityAggregation(String field) {
    return AggregationDSL.cardinalityAggregation(field);
  }

  public CardinalityAggregation cardinalityAggregation(String field, int precisionThreshold) {
    return AggregationDSL.cardinalityAggregation(field, precisionThreshold);
  }

  public CalendarInterval calendarIntervalByAlias(String alias) {
    return AggregationDSL.calendarIntervalByAlias(alias);
  }

  public DateHistogramAggregation dateHistogramAggregation(String field, String calendarIntervalAlias, String format, boolean keyed) {
    return AggregationDSL.dateHistogramAggregation(field, calendarIntervalAlias, format, keyed);
  }

  public FiltersAggregation filtersAggregation(Map<String, Query> queries) {
    return AggregationDSL.filtersAggregation(queries);
  }

  public TermsAggregation termAggregation(String field, int size) {
    return AggregationDSL.termAggregation(field, size);
  }

  public TermsAggregation termAggregation(String field, int size, Map<String, SortOrder> orderBy) {
    return AggregationDSL.termAggregation(field, size, orderBy);
  }

  public TopHitsAggregation topHitsAggregation(List<String> sourceFields, int size, SortOptions... sortOptions) {
    return AggregationDSL.topHitsAggregation(sourceFields, size, sortOptions);
  }

  public TopHitsAggregation topHitsAggregation(int size, SortOptions... sortOptions) {
    return AggregationDSL.topHitsAggregation(size, sortOptions);
  }

  public Aggregation withSubaggregations(DateHistogramAggregation aggregation, Map<String, Aggregation> aggregations) {
    return AggregationDSL.withSubaggregations(aggregation, aggregations);
  }

  public Aggregation withSubaggregations(FiltersAggregation aggregation, Map<String, Aggregation> aggregations) {
    return AggregationDSL.withSubaggregations(aggregation, aggregations);
  }

  public Aggregation withSubaggregations(ChildrenAggregation childrenAggregation, Map<String, Aggregation> aggregations) {
    return AggregationDSL.withSubaggregations(childrenAggregation, aggregations);
  }

  public Aggregation withSubaggregations(Query query, Map<String, Aggregation> aggregations) {
    return AggregationDSL.withSubaggregations(query, aggregations);
  }

  public Aggregation withSubaggregations(TermsAggregation aggregation, Map<String, Aggregation> aggregations) {
    return AggregationDSL.withSubaggregations(aggregation, aggregations);
  }

  public ParentAggregation parent(String type) {
    return AggregationDSL.parent(type);
  }

  public ChildrenAggregation children(String type) {
    return AggregationDSL.children(type);
  }
}
