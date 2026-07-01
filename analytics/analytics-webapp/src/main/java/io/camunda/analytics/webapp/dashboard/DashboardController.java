/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read API for the analytics dashboard: serves the metrics the pipeline pre-aggregates (duration
 * percentiles, SLA-met and no-incident percentages, distinct-process count, top processes, and the
 * per-element duration heatmap) straight from the serving tables.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

  private final DashboardRepository repository;

  public DashboardController(final DashboardRepository repository) {
    this.repository = repository;
  }

  @GetMapping("/processes")
  public List<String> processes() {
    return repository.processes();
  }

  @GetMapping("/tenants")
  public List<String> tenants() {
    return repository.tenants();
  }

  @GetMapping("/duration-percentiles")
  public List<DurationPercentilePoint> durationPercentiles(
      @RequestParam("process") final String bpmnProcessId,
      @RequestParam(value = "from", required = false) final Long from,
      @RequestParam(value = "to", required = false) final Long to) {
    return repository.durationPercentiles(bpmnProcessId, from, to);
  }

  /** A single exact duration distribution over the range (for the KPI tiles). */
  @GetMapping("/duration-summary")
  public DurationPercentilePoint durationSummary(
      @RequestParam("process") final String bpmnProcessId,
      @RequestParam(value = "from", required = false) final Long from,
      @RequestParam(value = "to", required = false) final Long to) {
    return repository.durationSummary(bpmnProcessId, from, to);
  }

  @GetMapping("/ratios")
  public List<RatioPoint> ratios(
      @RequestParam("process") final String bpmnProcessId,
      @RequestParam("metric") final String metric,
      @RequestParam(value = "from", required = false) final Long from,
      @RequestParam(value = "to", required = false) final Long to) {
    return repository.ratios(bpmnProcessId, metric, from, to);
  }

  @GetMapping("/distinct")
  public List<DistinctPoint> distinct(
      @RequestParam("tenant") final String tenantId,
      @RequestParam(value = "from", required = false) final Long from,
      @RequestParam(value = "to", required = false) final Long to) {
    return repository.distinct(tenantId, from, to);
  }

  @GetMapping("/top-processes")
  public List<TopProcess> topProcesses(
      @RequestParam("tenant") final String tenantId,
      @RequestParam(value = "from", required = false) final Long from,
      @RequestParam(value = "to", required = false) final Long to) {
    return repository.topProcesses(tenantId, from, to);
  }

  /** Per-start-cohort SLA breakdown (started split into met/breached/open) for the stacked bar. */
  @GetMapping("/sla-cohorts")
  public List<SlaCohortPoint> slaCohorts(
      @RequestParam("process") final String bpmnProcessId,
      @RequestParam(value = "from", required = false) final Long from,
      @RequestParam(value = "to", required = false) final Long to) {
    return repository.slaCohorts(bpmnProcessId, from, to);
  }

  @GetMapping("/element-durations")
  public List<ElementDuration> elementDurations(
      @RequestParam("process") final String bpmnProcessId,
      @RequestParam(value = "from", required = false) final Long from,
      @RequestParam(value = "to", required = false) final Long to) {
    return repository.elementDurations(bpmnProcessId, from, to);
  }

  /** Current in-flight instance count for a tenant (a gauge, independent of any time range). */
  @GetMapping("/active-instances")
  public long activeInstances(@RequestParam("tenant") final String tenantId) {
    return repository.activeInstances(tenantId);
  }

  /** Instances started (activated) for a process over the optional range. */
  @GetMapping("/activated-instances")
  public long activatedInstances(
      @RequestParam("process") final String bpmnProcessId,
      @RequestParam(value = "from", required = false) final Long from,
      @RequestParam(value = "to", required = false) final Long to) {
    return repository.activatedInstances(bpmnProcessId, from, to);
  }

  /** Incidents per flow node (raised over the range + currently open) for a process. */
  @GetMapping("/incidents")
  public List<IncidentFlowNode> incidents(
      @RequestParam("process") final String bpmnProcessId,
      @RequestParam(value = "from", required = false) final Long from,
      @RequestParam(value = "to", required = false) final Long to) {
    return repository.incidents(bpmnProcessId, from, to);
  }

  /** Currently-open incident count for a process (range-independent gauge). */
  @GetMapping("/open-incidents")
  public long openIncidents(@RequestParam("process") final String bpmnProcessId) {
    return repository.openIncidents(bpmnProcessId);
  }

  @GetMapping(value = "/diagram", produces = MediaType.APPLICATION_XML_VALUE)
  public ResponseEntity<String> diagram(@RequestParam("process") final String bpmnProcessId) {
    return repository
        .diagramXml(bpmnProcessId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
