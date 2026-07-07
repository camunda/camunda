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
import io.camunda.analytics.query.TableQuery;
import io.camunda.analytics.query.TableQueryExecutor;
import io.camunda.analytics.serving.spi.TableRow;
import io.camunda.analytics.sketch.DistinctCountResult;
import io.camunda.analytics.sketch.QuantileResult;
import io.camunda.analytics.sketch.TopKResult;
import io.camunda.analytics.table.ProcessDefinitionSink;
import io.camunda.analytics.webapp.DatasetCatalog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
 *
 * <p><b>Render-scoped dedup.</b> Several widgets derive from the same underlying query (the
 * process-instances lifecycle series feeds the SLA cohorts, the no-incident cohorts and the
 * duration buckets; each ratio series feeds both its own widget and its cohort join). {@link
 * #overview} computes one whole dashboard render against a single per-request memo, so each
 * distinct serving query runs at most once per render; the memo lives only for that call, so
 * nothing is ever stale across renders. The individual widget methods stay for the per-widget
 * endpoints and use a fresh memo each.
 */
@Repository
public class DashboardRepository {

  private static final long ONE_HOUR_MS = 3_600_000L;

  private final DatasetQueryExecutor executor;
  private final DatasetCatalog catalog;
  private final TableQueryExecutor tableExecutor;

  public DashboardRepository(
      final DatasetQueryExecutor executor,
      final DatasetCatalog catalog,
      final TableQueryExecutor tableExecutor) {
    this.executor = executor;
    this.catalog = catalog;
    this.tableExecutor = tableExecutor;
  }

  /**
   * One whole dashboard render computed against a single memo: every distinct serving query runs at
   * most once, however many widgets derive from it. Field names mirror the client's dashboard
   * state, so the response is consumed as-is.
   */
  public DashboardOverview overview(
      final String bpmnProcessId,
      final String tenantId,
      final Long fromWindow,
      final Long toWindow) {
    final Map<QueryKey, List<ReportRow>> memo = new HashMap<>();
    return new DashboardOverview(
        durationPercentiles(bpmnProcessId, fromWindow, toWindow, memo),
        durationSummary(bpmnProcessId, fromWindow, toWindow, memo),
        ratios(bpmnProcessId, "sla_compliance", fromWindow, toWindow, memo),
        slaCohorts(bpmnProcessId, fromWindow, toWindow, memo),
        ratios(bpmnProcessId, "no_incident", fromWindow, toWindow, memo),
        noIncidentCohorts(bpmnProcessId, fromWindow, toWindow, memo),
        durationBuckets(bpmnProcessId, fromWindow, toWindow, memo),
        distinct(tenantId, fromWindow, toWindow, memo),
        topProcesses(tenantId, fromWindow, toWindow, memo),
        elementDurations(bpmnProcessId, fromWindow, toWindow, memo),
        incidents(bpmnProcessId, fromWindow, toWindow, memo),
        openIncidents(bpmnProcessId, memo),
        activeInstances(bpmnProcessId, memo),
        activatedInstances(bpmnProcessId, fromWindow, toWindow, memo));
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
            List.of("lifecycle"),
            newMemo())) {
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
            "process-distinct",
            List.of("tenantId"),
            null,
            null,
            List.of(),
            List.of("distinct"),
            newMemo())) {
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
    return durationPercentiles(bpmnProcessId, fromWindow, toWindow, newMemo());
  }

  private List<DurationPercentilePoint> durationPercentiles(
      final String bpmnProcessId,
      final Long fromWindow,
      final Long toWindow,
      final Map<QueryKey, List<ReportRow>> memo) {
    final List<DurationPercentilePoint> out = new ArrayList<>();
    for (final ReportRow row :
        series(
            "process-duration",
            List.of(),
            fromWindow,
            toWindow,
            List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
            List.of("p95"),
            memo)) {
      out.add(durationPoint(row.windowStart(), (QuantileResult) row.measures().get("p95")));
    }
    out.sort(Comparator.comparingLong(DurationPercentilePoint::windowStart));
    return out;
  }

  /** A single exact duration distribution over the range (for the KPI tiles). */
  public DurationPercentilePoint durationSummary(
      final String bpmnProcessId, final Long fromWindow, final Long toWindow) {
    return durationSummary(bpmnProcessId, fromWindow, toWindow, newMemo());
  }

  private DurationPercentilePoint durationSummary(
      final String bpmnProcessId,
      final Long fromWindow,
      final Long toWindow,
      final Map<QueryKey, List<ReportRow>> memo) {
    final long windowStart = fromWindow == null ? 0L : fromWindow;
    final List<ReportRow> rows =
        total(
            "process-duration",
            List.of(),
            fromWindow,
            toWindow,
            List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
            List.of("p95"),
            memo);
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

  /**
   * The per-window ratio series for a process, keyed by the declared ratio meter name (e.g. {@code
   * sla_compliance}, {@code no_incident}). The owning dataset is resolved from the catalog rather
   * than hard-coded, so any ratio meter a declaration adds is served without touching this class.
   */
  public List<RatioPoint> ratios(
      final String bpmnProcessId, final String meter, final Long fromWindow, final Long toWindow) {
    return ratios(bpmnProcessId, meter, fromWindow, toWindow, newMemo());
  }

  private List<RatioPoint> ratios(
      final String bpmnProcessId,
      final String meter,
      final Long fromWindow,
      final Long toWindow,
      final Map<QueryKey, List<ReportRow>> memo) {
    final CompiledDataset dataset = datasetWithMeter(meter);
    if (dataset == null) {
      return List.of();
    }
    final List<RatioPoint> out = new ArrayList<>();
    for (final ReportRow row :
        series(
            dataset.name(),
            List.of(),
            fromWindow,
            toWindow,
            List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
            List.of(meter),
            memo)) {
      if (row.measures().get(meter) instanceof final RatioResult r) {
        out.add(
            new RatioPoint(row.windowStart(), r.matched(), r.total(), r.ratio(), r.ratio(), false));
      }
    }
    out.sort(Comparator.comparingLong(RatioPoint::windowStart));
    return out;
  }

  /** The declared dataset that owns a meter of the given name, or {@code null} if none does. */
  private CompiledDataset datasetWithMeter(final String meter) {
    for (final CompiledDataset dataset : catalog.byName().values()) {
      for (final CompiledMeter compiled : dataset.meters()) {
        if (compiled.meterName().equals(meter)) {
          return dataset;
        }
      }
    }
    return null;
  }

  /** The per-window distinct-process estimate for a tenant. */
  public List<DistinctPoint> distinct(
      final String tenantId, final Long fromWindow, final Long toWindow) {
    return distinct(tenantId, fromWindow, toWindow, newMemo());
  }

  private List<DistinctPoint> distinct(
      final String tenantId,
      final Long fromWindow,
      final Long toWindow,
      final Map<QueryKey, List<ReportRow>> memo) {
    final List<DistinctPoint> out = new ArrayList<>();
    for (final ReportRow row :
        series(
            "process-distinct",
            List.of(),
            fromWindow,
            toWindow,
            List.of(FilterPredicate.equals("tenantId", tenantId)),
            List.of("distinct"),
            memo)) {
      final DistinctCountResult d = (DistinctCountResult) row.measures().get("distinct");
      out.add(new DistinctPoint(row.windowStart(), d.estimate(), d.lowerBound(), d.upperBound()));
    }
    out.sort(Comparator.comparingLong(DistinctPoint::windowStart));
    return out;
  }

  /** The ranked top processes for a tenant over the range. */
  public List<TopProcess> topProcesses(
      final String tenantId, final Long fromWindow, final Long toWindow) {
    return topProcesses(tenantId, fromWindow, toWindow, newMemo());
  }

  private List<TopProcess> topProcesses(
      final String tenantId,
      final Long fromWindow,
      final Long toWindow,
      final Map<QueryKey, List<ReportRow>> memo) {
    final List<ReportRow> rows =
        total(
            "top-processes",
            List.of(),
            fromWindow,
            toWindow,
            List.of(FilterPredicate.equals("tenantId", tenantId)),
            List.of("top"),
            memo);
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
    return elementDurations(bpmnProcessId, fromWindow, toWindow, newMemo());
  }

  private List<ElementDuration> elementDurations(
      final String bpmnProcessId,
      final Long fromWindow,
      final Long toWindow,
      final Map<QueryKey, List<ReportRow>> memo) {
    final List<ElementDuration> out = new ArrayList<>();
    for (final ReportRow row :
        total(
            "element-duration",
            List.of("elementId"),
            fromWindow,
            toWindow,
            List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
            List.of("duration"),
            memo)) {
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
    return activatedInstances(bpmnProcessId, fromWindow, toWindow, newMemo());
  }

  private long activatedInstances(
      final String bpmnProcessId,
      final Long fromWindow,
      final Long toWindow,
      final Map<QueryKey, List<ReportRow>> memo) {
    final LifecycleSummaryResult lifecycle = lifecycle(bpmnProcessId, fromWindow, toWindow, memo);
    return lifecycle == null ? 0L : lifecycle.activated();
  }

  /** Current in-flight instance count for a process (activated − completed − terminated). */
  public long activeInstances(final String bpmnProcessId, final String tenantId) {
    // The process-instances grain carries no tenant, so the tenant argument is ignored here.
    return activeInstances(bpmnProcessId, newMemo());
  }

  private long activeInstances(
      final String bpmnProcessId, final Map<QueryKey, List<ReportRow>> memo) {
    final LifecycleSummaryResult lifecycle = lifecycle(bpmnProcessId, null, null, memo);
    if (lifecycle == null) {
      return 0L;
    }
    return Math.max(0L, lifecycle.activated() - lifecycle.completed() - lifecycle.terminated());
  }

  private LifecycleSummaryResult lifecycle(
      final String bpmnProcessId,
      final Long fromWindow,
      final Long toWindow,
      final Map<QueryKey, List<ReportRow>> memo) {
    final List<ReportRow> rows =
        total(
            "process-instances",
            List.of(),
            fromWindow,
            toWindow,
            List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
            List.of("lifecycle"),
            memo);
    return rows.isEmpty() ? null : (LifecycleSummaryResult) rows.get(0).measures().get("lifecycle");
  }

  /** Currently-open incident count for a process (sum of the ±1 delta level over the grain). */
  public long openIncidents(final String bpmnProcessId) {
    return openIncidents(bpmnProcessId, newMemo());
  }

  private long openIncidents(
      final String bpmnProcessId, final Map<QueryKey, List<ReportRow>> memo) {
    long open = 0L;
    for (final ReportRow row :
        total(
            "incident-open",
            List.of(),
            null,
            null,
            List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
            List.of("open"),
            memo)) {
      open += ((Number) row.measures().get("open")).longValue();
    }
    return Math.max(0L, open);
  }

  /** Incidents per flow node: raised (count over the range) + currently open (level gauge). */
  public List<IncidentFlowNode> incidents(
      final String bpmnProcessId, final Long fromWindow, final Long toWindow) {
    return incidents(bpmnProcessId, fromWindow, toWindow, newMemo());
  }

  private List<IncidentFlowNode> incidents(
      final String bpmnProcessId,
      final Long fromWindow,
      final Long toWindow,
      final Map<QueryKey, List<ReportRow>> memo) {
    // element -> {raised, open}; avg/max resolution duration are not modeled as a cube.
    final Map<String, long[]> byElement = new LinkedHashMap<>();
    for (final ReportRow row :
        total(
            "incidents",
            List.of("elementId"),
            fromWindow,
            toWindow,
            List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
            List.of("count"),
            memo)) {
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
            List.of("open"),
            memo)) {
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
    return slaCohorts(bpmnProcessId, fromWindow, toWindow, newMemo());
  }

  private List<SlaCohortPoint> slaCohorts(
      final String bpmnProcessId,
      final Long fromWindow,
      final Long toWindow,
      final Map<QueryKey, List<ReportRow>> memo) {
    // The cohort size ("started") and the still-open/maturing split come from the lifecycle summary
    // (every instance that started in the window); the met/breached split comes from the
    // completion-based SLA ratio. Joining the two keeps "started" the true cohort size rather than
    // just its settled part — a completion-based total badly undercounts starts while a large
    // backlog of instances is still running.
    final Map<Long, RatioPoint> settled = new LinkedHashMap<>();
    for (final RatioPoint p : ratios(bpmnProcessId, "sla_compliance", fromWindow, toWindow, memo)) {
      settled.put(p.windowStart(), p);
    }
    final List<SlaCohortPoint> out = new ArrayList<>();
    lifecycleByWindow(bpmnProcessId, fromWindow, toWindow, memo)
        .forEach(
            (windowStart, lc) -> {
              final long started = lc.activated();
              final long open = Math.max(0L, started - lc.completed() - lc.terminated());
              final RatioPoint p = settled.get(windowStart);
              final long met = p == null ? 0L : p.matched();
              // The remainder of the settled instances: completed-but-missed plus terminated.
              final long breached = Math.max(0L, started - met - open);
              out.add(new SlaCohortPoint(windowStart, started, met, breached, open, open > 0L));
            });
    out.sort(Comparator.comparingLong(SlaCohortPoint::windowStart));
    return out;
  }

  /**
   * No-incident outcomes per start cohort — the same lifecycle-joined shape as {@link #slaCohorts}:
   * {@code started} (all instances that started in the window) split into {@code clean} (completed
   * without an incident), {@code withIncident} (completed having raised one, or terminated) and
   * {@code open} (still running, undecided).
   */
  public List<NoIncidentCohortPoint> noIncidentCohorts(
      final String bpmnProcessId, final Long fromWindow, final Long toWindow) {
    return noIncidentCohorts(bpmnProcessId, fromWindow, toWindow, newMemo());
  }

  private List<NoIncidentCohortPoint> noIncidentCohorts(
      final String bpmnProcessId,
      final Long fromWindow,
      final Long toWindow,
      final Map<QueryKey, List<ReportRow>> memo) {
    final Map<Long, RatioPoint> settled = new LinkedHashMap<>();
    for (final RatioPoint p : ratios(bpmnProcessId, "no_incident", fromWindow, toWindow, memo)) {
      settled.put(p.windowStart(), p);
    }
    final List<NoIncidentCohortPoint> out = new ArrayList<>();
    lifecycleByWindow(bpmnProcessId, fromWindow, toWindow, memo)
        .forEach(
            (windowStart, lc) -> {
              final long started = lc.activated();
              final long open = Math.max(0L, started - lc.completed() - lc.terminated());
              final RatioPoint p = settled.get(windowStart);
              final long clean = p == null ? 0L : p.matched();
              final long withIncident = Math.max(0L, started - clean - open);
              out.add(
                  new NoIncidentCohortPoint(
                      windowStart, started, clean, withIncident, open, open > 0L));
            });
    out.sort(Comparator.comparingLong(NoIncidentCohortPoint::windowStart));
    return out;
  }

  /**
   * Per-window lifecycle summaries for a process keyed by window start — the authoritative
   * "started" cohort set (one entry per window in which any instance was activated). Shared by the
   * cohort widgets and the duration buckets; the render memo makes it one query per render.
   */
  private Map<Long, LifecycleSummaryResult> lifecycleByWindow(
      final String bpmnProcessId,
      final Long fromWindow,
      final Long toWindow,
      final Map<QueryKey, List<ReportRow>> memo) {
    final Map<Long, LifecycleSummaryResult> byWindow = new LinkedHashMap<>();
    for (final ReportRow row :
        series(
            "process-instances",
            List.of(),
            fromWindow,
            toWindow,
            List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
            List.of("lifecycle"),
            memo)) {
      if (row.measures().get("lifecycle") instanceof final LifecycleSummaryResult lc) {
        byWindow.put(row.windowStart(), lc);
      }
    }
    return byWindow;
  }

  /**
   * Completion-time histogram per window: for each window of the process-instances cube, split the
   * completed instances across the fixed duration bands (from the lifecycle summary's duration
   * sketch) and report how many were still open ({@code activated − completed − terminated}).
   */
  public List<DurationBucketPoint> durationBuckets(
      final String bpmnProcessId, final Long fromWindow, final Long toWindow) {
    return durationBuckets(bpmnProcessId, fromWindow, toWindow, newMemo());
  }

  private List<DurationBucketPoint> durationBuckets(
      final String bpmnProcessId,
      final Long fromWindow,
      final Long toWindow,
      final Map<QueryKey, List<ReportRow>> memo) {
    final List<DurationBucketPoint> out = new ArrayList<>();
    lifecycleByWindow(bpmnProcessId, fromWindow, toWindow, memo)
        .forEach(
            (windowStart, lc) -> {
              final long open = Math.max(0L, lc.activated() - lc.completed() - lc.terminated());
              out.add(
                  new DurationBucketPoint(
                      windowStart, lc.activated(), lc.duration().durationBands(), open));
            });
    out.sort(Comparator.comparingLong(DurationBucketPoint::windowStart));
    return out;
  }

  /**
   * The deployed BPMN diagram XML for a process, read from the built-in process-definitions table
   * that {@link ProcessDefinitionSink} writes. Feeds the flow-node and incident heatmaps, which
   * overlay per-element metrics on the rendered diagram. Returns the highest version seen if a
   * process was redeployed; empty if the definition has not been observed yet.
   */
  public Optional<String> diagramXml(final String bpmnProcessId) {
    final List<TableRow> rows =
        tableExecutor.execute(
            new TableQuery(List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)), 100),
            ProcessDefinitionSink.TABLE);
    return rows.stream()
        .max(Comparator.comparingLong(r -> asLong(r.values().get("version"))))
        .map(r -> r.values().get("bpmnXml"))
        .filter(String.class::isInstance)
        .map(String.class::cast);
  }

  private static long asLong(final Object value) {
    return value instanceof final Number n ? n.longValue() : 0L;
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

  /** A fresh single-call memo, so a per-widget endpoint reuses the same query paths. */
  private static Map<QueryKey, List<ReportRow>> newMemo() {
    return new HashMap<>();
  }

  /**
   * The identity of one serving query within a render. Keyed on the <em>raw</em> widget arguments
   * (a {@code null} bound stays {@code null}), so two widgets asking the same question share one
   * result even though the executed query resolves "now" per call.
   */
  private record QueryKey(
      boolean series,
      String dataset,
      List<String> groupBy,
      Long fromWindow,
      Long toWindow,
      List<FilterPredicate> filters,
      List<String> meters) {}

  /** Per-window series: bucket at the cube's finest tier (one output row per stored window). */
  private List<ReportRow> series(
      final String name,
      final List<String> groupBy,
      final Long fromWindow,
      final Long toWindow,
      final List<FilterPredicate> filters,
      final List<String> meters,
      final Map<QueryKey, List<ReportRow>> memo) {
    return memo.computeIfAbsent(
        new QueryKey(true, name, groupBy, fromWindow, toWindow, filters, meters),
        key -> {
          final CompiledDataset dataset = catalog.require(name);
          return executor
              .execute(
                  new ReportQuery(
                      groupBy,
                      fromMs(fromWindow),
                      toMs(toWindow),
                      finestTier(dataset),
                      filters,
                      meters),
                  dataset)
              .rows();
        });
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
      final List<String> meters,
      final Map<QueryKey, List<ReportRow>> memo) {
    return memo.computeIfAbsent(
        new QueryKey(false, name, groupBy, fromWindow, toWindow, filters, meters),
        key -> {
          final CompiledDataset dataset = catalog.require(name);
          final long toMs = toMs(toWindow);
          return executor
              .execute(
                  new ReportQuery(groupBy, fromMs(fromWindow), toMs, toMs, filters, meters),
                  dataset)
              .rows();
        });
  }

  private static long fromMs(final Long fromWindow) {
    return fromWindow == null ? 0L : fromWindow;
  }

  private static long toMs(final Long toWindow) {
    return toWindow == null ? System.currentTimeMillis() + ONE_HOUR_MS : toWindow;
  }
}
