/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import io.camunda.analytics.dataset.ActiveCube;
import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dataset.DatasetKind;
import io.camunda.analytics.dataset.EnrichmentTiming;
import io.camunda.analytics.dataset.FilterPredicate;
import io.camunda.analytics.dataset.RegisteredDataset;
import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.query.DatasetQueryExecutor;
import io.camunda.analytics.query.ReportExecutor;
import io.camunda.analytics.query.ReportResult;
import io.camunda.analytics.query.ReportRow;
import io.camunda.analytics.query.SnapshotQueryExecutor;
import io.camunda.analytics.report.Combination;
import io.camunda.analytics.report.ReportDefinition;
import io.camunda.analytics.report.ReportSource;
import io.camunda.analytics.serving.catalog.DatasetProvisioningService;
import io.camunda.analytics.serving.catalog.StandardDatasets;
import io.camunda.analytics.serving.spi.DatasetSpecQuery;
import io.camunda.analytics.serving.spi.MetadataStore;
import io.camunda.analytics.webapp.model.HeatmapCell;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The declare-and-read API behind the UI: define a dataset (compiled + provisioned + admitted to
 * the metadata plane via {@link DatasetProvisioningService}), define a report over one or more
 * datasets (persisted in the metadata plane via {@link MetadataStore#reportSpecStore()}), and run a
 * report (executed as a union over the pre-aggregated cubes via {@link ReportExecutor}). Datasets
 * and reports are backend-neutral specs in the metadata plane, not webapp-local storage.
 */
@RestController
@RequestMapping("/api")
public class AnalyticsController {

  private final DatasetProvisioningService provisioningService;
  private final MetadataStore metadataStore;
  private final DatasetQueryExecutor datasetQueryExecutor;
  private final SnapshotQueryExecutor snapshotQueryExecutor;
  private final MeasureCatalog measureCatalog;
  private final AnalyticsRepository repository;
  private final TableRepository tableRepository;

  public AnalyticsController(
      final DatasetProvisioningService provisioningService,
      final MetadataStore metadataStore,
      final DatasetQueryExecutor datasetQueryExecutor,
      final SnapshotQueryExecutor snapshotQueryExecutor,
      final MeasureCatalog measureCatalog,
      final AnalyticsRepository repository,
      final TableRepository tableRepository) {
    this.provisioningService = provisioningService;
    this.metadataStore = metadataStore;
    this.datasetQueryExecutor = datasetQueryExecutor;
    this.snapshotQueryExecutor = snapshotQueryExecutor;
    this.measureCatalog = measureCatalog;
    this.repository = repository;
    this.tableRepository = tableRepository;
  }

  // --- semantic layer: measures + question-shaped reports (the primary, friendly surface) -------

  /** The business-language catalog (entities, measures, group-bys) the report builder renders. */
  @GetMapping("/measures")
  public MeasureCatalog.CatalogView measures() {
    return measureCatalog.view();
  }

  /**
   * Creates a report from a question in business language: compile it to a dataset declaration,
   * find-or-provision the backing cube, then persist a report reading the measure over the shared
   * group-by. The dataset is managed behind the report (report-first).
   *
   * <p>A question may additionally carry {@code compare} filters: the report then reads the same
   * dataset <em>twice</em> — the unfiltered baseline plus a read-time-filtered slice — and {@link
   * ReportExecutor} namespaces the repeat as {@code dataset#2.meter} so the two series render side
   * by side. Each compare field joins the derived cube's <em>grain</em> (the read-time filter needs
   * a dimension column to hit) but stays out of the report's group-by, so the baseline aggregates
   * over it and both sources emit one comparable row per group and bucket.
   */
  @PostMapping("/reports/from-question")
  @ResponseStatus(HttpStatus.CREATED)
  public ReportDefinition createReportFromQuestion(@RequestBody final QuestionRequest request) {
    final List<MeasureCatalog.QuestionFilter> compare =
        request.compare() == null ? List.of() : request.compare();
    final MeasureCatalog.CompiledQuestion compiled =
        measureCatalog.compile(
            request.entity(),
            request.measure(),
            request.params(),
            withCompareDimensions(request.groupBy(), compare),
            request.filters(),
            request.granularityMs());
    // Reuse an existing dataset with the same derived declaration, else provision a new one.
    if (metadataStore
        .datasetSpecStore()
        .search(DatasetSpecQuery.byName(compiled.datasetName()))
        .isEmpty()) {
      provisioningService.provision(compiled.declaration());
    }
    final List<String> groupByFields =
        request.groupBy() == null
            ? List.of()
            : request.groupBy().stream().map(MeasureCatalog.QuestionGroupBy::field).toList();
    final List<ReportSource> sources = new ArrayList<>();
    sources.add(new ReportSource(compiled.datasetName(), List.of(compiled.meterName()), List.of()));
    if (!compare.isEmpty()) {
      sources.add(
          new ReportSource(
              compiled.datasetName(),
              List.of(compiled.meterName()),
              compare.stream()
                  .map(filter -> FilterPredicate.equals(filter.field(), filter.value()))
                  .toList()));
    }
    final ReportDefinition report =
        new ReportDefinition(
            0L,
            request.name(),
            sources,
            groupByFields,
            request.granularityMs(),
            Combination.UNION,
            request.viz());
    return metadataStore.reportSpecStore().create(report);
  }

  /**
   * The question's group-by plus one dimension per compare field not already grouped by — the
   * compiled declaration's dimension set, which is what makes a compare field filterable at read
   * time on the pre-aggregated cube.
   */
  private static List<MeasureCatalog.QuestionGroupBy> withCompareDimensions(
      final List<MeasureCatalog.QuestionGroupBy> groupBy,
      final List<MeasureCatalog.QuestionFilter> compare) {
    if (compare.isEmpty()) {
      return groupBy;
    }
    final List<MeasureCatalog.QuestionGroupBy> dimensions =
        groupBy == null ? new ArrayList<>() : new ArrayList<>(groupBy);
    for (final MeasureCatalog.QuestionFilter filter : compare) {
      if (dimensions.stream().noneMatch(dim -> dim.field().equals(filter.field()))) {
        dimensions.add(new MeasureCatalog.QuestionGroupBy(filter.field(), false));
      }
    }
    return dimensions;
  }

  // --- datasets --------------------------------------------------------------------------------

  @GetMapping("/datasets")
  public List<DatasetView> datasets() {
    return metadataStore.datasetSpecStore().search(DatasetSpecQuery.all()).stream()
        .map(AnalyticsController::toView)
        .toList();
  }

  @PostMapping("/datasets")
  @ResponseStatus(HttpStatus.CREATED)
  public DatasetView createDataset(@RequestBody final CreateDatasetRequest request) {
    return toView(provisioningService.provision(request.toDeclaration()));
  }

  /**
   * Runs a SNAPSHOT query (ADR 0010) against a snapshot-enabled dataset: "where did the value stand
   * at each moment" as a dense per-key series over {@code (fromMs, toMs]} at the requested
   * granularity (a multiple of the dataset's declared sample interval). The executor's rejections
   * (no snapshots declared, off-grid granularity, empty range) surface as 400s via the {@link
   * IllegalArgumentException} handler below; an unknown dataset is a 404.
   */
  @GetMapping("/datasets/{name}/snapshots")
  public ResponseEntity<List<SnapshotSeriesView>> datasetSnapshots(
      @PathVariable final String name,
      @RequestParam("fromMs") final long fromMs,
      @RequestParam("toMs") final long toMs,
      @RequestParam("granularityMs") final long granularityMs) {
    // Resolve against the current metadata plane, so a just-defined dataset resolves.
    CompiledDataset dataset = null;
    for (final ActiveCube cube : StandardDatasets.loadCubes(metadataStore)) {
      if (cube.compiled().name().equals(name)) {
        dataset = cube.compiled();
        break;
      }
    }
    if (dataset == null) {
      return ResponseEntity.notFound().build();
    }
    final List<SnapshotQueryExecutor.SnapshotSeriesPoint> points =
        snapshotQueryExecutor.execute(
            new SnapshotQueryExecutor.SnapshotQuery(fromMs, toMs, granularityMs), dataset);

    // Group the flat per-key points into one series per grain key (points arrive key-grouped,
    // time-ascending); the key values are named by the grain columns for the client.
    final List<DimensionColumn> grain = dataset.grain().columns();
    final Map<List<Object>, SnapshotSeriesView> byKey = new LinkedHashMap<>();
    for (final SnapshotQueryExecutor.SnapshotSeriesPoint point : points) {
      byKey
          .computeIfAbsent(
              point.keyValues(),
              key -> {
                final Map<String, Object> dimensions = new LinkedHashMap<>();
                for (int i = 0; i < grain.size(); i++) {
                  dimensions.put(grain.get(i).name(), key.get(i));
                }
                return new SnapshotSeriesView(dimensions, new ArrayList<>());
              })
          .points()
          .add(new SnapshotPointView(point.time(), point.measures()));
    }
    return ResponseEntity.ok(List.copyOf(byKey.values()));
  }

  /** One key's dense snapshot series: the grain values and the (time, absolute values) points. */
  public record SnapshotSeriesView(
      Map<String, Object> dimensions, List<SnapshotPointView> points) {}

  /** One materialised snapshot point: the bucket end and the absolute measure values. */
  public record SnapshotPointView(long time, Map<String, Object> measures) {}

  // --- reports ---------------------------------------------------------------------------------

  @GetMapping("/reports")
  public List<ReportDefinition> reports() {
    return metadataStore.reportSpecStore().search();
  }

  @PostMapping("/reports")
  @ResponseStatus(HttpStatus.CREATED)
  public ReportDefinition createReport(@RequestBody final ReportDefinition report) {
    return metadataStore.reportSpecStore().create(report);
  }

  /**
   * Runs a saved report over {@code [fromMs, toMs)}. With {@code compareWithPrevious=true} the same
   * report also runs over the immediately preceding same-length range and the response carries both
   * row sets; the previous rows are re-timestamped onto the current period's grid ({@code
   * windowStart + P}) so consumers overlay them directly. Without the flag the response shape is
   * unchanged.
   */
  @GetMapping("/reports/{id}/data")
  public ResponseEntity<?> reportData(
      @PathVariable final long id,
      @RequestParam("fromMs") final long fromMs,
      @RequestParam("toMs") final long toMs,
      @RequestParam(value = "compareWithPrevious", defaultValue = "false")
          final boolean compareWithPrevious) {
    return metadataStore
        .reportSpecStore()
        .read(id)
        .<ResponseEntity<?>>map(
            report -> {
              final ReportResult current = runReport(report, fromMs, toMs);
              if (!compareWithPrevious) {
                return ResponseEntity.ok(current);
              }
              if (toMs <= fromMs) {
                // a zero period would "compare" the range with itself; a negative one would shift
                // rows backwards — same guard as the dashboard comparison endpoints
                throw new IllegalArgumentException(
                    "compareWithPrevious needs a non-empty range [" + fromMs + ", " + toMs + ")");
              }
              final long period = toMs - fromMs;
              final ReportResult previous = runReport(report, fromMs - period, toMs - period);
              return ResponseEntity.ok(
                  new ComparedReportResult(current.rows(), shiftWindows(previous.rows(), period)));
            })
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /**
   * A saved report run twice — the requested range and the preceding same-length range — for the
   * compare-to-previous-period option. {@code previousRows} ride on the current period's time grid.
   */
  public record ComparedReportResult(List<ReportRow> rows, List<ReportRow> previousRows) {}

  private static List<ReportRow> shiftWindows(final List<ReportRow> rows, final long period) {
    final List<ReportRow> shifted = new ArrayList<>(rows.size());
    for (final ReportRow row : rows) {
      shifted.add(new ReportRow(row.dimensions(), row.windowStart() + period, row.measures()));
    }
    return shifted;
  }

  private ReportResult runReport(
      final ReportDefinition report, final long fromMs, final long toMs) {
    // Resolve dataset names against the current metadata plane (so a just-defined dataset
    // resolves);
    // a UI read, so recompiling the cubes per run is fine.
    final Map<String, CompiledDataset> byName = new LinkedHashMap<>();
    for (final ActiveCube cube : StandardDatasets.loadCubes(metadataStore)) {
      byName.put(cube.compiled().name(), cube.compiled());
    }
    return new ReportExecutor(datasetQueryExecutor, byName::get).execute(report, fromMs, toMs);
  }

  // --- heatmap + raw tables (unchanged) --------------------------------------------------------

  /** Process definitions the element heatmap has data for. */
  @GetMapping("/heatmap/processes")
  public List<String> heatmapProcesses() {
    return repository.heatmapProcesses();
  }

  /** The element heatmap for one process definition. */
  @GetMapping("/heatmap")
  public List<HeatmapCell> heatmap(@RequestParam("process") final String bpmnProcessId) {
    return repository.elementHeatmap(bpmnProcessId);
  }

  /** The raw tables the serving store can answer (unaggregated, keyed rows). */
  @GetMapping("/tables")
  public List<String> tables() {
    return tableRepository.listTables();
  }

  /** Up to {@code limit} rows of one table, each as its declared column values. */
  @GetMapping("/tables/{name}")
  public ResponseEntity<List<Map<String, Object>>> tableRows(
      @PathVariable final String name,
      @RequestParam(value = "limit", required = false, defaultValue = "1000") final int limit) {
    if (!tableRepository.hasTable(name)) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(tableRepository.rows(name, limit));
  }

  /**
   * A rejected declaration (validation) is a client error, not a server fault. This also covers
   * {@link io.camunda.analytics.dataset.DatasetValidationException} — the provisioning dry-run's
   * rejection of a declaration whose meters fail to compile — which subclasses {@code
   * IllegalArgumentException} precisely so it maps to a 400 here with its message intact.
   */
  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> onInvalid(final IllegalArgumentException e) {
    return Map.of("error", e.getMessage());
  }

  private static DatasetView toView(final RegisteredDataset registered) {
    final DatasetDeclaration d = registered.declaration();
    return new DatasetView(
        registered.cubeId(),
        d.name(),
        d.sourceFact().name(),
        d.kind().name(),
        d.dimensions().stream()
            .map(dim -> new DimensionView(dim.name(), dim.type().name(), dim.enrichment().name()))
            .toList(),
        d.meters().stream()
            .map(
                m ->
                    new MeterView(
                        m.name(),
                        m.type(),
                        m.measureField(),
                        m.params(),
                        toFilterViews(m.filters()),
                        toFilterViews(m.matched())))
            .toList(),
        d.windowSizesMs(),
        d.keyField(),
        d.snapshotEveryMs(),
        registered.activationTimestampMs());
  }

  /**
   * Request body for a question-shaped report (the friendly, report-first surface). {@code compare}
   * (optional) turns it into a same-dataset comparison: a second read-time-filtered source next to
   * the unfiltered baseline; each compare field must also be a group-by field.
   */
  public record QuestionRequest(
      String name,
      String entity,
      String measure,
      Map<String, Double> params,
      List<MeasureCatalog.QuestionGroupBy> groupBy,
      List<MeasureCatalog.QuestionFilter> filters,
      List<MeasureCatalog.QuestionFilter> compare,
      long granularityMs,
      String viz) {}

  /** Request body to declare a dataset — mapped to a {@link DatasetDeclaration} via its builder. */
  public record CreateDatasetRequest(
      String name,
      FactType sourceFact,
      DatasetKind kind,
      List<FilterRequest> filters,
      List<DimensionRequest> dimensions,
      List<MeterRequest> meters,
      List<Long> windowSizesMs,
      String keyField,
      Long latenessMs,
      Long snapshotEveryMs) {

    DatasetDeclaration toDeclaration() {
      final DatasetDeclaration.Builder builder = DatasetDeclaration.builder(name, sourceFact);
      if (kind == DatasetKind.TABLE) {
        builder.asTable(keyField);
      }
      if (filters != null) {
        filters.forEach(f -> builder.filter(f.toPredicate()));
      }
      if (dimensions != null) {
        for (final DimensionRequest dim : dimensions) {
          if (dim.enrichment() == null) {
            builder.dimension(dim.name(), dim.type());
          } else {
            builder.dimension(dim.name(), dim.type(), dim.enrichment());
          }
        }
      }
      if (meters != null) {
        meters.forEach(m -> builder.meter(m.toMeter()));
      }
      if (windowSizesMs != null) {
        windowSizesMs.forEach(builder::window);
      }
      if (latenessMs != null) {
        builder.lateness(latenessMs);
      }
      if (snapshotEveryMs != null && snapshotEveryMs > 0) {
        builder.snapshots(snapshotEveryMs);
      }
      return builder.build();
    }
  }

  public record DimensionRequest(String name, DimensionType type, EnrichmentTiming enrichment) {}

  /**
   * One declared meter; {@code filters} are the per-meter fold predicates and {@code matched} the
   * ratio-only numerator predicates (either may be omitted).
   */
  public record MeterRequest(
      String name,
      String type,
      String measureField,
      Map<String, String> params,
      List<FilterRequest> filters,
      List<FilterRequest> matched) {

    Meter toMeter() {
      return new Meter(
          name, type, measureField, params, toPredicates(filters), toPredicates(matched));
    }

    private static List<FilterPredicate> toPredicates(final List<FilterRequest> filters) {
      return filters == null
          ? List.of()
          : filters.stream().map(FilterRequest::toPredicate).toList();
    }
  }

  /** One declared filter; a missing operator means {@code EQUALS} (the pre-operator wire shape). */
  public record FilterRequest(String field, FilterPredicate.Operator operator, String value) {

    FilterPredicate toPredicate() {
      return new FilterPredicate(
          field, operator == null ? FilterPredicate.Operator.EQUALS : operator, value);
    }
  }

  /** Read view of a dataset spec for the UI. */
  public record DatasetView(
      long cubeId,
      String name,
      String sourceFact,
      String kind,
      List<DimensionView> dimensions,
      List<MeterView> meters,
      List<Long> windowSizesMs,
      String keyField,
      long snapshotEveryMs,
      long activationTimestampMs) {}

  public record DimensionView(String name, String type, String enrichment) {}

  public record MeterView(
      String name,
      String type,
      String measureField,
      Map<String, String> params,
      List<FilterRequest> filters,
      List<FilterRequest> matched) {}

  private static List<FilterRequest> toFilterViews(final List<FilterPredicate> predicates) {
    return predicates.stream()
        .map(f -> new FilterRequest(f.field(), f.operator(), f.value()))
        .toList();
  }
}
