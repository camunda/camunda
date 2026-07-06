/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.CompiledMeter;
import io.camunda.analytics.dataset.FilterPredicate;
import io.camunda.analytics.metric.ExecutionTimeSummaryResult;
import io.camunda.analytics.metric.LifecycleSummaryResult;
import io.camunda.analytics.metric.RatioResult;
import io.camunda.analytics.query.DatasetQueryExecutor;
import io.camunda.analytics.query.ReportQuery;
import io.camunda.analytics.query.ReportRow;
import io.camunda.analytics.sketch.DistinctCountResult;
import io.camunda.analytics.sketch.QuantileResult;
import io.camunda.analytics.sketch.TopKResult;
import io.camunda.analytics.webapp.DatasetCatalog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import org.springframework.stereotype.Repository;

/**
 * Read-only queries for the dashboard, answered through the backend-neutral serving executor rather
 * than bespoke SQL. Each call builds a {@link ReportQuery} against a standard cube (see {@code
 * StandardDatasets}) and maps the finalized {@link ReportRow} measures to the dashboard DTOs. A
 * series read buckets at the cube's finest tier (one point per window); a summary read uses a
 * single bucket &ge; the range so the whole range collapses to one total row.
 */
@Repository
public class DashboardRepository {

  private static final long ONE_HOUR_MS = 3_600_000L;

  private final DatasetQueryExecutor executor;
  private final DatasetCatalog catalog;

  public DashboardRepository(final DatasetQueryExecutor executor, final DatasetCatalog catalog) {
    this.executor = executor;
    this.catalog = catalog;
  }

  /** Process ids the process-instances cube has data for (for the process picker). */
  public List<String> processes() {
    final TreeSet<String> ids = new TreeSet<>();
    for (final ReportRow row :
        total(
            "process-instances",
            List.of("bpmnProcessId"),
            null,
            null,
            List.of(),
            List.of("lifecycle"))) {
      final Object id = row.dimensions().get("bpmnProcessId");
      if (id != null) {
        ids.add(id.toString());
      }
    }
    return new ArrayList<>(ids);
  }

  /** Tenants the distinct-process cube has data for. */
  public List<String> tenants() {
    final TreeSet<String> ids = new TreeSet<>();
    for (final ReportRow row :
        total(
            "process-distinct", List.of("tenantId"), null, null, List.of(), List.of("distinct"))) {
      final Object id = row.dimensions().get("tenantId");
      if (id != null) {
        ids.add(id.toString());
      }
    }
    return new ArrayList<>(ids);
  }

  /** The per-window duration distribution for a process (control-chart trend). */
  public List<DurationPercentilePoint> durationPercentiles(
      final String bpmnProcessId, final Long fromWindow, final Long toWindow) {
    final List<DurationPercentilePoint> out = new ArrayList<>();
    for (final ReportRow row :
        series(
            "process-duration",
            List.of(),
            fromWindow,
            toWindow,
            List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
            List.of("p95"))) {
      out.add(durationPoint(row.windowStart(), (QuantileResult) row.measures().get("p95")));
    }
    out.sort(Comparator.comparingLong(DurationPercentilePoint::windowStart));
    return out;
  }

  /** A single exact duration distribution over the range (for the KPI tiles). */
  public DurationPercentilePoint durationSummary(
      final String bpmnProcessId, final Long fromWindow, final Long toWindow) {
    final long windowStart = fromWindow == null ? 0L : fromWindow;
    final List<ReportRow> rows =
        total(
            "process-duration",
            List.of(),
            fromWindow,
            toWindow,
            List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
            List.of("p95"));
    if (rows.isEmpty()) {
      return new DurationPercentilePoint(windowStart, 0, 0, 0, 0, 0, 0, 0);
    }
    return durationPoint(windowStart, (QuantileResult) rows.get(0).measures().get("p95"));
  }

  private static DurationPercentilePoint durationPoint(
      final long windowStart, final QuantileResult q) {
    if (q == null || q.count() == 0L) {
      return new DurationPercentilePoint(windowStart, 0, 0, 0, 0, 0, 0, 0);
    }
    return new DurationPercentilePoint(
        windowStart,
        q.count(),
        Math.round(q.min()),
        Math.round(q.max()),
        Math.round(q.valueAt(0.5)),
        Math.round(q.valueAt(0.75)),
        Math.round(q.valueAt(0.9)),
        Math.round(q.valueAt(0.99)));
  }

