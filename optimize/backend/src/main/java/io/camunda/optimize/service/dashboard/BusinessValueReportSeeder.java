/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.NoneDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import io.camunda.optimize.service.db.writer.ReportWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Seeds the canonical saved reports that power the Business Value Dashboard rendered in the Web
 * Modeler Hub. The Hub does not evaluate ad-hoc report definitions; instead it references these
 * reports by their (deterministic) id and evaluates them via {@code POST
 * /api/report/{id}/evaluate}, passing the process / version / date selection as additional filters
 * at evaluate time.
 *
 * <p>Reports are marked as {@link ProcessReportDataDto#isBusinessValueReport() system-generated} so
 * that a report seeded with <em>no</em> definitions is scoped at evaluate time to all the calling
 * user's process definitions (dashboard root) or to the definitions the caller passes in the
 * additional filter (single-process view) — always validated against that user's authorizations.
 * This reuses the same mechanism the Agentic Control Plane dashboard relies on (see {@link
 * AgenticControlDashboardService}).
 *
 * <p>Ids are derived from fixed seed strings so they are stable across restarts and DB reimports;
 * upserts are therefore idempotent and Hub references never orphan.
 *
 * <p>MVP scope: a single Cycle Time report (avg / P50 / P95 of completed-instance duration).
 * Further tiles are added here as the dashboard grows.
 */
@Component
public class BusinessValueReportSeeder {

  // Localization codes resolved under the "agenticControl" category by ReportRestMapper ->
  // LocalizationService (system-generated reports share that localization category).
  public static final String CYCLE_TIME_NAME = "businessValueCycleTimeName";
  public static final String CYCLE_TIME_DESCRIPTION = "businessValueCycleTimeDescription";
  public static final String WORST_INSTANCES_NAME = "businessValueWorstInstancesName";
  public static final String WORST_INSTANCES_DESCRIPTION = "businessValueWorstInstancesDescription";
  public static final String AGENT_COST_NAME = "businessValueAgentCostName";
  public static final String AGENT_COST_DESCRIPTION = "businessValueAgentCostDescription";

  // Deterministic report ids — same seed always produces the same UUID (UUID v3 / name-based), so
  // ids are identical across restarts and across every cluster's Optimize.
  public static final String CYCLE_TIME_REPORT_ID = reportId("bv-cycle-time");

  // Agent cost report powering the "Agentic Adoption & Cost" tiles. Carries two measures over the
  // per-instance agentTotalCost (stamped at import from the configured per-model rates):
  //   Total Agent Cost      = SUM(agentTotalCost)
  //   Avg Agent Cost per Run = AVG(agentTotalCost)
  public static final String AGENT_COST_REPORT_ID = reportId("bv-agent-cost");

  // Raw-data report powering the Worst-Performing Instances tile. The Hub evaluates it with a small
  // page size (the report is pre-sorted by descending instance duration) and derives the "slowest
  // step" per instance from the returned flow-node durations.
  public static final String WORST_INSTANCES_REPORT_ID = reportId("bv-worst-instances");

  // Frequency (count) reports powering the Automation Rate + Straight-Through tiles. The Hub
  // evaluates these and computes the ratios (Optimize can't divide two aggregates at query time).
  //   automationRate      = (completed - completedWithUserTasks) / completed
  //   straightThroughRate = (completedNoIncident - completedWithUserTasksNoIncident) / completed
  public static final String COMPLETED_COUNT_REPORT_ID = reportId("bv-completed-count");
  public static final String COMPLETED_WITH_USER_TASKS_REPORT_ID =
      reportId("bv-completed-count-usertasks");
  public static final String COMPLETED_NO_INCIDENT_REPORT_ID =
      reportId("bv-completed-count-noincident");
  public static final String COMPLETED_WITH_USER_TASKS_NO_INCIDENT_REPORT_ID =
      reportId("bv-completed-count-usertasks-noincident");

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(BusinessValueReportSeeder.class);

  private final ReportWriter reportWriter;
  private final ConfigurationService configurationService;

  public BusinessValueReportSeeder(
      final ReportWriter reportWriter, final ConfigurationService configurationService) {
    this.reportWriter = reportWriter;
    this.configurationService = configurationService;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void init() {
    if (configurationService.getEntityConfiguration().getCreateOnStartup()) {
      LOG.info("Seeding Business Value Dashboard reports");
      seed();
      LOG.info("Finished seeding Business Value Dashboard reports");
    }
  }

  public void seed() {
    // Always upsert so config changes are applied on every restart. Ids are deterministic, so
    // upserts are safe and never create duplicates.
    seedCycleTimeReport();
    seedWorstInstancesReport();
    seedAgentCostReport();
    // Count reports for Automation Rate + Straight-Through (Hub computes the ratios).
    seedCountReport(COMPLETED_COUNT_REPORT_ID, "businessValueCompletedCount", false, false);
    seedCountReport(
        COMPLETED_WITH_USER_TASKS_REPORT_ID, "businessValueCompletedWithUserTasks", true, false);
    seedCountReport(
        COMPLETED_NO_INCIDENT_REPORT_ID, "businessValueCompletedNoIncident", false, true);
    seedCountReport(
        COMPLETED_WITH_USER_TASKS_NO_INCIDENT_REPORT_ID,
        "businessValueCompletedWithUserTasksNoIncident",
        true,
        true);
  }

  /**
   * Seeds a completed-instance frequency (count) report, optionally constrained to instances that
   * contain user tasks and/or have no incidents. The result's single measure is the instance count.
   */
  private void seedCountReport(
      final String reportId,
      final String nameKey,
      final boolean requireUserTasks,
      final boolean requireNoIncident) {
    final ProcessFilterBuilder filterBuilder = ProcessFilterBuilder.filter();
    filterBuilder.completedInstancesOnly().add();
    if (requireUserTasks) {
      filterBuilder.userTaskFlowNodesOnly().add();
    }
    if (requireNoIncident) {
      filterBuilder.noIncidents().add();
    }
    final ProcessReportDataDto reportData =
        ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.FREQUENCY))
            .groupBy(new NoneGroupByDto())
            .distributedBy(new NoneDistributedByDto())
            .visualization(ProcessVisualization.NUMBER)
            .filter(filterBuilder.buildList())
            .businessValueReport(true)
            .build();
    reportWriter.createOrUpdateSingleProcessReport(
        reportId, null, reportData, nameKey, nameKey, null);
  }

  private void seedCycleTimeReport() {
    final ProcessReportDataDto reportData =
        ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.DURATION))
            .groupBy(new NoneGroupByDto())
            .distributedBy(new NoneDistributedByDto())
            .visualization(ProcessVisualization.NUMBER)
            .configuration(
                SingleReportConfigurationDto.builder()
                    .aggregationTypes(
                        new LinkedHashSet<>(
                            List.of(
                                new AggregationDto(AggregationType.AVERAGE),
                                new AggregationDto(AggregationType.PERCENTILE, 50.0),
                                new AggregationDto(AggregationType.PERCENTILE, 95.0))))
                    .build())
            .filter(ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList())
            .businessValueReport(true)
            .build();
    reportWriter.createOrUpdateSingleProcessReport(
        CYCLE_TIME_REPORT_ID, null, reportData, CYCLE_TIME_NAME, CYCLE_TIME_DESCRIPTION, null);
  }

  /**
   * Seeds a raw-data report of completed instances pre-sorted by descending duration. The Hub reads
   * only the first page (the slowest N instances) to render the Worst-Performing Instances tile.
   */
  private void seedWorstInstancesReport() {
    final ProcessReportDataDto reportData =
        ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            // Raw-data reports carry NO view entity — the registered command is
            // `rawData_none_none_...`.
            // Setting PROCESS_INSTANCE here yields `processInstance-rawData_...`, which Optimize
            // rejects.
            .view(new ProcessViewDto(ViewProperty.RAW_DATA))
            .groupBy(new NoneGroupByDto())
            .distributedBy(new NoneDistributedByDto())
            .visualization(ProcessVisualization.TABLE)
            .configuration(
                SingleReportConfigurationDto.builder()
                    .sorting(new ReportSortingDto(ProcessInstanceIndex.DURATION, SortOrder.DESC))
                    .build())
            .filter(ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList())
            .businessValueReport(true)
            .build();
    reportWriter.createOrUpdateSingleProcessReport(
        WORST_INSTANCES_REPORT_ID,
        null,
        reportData,
        WORST_INSTANCES_NAME,
        WORST_INSTANCES_DESCRIPTION,
        null);
  }

  /**
   * Seeds the agent cost report with two measures (SUM + AVG) over the per-instance {@code
   * agentTotalCost}. The Hub reads both measures to render the Total and Avg Agent Cost tiles — no
   * Hub-side arithmetic, no group-by-model (per-model pricing is resolved at import time).
   */
  private void seedAgentCostReport() {
    final ProcessReportDataDto reportData =
        ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(new ProcessViewDto(ProcessViewEntity.AGENT_INSTANCE, ViewProperty.COST))
            .groupBy(new NoneGroupByDto())
            .distributedBy(new NoneDistributedByDto())
            .visualization(ProcessVisualization.NUMBER)
            .configuration(
                SingleReportConfigurationDto.builder()
                    .aggregationTypes(
                        new LinkedHashSet<>(
                            List.of(
                                new AggregationDto(AggregationType.SUM),
                                new AggregationDto(AggregationType.AVERAGE))))
                    .build())
            .businessValueReport(true)
            .build();
    reportWriter.createOrUpdateSingleProcessReport(
        AGENT_COST_REPORT_ID, null, reportData, AGENT_COST_NAME, AGENT_COST_DESCRIPTION, null);
  }

  private static String reportId(final String seed) {
    return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
  }
}
