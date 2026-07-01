/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.datasketches.common.ArrayOfStringsSerDe;
import org.apache.datasketches.frequencies.ErrorType;
import org.apache.datasketches.frequencies.ItemsSketch;
import org.apache.datasketches.kll.KllDoublesSketch;
import org.apache.datasketches.memory.Memory;
import org.apache.datasketches.quantilescommon.QuantileSearchCriteria;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Read-only queries over the read-optimized serving tables the analytics pipeline writes, for the
 * dashboard. Each row in these tables is the aggregate already merged across source partitions for
 * one (key, window) cell, so the dashboard reads them directly; where a query spans windows it
 * merges only what is exactly mergeable (additive counts) and takes an envelope for percentiles
 * (window percentiles do not re-combine in SQL — the per-window series is the control-chart trend).
 */
@Repository
public class DashboardRepository {

  private static final RowMapper<DurationPercentilePoint> DURATION_MAPPER =
      (rs, n) ->
          new DurationPercentilePoint(
              rs.getLong("window_start"),
              rs.getLong("observation_count"),
              rs.getLong("min_ms"),
              rs.getLong("max_ms"),
              rs.getLong("p50"),
              rs.getLong("p75"),
              rs.getLong("p90"),
              rs.getLong("p99"));

  private static final RowMapper<RatioPoint> RATIO_MAPPER =
      (rs, n) -> {
        final long matched = rs.getLong("matched");
        final long total = rs.getLong("total");
        final double ratio = total == 0L ? 0.0 : (double) matched / total;
        return new RatioPoint(rs.getLong("window_start"), matched, total, ratio, ratio, false);
      };

  private static final RowMapper<DistinctPoint> DISTINCT_MAPPER =
      (rs, n) ->
          new DistinctPoint(
              rs.getLong("window_start"),
              rs.getLong("distinct_estimate"),
              rs.getLong("distinct_lower"),
              rs.getLong("distinct_upper"));

  private static final int TOP_K = 10;
  private static final int TOPK_MAP_SIZE = 256; // matches the pipeline's frequent-items map size
  private static final ArrayOfStringsSerDe STRINGS_SERDE = new ArrayOfStringsSerDe();

  // Time-hierarchy tiers the pipeline writes for the mergeable-sketch metrics. A range read merges
  // the coarsest tier that still tiles the range within the budget, so a wide range costs a handful
  // of sketch blobs instead of thousands of 1m ones (edges snap to the tier — the Grafana pattern).
  private static final long MINUTE_MS = 60_000L;
  private static final long HOUR_MS = 3_600_000L;
  private static final int MAX_MERGE_WINDOWS = 400;

  private static final RowMapper<ElementDuration> ELEMENT_MAPPER =
      (rs, n) ->
          new ElementDuration(
              rs.getString("element_id"),
              rs.getString("element_type"),
              rs.getLong("executed"),
              rs.getLong("avg_ms"),
              rs.getLong("p50"),
              rs.getLong("p90"),
              rs.getLong("max_ms"));

  private final JdbcTemplate jdbc;

