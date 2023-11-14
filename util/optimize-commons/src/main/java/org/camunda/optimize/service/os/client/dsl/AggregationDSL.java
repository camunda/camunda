/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.client.dsl;

import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.BucketSortAggregation;
import org.opensearch.client.opensearch._types.aggregations.Buckets;
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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static org.camunda.optimize.service.os.client.dsl.QueryDSL.sourceInclude;

public interface AggregationDSL {
  static BucketSortAggregation bucketSortAggregation(@Nullable Integer size, SortOptions... sortOptions) {
    return BucketSortAggregation.of(a -> a.sort(Arrays.asList(sortOptions)).size(size));
  }

  static CardinalityAggregation cardinalityAggregation(String field) {
    return CardinalityAggregation.of(a -> a.field(field));
  }

  static CardinalityAggregation cardinalityAggregation(String field, int precisionThreshold) {
    return CardinalityAggregation.of(a -> a.field(field).precisionThreshold(precisionThreshold));
  }

  static CalendarInterval calendarIntervalByAlias(String alias) {
    return Arrays.stream(CalendarInterval.values())
      .filter(ci -> Arrays.asList(ci.aliases()).contains(alias))
      .findFirst()
      .orElseThrow(() -> {
        List<String> legalAliases = Arrays.stream(CalendarInterval.values()).flatMap(v -> Arrays.stream(v.aliases())).sorted().toList();
        return new OptimizeRuntimeException(format("Unknown CalendarInterval alias %s! Legal aliases: %s", alias, legalAliases));
      });
  }

  static DateHistogramAggregation dateHistogramAggregation(String field, String calendarIntervalAlias, String format, boolean keyed) {
    return DateHistogramAggregation.of(a ->
      a.field(field)
        .calendarInterval(calendarIntervalByAlias(calendarIntervalAlias))
        .format(format)
        .keyed(keyed)
    );
  }

  static FiltersAggregation filtersAggregation(Map<String, Query> queries) {
    return FiltersAggregation.of(a -> a.filters(Buckets.of(b -> b.keyed(queries))));
  }

  static TermsAggregation termAggregation(String field, int size) {
    return TermsAggregation.of(a -> a.field(field).size(size));
  }

  static TermsAggregation termAggregation(String field, int size, Map<String, SortOrder> orderBy) {
    return TermsAggregation.of(a -> a.field(field).size(size).order(orderBy));
  }

  static TopHitsAggregation topHitsAggregation(List<String> sourceFields, int size, SortOptions... sortOptions) {
    return TopHitsAggregation.of(a -> a.source(sourceInclude(sourceFields)).size(size).sort(List.of(sortOptions)));
  }

  static TopHitsAggregation topHitsAggregation(int size, SortOptions... sortOptions) {
    return TopHitsAggregation.of(a -> a.size(size).sort(List.of(sortOptions)));
  }

  static Aggregation withSubaggregations(DateHistogramAggregation aggregation, Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.dateHistogram(aggregation).aggregations(aggregations));
  }

  static Aggregation withSubaggregations(FiltersAggregation aggregation, Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.filters(aggregation).aggregations(aggregations));
  }

  static Aggregation withSubaggregations(ChildrenAggregation childrenAggregation, Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.children(childrenAggregation).aggregations(aggregations));
  }

  static Aggregation withSubaggregations(Query query, Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.filter(query).aggregations(aggregations));
  }

  static Aggregation withSubaggregations(TermsAggregation aggregation, Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.terms(aggregation).aggregations(aggregations));
  }

  static ParentAggregation parent(String type){
    return ParentAggregation.of(p -> p.type(type));
  }

  static ChildrenAggregation children(String type){
    return ChildrenAggregation.of(c -> c.type(type));
  }
}
