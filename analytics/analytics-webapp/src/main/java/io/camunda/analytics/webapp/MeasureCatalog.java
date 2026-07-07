/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * The semantic layer behind the report builder (see {@code docs/design/report-builder-ux.md}): it
 * presents datasets in business language — <b>entities</b> (friendly names for {@link FactType}s),
 * <b>measures</b> (friendly names for meter templates), and <b>group-bys</b> (friendly names for
 * dimensions) — and compiles a user's "question" into a {@link DatasetDeclaration}. All engine
 * vocabulary (meters, params, windows, enrichment) lives here, so the UI never shows it.
 *
 * <p>A measure carries a deterministic meter name so the report can reference it, and its default
 * window tiers; the chosen output granularity is added as a tier so it reads directly.
 * Where-filters are compiled into the dataset (a narrower pre-filtered cube); grouping onto a
 * shared cube with report-time filters is the ADR 0001 planner optimization, deferred.
 */
@Component
public class MeasureCatalog {

  private static final long MINUTE = 60_000L;
  private static final long HOUR = 3_600_000L;
  private static final long DAY = 86_400_000L;
  private static final long DEFAULT_SLA_MS = 5 * MINUTE;

  private final List<Entity> entities = buildEntities();

  /** The catalog for {@code GET /api/measures}, in the shape the client renders. */
  public CatalogView view() {
    final List<EntityView> entityViews = new ArrayList<>();
    for (final Entity entity : entities) {
      final List<MeasureView> measures = new ArrayList<>();
      for (final Measure measure : entity.measures()) {
        measures.add(
            new MeasureView(
                measure.id(),
                measure.label(),
                measure.description(),
                measure.unit(),
                measure.param()));
      }
      final List<GroupByView> groupBys = new ArrayList<>();
      for (final GroupBy groupBy : entity.groupBys()) {
        groupBys.add(new GroupByView(groupBy.id(), groupBy.label(), groupBy.variable()));
      }
      entityViews.add(
          new EntityView(entity.id(), entity.label(), entity.description(), measures, groupBys));
    }
    return new CatalogView(
        entityViews,
        List.of(
            new GranularityView(MINUTE, "Minute"),
            new GranularityView(HOUR, "Hour"),
            new GranularityView(DAY, "Day")),
        List.of("number", "line", "bar", "table"));
  }

  /**
   * Compiles a question into a dataset declaration plus the meter name the report reads. The
   * dataset name is derived deterministically from the question shape, so an identical question
   * maps to the same declaration and can be reused rather than minting a duplicate cube.
   */
  public CompiledQuestion compile(
      final String entityId,
      final String measureId,
      final Map<String, Double> params,
      final List<QuestionGroupBy> groupBy,
      final List<QuestionFilter> filters,
      final long granularityMs) {
    final Entity entity = entity(entityId);
    final Measure measure = measure(entity, measureId);
    final Map<String, Double> effectiveParams = params == null ? Map.of() : params;

    final String datasetName =
        datasetName(entityId, measureId, effectiveParams, groupBy, filters, granularityMs);
    final DatasetDeclaration.Builder builder =
        DatasetDeclaration.builder(datasetName, entity.fact());
    if (filters != null) {
      for (final QuestionFilter filter : filters) {
        builder.filterEquals(filter.field(), filter.value());
      }
    }
    if (groupBy != null) {
      for (final QuestionGroupBy dimension : groupBy) {
        builder.dimension(dimension.field(), DimensionType.STRING);
      }
    }
    builder.meter(measure.meter().apply(effectiveParams));
    // The measure's default tiers plus the requested granularity (so the read hits a stored tier),
    // sorted so the declaration's tiers are ascending regardless of where the granularity falls.
    final List<Long> windows = new ArrayList<>(measure.windows());
    if (!windows.contains(granularityMs)) {
      windows.add(granularityMs);
    }
    windows.sort(null);
    windows.forEach(builder::window);
    return new CompiledQuestion(datasetName, builder.build(), measure.meterName());
  }

  private static String datasetName(
      final String entityId,
      final String measureId,
      final Map<String, Double> params,
      final List<QuestionGroupBy> groupBy,
      final List<QuestionFilter> filters,
      final long granularityMs) {
    final StringBuilder name =
        new StringBuilder("q:").append(entityId).append(':').append(measureId);
    params.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(e -> name.append(':').append(e.getKey()).append('=').append(e.getValue()));
    if (groupBy != null && !groupBy.isEmpty()) {
      name.append(":by:");
      groupBy.forEach(g -> name.append(g.field()).append(','));
    }
    if (filters != null && !filters.isEmpty()) {
      name.append(":where:");
      filters.forEach(f -> name.append(f.field()).append('=').append(f.value()).append(','));
    }
    return name.append(":g:").append(granularityMs).toString();
  }

  private Entity entity(final String id) {
    return entities.stream()
        .filter(e -> e.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("unknown entity '" + id + "'"));
  }

