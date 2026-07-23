/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.planner;

import io.camunda.analytics.lake.serving.catalog.EntityCatalog;
import io.camunda.analytics.lake.serving.catalog.MetricRegistry;
import io.camunda.analytics.lake.serving.config.LakeServingProperties;
import io.camunda.analytics.lake.serving.duckdb.LakeQueryService;
import io.camunda.analytics.lake.serving.duckdb.LakeQueryService.QueryResult;
import io.camunda.analytics.lake.serving.duckdb.LakeViewRegistry;
import io.camunda.analytics.lake.serving.sql.FilterClause;
import io.camunda.analytics.lake.serving.sql.SqlText;
import io.camunda.analytics.lake.serving.tools.ChangepointService;
import io.camunda.analytics.lake.serving.tools.ChangepointService.ChangepointResult;
import io.camunda.analytics.lake.serving.tools.CohortCompareQuery;
import io.camunda.analytics.lake.serving.tools.CohortCompareService;
import io.camunda.analytics.lake.serving.tools.CohortCompareService.CohortCompareResult;
import io.camunda.analytics.lake.serving.tools.CohortCompareService.CohortCompareRow;
import io.camunda.analytics.lake.serving.tools.CohortSpec;
import io.camunda.analytics.lake.serving.tools.DecomposeQuery;
import io.camunda.analytics.lake.serving.tools.DecomposeService;
import io.camunda.analytics.lake.serving.tools.DecomposeService.DecomposeResult;
import io.camunda.analytics.lake.serving.tools.DecomposeService.DecomposeRow;
import io.camunda.analytics.lake.serving.tools.ExemplarsQuery;
import io.camunda.analytics.lake.serving.tools.ExemplarsService;
import io.camunda.analytics.lake.serving.tools.ExemplarsService.ExemplarsResult;
import io.camunda.analytics.lake.serving.tools.ScreenService;
import io.camunda.analytics.lake.serving.tools.ScreenService.ScreenResult;
import io.camunda.analytics.lake.serving.tools.ScreenService.ScreenRow;
import io.camunda.analytics.lake.serving.tools.SeriesQuery;
import io.camunda.analytics.lake.serving.tools.TimeRange;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * {@code POST /api/investigate}: runs the fixed rung sequence (changepoint → decompose-every-dim →
 * screen-auto → cohort-compare-auto → exemplars-for-top-cohort) over one series, translating each
 * rung's raw tool output into {@link Finding}s the UI can render without re-deriving anything.
 *
 * <h2>Finding kinds and claim shapes (pinned by the UI lane's contract addendum)</h2>
 *
 * <ul>
 *   <li>{@code CHANGEPOINT} (rung 0, always present): {@code claim = {shape, before, after,
 *       confidence}}.
 *   <li>{@code DIMENSION_DRIVER} (rung 1, one per dim, top row only): {@code claim = {dim, value,
 *       contributionShare}}.
 *   <li>{@code SCREEN_CORRELATION} (rung 2, up to {@link #MAX_SCREEN_FINDINGS}): {@code claim =
 *       {series, shiftSlots, correlation}}.
 *   <li>{@code COHORT_ATTRIBUTE} (rung 3, up to {@link #MAX_COHORT_FINDINGS}): {@code claim =
 *       {attribute, bucket, slowShare, fastShare, lift}}.
 *   <li>{@code SCAN_DEFERRED} (rung 3 fallback): {@code claim = {estimatedRows}}.
 * </ul>
 *
 * <h2>Rung 3 scope (cohort-compare only ever targets {@code instances})</h2>
 *
 * <p>{@code cohort-compare} (see {@link CohortCompareService}) is v1-restricted to the {@code
 * instances} raw view. When the investigated {@code entity} isn't {@code instances}, rung 3 (and
 * rung 4) is skipped entirely — no finding, not even {@code SCAN_DEFERRED} — since there is no
 * well-defined cohort scan to defer. When it is, the "slow" cohort predicate is synthesized (never
 * supplied by the caller, since {@code POST /api/investigate}'s own request has no {@code cohort}
 * field): a {@link CohortSpec.WindowSplit} at the rung-0 changepoint's {@code at} when one exists,
 * else a {@link CohortSpec.Threshold} on the investigated {@code measure} at the midpoint of the
 * changepoint algorithm's own best-split before/after values (a reasonable central estimate without
 * a second query) — when neither is available (too few points for any changepoint candidate, and no
 * measure to threshold on), rung 3 is skipped.
 *
 * <h2>Rung 4 (exemplars)</h2>
 *
 * <p>{@code POST /api/tools/exemplars} only accepts a {@code cohort} predicate, not an arbitrary
 * attribute filter — so it cannot be narrowed to "instances where {@code variant_hash = X}" for the
 * specific top {@code COHORT_ATTRIBUTE} finding's bucket. Rung 4 instead re-runs exemplars for the
 * <em>same</em> slow-cohort predicate rung 3 already used, and attaches the result to the top
 * cohort finding's {@link Finding#exemplars()} rather than inventing an unspecified extra parameter
 * on the exemplars tool.
 *
 * <h2>Ranking</h2>
 *
 * <p>{@code CHANGEPOINT} always leads (it's the anchor/context finding, reported even when {@code
 * shape == NONE}); {@code SCAN_DEFERRED} always trails (informational, not a ranked claim). Every
 * other finding is sorted by {@code effect * support} descending; findings whose {@code effect} is
 * below {@link #MIN_EFFECT} are omitted, and rung 1/2/3 each cap how many findings they contribute
 * ({@link #MAX_SCREEN_FINDINGS}, {@link #MAX_COHORT_FINDINGS}) to keep the list readable.
 */
@Service
public class InvestigateService {

  private static final double MIN_EFFECT = 0.05;
  private static final int MAX_SCREEN_FINDINGS = 5;
  private static final int MAX_COHORT_FINDINGS = 5;
  private static final int TARGET_POINTS = 200;
  private static final int[] GRAIN_CANDIDATES_MINUTES = {1, 5, 15, 60, 240, 1440};
  private static final String INSTANCES = "instances";

  private final MetricRegistry metricRegistry;
  private final LakeViewRegistry viewRegistry;
  private final LakeQueryService queryService;
  private final ChangepointService changepointService;
  private final DecomposeService decomposeService;
  private final ScreenService screenService;
  private final CohortCompareService cohortCompareService;
  private final ExemplarsService exemplarsService;
  private final long maxExplainScanRows;

  public InvestigateService(
      final MetricRegistry metricRegistry,
      final LakeViewRegistry viewRegistry,
      final LakeQueryService queryService,
      final ChangepointService changepointService,
      final DecomposeService decomposeService,
      final ScreenService screenService,
      final CohortCompareService cohortCompareService,
      final ExemplarsService exemplarsService,
      final LakeServingProperties properties) {
    this.metricRegistry = metricRegistry;
    this.viewRegistry = viewRegistry;
    this.queryService = queryService;
    this.changepointService = changepointService;
    this.decomposeService = decomposeService;
    this.screenService = screenService;
    this.cohortCompareService = cohortCompareService;
    this.exemplarsService = exemplarsService;
    maxExplainScanRows = properties.maxExplainScanRows();
  }

  public InvestigateResult investigate(final InvestigateQuery query) {
    final EntityCatalog entity = metricRegistry.require(query.entity());
    final int grainMinutes = defaultGrainMinutes(query.from(), query.to());
    final SeriesQuery baseSeries =
        new SeriesQuery(
            query.entity(),
            query.measure(),
            query.quantile(),
            query.filters(),
            query.from(),
            query.to(),
            grainMinutes);

    final List<Finding> middle = new ArrayList<>();
    int nextId = 0;

    // Rung 0 -- always present, always first.
    final ChangepointResult changepoint = changepointService.changepoint(baseSeries);
    final Finding changepointFinding = changepointFinding("f" + nextId++, baseSeries, changepoint);

    // Rung 1 -- decompose every dim.
    final TimeRange window;
    final TimeRange baseline;
    if ("STEP".equals(changepoint.shape()) && changepoint.at() != null) {
      window = new TimeRange(changepoint.at(), query.to());
      baseline = new TimeRange(query.from(), changepoint.at());
    } else {
      final String mid = midpoint(query.from(), query.to());
      window = new TimeRange(mid, query.to());
      baseline = new TimeRange(query.from(), mid);
    }
    for (final String dim : entity.dimNames()) {
      final DecomposeQuery decomposeQuery =
          new DecomposeQuery(
              query.entity(),
              query.measure(),
              query.quantile(),
              query.filters(),
              window,
              baseline,
              dim);
      final DecomposeResult decomposeResult = decomposeService.decompose(decomposeQuery);
      if (decomposeResult.rows().isEmpty()) {
        continue;
      }
      final DecomposeRow top = decomposeResult.rows().get(0);
      final double effect = Math.abs(top.contributionShare());
      if (effect < MIN_EFFECT) {
        continue;
      }
      middle.add(
          new Finding(
              "f" + nextId++,
              1,
              "DIMENSION_DRIVER",
              Map.of(
                  "dim", dim, "value", top.value(), "contributionShare", top.contributionShare()),
              Map.of(
                  "current",
                  top.current(),
                  "baseline",
                  top.baseline(),
                  "delta",
                  top.delta(),
                  "contributionShare",
                  top.contributionShare()),
              top.weight(),
              effect,
              "decompose",
              decomposeQuery,
              decomposeResult.sql(),
              null));
    }

    // Rung 2 -- screen auto.
    final TimeRange fullWindow = new TimeRange(query.from(), query.to());
    final ScreenResult screen = screenService.screen(baseSeries, fullWindow, null);
    int screenAdded = 0;
    for (final ScreenRow row : screen.rows()) {
      if (screenAdded >= MAX_SCREEN_FINDINGS) {
        break;
      }
      final double effect = Math.abs(row.correlation());
      if (effect < MIN_EFFECT) {
        continue;
      }
      middle.add(
          new Finding(
              "f" + nextId++,
              2,
              "SCREEN_CORRELATION",
              Map.of(
                  "series",
                  row.series(),
                  "shiftSlots",
                  row.shiftSlots(),
                  "correlation",
                  row.correlation()),
              Map.of("shiftSlots", row.shiftSlots(), "correlation", row.correlation()),
              1.0,
              effect,
              "screen",
              Map.of("targetSeries", baseSeries, "window", fullWindow, "candidates", "auto"),
              screen.sql(),
              null));
      screenAdded++;
    }

    // Rung 3 -- cohort-compare auto (instances only) + rung 4 exemplars for the top finding.
    final CohortRungOutcome cohortOutcome =
        INSTANCES.equals(query.entity())
            ? runCohortRung(entity, query, changepoint, nextId)
            : new CohortRungOutcome(List.of(), null, nextId);
    middle.addAll(cohortOutcome.findings());
    final Finding scanDeferred = cohortOutcome.scanDeferred();

    middle.sort((a, b) -> Double.compare(b.effect() * b.support(), a.effect() * a.support()));
    final List<Finding> findings = new ArrayList<>();
    findings.add(changepointFinding);
    findings.addAll(middle);
    if (scanDeferred != null) {
      findings.add(scanDeferred);
    }
    return new InvestigateResult(query, findings);
  }

  private Finding changepointFinding(
      final String id, final SeriesQuery series, final ChangepointResult changepoint) {
    final double scale =
        changepoint.before() != null ? Math.max(Math.abs(changepoint.before()), 1e-9) : 1e-9;
    final double effect =
        changepoint.before() != null && changepoint.after() != null
            ? Math.abs(changepoint.after() - changepoint.before()) / scale
            : 0.0;
    return new Finding(
        id,
        0,
        "CHANGEPOINT",
        mapOf(
            "shape",
            changepoint.shape(),
            "before",
            changepoint.before(),
            "after",
            changepoint.after(),
            "confidence",
            changepoint.confidence()),
        mapOf(
            "before",
            changepoint.before(),
            "after",
            changepoint.after(),
            "confidence",
            changepoint.confidence()),
        1.0,
        effect,
        "changepoint",
        series,
        changepoint.sql(),
        null);
  }

  private CohortSpec synthesizeCohort(
      final InvestigateQuery query, final ChangepointResult changepoint) {
    if (changepoint.at() != null && !"NONE".equals(changepoint.shape())) {
      return new CohortSpec.WindowSplit(changepoint.at());
    }
    if (query.measure() != null && changepoint.before() != null && changepoint.after() != null) {
      final double threshold = (changepoint.before() + changepoint.after()) / 2.0;
      return new CohortSpec.Threshold(query.measure(), ">", threshold);
    }
    return null;
  }

  /**
   * Rungs 3+4, factored out of {@link #investigate} to keep nesting flat -- see that method's
   * javadoc.
   */
  private CohortRungOutcome runCohortRung(
      final EntityCatalog entity,
      final InvestigateQuery query,
      final ChangepointResult changepoint,
      final int startId) {
    final CohortSpec cohort = synthesizeCohort(query, changepoint);
    if (cohort == null) {
      return new CohortRungOutcome(List.of(), null, startId);
    }
    final Long estimatedRows = estimateRows(entity, query.filters(), query.from(), query.to());
    if (estimatedRows != null && estimatedRows > maxExplainScanRows) {
      final CohortCompareQuery deferredParams =
          new CohortCompareQuery(
              INSTANCES, cohort, query.filters(), query.from(), query.to(), null, null);
      final Finding scanDeferred =
          new Finding(
              "f" + startId,
              3,
              "SCAN_DEFERRED",
              Map.of("estimatedRows", estimatedRows),
              Map.of("estimatedRows", estimatedRows),
              0.0,
              0.0,
              "cohort-compare",
              deferredParams,
              List.of(),
              null);
      return new CohortRungOutcome(List.of(), scanDeferred, startId + 1);
    }

    // The raw `instances` view is a genuinely optional table (appears once analytics-lake has
    // flushed it) -- unlike a direct POST /api/tools/cohort-compare call (which SHOULD error
    // loudly if a caller explicitly names a missing entity), this automatic rung must degrade to
    // "no rung-3 finding" rather than fail rungs 0-2's already-good results just because the raw
    // scan isn't possible yet. This check only guards the actual scan below -- the SCAN_DEFERRED
    // estimate above reads only the (always-required) `_metrics` view, never the raw table.
    if (!viewRegistry.ensureAvailable(INSTANCES)) {
      return new CohortRungOutcome(List.of(), null, startId);
    }

    final CohortCompareQuery cohortQuery =
        new CohortCompareQuery(
            INSTANCES, cohort, query.filters(), query.from(), query.to(), null, null);
    final CohortCompareResult cohortResult = cohortCompareService.compare(cohortQuery);
    final List<Finding> cohortFindings = new ArrayList<>();
    int id = startId;
    int added = 0;
    for (final CohortCompareRow row : cohortResult.rows()) {
      if (added >= MAX_COHORT_FINDINGS) {
        break;
      }
      final Finding finding = cohortFinding("f" + id, row, cohortQuery, cohortResult.sql());
      if (finding == null) {
        continue;
      }
      id++;
      added++;
      cohortFindings.add(finding);
    }
    if (!cohortFindings.isEmpty()) {
      cohortFindings.sort(
          (a, b) -> Double.compare(b.effect() * b.support(), a.effect() * a.support()));
      cohortFindings.set(0, attachExemplars(cohortFindings.get(0), cohort));
    }
    return new CohortRungOutcome(cohortFindings, null, id);
  }

  private Finding cohortFinding(
      final String id,
      final CohortCompareRow row,
      final CohortCompareQuery cohortQuery,
      final List<String> sql) {
    final double effect = row.lift() == null ? 1.0 : Math.abs(row.lift() - 1);
    if (effect < MIN_EFFECT) {
      return null;
    }
    final double lift = row.lift() == null ? 0.0 : row.lift();
    return new Finding(
        id,
        3,
        "COHORT_ATTRIBUTE",
        Map.of(
            "attribute",
            row.attribute(),
            "bucket",
            row.bucket(),
            "slowShare",
            row.slowShare(),
            "fastShare",
            row.fastShare(),
            "lift",
            lift),
        Map.of("slowShare", row.slowShare(), "fastShare", row.fastShare(), "lift", lift),
        row.slowShare(),
        effect,
        "cohort-compare",
        cohortQuery,
        sql,
        null);
  }

  private Finding attachExemplars(final Finding topCohort, final CohortSpec cohort) {
    final ExemplarsQuery exemplarsQuery = new ExemplarsQuery(INSTANCES, cohort, 3);
    final ExemplarsResult exemplarsResult = exemplarsService.exemplars(exemplarsQuery);
    final List<String> combinedSql = new ArrayList<>(topCohort.sql());
    combinedSql.addAll(exemplarsResult.sql());
    return new Finding(
        topCohort.id(),
        topCohort.rung(),
        topCohort.kind(),
        topCohort.claim(),
        topCohort.numbers(),
        topCohort.support(),
        topCohort.effect(),
        topCohort.tool(),
        topCohort.toolParams(),
        combinedSql,
        exemplarsResult.rows());
  }

  private Long estimateRows(
      final EntityCatalog entity,
      final Map<String, Object> filters,
      final String from,
      final String to) {
    final String countExpr;
    if (entity.hasCnt()) {
      countExpr = "SUM(cnt)";
    } else if (!entity.measures().isEmpty()) {
      countExpr = "SUM(" + SqlText.identifier(entity.measures().get(0).name() + "_cnt") + ")";
    } else {
      return null;
    }
    if (!viewRegistry.ensureAvailable(entity.metricsView())) {
      return null;
    }
    final String filterSql = FilterClause.toSql(filters, entity.dimNames());
    final String sql =
        "SELECT "
            + countExpr
            + " FROM "
            + SqlText.identifier(entity.metricsView())
            + " WHERE window_start >= "
            + SqlText.timestamptzLiteral(from)
            + " AND window_start < "
            + SqlText.timestamptzLiteral(to)
            + " AND "
            + filterSql;
    try {
      final QueryResult result = queryService.execute(sql);
      if (result.rows().isEmpty() || result.rows().get(0).get(0) == null) {
        return 0L;
      }
      return ((Number) result.rows().get(0).get(0)).longValue();
    } catch (final SQLException e) {
      return null;
    }
  }

  private static int defaultGrainMinutes(final String from, final String to) {
    final Instant fromInstant = SqlText.parseInstant(from);
    final Instant toInstant = SqlText.parseInstant(to);
    final long spanMinutes = Math.max(1, Duration.between(fromInstant, toInstant).toMinutes());
    for (final int grain : GRAIN_CANDIDATES_MINUTES) {
      if (spanMinutes / grain <= TARGET_POINTS) {
        return grain;
      }
    }
    return GRAIN_CANDIDATES_MINUTES[GRAIN_CANDIDATES_MINUTES.length - 1];
  }

  private static String midpoint(final String from, final String to) {
    final Instant fromInstant = SqlText.parseInstant(from);
    final Instant toInstant = SqlText.parseInstant(to);
    final Duration half = Duration.between(fromInstant, toInstant).dividedBy(2);
    return fromInstant.plus(half).toString();
  }

  private static Map<String, Object> mapOf(
      final String k1,
      final Object v1,
      final String k2,
      final Object v2,
      final String k3,
      final Object v3) {
    final Map<String, Object> map = new LinkedHashMap<>();
    map.put(k1, v1);
    map.put(k2, v2);
    map.put(k3, v3);
    return map;
  }

  private static Map<String, Object> mapOf(
      final String k1,
      final Object v1,
      final String k2,
      final Object v2,
      final String k3,
      final Object v3,
      final String k4,
      final Object v4) {
    final Map<String, Object> map = new LinkedHashMap<>();
    map.put(k1, v1);
    map.put(k2, v2);
    map.put(k3, v3);
    map.put(k4, v4);
    return map;
  }

  /** {@code POST /api/investigate} response. */
  public record InvestigateResult(InvestigateQuery spec, List<Finding> findings) {}

  /** Rung 3+4 outcome: either some {@code COHORT_ATTRIBUTE} findings, or a single deferred one. */
  private record CohortRungOutcome(List<Finding> findings, Finding scanDeferred, int nextId) {}
}
