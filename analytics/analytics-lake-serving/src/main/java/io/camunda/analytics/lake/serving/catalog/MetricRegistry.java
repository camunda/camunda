/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.catalog;

import io.camunda.analytics.lake.serving.config.LakeServingProperties;
import io.camunda.analytics.lake.serving.duckdb.LakeQueryService;
import io.camunda.analytics.lake.serving.duckdb.LakeViewRegistry;
import io.camunda.analytics.lake.serving.sql.SqlText;
import io.camunda.analytics.lake.serving.sql.ViewDescribe;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Derives the entity/dim/measure catalog purely from the discovered views' own schemas — never from
 * a hardcoded table/column list, so a new entity a lane adds to {@code analytics-lake} shows up
 * here the moment its partials view is registered, with no code change on this side.
 *
 * <h2>Derivation rule</h2>
 *
 * <p>An entity is any pair of discovered views named {@code <entity>_metrics}/{@code <entity>_hist}
 * (either may be absent). For the {@code _metrics} view, every column is classified by name alone
 * (via {@code DESCRIBE}, which preserves physical column order — see this class's tests):
 *
 * <ul>
 *   <li>{@code window_start} is always the time bucket column; never a dim, never a measure.
 *   <li>a bare column named exactly {@code cnt} is the entity's plain row count ({@link
 *       EntityCatalog#hasCnt()}).
 *   <li>every other column is tested against the suffixes {@code _nonfinite_cnt}, {@code _sum},
 *       {@code _min}, {@code _max}, {@code _cnt} (checked in that order — {@code _nonfinite_cnt}
 *       first so it isn't mis-split as a {@code _cnt} column with a {@code _nonfinite} prefix),
 *       grouping same-prefix columns together.
 *   <li>a prefix group that includes a {@code _sum} column is a real measure ({@link
 *       MeasureCatalog}); a group with only a {@code _cnt} column (no {@code _sum} sibling) is a
 *       named counter (e.g. {@code type_number_cnt}), listed by its bare prefix in {@link
 *       EntityCatalog#counters()}.
 *   <li>everything left over is a dim — this is deliberately column-order-independent (robust to
 *       {@code window_start} coming before or after the dims physically) since it depends only on
 *       exclusion, not position.
 * </ul>
 *
 * <p>The {@code _hist} view (when present) contributes only {@link EntityCatalog#hasHistTable()}
 * plus, per already-known measure, {@link MeasureCatalog#hasHist()} — read from its own {@code
 * measure} column's distinct values, never assumed.
 */
@Service
public class MetricRegistry {

  private static final String WINDOW_START = "window_start";
  private static final String BARE_CNT = "cnt";
  private static final List<String> MEASURE_SUFFIXES_IN_PRIORITY_ORDER =
      List.of("_nonfinite_cnt", "_sum", "_min", "_max", "_cnt");
  private static final String METRICS_SUFFIX = "_metrics";
  private static final String HIST_SUFFIX = "_hist";

  private final LakeViewRegistry viewRegistry;
  private final LakeQueryService queryService;
  private final Map<String, String> dimKinds;

  public MetricRegistry(
      final LakeViewRegistry viewRegistry,
      final LakeQueryService queryService,
      final LakeServingProperties properties) {
    this.viewRegistry = viewRegistry;
    this.queryService = queryService;
    dimKinds = properties.effectiveDimKinds();
  }

  /** Every entity discovered from the currently registered views, in view-discovery order. */
  public List<EntityCatalog> entities() {
    final List<String> views = viewRegistry.registeredTables();
    final Set<String> viewSet = Set.copyOf(views);
    final LinkedHashSet<String> entityNames = new LinkedHashSet<>();
    for (final String view : views) {
      if (view.endsWith(METRICS_SUFFIX)) {
        entityNames.add(view.substring(0, view.length() - METRICS_SUFFIX.length()));
      } else if (view.endsWith(HIST_SUFFIX)) {
        entityNames.add(view.substring(0, view.length() - HIST_SUFFIX.length()));
      }
    }
    final List<EntityCatalog> catalog = new ArrayList<>();
    for (final String entityName : entityNames) {
      catalog.add(describeEntity(entityName, viewSet));
    }
    return catalog;
  }

  /** The effective (default-merged) dim-name -> semantic-kind overlay. */
  public Map<String, String> dimKinds() {
    return dimKinds;
  }

  /**
   * The named entity's catalog, or throws {@link IllegalArgumentException} (translated to a {@code
   * 400}) if it isn't currently discoverable.
   */
  public EntityCatalog require(final String entityName) {
    return entities().stream()
        .filter(e -> e.name().equals(entityName))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Unknown entity '"
                        + entityName
                        + "'; no "
                        + entityName
                        + "_metrics/_hist view is registered"));
  }

  private EntityCatalog describeEntity(final String entityName, final Set<String> viewSet) {
    final String metricsView = entityName + METRICS_SUFFIX;
    final String histView = entityName + HIST_SUFFIX;
    final boolean hasMetricsView = viewSet.contains(metricsView);
    final boolean hasHistView = viewSet.contains(histView);

    final List<DimCatalog> dims = new ArrayList<>();
    boolean hasCnt = false;
    final List<String> counters = new ArrayList<>();
    final List<MeasureCatalog> measures = new ArrayList<>();

    if (hasMetricsView) {
      final List<String> columns = describeColumns(metricsView);
      final Map<String, Set<String>> suffixGroups = new LinkedHashMap<>();
      for (final String column : columns) {
        if (column.equals(WINDOW_START)) {
          continue;
        }
        if (column.equals(BARE_CNT)) {
          hasCnt = true;
          continue;
        }
        final String matchedSuffix = matchSuffix(column);
        if (matchedSuffix == null) {
          dims.add(new DimCatalog(column, dimKinds.get(column)));
          continue;
        }
        final String prefix = column.substring(0, column.length() - matchedSuffix.length());
        suffixGroups.computeIfAbsent(prefix, k -> new LinkedHashSet<>()).add(matchedSuffix);
      }
      final Set<String> histMeasures = hasHistView ? distinctHistMeasures(histView) : Set.of();
      for (final Map.Entry<String, Set<String>> group : suffixGroups.entrySet()) {
        final String prefix = group.getKey();
        final Set<String> suffixes = group.getValue();
        if (suffixes.contains("_sum")) {
          measures.add(
              new MeasureCatalog(
                  prefix,
                  true,
                  suffixes.contains("_min"),
                  suffixes.contains("_max"),
                  suffixes.contains("_nonfinite_cnt"),
                  histMeasures.contains(prefix)));
        } else {
          // _cnt-only group with no _sum sibling -- a named counter, not a measure.
          counters.add(prefix);
        }
      }
    } else if (hasHistView) {
      // Hist-only entity (no wide _metrics view registered) -- derive dims from the hist schema's
      // own non-fixed columns, and one measure per distinct `measure` value it holds.
      final List<String> columns = describeColumns(histView);
      final Set<String> histFixedColumns =
          Set.of(WINDOW_START, "measure", "scheme", "bin_lo", "bin_hi", BARE_CNT);
      for (final String column : columns) {
        if (!histFixedColumns.contains(column)) {
          dims.add(new DimCatalog(column, dimKinds.get(column)));
        }
      }
      for (final String measureName : distinctHistMeasures(histView)) {
        measures.add(new MeasureCatalog(measureName, false, false, false, false, true));
      }
    }

    return new EntityCatalog(
        entityName,
        List.copyOf(dims),
        hasCnt,
        List.copyOf(counters),
        List.copyOf(measures),
        hasHistView);
  }

  private static String matchSuffix(final String column) {
    for (final String suffix : MEASURE_SUFFIXES_IN_PRIORITY_ORDER) {
      if (column.endsWith(suffix) && column.length() > suffix.length()) {
        return suffix;
      }
    }
    return null;
  }

  private List<String> describeColumns(final String view) {
    try {
      return ViewDescribe.columns(queryService, view);
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to DESCRIBE view " + view, e);
    }
  }

  private Set<String> distinctHistMeasures(final String histView) {
    try {
      final LakeQueryService.QueryResult result =
          queryService.execute("SELECT DISTINCT measure FROM " + SqlText.identifier(histView));
      final Set<String> measures = new LinkedHashSet<>();
      for (final List<Object> row : result.rows()) {
        measures.add((String) row.get(0));
      }
      return measures;
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to read distinct measures from " + histView, e);
    }
  }
}