  private static Measure measure(final Entity entity, final String id) {
    return entity.measures().stream()
        .filter(m -> m.id().equals(id))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "unknown measure '" + id + "' for entity '" + entity.id() + "'"));
  }

  // --- the catalog ------------------------------------------------------------------------------

  private static List<Entity> buildEntities() {
    final GroupBy process = new GroupBy("bpmnProcessId", "Process", false);
    final GroupBy version = new GroupBy("processVersion", "Version", false);
    final GroupBy tenant = new GroupBy("tenantId", "Tenant", false);
    final GroupBy flowNode = new GroupBy("elementId", "Flow node", false);
    final GroupBy variable = new GroupBy("var", "Variable…", true);

    final Measure avgDuration =
        new Measure(
            "avg-duration",
            "Average duration",
            "Average time instances took to complete.",
            "duration",
            null,
            "duration",
            params -> Meter.of("duration", MeterCatalog.EXECUTION_TIME_SUMMARY, "durationMs"),
            List.of(MINUTE, HOUR));
    final Measure durationPercentile =
        new Measure(
            "duration-percentile",
            "Duration percentile",
            "A duration percentile (e.g. p95) — the value most instances stay under.",
            "duration",
            new Param("percentile", "Percentile", "number", 95),
            "percentile",
            params ->
                new Meter(
                    "percentile",
                    MeterCatalog.PERCENTILE,
                    "durationMs",
                    Map.of(
                        "ranks", Double.toString(params.getOrDefault("percentile", 95.0) / 100.0))),
            List.of(MINUTE, HOUR));

    return List.of(
        new Entity(
            "process-instances",
            "Process instances",
            "Whole process executions.",
            FactType.PROCESS_INSTANCE,
            List.of(
                new Measure(
                    "instance-count",
                    "Number of instances",
                    "How many process instances.",
                    "count",
                    null,
                    "count",
                    params -> Meter.of("count", MeterCatalog.COUNT),
                    List.of(MINUTE)),
                avgDuration,
                durationPercentile,
                new Measure(
                    "sla-compliance",
                    "% within SLA",
                    "Share of instances that completed within the SLA target.",
                    "percent",
                    new Param("slaTargetMs", "SLA target", "duration", DEFAULT_SLA_MS),
                    "sla",
                    params ->
                        new Meter(
                            "sla",
                            MeterCatalog.RATIO,
                            "durationMs",
                            Map.of(
                                "op",
                                "le",
                                "threshold",
                                Long.toString(
                                    (long)
                                        (double)
                                            params.getOrDefault(
                                                "slaTargetMs", (double) DEFAULT_SLA_MS)))),
                    List.of(MINUTE))),
            List.of(process, version, tenant, variable)),
        new Entity(
            "flow-nodes",
            "Flow nodes / tasks",
            "Individual steps within processes.",
            FactType.ELEMENT,
            List.of(
                new Measure(
                    "execution-count",
                    "Times executed",
                    "How many times flow nodes were executed.",
                    "count",
                    null,
                    "count",
                    params -> Meter.of("count", MeterCatalog.COUNT),
                    List.of(MINUTE)),
                avgDuration,
                durationPercentile),
            List.of(process, flowNode, variable)),
        new Entity(
            "incidents",
            "Incidents",
            "Errors raised during execution.",
            FactType.INCIDENT,
            List.of(
                new Measure(
                    "incident-count",
                    "Number of incidents",
                    "How many incidents were raised.",
                    "count",
                    null,
                    "count",
                    params -> Meter.of("count", MeterCatalog.COUNT),
                    List.of(MINUTE)),
                new Measure(
                    "open-incidents",
                    "Currently open",
                    "Incidents still open (raised minus resolved).",
                    "count",
                    null,
                    "open",
                    params -> Meter.of("open", MeterCatalog.LEVEL, "delta"),
                    List.of(MINUTE))),
            List.of(process, flowNode)));
  }

  // --- internal catalog model -------------------------------------------------------------------

  private record Entity(
      String id,
      String label,
      String description,
      FactType fact,
      List<Measure> measures,
      List<GroupBy> groupBys) {}

  private record Measure(
      String id,
      String label,
      String description,
      String unit,
      Param param,
      String meterName,
      Function<Map<String, Double>, Meter> meter,
      List<Long> windows) {}

  private record GroupBy(String id, String label, boolean variable) {}

  // --- request + result -------------------------------------------------------------------------

  /**
   * One group-by from a question; {@code field} already carries the {@code var.} prefix if a var.
   */
  public record QuestionGroupBy(String field, boolean variable) {}

  public record QuestionFilter(String field, String value) {}

  /** The compiled question: the derived dataset, its deterministic name, and the meter to read. */
  public record CompiledQuestion(
      String datasetName, DatasetDeclaration declaration, String meterName) {}

  // --- view (GET /api/measures) -----------------------------------------------------------------

  public record CatalogView(
      List<EntityView> entities,
      List<GranularityView> granularities,
      List<String> visualizations) {}

  public record EntityView(
      String id,
      String label,
      String description,
      List<MeasureView> measures,
      List<GroupByView> groupBys) {}

  public record MeasureView(
      String id, String label, String description, String unit, Param param) {}

  public record Param(String key, String label, String type, double defaultValue) {}

  public record GroupByView(String id, String label, boolean variable) {}

  public record GranularityView(long ms, String label) {}
}