  /** The per-window ratio series for a process and metric. */
  public List<RatioPoint> ratios(
      final String bpmnProcessId, final String metric, final Long fromWindow, final Long toWindow) {
    // Only the SLA-compliance cohort is a declared dataset; other ratios (e.g. no_incident) have no
    // cube yet, so they read as empty until one is declared.
    if (!"sla_met".equals(metric)) {
      return List.of();
    }
    final List<RatioPoint> out = new ArrayList<>();
    for (final ReportRow row :
        series(
            "process-sla",
            List.of(),
            fromWindow,
            toWindow,
            List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
            List.of("sla_compliance"))) {
      final RatioResult r = (RatioResult) row.measures().get("sla_compliance");
      out.add(
          new RatioPoint(row.windowStart(), r.matched(), r.total(), r.ratio(), r.ratio(), false));
    }
    out.sort(Comparator.comparingLong(RatioPoint::windowStart));
    return out;
  }

  /** The per-window distinct-process estimate for a tenant. */
  public List<DistinctPoint> distinct(
      final String tenantId, final Long fromWindow, final Long toWindow) {
    final List<DistinctPoint> out = new ArrayList<>();
    for (final ReportRow row :
        series(
            "process-distinct",
            List.of(),
            fromWindow,
            toWindow,
            List.of(FilterPredicate.equals("tenantId", tenantId)),
            List.of("distinct"))) {
      final DistinctCountResult d = (DistinctCountResult) row.measures().get("distinct");
      out.add(new DistinctPoint(row.windowStart(), d.estimate(), d.lowerBound(), d.upperBound()));
    }
    out.sort(Comparator.comparingLong(DistinctPoint::windowStart));
    return out;
  }

  /** The ranked top processes for a tenant over the range. */
  public List<TopProcess> topProcesses(
      final String tenantId, final Long fromWindow, final Long toWindow) {
    final List<ReportRow> rows =
        total(
            "top-processes",
            List.of(),
            fromWindow,
            toWindow,
            List.of(FilterPredicate.equals("tenantId", tenantId)),
            List.of("top"));
    if (rows.isEmpty()) {
      return List.of();
    }
    final TopKResult top = (TopKResult) rows.get(0).measures().get("top");
    final List<TopProcess> out = new ArrayList<>();
    final List<TopKResult.Item> items = top.items();
    for (int i = 0; i < items.size(); i++) {
      final TopKResult.Item item = items.get(i);
      out.add(
          new TopProcess(
              i + 1, item.item(), item.estimate(), item.lowerBound(), item.upperBound()));
    }
    return out;
  }

  /** Per-element duration summary for a process (for the flow-node heatmap). */
  public List<ElementDuration> elementDurations(
      final String bpmnProcessId, final Long fromWindow, final Long toWindow) {
    final List<ElementDuration> out = new ArrayList<>();
    for (final ReportRow row :
        total(
            "element-duration",
            List.of("elementId"),
            fromWindow,
            toWindow,
            List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
            List.of("duration"))) {
      final ExecutionTimeSummaryResult d =
          (ExecutionTimeSummaryResult) row.measures().get("duration");
      final Object elementId = row.dimensions().get("elementId");
      out.add(
          new ElementDuration(
              elementId == null ? "" : elementId.toString(),
              "", // element type is not modeled as a dimension in the cube
              d.count(),
              Math.round(d.averageMs()),
              quantileMs(d, 0.5),
              quantileMs(d, 0.9),
              d.maxMs()));
    }
    out.sort(Comparator.comparingLong(ElementDuration::executedCount).reversed());
    return out;
  }

  /** Instances started (activated) for a process over the range. */
  public long activatedInstances(
      final String bpmnProcessId, final Long fromWindow, final Long toWindow) {
    final LifecycleSummaryResult lifecycle = lifecycle(bpmnProcessId, fromWindow, toWindow);
    return lifecycle == null ? 0L : lifecycle.activated();
  }

  /** Current in-flight instance count for a process (activated − completed − terminated). */
  public long activeInstances(final String bpmnProcessId, final String tenantId) {
    // The process-instances grain carries no tenant, so the tenant argument is ignored here.
    final LifecycleSummaryResult lifecycle = lifecycle(bpmnProcessId, null, null);
    if (lifecycle == null) {
      return 0L;
    }
    return Math.max(0L, lifecycle.activated() - lifecycle.completed() - lifecycle.terminated());
  }

  private LifecycleSummaryResult lifecycle(
      final String bpmnProcessId, final Long fromWindow, final Long toWindow) {
    final List<ReportRow> rows =
        total(
            "process-instances",
            List.of(),
            fromWindow,
            toWindow,
            List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
            List.of("lifecycle"));
    return rows.isEmpty() ? null : (LifecycleSummaryResult) rows.get(0).measures().get("lifecycle");
  }

  /** Currently-open incident count for a process (sum of the ±1 delta level over the grain). */
  public long openIncidents(final String bpmnProcessId) {
    long open = 0L;
    for (final ReportRow row :
        total(
            "incident-open",
            List.of(),
            null,
            null,
            List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
            List.of("open"))) {
      open += ((Number) row.measures().get("open")).longValue();
    }
    return Math.max(0L, open);
  }

