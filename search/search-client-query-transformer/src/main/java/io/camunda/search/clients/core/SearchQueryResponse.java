/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.core;

import io.camunda.search.clients.aggregator.AggregationResult;
import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public record SearchQueryResponse<T>(
    long totalHits,
    String scrollId,
    List<SearchQueryHit<T>> hits,
    Map<String, AggregationResult> aggregations) {

  public static <T> SearchQueryResponse<T> of(
      final Function<Builder<T>, ObjectBuilder<SearchQueryResponse<T>>> fn) {
    return fn.apply(new Builder<T>()).build();
  }

  public static final class Builder<T> implements ObjectBuilder<SearchQueryResponse<T>> {

    private long totalHits;
    private String scrollId;
    private List<SearchQueryHit<T>> hits;
    private Map<String, AggregationResult> aggregations;

    public Builder<T> totalHits(final long value) {
      totalHits = value;
      return this;
    }

    public Builder<T> scrollId(final String value) {
      scrollId = value;
      return this;
    }

    public Builder<T> hits(final List<SearchQueryHit<T>> value) {
      hits = value;
      return this;
    }

    public Builder<T> aggregations(final Map<String, AggregationResult> aggregations) {
      this.aggregations = aggregations;
      return this;
    }

    @Override
    public SearchQueryResponse<T> build() {
      return new SearchQueryResponse<T>(
          totalHits,
          scrollId,
          Objects.requireNonNullElse(hits, Collections.emptyList()),
          aggregations);
    }
  }
}
