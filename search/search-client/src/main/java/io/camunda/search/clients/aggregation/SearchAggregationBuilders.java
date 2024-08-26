/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.aggregation;

import io.camunda.search.clients.aggregation.SearchAggregation.Builder;
import io.camunda.util.ObjectBuilder;
import java.util.Map;
import java.util.function.Function;

public class SearchAggregationBuilders {

  public static SearchAggregation aggregation(
      final Function<Builder, ObjectBuilder<SearchAggregation>> fn) {
    return fn.apply(aggregation()).build();
  }

  public static SearchAggregation.Builder aggregation() {
    return new SearchAggregation.Builder();
  }

  public static SearchCardinalityAggregation.Builder cardinality() {
    return new SearchCardinalityAggregation.Builder();
  }

  public static SearchCardinalityAggregation cardinality(
      final Function<
              SearchCardinalityAggregation.Builder, ObjectBuilder<SearchCardinalityAggregation>>
          fn) {
    return fn.apply(cardinality()).build();
  }

  public static SearchCardinalityAggregation cardinality(
      final String field, final Integer precisionThreshold) {
    return cardinality(c -> c.field(field).precisionThreshold(precisionThreshold));
  }

  public static SearchTermsAggregation.Builder terms() {
    return new SearchTermsAggregation.Builder();
  }

  public static SearchTermsAggregation terms(
      final Function<SearchTermsAggregation.Builder, ObjectBuilder<SearchTermsAggregation>> fn) {
    return fn.apply(terms()).build();
  }

  public static SearchTermsAggregation termsAggregation(
      final String field,
      final Integer size,
      final Map<String, SearchAggregation> subAggregations) {
    return terms(c -> c.field(field).size(size).subAggregations(subAggregations));
  }
}
