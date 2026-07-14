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

/**
 * One backend-neutral aggregation result node.
 *
 * @param docCount the bucket's document count, or a single-metric aggregate's value rounded to a
 *     {@code long} (see {@link #value} for the unrounded metric)
 * @param value a single-metric aggregate's (sum/min/max) exact {@code double} value; {@code null}
 *     for bucket aggregations and for metrics over no values
 * @param keyValues a composite bucket's structured key: source name → the source's raw key value
 *     (String/Long/Double/Boolean, or {@code null} for a {@code missing_bucket} source). {@code
 *     null} for non-composite buckets. Unlike the joined-string map key, values here never need
 *     delimiter-safe parsing.
 * @param aggregations the named sub-aggregation results (buckets of a multi-bucket aggregation, or
 *     nested aggregations of a bucket)
 * @param hits a top-hits aggregation's hits
 * @param endCursor the composite-aggregation resume cursor, if any
 */
public record AggregationResult(
    Long docCount,
    Double value,
    Map<String, Object> keyValues,
    Map<String, AggregationResult> aggregations,
    List<SearchQueryHit> hits,
    String endCursor) {

  public static final AggregationResult EMPTY =
      new AggregationResult(0L, Map.of(), List.of(), null);

  public AggregationResult(final Long docCount, final Map<String, AggregationResult> aggregations) {
    this(docCount, aggregations, List.of(), null);
  }

  public AggregationResult(
      final Long docCount,
      final Map<String, AggregationResult> aggregations,
      final List<SearchQueryHit> hits,
      final String endCursor) {
    this(docCount, null, null, aggregations, hits, endCursor);
  }

  public static final class Builder implements ObjectBuilder<AggregationResult> {

    private Long docCount;
    private Double value;
    private Map<String, Object> keyValues;
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

    public Builder value(final Double metricValue) {
      value = metricValue;
      return this;
    }

    public Builder keyValues(final Map<String, Object> value) {
      keyValues = value;
      return this;
    }

    public Builder aggregations(final Map<String, AggregationResult> value) {
      aggregations = value;
      return this;
    }

    @Override
    public AggregationResult build() {
      return new AggregationResult(docCount, value, keyValues, aggregations, hits, endCursor);
    }
  }
}
