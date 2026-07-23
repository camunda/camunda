/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.tools;

import io.camunda.analytics.lake.serving.tools.ChangepointAlgorithm.Outcome;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * {@code POST /api/tools/changepoint}: computes the same series {@link SeriesService} would (with
 * SQL tracking), then runs {@link ChangepointAlgorithm} over the resulting points.
 */
@Service
public class ChangepointService {

  private final SeriesService seriesService;

  public ChangepointService(final SeriesService seriesService) {
    this.seriesService = seriesService;
  }

  public ChangepointResult changepoint(final SeriesQuery query) {
    final SeriesResult series = seriesService.series(query);
    final Outcome outcome = ChangepointAlgorithm.detect(series.points());
    return new ChangepointResult(
        outcome.at(),
        outcome.shape().name(),
        outcome.confidence(),
        outcome.before(),
        outcome.after(),
        series.sql());
  }

  /** {@code POST /api/tools/changepoint} response. */
  public record ChangepointResult(
      String at, String shape, double confidence, Double before, Double after, List<String> sql) {}
}
