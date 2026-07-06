/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms;

import io.camunda.analytics.metric.ExecutionTimeSummaryResult;
import io.camunda.analytics.metric.LifecycleSummaryResult;
import io.camunda.analytics.sketch.DistinctCountResult;
import io.camunda.analytics.sketch.QuantileResult;
import io.camunda.analytics.sketch.TopKResult;

/**
 * Derives the denormalized {@code <meter>value} scalar from a non-pushable meter's finalized result
 * — the cheap headline number a {@link ReadStrategy#DIRECT DIRECT} read can serve without touching
 * the blob. The mapping picks each result's natural headline: a distinct estimate, the top hitter's
 * frequency, or the observation count for the summaries/histogram. It is a denormalization only;
 * the exact roll-up of a sketch across cells always streams the blobs and app-merges
 * (STREAM_MERGE).
 */
final class SketchScalar {

  private SketchScalar() {}

  static double of(final Object result) {
    if (result instanceof final Number number) {
      return number.doubleValue();
    }
    if (result instanceof final DistinctCountResult distinct) {
      return distinct.estimate();
    }
    if (result instanceof final QuantileResult quantile) {
      return quantile.count();
    }
    if (result instanceof final TopKResult topK) {
      return topK.items().isEmpty() ? 0.0 : topK.items().get(0).estimate();
    }
    if (result instanceof final ExecutionTimeSummaryResult summary) {
      return summary.count();
    }
    if (result instanceof final LifecycleSummaryResult lifecycle) {
      return lifecycle.duration().count();
    }
    if (result instanceof final long[] buckets) {
      long total = 0L;
      for (final long bucket : buckets) {
        total += bucket;
      }
      return total;
    }
    return 0.0;
  }
}
