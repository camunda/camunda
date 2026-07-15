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
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.metric.ExecutionTimeResult;
import io.camunda.analytics.metric.RatioResult;
import io.camunda.analytics.query.DatasetQueryExecutor;
import io.camunda.analytics.query.ReportQuery;
import io.camunda.analytics.query.ReportRow;
import io.camunda.analytics.query.SnapshotQueryExecutor;
import io.camunda.analytics.query.SnapshotQueryExecutor.SnapshotQuery;
import io.camunda.analytics.query.SnapshotQueryExecutor.SnapshotSeriesPoint;
import io.camunda.analytics.query.TableQuery;
import io.camunda.analytics.query.TableQueryExecutor;
import io.camunda.analytics.serving.spi.TableRow;
import io.camunda.analytics.sketch.DistinctCountResult;
import io.camunda.analytics.sketch.QuantileResult;
import io.camunda.analytics.sketch.TopKResult;
import io.camunda.analytics.table.ProcessDefinitionSink;
import io.camunda.analytics.webapp.DatasetCatalog;
import io.camunda.analytics.webapp.TableCatalog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
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

  /** Default active-series lookback when no range start is given (the walk is O(buckets)). */
  private static final long ACTIVE_SERIES_LOOKBACK_MS = 6 * ONE_HOUR_MS;

  /** The cube every lifecycle-derived widget (and the cohort joins' "started" side) reads. */
  private static final String LIFECYCLE_CUBE = "process-instances";

  /**
   * The process-instances cube's primitive lifecycle meters (per-transition counts composed via
   * per-meter filters, replacing the deprecated {@code lifecycle_summary} bundle) plus the
   * completion-time histogram. One shared meter list for every lifecycle-derived widget, so the
   * render memo collapses them onto a single serving query.
   */
  private static final List<String> LIFECYCLE_SERIES_METERS =
      List.of("activated", "completed", "terminated", "duration_bands");

  private static final List<String> LIFECYCLE_TOTAL_METERS =
      List.of("activated", "completed", "terminated");

  private final DatasetQueryExecutor executor;
  private final DatasetCatalog catalog;
  private final TableCatalog tableCatalog;
  private final TableQueryExecutor tableExecutor;
  private final SnapshotQueryExecutor snapshotExecutor;

  public DashboardRepository(
      final DatasetQueryExecutor executor,
      final DatasetCatalog catalog,
      final TableCatalog tableCatalog,
      final TableQueryExecutor tableExecutor,
      final SnapshotQueryExecutor snapshotExecutor) {
    this.executor = executor;
    this.catalog = catalog;
    this.tableCatalog = tableCatalog;
    this.tableExecutor = tableExecutor;
    this.snapshotExecutor = snapshotExecutor;
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
        activatedInstances(bpmnProcessId, fromWindow, toWindow, memo),
        endedInstances(bpmnProcessId, fromWindow, toWindow, memo));
  }

  /** Process ids the process-instances cube has data for (for the process picker). */
  public List<String> processes() {
    final TreeSet<String> ids = new TreeSet<>();
    for (final ReportRow row :
        total(
            LIFECYCLE_CUBE,
            List.of("bpmnProcessId"),
            null,
            null,
            List.of(),
            List.of("activated"),
            newMemo())) {
      final Object id = row.dimensions().get("bpmnProcessId");
      if (id != null) {
        ids.add(id.toString());
      }
    }
    return new ArrayList<>(ids);
  }

  /** Tenants the tenant-overview cube has data for. */
  public List<String> tenants() {
    final TreeSet<String> ids = new TreeSet<>();
    for (final ReportRow row :
        total(
            "tenant-overview",
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
            List.of("percentiles"),
            memo)) {
      out.add(durationPoint(row.windowStart(), (QuantileResult) row.measures().get("percentiles")));
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
            List.of("percentiles"),
            memo);
    if (rows.isEmpty()) {
      return new DurationPercentilePoint(windowStart, 0, 0, 0, 0, 0, 0, 0);
    }
    return durationPoint(windowStart, (QuantileResult) rows.get(0).measures().get("percentiles"));
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
   * The period-over-period KPI comparison: the whole-range KPI aggregates over {@code [from, to)}
   * and over the immediately preceding same-length range {@code [from − P, to − P)}, {@code P = to
   * − from}. Requires an explicit range — a null bound has no well-defined "previous period". Both
   * periods run against one shared memo, so a widget read the current period already answered is
   * not re-executed.
   */
  public KpiComparison kpiComparison(final String bpmnProcessId, final long from, final long to) {
    if (to <= from) {
      throw new IllegalArgumentException("empty comparison range [" + from + ", " + to + ")");
    }
    final long period = to - from;
    final Map<QueryKey, List<ReportRow>> memo = newMemo();
    return new KpiComparison(
        periodKpis(bpmnProcessId, from, to, memo),
        periodKpis(bpmnProcessId, from - period, to - period, memo));
  }

  /** One period's KPI-tile aggregates: lifecycle counts, duration summary, quality ratios. */
  private PeriodKpis periodKpis(
      final String bpmnProcessId,
      final long from,
      final long to,
      final Map<QueryKey, List<ReportRow>> memo) {
    final LifecycleCounts lifecycle = lifecycle(bpmnProcessId, from, to, memo);
    return new PeriodKpis(
        lifecycle == null ? 0L : lifecycle.activated(),
        lifecycle == null ? 0L : lifecycle.completed() + lifecycle.terminated(),
        durationSummary(bpmnProcessId, from, to, memo),
        ratioKpi(bpmnProcessId, "sla_compliance", from, to, memo),
        ratioKpi(bpmnProcessId, "no_incident", from, to, memo),
        ratioKpi(bpmnProcessId, "first_time_right", from, to, memo));
  }

  /**
   * One ratio meter collapsed to a single whole-range total (a single-bucket read, not a series):
   * the exact {@code matched / total} over the period. Reads every ratio meter of the owning
   * dataset (the shared meter list), so the three quality KPIs of one period are one query.
   */
  private RatioKpi ratioKpi(
      final String bpmnProcessId,
      final String meter,
      final Long from,
      final Long to,
      final Map<QueryKey, List<ReportRow>> memo) {
    final CompiledDataset dataset = datasetWithMeter(meter);
    if (dataset == null) {
      return RatioKpi.EMPTY;
    }
    for (final ReportRow row :
        total(
            dataset.name(),
            List.of(),
            from,
            to,
            List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
            ratioMeters(dataset),
            memo)) {
      if (row.measures().get(meter) instanceof final RatioResult r) {
        return new RatioKpi(r.matched(), r.total(), r.ratio());
      }
    }
    return RatioKpi.EMPTY;
  }

  /**
   * The percentile control chart with its previous-period overlay: the current series plus the
   * preceding same-length range's series re-timestamped onto the current grid ({@code windowStart +
   * P}), so the two overlay on one time axis. Requires an explicit range, like {@link
   * #kpiComparison}.
   */
  public PercentileComparison durationPercentilesCompare(
      final String bpmnProcessId, final long from, final long to) {
    if (to <= from) {
      throw new IllegalArgumentException("empty comparison range [" + from + ", " + to + ")");
    }
    final long period = to - from;
    final Map<QueryKey, List<ReportRow>> memo = newMemo();
    final List<DurationPercentilePoint> previous = new ArrayList<>();
    for (final DurationPercentilePoint p :
        durationPercentiles(bpmnProcessId, from - period, to - period, memo)) {
      previous.add(
          new DurationPercentilePoint(
              p.windowStart() + period,
              p.observationCount(),
              p.minMs(),
              p.maxMs(),
              p.p50Ms(),
              p.p75Ms(),
              p.p90Ms(),
              p.p99Ms()));
    }
    return new PercentileComparison(
        durationPercentiles(bpmnProcessId, from, to, memo), List.copyOf(previous));
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
    return ratios(dataset, meter, bpmnProcessId, fromWindow, toWindow, finestTier(dataset), memo);
  }

  /**
   * The ratio series of one dataset's meter, bucketed at an explicit granularity. The query always
   * fetches <em>every</em> ratio meter the dataset declares (one shared meter list, like {@link
   * #LIFECYCLE_SERIES_METERS}), so all ratio widgets and cohort joins over one consolidated cube
   * collapse onto a single serving query per render.
   */
  private List<RatioPoint> ratios(
      final CompiledDataset dataset,
      final String meter,
      final String bpmnProcessId,
      final Long fromWindow,
      final Long toWindow,
      final long granularityMs,
      final Map<QueryKey, List<ReportRow>> memo) {
    final List<RatioPoint> out = new ArrayList<>();
    for (final ReportRow row :
        series(
            dataset.name(),
            List.of(),
            fromWindow,
            toWindow,
            List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
            ratioMeters(dataset),
            granularityMs,
            memo)) {
      if (row.measures().get(meter) instanceof final RatioResult r) {
        out.add(
            new RatioPoint(row.windowStart(), r.matched(), r.total(), r.ratio(), r.ratio(), false));
      }
    }
    out.sort(Comparator.comparingLong(RatioPoint::windowStart));
    return out;
  }

  /** All ratio meters a dataset declares, in declaration order. */
  private static List<String> ratioMeters(final CompiledDataset dataset) {
    final List<String> names = new ArrayList<>();
    for (final CompiledMeter compiled : dataset.meters()) {
      if (MeterCatalog.RATIO.equals(compiled.bound().meter().type())) {
        names.add(compiled.meterName());
      }
    }
    return names;
  }

  /**
   * The single declared dataset that owns a meter of the given name, or {@code null} if none does.
   * Ambiguity is a loud failure, never a silent first-match: meter names are only unique per
   * dataset (e.g. {@code count} exists in several standard cubes), so picking "the first" would
   * make the answer depend on catalog iteration order and silently read the wrong cube.
   */
  private CompiledDataset datasetWithMeter(final String meter) {
    CompiledDataset owner = null;
    for (final CompiledDataset dataset : catalog.byName().values()) {
      for (final CompiledMeter compiled : dataset.meters()) {
        if (compiled.meterName().equals(meter)) {
          if (owner != null) {
            throw new IllegalStateException(
                "meter '"
                    + meter
                    + "' is owned by more than one dataset ('"
                    + owner.name()
                    + "' and '"
                    + dataset.name()
                    + "') — qualify the read by dataset name instead of by meter name");
          }
          owner = dataset;
        }
      }
    }
    return owner;
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
            "tenant-overview",
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
            "tenant-overview",
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
            "elements",
            List.of("elementId"),
            fromWindow,
            toWindow,
            List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
            List.of("duration", "duration_p"),
            memo)) {
      final ExecutionTimeResult d = (ExecutionTimeResult) row.measures().get("duration");
      final QuantileResult q = (QuantileResult) row.measures().get("duration_p");
      final Object elementId = row.dimensions().get("elementId");
      out.add(
          new ElementDuration(
              elementId == null ? "" : elementId.toString(),
              "", // element type is not modeled as a dimension in the cube
              d.count(),
              Math.round(d.averageMs()),
              quantileMs(q, 0.5),
              quantileMs(q, 0.9),
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
    final LifecycleCounts lifecycle = lifecycle(bpmnProcessId, fromWindow, toWindow, memo);
    return lifecycle == null ? 0L : lifecycle.activated();
  }

  /**
   * How many instances of the process were running at each moment of the range — the
   * active-instances cube's periodic snapshots (ADR 0010), carried forward at the cube's sample
   * interval. Absolute values, not per-window flows. A null range start defaults to a bounded
   * lookback (the carry-forward walk is O(buckets), so "since forever" must not mean epoch 0); a
   * null range end defaults to the newest <em>releasable</em> boundary, {@code now − grace} —
   * beyond it no snapshot can exist yet, and carrying the last value into that unknowable stretch
   * would render a misleading flat tail.
   */
  public List<ActiveInstancesPoint> activeSeries(
      final String bpmnProcessId, final Long fromWindow, final Long toWindow) {
    final CompiledDataset dataset = catalog.require("active-instances");
    final long everyMs = dataset.snapshots().everyMs();
    final long toMs =
        toWindow != null
            ? toWindow
            : System.currentTimeMillis() - dataset.finestTier().windows().graceMs();
    final long fromMs = fromWindow == null ? toMs - ACTIVE_SERIES_LOOKBACK_MS : fromWindow;
    final List<ActiveInstancesPoint> out = new ArrayList<>();
    for (final SnapshotSeriesPoint point :
        snapshotExecutor.execute(new SnapshotQuery(fromMs, toMs, everyMs), dataset)) {
      if (bpmnProcessId.equals(point.keyValues().get(0))
          && point.measures().get("active") instanceof final Number active) {
        out.add(new ActiveInstancesPoint(point.time(), active.longValue()));
      }
    }
    return out;
  }

  /**
   * The business value processed over the range: the whole-range sum of the value-throughput cube's
   * COMPLETED-filtered SUM. {@code processed} stays {@code null} when no row exists — the process
   * never carried the value variable in range — so the tile can show a dash instead of a misleading
   * 0.
   */
  public ValueSummary valueSummary(
      final String bpmnProcessId, final Long fromWindow, final Long toWindow) {
    for (final ReportRow row :
        total(
            "value-throughput",
            List.of(),
            fromWindow,
            toWindow,
            List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
            List.of("processed"),
            newMemo())) {
      if (row.measures().get("processed") instanceof final Number processed) {
        return new ValueSummary(processed.longValue());
      }
    }
    return new ValueSummary(null);
  }

  /**
   * The business value in flight at each moment of the range — the value-in-flight cube's periodic
   * snapshots, carried forward exactly like {@link #activeSeries} (same lookback clamp, same
   * newest-releasable default end). Empty when the process never carried the value variable.
   */
  public List<ValuePoint> valueSeries(
      final String bpmnProcessId, final Long fromWindow, final Long toWindow) {
    final CompiledDataset dataset = catalog.require("value-in-flight");
    final long everyMs = dataset.snapshots().everyMs();
    final long toMs =
        toWindow != null
            ? toWindow
            : System.currentTimeMillis() - dataset.finestTier().windows().graceMs();
    final long fromMs = fromWindow == null ? toMs - ACTIVE_SERIES_LOOKBACK_MS : fromWindow;
    final List<ValuePoint> out = new ArrayList<>();
    for (final SnapshotSeriesPoint point :
        snapshotExecutor.execute(new SnapshotQuery(fromMs, toMs, everyMs), dataset)) {
      if (bpmnProcessId.equals(point.keyValues().get(0))
          && point.measures().get("value") instanceof final Number value) {
        out.add(new ValuePoint(point.time(), value.longValue()));
      }
    }
    return out;
  }

  /** The per-window completion-duration spread (avg±stddev band and the exact extrema). */
  public List<DurationSpreadPoint> durationSpread(
      final String bpmnProcessId, final Long fromWindow, final Long toWindow) {
    final List<DurationSpreadPoint> out = new ArrayList<>();
    for (final ReportRow row :
        series(
            "process-duration",
            List.of(),
            fromWindow,
            toWindow,
            List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
            List.of("duration", "stddev"),
            newMemo())) {
      if (row.measures().get("duration") instanceof final ExecutionTimeResult duration) {
        out.add(
            new DurationSpreadPoint(
                row.windowStart(),
                duration.averageMs(),
                duration.minMs(),
                duration.maxMs(),
                row.measures().get("stddev") instanceof final Number stddev
                    ? stddev.doubleValue()
                    : 0.0));
      }
    }
    out.sort(Comparator.comparingLong(DurationSpreadPoint::windowStart));
    return out;
  }

  /**
   * Instances ended (completed OR terminated) for a process over the range — NOT the duration
   * summary's observation count, which reads the COMPLETED-filtered duration cube and would
   * silently drop terminated instances from the books.
   */
  public long endedInstances(
      final String bpmnProcessId, final Long fromWindow, final Long toWindow) {
    return endedInstances(bpmnProcessId, fromWindow, toWindow, newMemo());
  }

  private long endedInstances(
      final String bpmnProcessId,
      final Long fromWindow,
      final Long toWindow,
      final Map<QueryKey, List<ReportRow>> memo) {
    final LifecycleCounts lifecycle = lifecycle(bpmnProcessId, fromWindow, toWindow, memo);
    return lifecycle == null ? 0L : lifecycle.completed() + lifecycle.terminated();
  }

  /** Current in-flight instance count for a process (activated − completed − terminated). */
  public long activeInstances(final String bpmnProcessId, final String tenantId) {
    // The process-instances grain carries no tenant, so the tenant argument is ignored here.
    return activeInstances(bpmnProcessId, newMemo());
  }

  private long activeInstances(
      final String bpmnProcessId, final Map<QueryKey, List<ReportRow>> memo) {
    final LifecycleCounts lifecycle = lifecycle(bpmnProcessId, null, null, memo);
    return lifecycle == null ? 0L : lifecycle.open();
  }

  private LifecycleCounts lifecycle(
      final String bpmnProcessId,
      final Long fromWindow,
      final Long toWindow,
      final Map<QueryKey, List<ReportRow>> memo) {
    final List<ReportRow> rows =
        total(
            LIFECYCLE_CUBE,
            List.of(),
            fromWindow,
            toWindow,
            List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
            LIFECYCLE_TOTAL_METERS,
            memo);
    return rows.isEmpty() ? null : lifecycleCounts(rows.get(0));
  }

  /** The per-transition counts of one process-instances row (a missing measure reads 0). */
  private static LifecycleCounts lifecycleCounts(final ReportRow row) {
    return new LifecycleCounts(
        measureAsLong(row, "activated"),
        measureAsLong(row, "completed"),
        measureAsLong(row, "terminated"));
  }

  private static long measureAsLong(final ReportRow row, final String meter) {
    return row.measures().get(meter) instanceof final Number n ? n.longValue() : 0L;
  }

  /**
   * Per-transition instance counts, composed from the count-with-filter primitive meters that
   * replaced the {@code lifecycle_summary} bundle.
   */
  private record LifecycleCounts(long activated, long completed, long terminated) {

    /** Still running: started but neither completed nor terminated (never negative). */
    long open() {
      return Math.max(0L, activated - completed - terminated);
    }
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
            "incidents",
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
    // One consolidated read per element: raised (CREATED count over the range) and the open level
    // gauge live on the same rows.
    final List<IncidentFlowNode> out = new ArrayList<>();
    for (final ReportRow row :
        total(
            "incidents",
            List.of("elementId"),
            fromWindow,
            toWindow,
            List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
            List.of("count", "open"),
            memo)) {
      final long raised = measureAsLong(row, "count");
      final long open = Math.max(0L, measureAsLong(row, "open"));
      out.add(new IncidentFlowNode(elementId(row), raised, open));
    }
    out.sort(Comparator.comparingLong(IncidentFlowNode::raised).reversed());
    return out;
  }

  /**
   * Incidents raised per window for a process (summed across its flow nodes) — the quality page's
   * incident trend, read from the consolidated incidents cube's CREATED-filtered count.
   */
  public List<IncidentTrendPoint> incidentTrend(
      final String bpmnProcessId, final Long fromWindow, final Long toWindow) {
    final List<IncidentTrendPoint> out = new ArrayList<>();
    for (final ReportRow row :
        series(
            "incidents",
            List.of(),
            fromWindow,
            toWindow,
            List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
            List.of("count"),
            newMemo())) {
      out.add(new IncidentTrendPoint(row.windowStart(), measureAsLong(row, "count")));
    }
    out.sort(Comparator.comparingLong(IncidentTrendPoint::windowStart));
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
    // backlog of instances is still running. Both sides are bucketed at one explicit common
    // granularity (see joinGranularity) so the windowStart join keys align by construction.
    final CompiledDataset ratioDataset = datasetWithMeter("sla_compliance");
    final long joinMs = cohortJoinGranularity(ratioDataset);
    final Map<Long, RatioPoint> settled = new LinkedHashMap<>();
    if (ratioDataset != null) {
      for (final RatioPoint p :
          ratios(
              ratioDataset, "sla_compliance", bpmnProcessId, fromWindow, toWindow, joinMs, memo)) {
        settled.put(p.windowStart(), p);
      }
    }
    final List<SlaCohortPoint> out = new ArrayList<>();
    lifecycleByWindow(bpmnProcessId, fromWindow, toWindow, joinMs, memo)
        .forEach(
            (windowStart, lc) -> {
              final long started = lc.counts().activated();
              final long open = lc.counts().open();
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
    final CompiledDataset ratioDataset = datasetWithMeter("no_incident");
    final long joinMs = cohortJoinGranularity(ratioDataset);
    final Map<Long, RatioPoint> settled = new LinkedHashMap<>();
    if (ratioDataset != null) {
      for (final RatioPoint p :
          ratios(ratioDataset, "no_incident", bpmnProcessId, fromWindow, toWindow, joinMs, memo)) {
        settled.put(p.windowStart(), p);
      }
    }
    final List<NoIncidentCohortPoint> out = new ArrayList<>();
    lifecycleByWindow(bpmnProcessId, fromWindow, toWindow, joinMs, memo)
        .forEach(
            (windowStart, lc) -> {
              final long started = lc.counts().activated();
              final long open = lc.counts().open();
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
   * Per-window lifecycle counts + completion-time bands for a process keyed by window start — the
   * authoritative "started" cohort set (one entry per window in which any instance was activated).
   * One shared meter list ({@link #LIFECYCLE_SERIES_METERS}) for the cohort widgets and the
   * duration buckets, so the render memo makes it one query per render.
   */
  private Map<Long, LifecyclePoint> lifecycleByWindow(
      final String bpmnProcessId,
      final Long fromWindow,
      final Long toWindow,
      final long granularityMs,
      final Map<QueryKey, List<ReportRow>> memo) {
    final Map<Long, LifecyclePoint> byWindow = new LinkedHashMap<>();
    for (final ReportRow row :
        series(
            LIFECYCLE_CUBE,
            List.of(),
            fromWindow,
            toWindow,
            List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
            LIFECYCLE_SERIES_METERS,
            granularityMs,
            memo)) {
      final long[] bands =
          row.measures().get("duration_bands") instanceof final long[] histogram
              ? histogram
              : new long[0];
      byWindow.put(row.windowStart(), new LifecyclePoint(lifecycleCounts(row), bands));
    }
    return byWindow;
  }

  /** One process-instances window: the per-transition counts and the completion-time bands. */
  private record LifecyclePoint(LifecycleCounts counts, long[] bands) {}

  /**
   * Completion-time histogram per window: for each window of the process-instances cube, the exact
   * duration-band counts of the completed instances (the {@code duration_bands} histogram meter)
   * and how many were still open ({@code activated − completed − terminated}).
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
    lifecycleByWindow(
            bpmnProcessId, fromWindow, toWindow, finestTier(catalog.require(LIFECYCLE_CUBE)), memo)
        .forEach(
            (windowStart, lc) ->
                out.add(
                    new DurationBucketPoint(
                        windowStart, lc.counts().activated(), lc.bands(), lc.counts().open())));
    out.sort(Comparator.comparingLong(DurationBucketPoint::windowStart));
    return out;
  }

  /**
   * The per-window flow-balance series (Little's law triple minus the WIP, which the widget reads
   * from {@link #activeSeries}): instances started and ended per window, derived from the
   * process-instances cube's per-transition primitive meters through the same memoized lifecycle
   * query the cohort widgets share.
   */
  public List<LifecycleSeriesPoint> lifecycleSeries(
      final String bpmnProcessId, final Long fromWindow, final Long toWindow) {
    final List<LifecycleSeriesPoint> out = new ArrayList<>();
    lifecycleByWindow(
            bpmnProcessId,
            fromWindow,
            toWindow,
            finestTier(catalog.require(LIFECYCLE_CUBE)),
            newMemo())
        .forEach(
            (windowStart, lc) ->
                out.add(
                    new LifecycleSeriesPoint(
                        windowStart,
                        lc.counts().activated(),
                        lc.counts().completed() + lc.counts().terminated())));
    out.sort(Comparator.comparingLong(LifecycleSeriesPoint::windowStart));
    return out;
  }

  /**
   * Rework hotspots per flow node over the range: activations (exact count) versus distinct
   * instances (HLL estimate over processInstanceKey) from the elements cube. The rework estimate
   * {@code max(0, activations − instances)} is exact while the HLL is exact (small counts) and an
   * approximation at scale; zero-rework elements are dropped, the rest sorted by rework descending.
   */
  public List<ReworkHotspot> rework(
      final String bpmnProcessId, final Long fromWindow, final Long toWindow) {
    final List<ReworkHotspot> out = new ArrayList<>();
    for (final ReportRow row :
        total(
            "elements",
            List.of("elementId"),
            fromWindow,
            toWindow,
            List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
            List.of("activations", "instances"),
            newMemo())) {
      final long activations = measureAsLong(row, "activations");
      final long instances =
          row.measures().get("instances") instanceof final DistinctCountResult d
              ? d.estimate()
              : 0L;
      final long rework = Math.max(0L, activations - instances);
      if (rework > 0L) {
        out.add(new ReworkHotspot(elementId(row), activations, instances, rework));
      }
    }
    out.sort(Comparator.comparingLong(ReworkHotspot::rework).reversed());
    return out;
  }

  /**
   * The oldest currently-open instances of a process (aging WIP), from the open-instances
   * working-set table. {@code ageMs} is computed here against one shared "now". The table read path
   * has no ordering or paging yet ({@link TableQuery} carries filters + limit only), so this
   * fetches a bounded page ({@value #OPEN_INSTANCES_FETCH_BOUND} rows) and sorts oldest-first
   * server-side — beyond the bound the oldest rows are best-effort.
   */
  public List<OpenInstanceRow> openInstances(final String bpmnProcessId, final int limit) {
    final long now = System.currentTimeMillis();
    final List<OpenInstanceRow> out = new ArrayList<>();
    for (final TableRow row :
        tableExecutor.execute(
            new TableQuery(
                List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
                OPEN_INSTANCES_FETCH_BOUND),
            tableCatalog.require("open-instances"))) {
      final long startedAt = asLong(row.values().get("startTime"));
      out.add(
          new OpenInstanceRow(
              asLong(row.values().get("processInstanceKey")),
              bpmnProcessId,
              startedAt,
              Math.max(0L, now - startedAt)));
    }
    out.sort(Comparator.comparingLong(OpenInstanceRow::startedAt));
    return out.size() > limit ? List.copyOf(out.subList(0, limit)) : out;
  }

  /** The bounded fetch backing {@link #openInstances} (the table read has no ORDER BY yet). */
  private static final int OPEN_INSTANCES_FETCH_BOUND = 1_000;

  /** The bounded variant-dictionary fetch backing {@link #variants} (one row per variant). */
  private static final int VARIANT_CATALOG_FETCH_BOUND = 1_000;

  /**
   * The top execution variants of a process over the range, by instance count: the process-variants
   * cube's whole-range totals grouped by {@code variantHash}, joined with the variant-catalog
   * dictionary for the human-readable element list. {@code share} is against all ended instances
   * that carried a variant in range; {@code p50/p95} come from the per-variant duration sketch
   * (terminated instances carry a duration too, so a fast-failing variant reads honestly fast).
   */
  public List<VariantRow> variants(
      final String bpmnProcessId, final Long fromWindow, final Long toWindow, final int limit) {
    final List<ReportRow> rows =
        total(
            "process-variants",
            List.of("variantHash"),
            fromWindow,
            toWindow,
            List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
            List.of("count", "duration_p"),
            newMemo());
    if (rows.isEmpty()) {
      return List.of();
    }
    // The dictionary: variantHash -> canonical element list. One bounded fetch for the process;
    // a cube row whose dictionary entry has not landed yet joins to "" rather than dropping.
    final Map<Long, String> elementsByHash = new HashMap<>();
    for (final TableRow row :
        tableExecutor.execute(
            new TableQuery(
                List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
                VARIANT_CATALOG_FETCH_BOUND),
            tableCatalog.require("variant-catalog"))) {
      if (row.values().get("variantElements") instanceof final String elements) {
        elementsByHash.put(asLong(row.values().get("variantHash")), elements);
      }
    }
    long totalEnded = 0L;
    for (final ReportRow row : rows) {
      totalEnded += measureAsLong(row, "count");
    }
    final List<VariantRow> out = new ArrayList<>(rows.size());
    for (final ReportRow row : rows) {
      final long hash = asLong(row.dimensions().get("variantHash"));
      final long count = measureAsLong(row, "count");
      final QuantileResult q =
          row.measures().get("duration_p") instanceof final QuantileResult result ? result : null;
      out.add(
          new VariantRow(
              hash,
              elementsByHash.getOrDefault(hash, ""),
              count,
              totalEnded == 0 ? 0.0 : (double) count / totalEnded,
              quantileMs(q, 0.5),
              quantileMs(q, 0.95)));
    }
    out.sort(Comparator.comparingLong(VariantRow::count).reversed());
    return out.size() > limit ? List.copyOf(out.subList(0, limit)) : out;
  }

  /**
   * The deployed BPMN diagram XML for a process, read from the built-in process-definitions table
   * that {@link ProcessDefinitionSink} writes. Feeds the flow-node and incident heatmaps, which
   * overlay per-element metrics on the rendered diagram. Returns the highest version seen if a
   * process was redeployed; empty if the definition has not been observed yet.
   */
  public Optional<String> diagramXml(final String bpmnProcessId) {
    return latestDefinition(bpmnProcessId)
        .map(r -> r.values().get("bpmnXml"))
        .filter(String.class::isInstance)
        .map(String.class::cast);
  }

  /** The highest-version process-definitions row for a process, if one has been observed. */
  private Optional<TableRow> latestDefinition(final String bpmnProcessId) {
    final List<TableRow> rows =
        tableExecutor.execute(
            new TableQuery(List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)), 100),
            ProcessDefinitionSink.TABLE);
    return rows.stream().max(Comparator.comparingLong(r -> asLong(r.values().get("version"))));
  }

  /**
   * Parsed decision gateways per process definition. A definition key is immutable (a redeploy
   * mints a new key and this map is keyed by it), so a model is parsed exactly once per deploy; the
   * map stays tiny — one entry per observed definition.
   */
  private final Map<Long, List<GatewayTopology.GatewaySpec>> gatewaysByDefinition =
      new ConcurrentHashMap<>();

  /**
   * How each exclusive gateway's traffic split over its outgoing branches in the range: the
   * deployed model's decision gateways (see {@link GatewayTopology}, cached per definition) joined
   * with the elements cube's per-element activation counts. Share semantics and the multi-inflow
   * over-attribution caveat are documented on {@link BranchDistribution}.
   */
  public List<BranchDistribution> branchDistribution(
      final String bpmnProcessId, final Long fromWindow, final Long toWindow) {
    final TableRow definition = latestDefinition(bpmnProcessId).orElse(null);
    if (definition == null || !(definition.values().get("bpmnXml") instanceof final String xml)) {
      return List.of();
    }
    final long definitionKey = asLong(definition.values().get("processDefinitionKey"));
    final List<GatewayTopology.GatewaySpec> gateways =
        gatewaysByDefinition.computeIfAbsent(definitionKey, key -> GatewayTopology.parse(xml));
    if (gateways.isEmpty()) {
      return List.of();
    }
    // One activations-by-element read serves every gateway (and reuses the rework/hotspot query
    // shape). Missing elements read as 0 — an unexecuted branch is still listed with share 0.
    final Map<String, Long> activationsByElement = new HashMap<>();
    for (final ReportRow row :
        total(
            "elements",
            List.of("elementId"),
            fromWindow,
            toWindow,
            List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
            List.of("activations"),
            newMemo())) {
      activationsByElement.put(elementId(row), measureAsLong(row, "activations"));
    }
    final List<BranchDistribution> out = new ArrayList<>(gateways.size());
    for (final GatewayTopology.GatewaySpec gateway : gateways) {
      final long gatewayActivations = activationsByElement.getOrDefault(gateway.gatewayId(), 0L);
      final List<BranchDistribution.Branch> branches = new ArrayList<>(gateway.branches().size());
      for (final GatewayTopology.BranchSpec branch : gateway.branches()) {
        final long targetActivations = activationsByElement.getOrDefault(branch.targetId(), 0L);
        branches.add(
            new BranchDistribution.Branch(
                branch.targetId(),
                branch.targetLabel(),
                targetActivations,
                gatewayActivations == 0 ? 0.0 : (double) targetActivations / gatewayActivations));
      }
      out.add(
          new BranchDistribution(
              gateway.gatewayId(), gateway.gatewayLabel(), gatewayActivations, branches));
    }
    return out;
  }

  private static long asLong(final Object value) {
    return value instanceof final Number n ? n.longValue() : 0L;
  }

  private static String elementId(final ReportRow row) {
    final Object id = row.dimensions().get("elementId");
    return id == null ? "" : id.toString();
  }

  private static long quantileMs(final QuantileResult result, final double rank) {
    if (result == null || result.count() == 0L) {
      return 0L;
    }
    final double value = result.valueAt(rank);
    return Double.isNaN(value) ? 0L : Math.round(value);
  }

  private static long finestTier(final CompiledDataset dataset) {
    return dataset.finestTier().windowMs();
  }

  /**
   * The bucket granularity for the cohort join of the lifecycle cube and a ratio cube on raw {@code
   * windowStart}: the least common multiple of both datasets' finest tiers. Bucketing each side at
   * its own tier aligns only while the two tiers happen to be equal — if either declaration
   * changes, {@code settled.get(windowStart)} misses every row and the cohorts silently report
   * {@code met = 0}. The LCM is a valid granularity for both cubes (a multiple of each finest tier
   * — the planner invariant) and makes the join keys identical by construction. With no ratio
   * dataset in the catalog the lifecycle side simply buckets at its own finest tier.
   */
  private long cohortJoinGranularity(final CompiledDataset ratioDataset) {
    final long lifecycleMs = finestTier(catalog.require(LIFECYCLE_CUBE));
    if (ratioDataset == null) {
      return lifecycleMs;
    }
    final long ratioMs = finestTier(ratioDataset);
    long a = lifecycleMs;
    long b = ratioMs;
    while (b != 0) {
      final long rest = a % b;
      a = b;
      b = rest;
    }
    return lifecycleMs / a * ratioMs;
  }

  /** A fresh single-call memo, so a per-widget endpoint reuses the same query paths. */
  private static Map<QueryKey, List<ReportRow>> newMemo() {
    return new HashMap<>();
  }

  /**
   * The identity of one serving query within a render. Keyed on the <em>raw</em> widget arguments
   * (a {@code null} bound stays {@code null}) plus the bucket granularity (0 for a total read,
   * whose effective granularity is a function of the other components), so two widgets asking the
   * same question share one result even though the executed query resolves "now" per call.
   */
  private record QueryKey(
      boolean series,
      String dataset,
      List<String> groupBy,
      Long fromWindow,
      Long toWindow,
      List<FilterPredicate> filters,
      List<String> meters,
      long granularityMs) {}

  /** Per-window series: bucket at the cube's finest tier (one output row per stored window). */
  private List<ReportRow> series(
      final String name,
      final List<String> groupBy,
      final Long fromWindow,
      final Long toWindow,
      final List<FilterPredicate> filters,
      final List<String> meters,
      final Map<QueryKey, List<ReportRow>> memo) {
    return series(
        name,
        groupBy,
        fromWindow,
        toWindow,
        filters,
        meters,
        finestTier(catalog.require(name)),
        memo);
  }

  /** Per-bucket series at an explicit granularity (a multiple of the cube's finest tier). */
  private List<ReportRow> series(
      final String name,
      final List<String> groupBy,
      final Long fromWindow,
      final Long toWindow,
      final List<FilterPredicate> filters,
      final List<String> meters,
      final long granularityMs,
      final Map<QueryKey, List<ReportRow>> memo) {
    return memo.computeIfAbsent(
        new QueryKey(true, name, groupBy, fromWindow, toWindow, filters, meters, granularityMs),
        key ->
            executor
                .execute(
                    new ReportQuery(
                        groupBy,
                        fromMs(fromWindow),
                        toMs(toWindow),
                        granularityMs,
                        filters,
                        meters),
                    catalog.require(name))
                .rows());
  }

  /**
   * Single total row: a granularity at-or-above {@code toMs} guarantees one bucket, since every
   * fetched cell has {@code window_start < toMs} and so aligns down to bucket 0. It is {@code toMs}
   * rounded up to the cube's finest tier, which the planner requires the granularity to divide by.
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
        new QueryKey(false, name, groupBy, fromWindow, toWindow, filters, meters, 0L),
        key -> {
          final CompiledDataset dataset = catalog.require(name);
          final long toMs = toMs(toWindow);
          final long finestMs = finestTier(dataset);
          final long granularityMs = ((toMs + finestMs - 1) / finestMs) * finestMs;
          return executor
              .execute(
                  new ReportQuery(
                      groupBy, fromMs(fromWindow), toMs, granularityMs, filters, meters),
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
