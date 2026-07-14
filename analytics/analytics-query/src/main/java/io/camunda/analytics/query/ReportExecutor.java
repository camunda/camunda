/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.query;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.report.Combination;
import io.camunda.analytics.report.ReportDefinition;
import io.camunda.analytics.report.ReportSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Runs a {@link ReportDefinition} over multiple datasets and combines the results (ADR 0006). For
 * {@link Combination#UNION} it runs the existing single-dataset {@link DatasetQueryExecutor} once
 * per {@link ReportSource} — with the report's shared group-by, time range, and granularity plus
 * that source's meters and filters — and stacks the resulting {@link ReportRow}s on the shared
 * {@code (dimensions, windowStart)} key, <b>namespacing each source's measures by its dataset
 * name</b> ({@code dataset.meter}) so same-named meters from different datasets never collide. When
 * a report reads the same dataset more than once (e.g. comparing two filters side by side), later
 * occurrences are suffixed with their ordinal ({@code dataset#2.meter}) so the sources never
 * overwrite each other. This adds no backend work: each source rides the full single-dataset read
 * path (tiering, pushdown, streaming); only the cross-source merge on the group key is new.
 *
 * <p>{@link Combination#JOIN} (cross-grain alignment) is not yet supported — see ADR 0006.
 */
public final class ReportExecutor {

  /**
   * Runs one source's query against its compiled dataset; production wires {@code
   * DatasetQueryExecutor::execute}.
   */
  @FunctionalInterface
  public interface SourceQuery {
    ReportResult execute(ReportQuery query, CompiledDataset dataset);
  }

  private final SourceQuery sourceQuery;
  private final Function<String, CompiledDataset> datasetResolver;

  public ReportExecutor(
      final SourceQuery sourceQuery, final Function<String, CompiledDataset> datasetResolver) {
    this.sourceQuery = sourceQuery;
    this.datasetResolver = datasetResolver;
  }

  /** Convenience wiring over a single-dataset executor. */
  public ReportExecutor(
      final DatasetQueryExecutor executor,
      final Function<String, CompiledDataset> datasetResolver) {
    this(executor::execute, datasetResolver);
  }

  /** Executes the report over {@code [fromMs, toMs)} at the definition's granularity. */
  public ReportResult execute(final ReportDefinition report, final long fromMs, final long toMs) {
    if (report.combination() != Combination.UNION) {
      throw new UnsupportedOperationException(
          "report combination " + report.combination() + " is not supported yet (only UNION)");
    }

    // (group-by values, time bucket) -> dataset-namespaced measures, in first-seen order.
    final Map<RowKey, Map<String, Object>> byKey = new LinkedHashMap<>();
    final Map<String, Integer> occurrences = new LinkedHashMap<>();
    for (final ReportSource source : report.sources()) {
      final CompiledDataset dataset = datasetResolver.apply(source.datasetName());
      if (dataset == null) {
        throw new IllegalArgumentException("unknown dataset '" + source.datasetName() + "'");
      }
      // The first source of a dataset keeps the plain name (the stable common case); repeats get
      // their ordinal so two sources over one dataset (compare-filters) never collide.
      final int occurrence = occurrences.merge(source.datasetName(), 1, Integer::sum);
      final String namespace =
          occurrence == 1 ? source.datasetName() : source.datasetName() + "#" + occurrence;
      final ReportQuery query =
          new ReportQuery(
              report.groupBy(),
              fromMs,
              toMs,
              report.granularityMs(),
              source.filters(),
              source.meters());
      for (final ReportRow row : sourceQuery.execute(query, dataset).rows()) {
        final Map<String, Object> measures =
            byKey.computeIfAbsent(
                new RowKey(row.dimensions(), row.windowStart()), k -> new LinkedHashMap<>());
        row.measures().forEach((meter, value) -> measures.put(namespace + "." + meter, value));
      }
    }

    final List<ReportRow> rows = new ArrayList<>(byKey.size());
    byKey.forEach(
        (key, measures) -> rows.add(new ReportRow(key.dimensions(), key.windowStart(), measures)));
    return new ReportResult(rows);
  }

  private record RowKey(Map<String, Object> dimensions, long windowStart) {}
}