  public DashboardRepository(final JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Process ids that any serving table has data for (for the process picker). */
  public List<String> processes() {
    return jdbc.queryForList(
        """
        SELECT bpmn_process_id FROM proc_inst_duration_pctl_window
        UNION SELECT bpmn_process_id FROM proc_inst_exec_time_window
        UNION SELECT bpmn_process_id FROM element_execution_window
        ORDER BY bpmn_process_id
        """,
        String.class);
  }

  /** Tenants that the distinct/top-process rollups have data for. */
  public List<String> tenants() {
    return jdbc.queryForList(
        """
        SELECT tenant_id FROM proc_distinct_window
        UNION SELECT tenant_id FROM top_processes_sketch
        ORDER BY tenant_id
        """,
        String.class);
  }

  /**
   * The per-1m-window duration distribution for a process (control-chart trend), optionally limited
   * to a {@code [from, to]} window range. Each row is exact for its window; no merge needed.
   */
  public List<DurationPercentilePoint> durationPercentiles(
      final String bpmnProcessId, final Long fromWindow, final Long toWindow) {
    final String granularity =
        trendGranularity(
            "proc_inst_duration_pctl_window",
            "bpmn_process_id",
            bpmnProcessId,
            fromWindow,
            toWindow,
            true);
    final StringBuilder sql =
        new StringBuilder(
            "SELECT window_start, SUM(observation_count) AS observation_count,"
                + " MIN(min_duration_ms) AS min_ms, MAX(max_duration_ms) AS max_ms,"
                + " MAX(p50_duration_ms) AS p50, MAX(p75_duration_ms) AS p75,"
                + " MAX(p90_duration_ms) AS p90, MAX(p99_duration_ms) AS p99"
                + " FROM proc_inst_duration_pctl_window"
                + " WHERE bpmn_process_id = ? AND granularity = ?");
    final List<Object> args = new ArrayList<>();
    args.add(bpmnProcessId);
    args.add(granularity);
    appendRange(sql, args, fromWindow, toWindow);
    sql.append(" GROUP BY window_start ORDER BY window_start");
    return jdbc.query(sql.toString(), DURATION_MAPPER, args.toArray());
  }

  /**
   * A single, exact duration distribution for a process over a range (for the KPI tiles). Merges
   * the mergeable KLL sketches — the 1m sketches within {@code [from, to]}, or the all-time {@code
   * total} bucket when no range is given — and derives the quantiles from the merged sketch. This
   * is the "store a distribution, merge over the range, quantile last" pattern (never averaging
   * pre-computed percentiles).
   */
  public DurationPercentilePoint durationSummary(
      final String bpmnProcessId, final Long fromWindow, final Long toWindow) {
    final boolean ranged = fromWindow != null && toWindow != null;
    final StringBuilder sql =
        new StringBuilder(
            "SELECT duration_sketch FROM proc_inst_duration_pctl_window"
                + " WHERE bpmn_process_id = ? AND granularity = ?");
    final List<Object> args = new ArrayList<>();
    args.add(bpmnProcessId);
    args.add(ranged ? granularityFor(fromWindow, toWindow, true) : "total");
    if (ranged) {
      appendRange(sql, args, fromWindow, toWindow);
    }
    final KllDoublesSketch merged = KllDoublesSketch.newHeapInstance();
    jdbc.query(
        sql.toString(),
        (RowCallbackHandler) rs -> mergeInto(merged, rs.getBytes(1)),
        args.toArray());
    final long windowStart = fromWindow == null ? 0L : fromWindow;
    if (merged.isEmpty()) {
      return new DurationPercentilePoint(windowStart, 0, 0, 0, 0, 0, 0, 0);
    }
    return new DurationPercentilePoint(
        windowStart,
        merged.getN(),
        Math.round(merged.getMinItem()),
        Math.round(merged.getMaxItem()),
        quantile(merged, 0.5),
        quantile(merged, 0.75),
        quantile(merged, 0.9),
        quantile(merged, 0.99));
  }

  /**
   * The per-window ratio series for a process and metric ({@code sla_met} / {@code no_incident}).
   */
  public List<RatioPoint> ratios(
      final String bpmnProcessId, final String metric, final Long fromWindow, final Long toWindow) {
    if ("sla_met".equals(metric)) {
      return slaCohortRatios(bpmnProcessId, fromWindow, toWindow);
    }
    final StringBuilder sql =
        new StringBuilder(
            "SELECT window_start, SUM(matched_count) AS matched, SUM(total_count) AS total"
                + " FROM proc_ratio_window WHERE bpmn_process_id = ? AND metric = ?");
    final List<Object> args = new ArrayList<>();
    args.add(bpmnProcessId);
    args.add(metric);
    appendRange(sql, args, fromWindow, toWindow);
    sql.append(" GROUP BY window_start ORDER BY window_start");
    return jdbc.query(sql.toString(), RATIO_MAPPER, args.toArray());
  }

  /**
   * The per-cohort SLA breakdown for the stacked-bar chart: for each START window, how many
   * instances started and how they split into met / breached / still-open. The {@code maturing}
   * window's split can still change as its open instances finish.
   */
  public List<SlaCohortPoint> slaCohorts(
      final String bpmnProcessId, final Long fromWindow, final Long toWindow) {
    final StringBuilder sql =
        new StringBuilder(
            "SELECT window_start, SUM(started_count) AS started, SUM(met_count) AS met,"
                + " SUM(settled_count) AS settled, MAX(window_size_ms) AS wsize, MAX(sla_ms) AS sla"
                + " FROM sla_cohort_window WHERE bpmn_process_id = ?");
    final List<Object> args = new ArrayList<>();
    args.add(bpmnProcessId);
    appendRange(sql, args, fromWindow, toWindow);
    sql.append(" GROUP BY window_start ORDER BY window_start");
    final long now = System.currentTimeMillis();
    return jdbc.query(
        sql.toString(),
        (rs, n) -> {
          final long windowStart = rs.getLong("window_start");
          final long started = rs.getLong("started");
          final long met = rs.getLong("met");
          final long settled = rs.getLong("settled");
          final long breached = Math.max(0L, settled - met); // completed/terminated but missed
          final long open = Math.max(0L, started - settled); // still running, undecided
          final boolean maturing = windowStart + rs.getLong("wsize") + rs.getLong("sla") > now;
          return new SlaCohortPoint(windowStart, started, met, breached, open, maturing);
        },
        args.toArray());
  }

  /**
   * The forward-looking SLA-met series, keyed by START window (cohort): matched = met within
   * target, total = started. A cohort younger than its SLA target is flagged {@code maturing} — its
   * met count may still rise, so its ratio is a lower bound.
   */
  private List<RatioPoint> slaCohortRatios(
      final String bpmnProcessId, final Long fromWindow, final Long toWindow) {
    final StringBuilder sql =
        new StringBuilder(
            "SELECT window_start, SUM(met_count) AS matched, SUM(started_count) AS total,"
                + " SUM(settled_count) AS settled, MAX(window_size_ms) AS wsize, MAX(sla_ms) AS sla"
                + " FROM sla_cohort_window WHERE bpmn_process_id = ?");
    final List<Object> args = new ArrayList<>();
    args.add(bpmnProcessId);
    appendRange(sql, args, fromWindow, toWindow);
    sql.append(" GROUP BY window_start ORDER BY window_start");
    final long now = System.currentTimeMillis();
    return jdbc.query(
        sql.toString(),
        (rs, n) -> {
          final long windowStart = rs.getLong("window_start");
          final long matched = rs.getLong("matched");
          final long total = rs.getLong("total");
          final long settled = rs.getLong("settled");
          final long open = Math.max(0L, total - settled); // still-running, could still meet
          final double lower = total == 0L ? 0.0 : (double) matched / total;
          final double upper = total == 0L ? 0.0 : (double) (matched + open) / total;
          final boolean maturing = windowStart + rs.getLong("wsize") + rs.getLong("sla") > now;
          // once settled, the band collapses to the point; while maturing it spans [lower, upper]
          return new RatioPoint(
              windowStart, matched, total, lower, maturing ? upper : lower, maturing);
        },
        args.toArray());
  }

  /** The per-window distinct-process estimate for a tenant. */
  public List<DistinctPoint> distinct(
      final String tenantId, final Long fromWindow, final Long toWindow) {
    // Distinct's finest tier is hourly; the trend granularity adapts to the history in range.
    final String granularity =
        trendGranularity(
            "proc_distinct_window", "tenant_id", tenantId, fromWindow, toWindow, false);
    final StringBuilder sql =
        new StringBuilder(
            "SELECT window_start, distinct_estimate, distinct_lower, distinct_upper"
                + " FROM proc_distinct_window WHERE tenant_id = ? AND granularity = ?");
    final List<Object> args = new ArrayList<>();
    args.add(tenantId);
    args.add(granularity);
    appendRange(sql, args, fromWindow, toWindow);
    sql.append(" ORDER BY window_start");
    return jdbc.query(sql.toString(), DISTINCT_MAPPER, args.toArray());
  }

  /** The latest window's ranked top processes for a tenant. */
  /**
   * Top processes by volume for a tenant over an optional range. Merges the frequent-items sketches
   * — the 1m sketches within {@code [from, to]}, or the all-time {@code total} bucket when no range
   * is given — and derives the heavy-hitter ranking from the merged sketch (ranking computed last).
   */
  public List<TopProcess> topProcesses(
      final String tenantId, final Long fromWindow, final Long toWindow) {
    final boolean ranged = fromWindow != null && toWindow != null;
    final StringBuilder sql =
        new StringBuilder(
            "SELECT items_sketch FROM top_processes_sketch WHERE tenant_id = ? AND granularity = ?");
    final List<Object> args = new ArrayList<>();
    args.add(tenantId);
    args.add(ranged ? granularityFor(fromWindow, toWindow, true) : "total");
    if (ranged) {
      appendRange(sql, args, fromWindow, toWindow);
    }
    final ItemsSketch<String> merged = new ItemsSketch<>(TOPK_MAP_SIZE);
    jdbc.query(
        sql.toString(),
        (RowCallbackHandler)
            rs -> {
              final byte[] bytes = rs.getBytes(1);
              if (bytes != null && bytes.length > 0) {
                merged.merge(ItemsSketch.getInstance(Memory.wrap(bytes), STRINGS_SERDE));
              }
            },
        args.toArray());

    final ItemsSketch.Row<String>[] rows = merged.getFrequentItems(ErrorType.NO_FALSE_POSITIVES);
    final List<TopProcess> out = new ArrayList<>();
    for (int i = 0; i < rows.length && i < TOP_K; i++) {
      out.add(
          new TopProcess(
              i + 1,
              rows[i].getItem(),
              rows[i].getEstimate(),
              rows[i].getLowerBound(),
              rows[i].getUpperBound()));
    }
    return out;
  }

  /** Current in-flight instance count for a tenant (range-independent gauge), summed over defs. */
  public long activeInstances(final String tenantId) {
    final Long n =
        jdbc.queryForObject(
            "SELECT COALESCE(SUM(active_count), 0) FROM active_instances WHERE tenant_id = ?",
            Long.class,
            tenantId);
    return n == null ? 0L : n;
  }

  /**
   * Instances started (activated) for a process over an optional range — additive sum of windows.
   */
  public long activatedInstances(
      final String bpmnProcessId, final Long fromWindow, final Long toWindow) {
    final StringBuilder sql =
        new StringBuilder(
            "SELECT COALESCE(SUM(activated_count), 0) FROM activated_instances_window"
                + " WHERE bpmn_process_id = ?");
    final List<Object> args = new ArrayList<>();
    args.add(bpmnProcessId);
    appendRange(sql, args, fromWindow, toWindow);
    final Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
    return n == null ? 0L : n;
  }

  /** The latest deployed version's BPMN XML for a process, for rendering the diagram heatmap. */
  public Optional<String> diagramXml(final String bpmnProcessId) {
    return jdbc
        .query(
            "SELECT bpmn_xml FROM process_definition WHERE bpmn_process_id = ?"
                + " ORDER BY version DESC LIMIT 1",
            (rs, n) -> rs.getString("bpmn_xml"),
            bpmnProcessId)
        .stream()
        .findFirst();
  }

  /**
   * Per-element duration summary for a process. Count/avg/max are the exact all-time aggregate over
   * {@code element_execution_window}; the p50/p90 come from the pipeline's {@code
   * total}-granularity percentile bucket — one exact all-time row per element, so no cross-window
   * merge or envelope.
   */
  public List<ElementDuration> elementDurations(
      final String bpmnProcessId, final Long fromWindow, final Long toWindow) {
    final boolean ranged = fromWindow != null && toWindow != null;

    // exact additive aggregates per element (count / avg / max)
    final StringBuilder execSql =
        new StringBuilder(
            "SELECT element_id, MIN(element_type) AS et, SUM(executed_count) AS ex,"
                + " SUM(total_duration_ms) AS tot, MAX(max_duration_ms) AS mx"
                + " FROM element_execution_window WHERE bpmn_process_id = ?");
    final List<Object> execArgs = new ArrayList<>();
    execArgs.add(bpmnProcessId);
    appendRange(execSql, execArgs, fromWindow, toWindow);
    execSql.append(" GROUP BY element_id ORDER BY ex DESC, element_id");

    final Map<String, String> types = new LinkedHashMap<>();
    final Map<String, long[]> exec = new LinkedHashMap<>(); // element -> {executed, total, max}
    jdbc.query(
        execSql.toString(),
        (RowCallbackHandler)
            rs -> {
              final String id = rs.getString("element_id");
              types.put(id, rs.getString("et"));
              exec.put(id, new long[] {rs.getLong("ex"), rs.getLong("tot"), rs.getLong("mx")});
            },
        execArgs.toArray());

    // merge the per-element sketches over the range (or the total bucket) — quantile computed last
    final StringBuilder skSql =
        new StringBuilder(
            "SELECT element_id, duration_sketch FROM element_duration_pctl_window"
                + " WHERE bpmn_process_id = ? AND granularity = ?");
    final List<Object> skArgs = new ArrayList<>();
    skArgs.add(bpmnProcessId);
    skArgs.add(ranged ? granularityFor(fromWindow, toWindow, true) : "total");
    if (ranged) {
      appendRange(skSql, skArgs, fromWindow, toWindow);
    }
    final Map<String, KllDoublesSketch> sketches = new LinkedHashMap<>();
    jdbc.query(
        skSql.toString(),
        (RowCallbackHandler)
            rs ->
                mergeInto(
                    sketches.computeIfAbsent(
                        rs.getString("element_id"), k -> KllDoublesSketch.newHeapInstance()),
                    rs.getBytes("duration_sketch")),
        skArgs.toArray());

    final List<ElementDuration> out = new ArrayList<>();
    for (final Map.Entry<String, long[]> e : exec.entrySet()) {
      final long[] a = e.getValue();
      final long avg = a[0] == 0 ? 0 : a[1] / a[0];
      final KllDoublesSketch s = sketches.get(e.getKey());
      final long p50 = s == null ? 0 : quantile(s, 0.5);
      final long p90 = s == null ? 0 : quantile(s, 0.9);
      out.add(new ElementDuration(e.getKey(), types.get(e.getKey()), a[0], avg, p50, p90, a[2]));
    }
    return out;
  }

  /**
   * The coarsest time-hierarchy tier that resolves {@code [from, to]} while merging at most {@link
   * #MAX_MERGE_WINDOWS} windows. {@code minuteTier} is false for distinct, whose finest tier is
   * hourly.
   */
  private static String granularityFor(final long from, final long to, final boolean minuteTier) {
    final long span = Math.max(0L, to - from);
    if (minuteTier && span <= MINUTE_MS * MAX_MERGE_WINDOWS) {
      return "1m";
    }
    if (span <= HOUR_MS * MAX_MERGE_WINDOWS) {
      return "1h";
    }
    return "1d";
  }

  /**
   * Trend granularity for a control-chart series, chosen from the data that actually exists within
   * {@code [from, to]} rather than the nominal span. A wide range over little history stays a dense
   * fine-grained trend (its rows are few and cheap) instead of collapsing to one coarse point,
   * while a range full of history still steps up to a coarser tier to bound the chart's point
   * count. A cheap indexed MIN/MAX over the finest tier finds the real extent.
   */
  private String trendGranularity(
      final String table,
      final String keyColumn,
      final String keyValue,
      final Long from,
      final Long to,
      final boolean minuteTier) {
    final String finest = minuteTier ? "1m" : "1h";
    final StringBuilder sql =
        new StringBuilder(
            "SELECT MIN(window_start) AS mn, MAX(window_start) AS mx FROM "
                + table
                + " WHERE "
                + keyColumn
                + " = ? AND granularity = ?");
    final List<Object> args = new ArrayList<>();
    args.add(keyValue);
    args.add(finest);
    appendRange(sql, args, from, to);
    final long[] extent = new long[] {-1L, -1L};
    jdbc.query(
        sql.toString(),
        (RowCallbackHandler)
            rs -> {
              final Object mn = rs.getObject("mn");
              final Object mx = rs.getObject("mx");
              if (mn != null && mx != null) {
                extent[0] = ((Number) mn).longValue();
                extent[1] = ((Number) mx).longValue();
              }
            },
        args.toArray());
    if (extent[0] < 0) {
      return finest; // no data in range — nothing to coarsen
    }
    return granularityFor(extent[0], extent[1], minuteTier);
  }

  /**
   * Incidents per flow node for a process: {@code raised} counts incidents created in the range
   * (incidents, not instances — multiple per instance all count); {@code open} is the current
   * created−resolved gauge. Left-joined so a flow node with only open (or only raised) still shows.
   */
  public List<IncidentFlowNode> incidents(
      final String bpmnProcessId, final Long fromWindow, final Long toWindow) {
    final Map<String, long[]> byElement = new LinkedHashMap<>(); // element -> {raised, open}
    final StringBuilder raisedSql =
        new StringBuilder(
            "SELECT element_id, SUM(incident_count) AS raised FROM incident_frequency_window"
                + " WHERE bpmn_process_id = ?");
    final List<Object> raisedArgs = new ArrayList<>();
    raisedArgs.add(bpmnProcessId);
    appendRange(raisedSql, raisedArgs, fromWindow, toWindow);
    raisedSql.append(" GROUP BY element_id");
    jdbc.query(
        raisedSql.toString(),
        (RowCallbackHandler)
            rs ->
                byElement.computeIfAbsent(rs.getString("element_id"), k -> new long[2])[0] =
                    rs.getLong("raised"),
        raisedArgs.toArray());
    jdbc.query(
        "SELECT element_id, open_count FROM open_incidents WHERE bpmn_process_id = ?",
        (RowCallbackHandler)
            rs ->
                byElement.computeIfAbsent(rs.getString("element_id"), k -> new long[2])[1] =
                    rs.getLong("open_count"),
        bpmnProcessId);
    final List<IncidentFlowNode> out = new ArrayList<>();
    for (final Map.Entry<String, long[]> e : byElement.entrySet()) {
      out.add(new IncidentFlowNode(e.getKey(), e.getValue()[0], e.getValue()[1]));
    }
    out.sort((a, b) -> Long.compare(b.raised(), a.raised()));
    return out;
  }

  /** Currently-open incident count for a process (sum of the per-flow-node gauge). */
  public long openIncidents(final String bpmnProcessId) {
    final Long n =
        jdbc.queryForObject(
            "SELECT COALESCE(SUM(open_count), 0) FROM open_incidents WHERE bpmn_process_id = ?",
            Long.class,
            bpmnProcessId);
    return n == null ? 0L : n;
  }

  /** Appends optional {@code window_start} range predicates and their bind args. */
  private static void appendRange(
      final StringBuilder sql, final List<Object> args, final Long from, final Long to) {
    if (from != null) {
      sql.append(" AND window_start >= ?");
      args.add(from);
    }
    if (to != null) {
      sql.append(" AND window_start <= ?");
      args.add(to);
    }
  }

  /** Merges serialized KLL sketch bytes into an accumulator (no-op for null/empty). */
  private static void mergeInto(final KllDoublesSketch accumulator, final byte[] sketchBytes) {
    if (sketchBytes != null && sketchBytes.length > 0) {
      accumulator.merge(KllDoublesSketch.heapify(Memory.wrap(sketchBytes)));
    }
  }

  private static long quantile(final KllDoublesSketch sketch, final double rank) {
    return sketch.isEmpty()
        ? 0L
        : Math.round(sketch.getQuantile(rank, QuantileSearchCriteria.INCLUSIVE));
  }
}