  /** Incidents per flow node: raised (count over the range) + currently open (level gauge). */
  public List<IncidentFlowNode> incidents(
      final String bpmnProcessId, final Long fromWindow, final Long toWindow) {
    // element -> {raised, open}; avg/max resolution duration are not modeled as a cube.
    final Map<String, long[]> byElement = new LinkedHashMap<>();
    for (final ReportRow row :
        total(
            "incidents",
            List.of("elementId"),
            fromWindow,
            toWindow,
            List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
            List.of("count"))) {
      byElement.computeIfAbsent(elementId(row), k -> new long[2])[0] =
          ((Number) row.measures().get("count")).longValue();
    }
    for (final ReportRow row :
        total(
            "incident-open",
            List.of("elementId"),
            fromWindow,
            toWindow,
            List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
            List.of("open"))) {
      byElement.computeIfAbsent(elementId(row), k -> new long[2])[1] =
          Math.max(0L, ((Number) row.measures().get("open")).longValue());
    }
    final List<IncidentFlowNode> out = new ArrayList<>();
    byElement.forEach((id, v) -> out.add(new IncidentFlowNode(id, v[0], v[1], 0L, 0L)));
    out.sort(Comparator.comparingLong(IncidentFlowNode::raised).reversed());
    return out;
  }

  /** Per-start-cohort SLA breakdown, derived from the SLA-compliance ratio series. */
  public List<SlaCohortPoint> slaCohorts(
      final String bpmnProcessId, final Long fromWindow, final Long toWindow) {
    // A forward-looking start cohort (with a still-open, maturing split) is not modeled; this
    // derives the settled met/breached split from the completion-based SLA ratio.
    final List<SlaCohortPoint> out = new ArrayList<>();
    for (final RatioPoint p : ratios(bpmnProcessId, "sla_met", fromWindow, toWindow)) {
      out.add(
          new SlaCohortPoint(
              p.windowStart(), p.total(), p.matched(), p.total() - p.matched(), 0L, false));
    }
    return out;
  }

  /** Completion-time histogram per cohort — no histogram dataset is declared, so empty for now. */
  public List<DurationBucketPoint> durationBuckets(
      final String bpmnProcessId, final Long fromWindow, final Long toWindow) {
    return List.of();
  }

  /** BPMN diagram XML is not modeled in a dataset, so no diagram is available. */
  public Optional<String> diagramXml(final String bpmnProcessId) {
    return Optional.empty();
  }

  private static String elementId(final ReportRow row) {
    final Object id = row.dimensions().get("elementId");
    return id == null ? "" : id.toString();
  }

  private static long quantileMs(final ExecutionTimeSummaryResult result, final double rank) {
    final double[] ranks = result.ranks();
    for (int i = 0; i < ranks.length; i++) {
      if (ranks[i] == rank) {
        return Math.round(result.quantilesMs()[i]);
      }
    }
    return 0L;
  }

  private static long finestTier(final CompiledDataset dataset) {
    long finest = Long.MAX_VALUE;
    for (final CompiledMeter meter : dataset.meters()) {
      finest = Math.min(finest, meter.windowMs());
    }
    return finest;
  }

  /** Per-window series: bucket at the cube's finest tier (one output row per stored window). */
  private List<ReportRow> series(
      final String name,
      final List<String> groupBy,
      final Long fromWindow,
      final Long toWindow,
      final List<FilterPredicate> filters,
      final List<String> meters) {
    final CompiledDataset dataset = catalog.require(name);
    return executor
        .execute(
            new ReportQuery(
                groupBy, fromMs(fromWindow), toMs(toWindow), finestTier(dataset), filters, meters),
            dataset)
        .rows();
  }

  /**
   * Single total row: a granularity of {@code toMs} guarantees one bucket, since every fetched cell
   * has {@code window_start < toMs} and so aligns down to bucket 0.
   */
  private List<ReportRow> total(
      final String name,
      final List<String> groupBy,
      final Long fromWindow,
      final Long toWindow,
      final List<FilterPredicate> filters,
      final List<String> meters) {
    final CompiledDataset dataset = catalog.require(name);
    final long toMs = toMs(toWindow);
    return executor
        .execute(new ReportQuery(groupBy, fromMs(fromWindow), toMs, toMs, filters, meters), dataset)
        .rows();
  }

  private static long fromMs(final Long fromWindow) {
    return fromWindow == null ? 0L : fromWindow;
  }

  private static long toMs(final Long toWindow) {
    return toWindow == null ? System.currentTimeMillis() + ONE_HOUR_MS : toWindow;
  }
}
