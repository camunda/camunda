/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.tools;

import io.camunda.analytics.lake.serving.catalog.EntityCatalog;
import io.camunda.analytics.lake.serving.catalog.MetricRegistry;
import io.camunda.analytics.lake.serving.tools.ChangepointAlgorithm.Outcome;
import io.camunda.analytics.lake.serving.tools.ScreenAlgorithm.ShiftCorrelation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * {@code POST /api/tools/screen}: correlates a target series' slot-over-slot deltas against a set
 * of candidate series, either explicitly given or auto-derived from the registry.
 *
 * <p>{@code targetSeries} is a full series request (entity/measure/quantile/filters/from/to/
 * grainMinutes); {@code window} supplies the {@code from}/{@code to} every candidate is evaluated
 * over (a candidate's own {@code entity} differs from the target's, but the time range and grain
 * must line up for the shift correlation to mean anything) — every candidate reuses {@code
 * targetSeries.grainMinutes()} for its own bucketing.
 *
 * <p>Auto candidates (see {@link #autoCandidates}): one per registry entity that has a {@code
 * process_id} dim, filtered to the target's own {@code process_id} filter value when the target
 * specified one (entities without a {@code process_id} dim can't be scoped consistently and are
 * skipped). Each candidate defaults to the entity's bare count ({@code cnt}) when available, else
 * its first measure's average.
 */
@Service
public class ScreenService {

  private final MetricRegistry metricRegistry;
  private final SeriesService seriesService;

  public ScreenService(final MetricRegistry metricRegistry, final SeriesService seriesService) {
    this.metricRegistry = metricRegistry;
    this.seriesService = seriesService;
  }

  public ScreenResult screen(
      final SeriesQuery targetSeries,
      final TimeRange window,
      final List<SeriesQuery> explicitCandidates) {
    final SeriesQuery target = withWindow(targetSeries, window, targetSeries.grainMinutes());
    final SeriesResult targetResult = seriesService.series(target);
    final List<Double> targetValues = denseValues(targetResult.points());

    final List<SeriesQuery> candidates =
        explicitCandidates != null
            ? explicitCandidates.stream()
                .map(c -> withWindow(c, window, target.grainMinutes()))
                .toList()
            : autoCandidates(target, window);

    final List<String> sql = new ArrayList<>(targetResult.sql());
    final List<ScreenRow> rows = new ArrayList<>();
    for (final SeriesQuery candidate : candidates) {
      final SeriesResult candidateResult = seriesService.series(candidate);
      sql.addAll(candidateResult.sql());
      final List<Double> candidateValues = denseValues(candidateResult.points());
      final ShiftCorrelation shiftCorrelation =
          ScreenAlgorithm.bestShift(targetValues, candidateValues);
      final Outcome candidateChangepoint = ChangepointAlgorithm.detect(candidateResult.points());
      rows.add(
          new ScreenRow(
              candidate,
              shiftCorrelation.shiftSlots(),
              shiftCorrelation.correlation(),
              candidateChangepoint.at()));
    }
    rows.sort((a, b) -> Double.compare(Math.abs(b.correlation()), Math.abs(a.correlation())));
    return new ScreenResult(rows, sql);
  }

  private List<SeriesQuery> autoCandidates(final SeriesQuery target, final TimeRange window) {
    final Object targetProcessId =
        target.filters() == null ? null : target.filters().get("process_id");
    final List<SeriesQuery> candidates = new ArrayList<>();
    for (final EntityCatalog entity : metricRegistry.entities()) {
      if (!entity.dimNames().contains("process_id")) {
        continue;
      }
      final Map<String, Object> filters =
          targetProcessId == null ? Map.of() : Map.of("process_id", targetProcessId);
      final String measure =
          entity.hasCnt() || entity.measures().isEmpty() ? null : entity.measures().get(0).name();
      candidates.add(
          new SeriesQuery(
              entity.name(),
              measure,
              null,
              filters,
              window.from(),
              window.to(),
              target.grainMinutes()));
    }
    return candidates;
  }

  private SeriesQuery withWindow(
      final SeriesQuery query, final TimeRange window, final Integer grainMinutes) {
    return new SeriesQuery(
        query.entity(),
        query.measure(),
        query.quantile(),
        query.filters(),
        window.from(),
        window.to(),
        grainMinutes);
  }

  private List<Double> denseValues(final List<SeriesPoint> points) {
    final List<Double> values = new ArrayList<>(points.size());
    for (final SeriesPoint point : points) {
      if (point.value() != null) {
        values.add(point.value());
      }
    }
    return values;
  }

  /** One candidate's correlation against the target series. */
  public record ScreenRow(SeriesQuery series, int shiftSlots, double correlation, String movedAt) {}

  /** {@code POST /api/tools/screen} response. */
  public record ScreenResult(List<ScreenRow> rows, List<String> sql) {}
}
