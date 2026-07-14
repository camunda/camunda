/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.serving.support;

import io.camunda.analytics.metric.ExecutionTimeSummaryResult;
import io.camunda.analytics.metric.LifecycleSummaryResult;
import io.camunda.analytics.serving.spi.ReadStrategy;
import io.camunda.analytics.sketch.DistinctCountResult;
import io.camunda.analytics.sketch.QuantileResult;
import io.camunda.analytics.sketch.TopKResult;

/**
 * Derives the denormalized {@code <meter>_value} scalar from a non-pushable meter's finalized
 * result — the cheap headline number a {@link ReadStrategy#DIRECT DIRECT} read can serve without
 * touching the blob. Shared by both serving backends so the denormalization is identical. The
 * mapping picks each result's natural headline: a distinct estimate, a quantile meter's <em>first
 * declared rank</em> (the median under the default ranks), the top hitter's frequency, or the
 * observation count for the summaries/histogram, which have no single value. {@code null} means "no
 * observations" — never 0, which is a legitimate value.
 *
 * <p>The scalar is exact <em>per cell</em> (finalized from the very blob written beside it in the
 * same statement) but display-only: a sketch's finalized value is not combinable, so it must never
 * be aggregated across rows (a {@code SUM} or {@code AVG} of percentiles is meaningless). The
 * planner enforces this structurally — the exact roll-up of a sketch across cells always streams
 * the blobs and app-merges (STREAM_MERGE).
 */
public final class SketchScalar {

  private SketchScalar() {}

  public static Double of(final Object result) {
    if (result instanceof final Number number) {
      return number.doubleValue();
    }
    if (result instanceof final DistinctCountResult distinct) {
      return (double) distinct.estimate();
    }
    if (result instanceof final QuantileResult quantile) {
      return quantile.count() == 0L || quantile.values().length == 0 ? null : quantile.values()[0];
    }
    if (result instanceof final TopKResult topK) {
      return topK.items().isEmpty() ? 0.0 : (double) topK.items().get(0).estimate();
    }
    if (result instanceof final ExecutionTimeSummaryResult summary) {
      return (double) summary.count();
    }
    if (result instanceof final LifecycleSummaryResult lifecycle) {
      return (double) lifecycle.duration().count();
    }
    if (result instanceof final long[] buckets) {
      long total = 0L;
      for (final long bucket : buckets) {
        total += bucket;
      }
      return (double) total;
    }
    return 0.0;
  }
}
