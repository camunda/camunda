/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.client.dsl;

import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.Buckets;
import org.opensearch.client.opensearch._types.aggregations.ChildrenAggregation;
import org.opensearch.client.opensearch._types.aggregations.CompositeAggregationSource;
import org.opensearch.client.opensearch._types.aggregations.CompositeTermsAggregationSource;
import org.opensearch.client.opensearch._types.aggregations.DateHistogramAggregation;
import org.opensearch.client.opensearch._types.aggregations.FieldDateMath;
import org.opensearch.client.opensearch._types.aggregations.FiltersAggregation;
import org.opensearch.client.opensearch._types.aggregations.NestedAggregation;
import org.opensearch.client.opensearch._types.aggregations.ParentAggregation;
import org.opensearch.client.opensearch._types.aggregations.ReverseNestedAggregation;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;
import org.opensearch.client.opensearch._types.aggregations.TopHitsAggregation;
import org.opensearch.client.opensearch._types.aggregations.ValueCountAggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public interface AggregationDSL {

  static ValueCountAggregation valueCountAggregation(final String field) {
    return ValueCountAggregation.of(a -> a.field(field));
  }

  static FieldDateMath fieldDateMath(final double value) {
    return FieldDateMath.of(b -> b.value(value));
  }

  static FieldDateMath fieldDateMath(final String value) {
    return FieldDateMath.of(b -> b.expr(value));
  }

  static Aggregation filterAggregation(final Query query) {
    return Aggregation.of(a -> a.filter(query));
  }

  static FiltersAggregation filtersAggregation(final Map<String, Query> queries) {
    return FiltersAggregation.of(a -> a.filters(Buckets.of(b -> b.keyed(queries))));
  }

  static TermsAggregation termAggregation(final String field, final int size) {
    return TermsAggregation.of(a -> a.field(field).size(size));
  }

  static TermsAggregation termAggregation(
      final String field, final int size, final Map<String, SortOrder> orderBy) {
    return TermsAggregation.of(a -> a.field(field).size(size).order(orderBy));
  }

  static TopHitsAggregation topHitsAggregation(final List<String> sourceFields, final int size) {
    return TopHitsAggregation.of(a -> a.source(QueryDSL.sourceInclude(sourceFields)).size(size));
  }

  static TopHitsAggregation topHitsAggregation(final int size, final SortOptions... sortOptions) {
    return TopHitsAggregation.of(a -> a.size(size).sort(List.of(sortOptions)));
  }

  static Aggregation withSubaggregations(
      final Aggregation aggregation, final Map<String, Aggregation> aggregations) {
    if (aggregation.isDateHistogram()) {
      return withSubaggregations(aggregation.dateHistogram(), aggregations);
    } else if (aggregation.isNested()) {
      return withSubaggregations(aggregation.nested(), aggregations);
    } else if (aggregation.isFilter()) {
      return withSubaggregations(aggregation.filter(), aggregations);
    } else if (aggregation.isFilters()) {
      return withSubaggregations(aggregation.filters(), aggregations);
    } else if (aggregation.isChildren()) {
      return withSubaggregations(aggregation.children(), aggregations);
    } else if (aggregation.isTerms()) {
      return withSubaggregations(aggregation.terms(), aggregations);
    } else {
      throw new OptimizeRuntimeException("Unsupported aggregation type: " + aggregation);
    }
  }

  static Aggregation withSubaggregations(
      final DateHistogramAggregation aggregation, final Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.dateHistogram(aggregation).aggregations(aggregations));
  }

  static Aggregation withSubaggregations(
      final NestedAggregation aggregation, final Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.nested(aggregation).aggregations(aggregations));
  }

  static Aggregation withSubaggregations(
      final FiltersAggregation aggregation, final Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.filters(aggregation).aggregations(aggregations));
  }

  static Aggregation withSubaggregations(
      final ChildrenAggregation childrenAggregation, final Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.children(childrenAggregation).aggregations(aggregations));
  }

  static Aggregation withSubaggregations(
      final Query query, final Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.filter(query).aggregations(aggregations));
  }

  static Aggregation withSubaggregations(
      final ReverseNestedAggregation aggregation, final Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.reverseNested(aggregation).aggregations(aggregations));
  }

  static Aggregation withSubaggregations(
      final TermsAggregation aggregation, final Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.terms(aggregation).aggregations(aggregations));
  }

  static ParentAggregation parent(final String type) {
    return ParentAggregation.of(p -> p.type(type));
  }

  static ChildrenAggregation children(final String type) {
    return ChildrenAggregation.of(c -> c.type(type));
  }

  static CompositeAggregationSource compositeTermsAggregationSource(
      final CompositeTermsAggregationSource aggregation) {
    return CompositeAggregationSource.of(a -> a.terms(aggregation));
  }
}
