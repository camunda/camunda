/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.core;

import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Map;

public record AggregationResult(
    Long docCount,
    Map<String, AggregationResult> aggregations,
    List<SearchQueryHit> hits,
    String endCursor) {

  public static final AggregationResult EMPTY =
      new AggregationResult(0L, Map.of(), List.of(), null);

  public AggregationResult(final Long docCount, final Map<String, AggregationResult> aggregations) {
    this(docCount, aggregations, List.of(), null);
  }

  public static final class Builder implements ObjectBuilder<AggregationResult> {

    private Long docCount;
    private Map<String, AggregationResult> aggregations;
    private List<SearchQueryHit> hits;
    private String endCursor;

    public Builder endCursor(final String value) {
      endCursor = value;
      return this;
    }

    public Builder hits(final List<SearchQueryHit> value) {
      hits = value;
      return this;
    }

    public Builder docCount(final Long value) {
      docCount = value;
      return this;
    }

    public Builder aggregations(final Map<String, AggregationResult> value) {
      aggregations = value;
      return this;
    }

    @Override
    public AggregationResult build() {
      return new AggregationResult(docCount, aggregations, hits, endCursor);
    }
  }
}
