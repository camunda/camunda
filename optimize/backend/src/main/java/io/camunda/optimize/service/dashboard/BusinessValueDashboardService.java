/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionUpdateDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DimensionDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.PositionDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.NoneDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessReportDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.EndDateGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessDefinitionKeyGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.service.db.reader.DashboardReader;
import io.camunda.optimize.service.db.writer.DashboardWriter;
import io.camunda.optimize.service.db.writer.ReportWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Seeds the Business Value Dashboard (BVD) and its backing reports on startup.
 *
 * <p>Mirrors {@link AgenticControlDashboardService}: deterministic UUIDs, idempotent reconcile,
 * upsert-on-startup. Ships the L0/L1 tile reports the Hub-side BVD (M3) and the target-driven
 * overview index (M2) will read from.
 *
 * <p>Note: the automation-rate tiles depend on the native {@code PROCESS_VIEW_AUTOMATION_RATE} view
 * landing in a follow-up (see docs/business-value/bvd-target-technical-design.md §3.3). Those
 * seeded reports are intentionally omitted here until that view exists.
 */
@Component
public class BusinessValueDashboardService {

  public static final String BUSINESS_VALUE_DASHBOARD_ID = "business-value-dashboard";

  // Names and descriptions ship as plain English. Hub (the sole FE consumer)
  // owns translation via its own i18next namespace and keys labels off the
  // stable report UUID, not this field. Human-readable text keeps the endpoint
  // sensible for curl / docs / debugging.
  public static final String BUSINESS_VALUE_DASHBOARD_NAME = "Business Value Dashboard";

  public static final String KPI_WORK_HANDLED_NAME = "Work handled";
  public static final String KPI_WORK_HANDLED_DESCRIPTION =
      "Completed process instances in the selected period.";
  public static final String KPI_MOMENTUM_NAME = "Momentum";
  public static final String KPI_MOMENTUM_DESCRIPTION = "Weekly completed-instance volume trend.";
  public static final String KPI_CYCLE_TIME_BY_PROCESS_NAME = "Cycle time by process";
  public static final String KPI_CYCLE_TIME_BY_PROCESS_DESCRIPTION =
      "Average cycle time per process definition.";
  public static final String KPI_CYCLE_TIME_DISTRIBUTION_NAME = "Cycle time distribution";
  public static final String KPI_CYCLE_TIME_DISTRIBUTION_DESCRIPTION =
      "Average, P50 and P95 cycle time of completed instances.";
  public static final String KPI_CYCLE_TIME_HISTORY_NAME = "Cycle time history";
  public static final String KPI_CYCLE_TIME_HISTORY_DESCRIPTION = "Weekly average cycle time.";
  public static final String KPI_AGENTIC_PRESENCE_NAME = "Agentic presence";
  public static final String KPI_AGENTIC_PRESENCE_DESCRIPTION =
      "Processes that ran at least one agent.";

  public static final String WORK_HANDLED_TOTAL_REPORT_ID =
      UUID.nameUUIDFromBytes("bv-work-handled-total".getBytes(StandardCharsets.UTF_8)).toString();
  public static final String COUNT_BY_PROCESS_REPORT_ID =
      UUID.nameUUIDFromBytes("bv-count-by-process".getBytes(StandardCharsets.UTF_8)).toString();
  public static final String COUNT_BY_DATE_REPORT_ID =
      UUID.nameUUIDFromBytes("bv-count-by-date".getBytes(StandardCharsets.UTF_8)).toString();
  public static final String DURATION_BY_PROCESS_REPORT_ID =
      UUID.nameUUIDFromBytes("bv-duration-by-process".getBytes(StandardCharsets.UTF_8)).toString();
  public static final String DURATION_PERCENTILES_REPORT_ID =
      UUID.nameUUIDFromBytes("bv-duration-percentiles".getBytes(StandardCharsets.UTF_8)).toString();
  public static final String DURATION_BY_DATE_REPORT_ID =
      UUID.nameUUIDFromBytes("bv-duration-by-date".getBytes(StandardCharsets.UTF_8)).toString();
  public static final String AGENT_PRESENCE_BY_PROCESS_REPORT_ID =
      UUID.nameUUIDFromBytes("bv-agent-presence-by-process".getBytes(StandardCharsets.UTF_8))
          .toString();

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(BusinessValueDashboardService.class);

  private final DashboardWriter dashboardWriter;
  private final DashboardReader dashboardReader;
  private final ReportWriter reportWriter;
  private final ConfigurationService configurationService;

