/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch.dsl;

import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.*;
import org.opensearch.client.opensearch._types.query_dsl.Query;

import java.util.List;
import java.util.Map;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.sourceInclude;

public interface AggregationDSL {
  static CardinalityAggregation cardinalityAggregation(String field) {
    return CardinalityAggregation.of(a -> a.field(field));
  }

  static CardinalityAggregation cardinalityAggregation(String field, int precisionThreshold) {
    return CardinalityAggregation.of(a -> a.field(field).precisionThreshold(precisionThreshold));
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

  static Aggregation withSubaggregations(TermsAggregation termsAggregation, Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.terms(termsAggregation).aggregations(aggregations));
  }

  static Aggregation withSubaggregations(FiltersAggregation filtersAggregation, Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.filters(filtersAggregation).aggregations(aggregations));
  }

  static Aggregation withSubaggregations(ChildrenAggregation childrenAggregation, Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.children(childrenAggregation).aggregations(aggregations));
  }

  static Aggregation withSubaggregations(Query query, Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.filter(query).aggregations(aggregations));
  }

  static ParentAggregation parent(String type){
    return ParentAggregation.of(p -> p.type(type));
  }

  static ChildrenAggregation children(String type){
    return ChildrenAggregation.of(c -> c.type(type));
  }
}


