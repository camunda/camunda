/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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

  /** A malformed read (e.g. an empty comparison range) is the caller's error, not a 500. */
  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public String badRequest(final IllegalArgumentException e) {
    return e.getMessage();
  }

  /**
   * One whole dashboard render in a single request: every widget payload computed against one
   * per-render memo, so queries shared between widgets (the lifecycle series, the ratio series) run
   * once instead of once per widget endpoint. The client fetches this instead of ~14 widget calls.
   */
  @GetMapping("/overview")
  public DashboardOverview overview(
      @RequestParam("process") final String bpmnProcessId,
      @RequestParam("tenant") final String tenantId,
      @RequestParam(value = "from", required = false) final Long from,
      @RequestParam(value = "to", required = false) final Long to) {
    return repository.overview(bpmnProcessId, tenantId, from, to);
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

  /**
   * The period-over-period KPI comparison for the delta badges: the same whole-range aggregation
   * over {@code [from, to)} and over the preceding same-length range. Both bounds are mandatory —
   * an open range has no "previous period" (the client hides the badges then).
   */
  @GetMapping("/kpi-comparison")
  public KpiComparison kpiComparison(
      @RequestParam("process") final String bpmnProcessId,
      @RequestParam("from") final long from,
      @RequestParam("to") final long to) {
    return repository.kpiComparison(bpmnProcessId, from, to);
  }

  /**
   * The percentile trend plus its previous-period overlay (previous points re-timestamped onto the
   * current grid). Both bounds are mandatory, like {@link #kpiComparison}.
   */
  @GetMapping("/duration-percentiles-compare")
  public PercentileComparison durationPercentilesCompare(
      @RequestParam("process") final String bpmnProcessId,
      @RequestParam("from") final long from,
      @RequestParam("to") final long to) {
    return repository.durationPercentilesCompare(bpmnProcessId, from, to);
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

  /** Per-start-cohort completion-time distribution (started split into duration bands + open). */
  @GetMapping("/duration-buckets")
  public List<DurationBucketPoint> durationBuckets(
      @RequestParam("process") final String bpmnProcessId,
      @RequestParam(value = "from", required = false) final Long from,
      @RequestParam(value = "to", required = false) final Long to) {
    return repository.durationBuckets(bpmnProcessId, from, to);
  }

  /** Per-start-cohort SLA breakdown (started split into met/breached/open) for the stacked bar. */
  @GetMapping("/sla-cohorts")
  public List<SlaCohortPoint> slaCohorts(
      @RequestParam("process") final String bpmnProcessId,
      @RequestParam(value = "from", required = false) final Long from,
      @RequestParam(value = "to", required = false) final Long to) {
    return repository.slaCohorts(bpmnProcessId, from, to);
  }

  /** Per-start-cohort no-incident breakdown (started split into clean/withIncident/open). */
  @GetMapping("/no-incident-cohorts")
  public List<NoIncidentCohortPoint> noIncidentCohorts(
      @RequestParam("process") final String bpmnProcessId,
      @RequestParam(value = "from", required = false) final Long from,
      @RequestParam(value = "to", required = false) final Long to) {
    return repository.noIncidentCohorts(bpmnProcessId, from, to);
  }

  @GetMapping("/element-durations")
  public List<ElementDuration> elementDurations(
      @RequestParam("process") final String bpmnProcessId,
      @RequestParam(value = "from", required = false) final Long from,
      @RequestParam(value = "to", required = false) final Long to) {
    return repository.elementDurations(bpmnProcessId, from, to);
  }

  /**
   * Current in-flight instance count for one process definition (a gauge, independent of any time
   * range).
   */
  @GetMapping("/active-instances")
  public long activeInstances(
      @RequestParam("process") final String bpmnProcessId,
      @RequestParam("tenant") final String tenantId) {
    return repository.activeInstances(bpmnProcessId, tenantId);
  }

  /** Instances started (activated) for a process over the optional range. */
  @GetMapping("/activated-instances")
  public long activatedInstances(
      @RequestParam("process") final String bpmnProcessId,
      @RequestParam(value = "from", required = false) final Long from,
      @RequestParam(value = "to", required = false) final Long to) {
    return repository.activatedInstances(bpmnProcessId, from, to);
  }

  /** Instances ended (completed or terminated) for a process over the optional range. */
  @GetMapping("/ended-instances")
  public long endedInstances(
      @RequestParam("process") final String bpmnProcessId,
      @RequestParam(value = "from", required = false) final Long from,
      @RequestParam(value = "to", required = false) final Long to) {
    return repository.endedInstances(bpmnProcessId, from, to);
  }

  /**
   * Business value processed (sum of the value variable over COMPLETED instances) in the range;
   * {@code processed} is null when the process carries no value variable.
   */
  @GetMapping("/value-summary")
  public ValueSummary valueSummary(
      @RequestParam("process") final String bpmnProcessId,
      @RequestParam(value = "from", required = false) final Long from,
      @RequestParam(value = "to", required = false) final Long to) {
    return repository.valueSummary(bpmnProcessId, from, to);
  }

  /** Business value in flight at each moment (the value-in-flight cube's periodic snapshots). */
  @GetMapping("/value-series")
  public List<ValuePoint> valueSeries(
      @RequestParam("process") final String bpmnProcessId,
      @RequestParam(value = "from", required = false) final Long from,
      @RequestParam(value = "to", required = false) final Long to) {
    return repository.valueSeries(bpmnProcessId, from, to);
  }

  /** Running instances at each moment (the active-instances cube's periodic snapshots). */
  @GetMapping("/active-series")
  public List<ActiveInstancesPoint> activeSeries(
      @RequestParam("process") final String bpmnProcessId,
      @RequestParam(value = "from", required = false) final Long from,
      @RequestParam(value = "to", required = false) final Long to) {
    return repository.activeSeries(bpmnProcessId, from, to);
  }

  /**
   * Per-window flow balance: instances started and ended (completed + terminated) per window, the
   * bars the flow-balance widget renders under the active-instances WIP line.
   */
  @GetMapping("/lifecycle-series")
  public List<LifecycleSeriesPoint> lifecycleSeries(
      @RequestParam("process") final String bpmnProcessId,
      @RequestParam(value = "from", required = false) final Long from,
      @RequestParam(value = "to", required = false) final Long to) {
    return repository.lifecycleSeries(bpmnProcessId, from, to);
  }

  /**
   * Rework hotspots per flow node: {@code rework = max(0, activations − instances)} — activations
   * are exact, distinct instances an HLL estimate, so the rework figure is exact for small counts
   * and an approximation at scale; zero-rework elements are omitted, sorted by rework descending.
   */
  @GetMapping("/rework")
  public List<ReworkHotspot> rework(
      @RequestParam("process") final String bpmnProcessId,
      @RequestParam(value = "from", required = false) final Long from,
      @RequestParam(value = "to", required = false) final Long to) {
    return repository.rework(bpmnProcessId, from, to);
  }

  /** The oldest currently-open instances (aging WIP), oldest first, with server-computed age. */
  @GetMapping("/open-instances")
  public List<OpenInstanceRow> openInstances(
      @RequestParam("process") final String bpmnProcessId,
      @RequestParam(value = "limit", required = false, defaultValue = "20") final int limit) {
    return repository.openInstances(bpmnProcessId, limit);
  }

  /** Per-window completion-duration spread (stddev/min/max) for a process. */
  @GetMapping("/duration-spread")
  public List<DurationSpreadPoint> durationSpread(
      @RequestParam("process") final String bpmnProcessId,
      @RequestParam(value = "from", required = false) final Long from,
      @RequestParam(value = "to", required = false) final Long to) {
    return repository.durationSpread(bpmnProcessId, from, to);
  }

  /** Incidents per flow node (raised over the range + currently open) for a process. */
  @GetMapping("/incidents")
  public List<IncidentFlowNode> incidents(
      @RequestParam("process") final String bpmnProcessId,
      @RequestParam(value = "from", required = false) final Long from,
      @RequestParam(value = "to", required = false) final Long to) {
    return repository.incidents(bpmnProcessId, from, to);
  }

  /** Incidents raised per window for a process (the quality page's incident trend). */
  @GetMapping("/incident-trend")
  public List<IncidentTrendPoint> incidentTrend(
      @RequestParam("process") final String bpmnProcessId,
      @RequestParam(value = "from", required = false) final Long from,
      @RequestParam(value = "to", required = false) final Long to) {
    return repository.incidentTrend(bpmnProcessId, from, to);
  }

  /** Currently-open incident count for a process (range-independent gauge). */
  @GetMapping("/open-incidents")
  public long openIncidents(@RequestParam("process") final String bpmnProcessId) {
    return repository.openIncidents(bpmnProcessId);
  }

  /**
   * The top execution variants of a process over the range (by instance count): signature hash,
   * canonical element list, count, share of ended-with-variant instances, duration p50/p95.
   */
  @GetMapping("/variants")
  public List<VariantRow> variants(
      @RequestParam("process") final String bpmnProcessId,
      @RequestParam(value = "from", required = false) final Long from,
      @RequestParam(value = "to", required = false) final Long to,
      @RequestParam(value = "limit", required = false, defaultValue = "10") final int limit) {
    return repository.variants(bpmnProcessId, from, to, limit);
  }

  /**
   * Per-gateway branch distribution over the range: the deployed model's exclusive gateways joined
   * with the elements cube's activation counts (activation-based shares; see the caveat on {@link
   * BranchDistribution}).
   */
  @GetMapping("/branch-distribution")
  public List<BranchDistribution> branchDistribution(
      @RequestParam("process") final String bpmnProcessId,
      @RequestParam(value = "from", required = false) final Long from,
      @RequestParam(value = "to", required = false) final Long to) {
    return repository.branchDistribution(bpmnProcessId, from, to);
  }

  @GetMapping(value = "/diagram", produces = MediaType.APPLICATION_XML_VALUE)
  public ResponseEntity<String> diagram(@RequestParam("process") final String bpmnProcessId) {
    return repository
        .diagramXml(bpmnProcessId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
