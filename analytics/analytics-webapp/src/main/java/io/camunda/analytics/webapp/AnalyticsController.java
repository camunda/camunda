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
import io.camunda.analytics.dataset.RegisteredDataset;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.query.DatasetQueryExecutor;
import io.camunda.analytics.query.ReportExecutor;
import io.camunda.analytics.query.ReportResult;
import io.camunda.analytics.report.ReportDefinition;
import io.camunda.analytics.serving.catalog.DatasetProvisioningService;
import io.camunda.analytics.serving.catalog.StandardDatasets;
import io.camunda.analytics.serving.spi.DatasetSpecQuery;
import io.camunda.analytics.serving.spi.MetadataStore;
import io.camunda.analytics.webapp.model.HeatmapCell;
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
  private final AnalyticsRepository repository;
  private final TableRepository tableRepository;

  public AnalyticsController(
      final DatasetProvisioningService provisioningService,
      final MetadataStore metadataStore,
      final DatasetQueryExecutor datasetQueryExecutor,
      final AnalyticsRepository repository,
      final TableRepository tableRepository) {
    this.provisioningService = provisioningService;
    this.metadataStore = metadataStore;
    this.datasetQueryExecutor = datasetQueryExecutor;
    this.repository = repository;
    this.tableRepository = tableRepository;
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

  @GetMapping("/reports/{id}/data")
  public ResponseEntity<ReportResult> reportData(
      @PathVariable final long id,
      @RequestParam("fromMs") final long fromMs,
      @RequestParam("toMs") final long toMs) {
    return metadataStore
        .reportSpecStore()
        .read(id)
        .map(report -> ResponseEntity.ok(runReport(report, fromMs, toMs)))
        .orElseGet(() -> ResponseEntity.notFound().build());
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

  /** A rejected declaration (validation) is a client error, not a server fault. */
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
            .map(m -> new MeterView(m.name(), m.type(), m.measureField(), m.params()))
            .toList(),
        d.windowSizesMs(),
        d.keyField(),
        registered.activationTimestampMs());
  }

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
      Long latenessMs) {

    DatasetDeclaration toDeclaration() {
      final DatasetDeclaration.Builder builder = DatasetDeclaration.builder(name, sourceFact);
      if (kind == DatasetKind.TABLE) {
        builder.asTable(keyField);
      }
      if (filters != null) {
        filters.forEach(f -> builder.filterEquals(f.field(), f.value()));
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
        meters.forEach(
            m -> builder.meter(new Meter(m.name(), m.type(), m.measureField(), m.params())));
      }
      if (windowSizesMs != null) {
        windowSizesMs.forEach(builder::window);
      }
      if (latenessMs != null) {
        builder.lateness(latenessMs);
      }
      return builder.build();
    }
  }

  public record DimensionRequest(String name, DimensionType type, EnrichmentTiming enrichment) {}

  public record MeterRequest(
      String name, String type, String measureField, Map<String, String> params) {}

  public record FilterRequest(String field, String value) {}

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
      long activationTimestampMs) {}

  public record DimensionView(String name, String type, String enrichment) {}

  public record MeterView(
      String name, String type, String measureField, Map<String, String> params) {}
}