  public BusinessValueDashboardService(
      final DashboardWriter dashboardWriter,
      final DashboardReader dashboardReader,
      final ReportWriter reportWriter,
      final ConfigurationService configurationService) {
    this.dashboardWriter = dashboardWriter;
    this.dashboardReader = dashboardReader;
    this.reportWriter = reportWriter;
    this.configurationService = configurationService;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void init() {
    if (configurationService.getEntityConfiguration().getCreateOnStartup()) {
      LOG.info("Reconciling Business Value dashboard");
      reconcile();
      LOG.info("Finished reconciling Business Value dashboard");
    }
  }

  public void reconcile() {
    final List<DashboardReportTileDto> tiles = new ArrayList<>();
    tiles.add(buildWorkHandledTotalTile());
    tiles.add(buildCountByProcessTile());
    tiles.add(buildCountByDateTile());
    tiles.add(buildDurationByProcessTile());
    tiles.add(buildDurationPercentilesTile());
    tiles.add(buildDurationByDateTile());
    tiles.add(buildAgentPresenceByProcessTile());

    final DashboardDefinitionRestDto dashboard = buildDashboard(tiles);
    if (dashboardReader.getDashboard(BUSINESS_VALUE_DASHBOARD_ID).isEmpty()) {
      dashboardWriter.saveDashboard(dashboard);
    } else {
      dashboardWriter.updateDashboard(toUpdateDto(dashboard), BUSINESS_VALUE_DASHBOARD_ID);
    }
  }

  // Work handled — total completed-instance count as a single NUMBER.
  // Maps to PROCESS_INSTANCE_FREQUENCY_GROUP_BY_NONE (view=FREQUENCY, groupBy=NONE,
  // distributedBy=NONE, result=NUMBER). Kept separate from the per-process BAR tile below
  // because the (groupBy=NONE + distributedBy=PROCESS) combo returns HYPER_MAP, not NUMBER,
  // and there is no FE aggregation over that result today.
  private DashboardReportTileDto buildWorkHandledTotalTile() {
    final ProcessReportDataDto reportData =
        ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.FREQUENCY))
            .groupBy(new NoneGroupByDto())
            .distributedBy(new NoneDistributedByDto())
            .visualization(ProcessVisualization.NUMBER)
            .filter(ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList())
            .businessValueReport(true)
            .build();
    reportWriter.createOrUpdateSingleProcessReport(
        WORK_HANDLED_TOTAL_REPORT_ID,
        null,
        reportData,
        KPI_WORK_HANDLED_NAME,
        KPI_WORK_HANDLED_DESCRIPTION,
        null);
    return buildTile(WORK_HANDLED_TOTAL_REPORT_ID, new PositionDto(0, 0), new DimensionDto(9, 4));
  }

  // Top-5 processes by completed-instance count, per-process breakdown rendered as a BAR chart.
  private DashboardReportTileDto buildCountByProcessTile() {
    final ProcessReportDataDto reportData =
        ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.FREQUENCY))
            .groupBy(new NoneGroupByDto())
            .distributedBy(processDistributedBy())
            .visualization(ProcessVisualization.BAR)
            .filter(ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList())
            .businessValueReport(true)
            .build();
    reportWriter.createOrUpdateSingleProcessReport(
        COUNT_BY_PROCESS_REPORT_ID,
        null,
        reportData,
        KPI_WORK_HANDLED_NAME,
        KPI_WORK_HANDLED_DESCRIPTION,
        null);
    return buildTile(COUNT_BY_PROCESS_REPORT_ID, new PositionDto(9, 0), new DimensionDto(9, 4));
  }

  // Momentum (volume trend over time) + L1 volume history.
  private DashboardReportTileDto buildCountByDateTile() {
    final EndDateGroupByDto groupBy = new EndDateGroupByDto();
    groupBy.setValue(new DateGroupByValueDto(AggregateByDateUnit.WEEK));
    final ProcessReportDataDto reportData =
        ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.FREQUENCY))
            .groupBy(groupBy)
            .distributedBy(new NoneDistributedByDto())
            .visualization(ProcessVisualization.LINE)
            .filter(ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList())
            .businessValueReport(true)
            .build();
    reportWriter.createOrUpdateSingleProcessReport(
        COUNT_BY_DATE_REPORT_ID,
        null,
        reportData,
        KPI_MOMENTUM_NAME,
        KPI_MOMENTUM_DESCRIPTION,
        null);
    return buildTile(COUNT_BY_DATE_REPORT_ID, new PositionDto(9, 0), new DimensionDto(9, 4));
  }

  // Cycle time top-5 by process (average duration).
  private DashboardReportTileDto buildDurationByProcessTile() {
    final ProcessReportDataDto reportData =
        ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.DURATION))
            .groupBy(new NoneGroupByDto())
            .distributedBy(processDistributedBy())
            .visualization(ProcessVisualization.BAR)
            .configuration(
                SingleReportConfigurationDto.builder()
                    .aggregationTypes(
                        new LinkedHashSet<>(
                            Collections.singletonList(new AggregationDto(AggregationType.AVERAGE))))
                    .build())
            .filter(ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList())
            .businessValueReport(true)
            .build();
    reportWriter.createOrUpdateSingleProcessReport(
        DURATION_BY_PROCESS_REPORT_ID,
        null,
        reportData,
        KPI_CYCLE_TIME_BY_PROCESS_NAME,
        KPI_CYCLE_TIME_BY_PROCESS_DESCRIPTION,
        null);
    return buildTile(DURATION_BY_PROCESS_REPORT_ID, new PositionDto(0, 4), new DimensionDto(9, 4));
  }

  // L1 cycle-time distribution — AVG + P50 + P95 in one report.
  private DashboardReportTileDto buildDurationPercentilesTile() {
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
        DURATION_PERCENTILES_REPORT_ID,
        null,
        reportData,
        KPI_CYCLE_TIME_DISTRIBUTION_NAME,
        KPI_CYCLE_TIME_DISTRIBUTION_DESCRIPTION,
        null);
    return buildTile(DURATION_PERCENTILES_REPORT_ID, new PositionDto(9, 4), new DimensionDto(9, 4));
  }

  // L1 cycle-time history (weekly).
  private DashboardReportTileDto buildDurationByDateTile() {
    final EndDateGroupByDto groupBy = new EndDateGroupByDto();
    groupBy.setValue(new DateGroupByValueDto(AggregateByDateUnit.WEEK));
    final ProcessReportDataDto reportData =
        ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.DURATION))
            .groupBy(groupBy)
            .distributedBy(new NoneDistributedByDto())
            .visualization(ProcessVisualization.LINE)
            .configuration(
                SingleReportConfigurationDto.builder()
                    .aggregationTypes(
                        new LinkedHashSet<>(
                            Collections.singletonList(new AggregationDto(AggregationType.AVERAGE))))
                    .build())
            .filter(ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList())
            .businessValueReport(true)
            .build();
    reportWriter.createOrUpdateSingleProcessReport(
        DURATION_BY_DATE_REPORT_ID,
        null,
        reportData,
        KPI_CYCLE_TIME_HISTORY_NAME,
        KPI_CYCLE_TIME_HISTORY_DESCRIPTION,
        null);
    return buildTile(DURATION_BY_DATE_REPORT_ID, new PositionDto(0, 8), new DimensionDto(9, 4));
  }

  // Agentic presence: which processes run at least one agent. Uses AGENT_INSTANCE total tokens
  // as a non-zero presence indicator, grouped by process definition. Only the
  // (view, groupBy, distributedBy) combos registered in ProcessExecutionPlan are valid — for
  // AGENT_TOTAL_TOKENS the per-process shape is GROUP_BY_PROCESS_DEFINITION_KEY +
  // DISTRIBUTED_BY_NONE
  // (see PROCESS_AGENT_TOTAL_TOKENS_GROUP_BY_PROCESS_DEFINITION_KEY). FE renders as a donut.
  private DashboardReportTileDto buildAgentPresenceByProcessTile() {
    final ProcessReportDataDto reportData =
        ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(new ProcessViewDto(ProcessViewEntity.AGENT_INSTANCE, ViewProperty.TOTAL_TOKENS))
            .groupBy(new ProcessDefinitionKeyGroupByDto())
            .distributedBy(new NoneDistributedByDto())
            .visualization(ProcessVisualization.PIE)
            .configuration(
                SingleReportConfigurationDto.builder()
                    .aggregationTypes(
                        new LinkedHashSet<>(
                            Collections.singletonList(new AggregationDto(AggregationType.SUM))))
                    .build())
            .filter(
                ProcessFilterBuilder.filter()
                    .completedInstancesOnly()
                    .add()
                    .hasAgentInstances()
                    .add()
                    .buildList())
            .businessValueReport(true)
            .build();
    reportWriter.createOrUpdateSingleProcessReport(
        AGENT_PRESENCE_BY_PROCESS_REPORT_ID,
        null,
        reportData,
        KPI_AGENTIC_PRESENCE_NAME,
        KPI_AGENTIC_PRESENCE_DESCRIPTION,
        null);
    return buildTile(
        AGENT_PRESENCE_BY_PROCESS_REPORT_ID, new PositionDto(9, 8), new DimensionDto(9, 4));
  }

  // Distribute results by process definition so a single report can drive both aggregate
  // and per-process (top-5) tiles in the FE.
  private ProcessReportDistributedByDto<?> processDistributedBy() {
    return new ProcessDistributedByDto();
  }

  private DashboardDefinitionRestDto buildDashboard(final List<DashboardReportTileDto> tiles) {
    final DashboardDefinitionRestDto dashboard = new DashboardDefinitionRestDto();
    dashboard.setId(BUSINESS_VALUE_DASHBOARD_ID);
    dashboard.setName(BUSINESS_VALUE_DASHBOARD_NAME);
    dashboard.setBusinessValueDashboard(true);
    dashboard.setCollectionId(null);
    dashboard.setTiles(new ArrayList<>(tiles));
    return dashboard;
  }

  private DashboardReportTileDto buildTile(
      final String reportId, final PositionDto position, final DimensionDto dimensions) {
    return DashboardReportTileDto.builder()
        .id(reportId)
        .type(DashboardTileType.OPTIMIZE_REPORT)
        .position(position)
        .dimensions(dimensions)
        .configuration(Map.of())
        .build();
  }

  private DashboardDefinitionUpdateDto toUpdateDto(final DashboardDefinitionRestDto source) {
    final DashboardDefinitionUpdateDto updateDto = new DashboardDefinitionUpdateDto();
    updateDto.setName(source.getName());
    updateDto.setTiles(source.getTiles());
    return updateDto;
  }
}
