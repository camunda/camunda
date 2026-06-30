/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import io.camunda.analytics.webapp.model.Dataset;
import io.camunda.analytics.webapp.model.Report;
import io.camunda.analytics.webapp.model.ReportRow;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Declare-and-read API behind the UI: define datasets, define reports, and run a report. */
@RestController
@RequestMapping("/api")
public class AnalyticsController {

  private final AnalyticsRepository repository;

  public AnalyticsController(final AnalyticsRepository repository) {
    this.repository = repository;
  }

  @GetMapping("/datasets")
  public List<Dataset> datasets() {
    return repository.listDatasets();
  }

  @PostMapping("/datasets")
  public Dataset createDataset(@RequestBody final CreateDataset request) {
    final String dimensions =
        request.dimensions() == null || request.dimensions().isBlank()
            ? "definition"
            : request.dimensions();
    final long window = request.windowSizeMs() == null ? 3_600_000L : request.windowSizeMs();
    return repository.createDataset(request.name(), dimensions, window);
  }

  @GetMapping("/reports")
  public List<Report> reports() {
    return repository.listReports();
  }

  @PostMapping("/reports")
  public Report createReport(@RequestBody final CreateReport request) {
    final String viz = request.vizType() == null ? "table" : request.vizType();
    return repository.createReport(
        request.name(),
        request.datasetId(),
        viz,
        request.bpmnProcessId(),
        request.region(),
        request.fromWindow(),
        request.toWindow());
  }

  @GetMapping("/reports/{id}/data")
  public ResponseEntity<List<ReportRow>> reportData(@PathVariable final long id) {
    return repository
        .getReport(id)
        .map(report -> ResponseEntity.ok(repository.runReport(report)))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /** Request body to declare a dataset. */
  public record CreateDataset(String name, String dimensions, Long windowSizeMs) {}

  /** Request body to declare a report on a dataset. */
  public record CreateReport(
      String name,
      long datasetId,
      String vizType,
      String bpmnProcessId,
      String region,
      Long fromWindow,
      Long toWindow) {}
}
