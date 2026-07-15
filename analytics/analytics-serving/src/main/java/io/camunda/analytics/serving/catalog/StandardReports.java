/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.serving.catalog;

import io.camunda.analytics.report.Combination;
import io.camunda.analytics.report.ReportDefinition;
import io.camunda.analytics.report.ReportSource;
import io.camunda.analytics.serving.spi.MetadataStore;
import io.camunda.analytics.serving.spi.ReportSpecStore;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The default saved reports seeded at startup so the Reports page is never empty out of the box —
 * the report-plane companion of {@link StandardDatasets}. Seeding is <b>create-if-absent by
 * name</b> (the {@code DemoSeeder} guard pattern): a report whose name already exists is left
 * untouched, so restarts never duplicate and user edits to a default report survive. Unlike {@link
 * StandardDatasets#bootstrap} this cannot key on an empty store — users create their own reports,
 * so emptiness says nothing about whether the defaults exist.
 */
public final class StandardReports {

  private static final long ONE_MINUTE_MS = 60_000L;

  private StandardReports() {}

  /** Seeds every default report whose name is not present yet (idempotent). */
  public static void seedDefaults(final MetadataStore metadataStore) {
    final ReportSpecStore reports = metadataStore.reportSpecStore();
    final Set<String> existing =
        reports.search().stream().map(ReportDefinition::name).collect(Collectors.toSet());
    for (final ReportDefinition report : defaults()) {
      if (!existing.contains(report.name())) {
        reports.create(report);
      }
    }
  }

  /** The default report definitions (the {@code reportId} is assigned by the store at create). */
  private static List<ReportDefinition> defaults() {
    return List.of(
        // Completed-instance count + duration p95 segmented by the region variable, over the
        // region-duration cube — the Celonis-style "duration by segment" default.
        new ReportDefinition(
            0L,
            "Duration by region",
            List.of(new ReportSource("region-duration", List.of("count", "p95"), List.of())),
            List.of("var.region"),
            ONE_MINUTE_MS,
            Combination.UNION,
            "bar"),
        // Completed instances per definition over time. The process-instances cube has no meter
        // literally named "count"; its per-transition primitives make "completed" the honest
        // throughput measure (activations count arrivals, not throughput).
        new ReportDefinition(
            0L,
            "Throughput by process",
            List.of(new ReportSource("process-instances", List.of("completed"), List.of())),
            List.of("bpmnProcessId"),
            ONE_MINUTE_MS,
            Combination.UNION,
            "line"));
  }
}
