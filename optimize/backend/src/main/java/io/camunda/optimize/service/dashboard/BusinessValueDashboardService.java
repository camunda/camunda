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
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.EndDateGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessDefinitionKeyGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import io.camunda.optimize.service.db.reader.DashboardReader;
import io.camunda.optimize.service.db.writer.DashboardWriter;
import io.camunda.optimize.service.db.writer.ReportWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds the Business Value Dashboard (BVD) and its backing reports on startup.
 *
 * <p>Mirrors {@link AgenticControlDashboardService}: deterministic UUIDs, idempotent reconcile,
 * upsert-on-startup. Ships the L0/L1 tile reports the Hub-side BVD reads from.
 *
 * <p>A tile renders at a level only if it carries that level's section key. No report carries both:
 * Hub renders the report name verbatim, so a metric both views show under different headings needs
 * one report per view.
 */
@Component
public class BusinessValueDashboardService {

  public static final String BUSINESS_VALUE_DASHBOARD_ID = "business-value-dashboard";

  public static final int GRID_WIDTH = 18;

  // Top-N limit hint for per-process breakdown tiles. Backend sorts DESC and returns the full
  // result set; Hub reads TILE_CONFIG_TOP_N from the tile configuration and slices client-side,
  // matching the pattern used by AgenticControlDashboardService.
  public static final int PER_PROCESS_TOP_N = 5;
  public static final String TILE_CONFIG_TOP_N = "topN";

  public static final String BUSINESS_VALUE_DASHBOARD_NAME = "Business Value Dashboard";

  // Both automation-rate tiles: the metric is defined the same way, only the heading differs.
  public static final String KPI_AUTOMATION_RATE_DESCRIPTION =
      "Automated tasks ÷ all tasks (automated + human) on completed instances, for the selected "
          + "period. Includes subprocess tasks where available.";

  // L0 — portfolio view.
  public static final String KPI_COMPLETED_INSTANCES_NAME = "Completed instances";
  public static final String KPI_COMPLETED_INSTANCES_DESCRIPTION =
      "Total instances completed across all processes in the selected period.";
  public static final String KPI_TOP_BY_VOLUME_NAME = "Top " + PER_PROCESS_TOP_N + " by volume";
  public static final String KPI_TOP_BY_VOLUME_DESCRIPTION =
      "Processes ranked by completed-instance volume.";
  public static final String KPI_COMPLETED_INSTANCES_OVER_TIME_NAME =
      "Completed instances over time";
  public static final String KPI_COMPLETED_INSTANCES_OVER_TIME_DESCRIPTION =
      "Completed-instance volume across all processes over the selected period.";
  public static final String KPI_TOP_BY_CYCLE_TIME_NAME =
      "Top " + PER_PROCESS_TOP_N + " by cycle time (slowest first)";
  public static final String KPI_TOP_BY_CYCLE_TIME_DESCRIPTION =
      "Processes ranked by average cycle time, slowest first.";
  public static final String KPI_AGENTIC_PROCESSES_NAME = "Agentic processes";
  public static final String KPI_AGENTIC_PROCESSES_DESCRIPTION =
      "Processes that ran at least one agent.";
  public static final String KPI_AGGREGATED_AUTOMATION_RATE_NAME = "Aggregated automation rate";
  public static final String KPI_AUTOMATION_RATE_BY_PROCESS_NAME =
      "Automation rate — top " + PER_PROCESS_TOP_N + " processes by volume";
  public static final String KPI_AUTOMATION_RATE_BY_PROCESS_DESCRIPTION =
      "Percentage of automated tasks for the highest-volume processes.";

  // L1 — single-process view.
  public static final String KPI_VOLUME_NAME = "Volume";
  public static final String KPI_VOLUME_DESCRIPTION =
      "Real cases this process completed in the selected period.";
  public static final String KPI_CYCLE_TIME_NAME = "Cycle time";
  public static final String KPI_CYCLE_TIME_DESCRIPTION =
      "Average cycle time of completed instances in the range. The target is your SLA for this "
          + "process.";
  public static final String KPI_AUTOMATION_RATE_NAME = "Automation rate";
  public static final String KPI_MOMENTUM_NAME = "Momentum — completed instances";
  public static final String KPI_MOMENTUM_DESCRIPTION =
      "Completed-instance volume for this process over the selected period.";
  public static final String KPI_CYCLE_TIME_DISTRIBUTION_NAME = "Cycle time distribution";
  public static final String KPI_CYCLE_TIME_DISTRIBUTION_DESCRIPTION =
      "Where the median (P50) and worst-case (P95) durations sit relative to the average. "
          + "P50: half of instances finish faster. P95: only 5% take longer.";
  public static final String KPI_CYCLE_TIME_HISTORY_NAME = "Cycle time history";
  public static final String KPI_CYCLE_TIME_HISTORY_DESCRIPTION =
      "Average cycle time for this process over the selected period.";

  // Ids are UUIDv3 over these seed strings, so a seed is part of the persisted contract: changing
  // one orphans its report on every existing installation. Hence seeds outliving display names.
  public static final String WORK_HANDLED_TOTAL_REPORT_ID =
      UUID.nameUUIDFromBytes("bv-work-handled-total".getBytes(StandardCharsets.UTF_8)).toString();
  public static final String DURATION_AVG_TOTAL_REPORT_ID =
      UUID.nameUUIDFromBytes("bv-duration-avg-total".getBytes(StandardCharsets.UTF_8)).toString();
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
  public static final String AUTOMATION_RATE_BY_PROCESS_REPORT_ID =
      UUID.nameUUIDFromBytes("bv-automation-rate-by-process".getBytes(StandardCharsets.UTF_8))
          .toString();
  public static final String AUTOMATION_RATE_AGGREGATE_REPORT_ID =
      UUID.nameUUIDFromBytes("bv-automation-rate-aggregate".getBytes(StandardCharsets.UTF_8))
          .toString();

  // L1 copies of three metrics the portfolio also shows: same shape, own name.
  public static final String VOLUME_TOTAL_L1_REPORT_ID =
      UUID.nameUUIDFromBytes("bv-volume-total-l1".getBytes(StandardCharsets.UTF_8)).toString();
  public static final String COUNT_BY_DATE_L1_REPORT_ID =
      UUID.nameUUIDFromBytes("bv-count-by-date-l1".getBytes(StandardCharsets.UTF_8)).toString();
  public static final String AUTOMATION_RATE_AGGREGATE_L1_REPORT_ID =
      UUID.nameUUIDFromBytes("bv-automation-rate-aggregate-l1".getBytes(StandardCharsets.UTF_8))
          .toString();

  // Tile configuration keys consumed by the Business Value Dashboard frontend. A tile renders at a
  // level only if it carries that level's key.
  public static final String TILE_CONFIG_L0_SECTION = "l0Section";
  public static final String TILE_CONFIG_L1_SECTION = "l1Section";
  // Section values that group tiles at the dashboard root (L0).
  public static final String L0_SECTION_ACTIVITY = "activity";
  public static final String L0_SECTION_CYCLE_TIME = "cycleTime";
  public static final String L0_SECTION_AGENTIC_ADOPTION = "agenticAdoption";
  public static final String L0_SECTION_AUTOMATION = "automation";
  // Section values that group tiles once a single process is selected (L1). Ungrouped tiles render
  // without a heading.
  public static final String L1_SECTION_OVERVIEW = "overview";
  public static final String L1_SECTION_UNGROUPED = "ungrouped";

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
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public void init() {
    if (configurationService.getEntityConfiguration().getCreateOnStartup()) {
      LOG.info("Reconciling Business Value dashboard");
      reconcile();
      LOG.info("Finished reconciling Business Value dashboard");
    }
  }

  public void reconcile() {
    final List<DashboardReportTileDto> tiles = new ArrayList<>();

    tiles.add(buildCompletedInstancesTotalTile());
    tiles.add(buildVolumeTotalL1Tile());
    tiles.add(buildDurationAvgTotalTile());
    tiles.add(buildCountByProcessTile());
    tiles.add(buildDurationByProcessTile());
    tiles.add(buildCountByDateTile());
    tiles.add(buildCountByDateL1Tile());
    tiles.add(buildAgentPresenceByProcessTile());
    tiles.add(buildDurationPercentilesTile());
    tiles.add(buildDurationByDateTile());
    tiles.add(buildAutomationRateAggregateTile());
    tiles.add(buildAutomationRateAggregateL1Tile());
    tiles.add(buildAutomationRateByProcessTile());

    final DashboardDefinitionRestDto dashboard = buildDashboard(tiles);
    if (dashboardReader.getDashboard(BUSINESS_VALUE_DASHBOARD_ID).isEmpty()) {
      dashboardWriter.saveDashboard(dashboard);
    } else {
      dashboardWriter.updateDashboard(toUpdateDto(dashboard), BUSINESS_VALUE_DASHBOARD_ID);
    }
  }

  // Total completed instances. Separate from the per-process tile because groupBy=NONE plus
  // distributedBy=PROCESS returns HYPER_MAP rather than NUMBER.
  private DashboardReportTileDto buildCompletedInstancesTotalTile() {
    reportWriter.createOrUpdateSingleProcessReport(
        WORK_HANDLED_TOTAL_REPORT_ID,
        null,
        completedInstanceCountData(),
        KPI_COMPLETED_INSTANCES_NAME,
        KPI_COMPLETED_INSTANCES_DESCRIPTION,
        null);
    return buildTile(
        WORK_HANDLED_TOTAL_REPORT_ID,
        new PositionDto(0, 0),
        new DimensionDto(9, 4),
        L0_SECTION_ACTIVITY,
        null);
  }

  private DashboardReportTileDto buildVolumeTotalL1Tile() {
    reportWriter.createOrUpdateSingleProcessReport(
        VOLUME_TOTAL_L1_REPORT_ID,
        null,
        completedInstanceCountData(),
        KPI_VOLUME_NAME,
        KPI_VOLUME_DESCRIPTION,
        null);
    return buildTile(
        VOLUME_TOTAL_L1_REPORT_ID,
        new PositionDto(12, 20),
        new DimensionDto(6, 2),
        null,
        L1_SECTION_OVERVIEW);
  }

  private ProcessReportDataDto completedInstanceCountData() {
    return ProcessReportDataDto.builder()
        .definitions(Collections.emptyList())
        .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.FREQUENCY))
        .groupBy(new NoneGroupByDto())
        .distributedBy(new NoneDistributedByDto())
        .visualization(ProcessVisualization.NUMBER)
        .filter(ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList())
        .businessValueReport(true)
        .build();
  }

  // Average cycle time as a single NUMBER, shown next to volume in the L1 overview.
  private DashboardReportTileDto buildDurationAvgTotalTile() {
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
                            Collections.singletonList(new AggregationDto(AggregationType.AVERAGE))))
                    .build())
            .filter(ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList())
            .businessValueReport(true)
            .build();
    reportWriter.createOrUpdateSingleProcessReport(
        DURATION_AVG_TOTAL_REPORT_ID,
        null,
        reportData,
        KPI_CYCLE_TIME_NAME,
        KPI_CYCLE_TIME_DESCRIPTION,
        null);
    return buildTile(
        DURATION_AVG_TOTAL_REPORT_ID,
        new PositionDto(0, 20),
        new DimensionDto(6, 2),
        null,
        L1_SECTION_OVERVIEW);
  }

  // Top-N processes by completed-instance volume, sorted DESC. Hub reads TILE_CONFIG_TOP_N and
  // slices client-side (see camunda-hub#26997 for the follow-up moving the slice server-side).
  private DashboardReportTileDto buildCountByProcessTile() {
    final ProcessReportDataDto reportData =
        ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.FREQUENCY))
            .groupBy(new ProcessDefinitionKeyGroupByDto())
            .distributedBy(new NoneDistributedByDto())
            .visualization(ProcessVisualization.BAR)
            .configuration(
                SingleReportConfigurationDto.builder()
                    .horizontalBar(true)
                    .sorting(new ReportSortingDto(ReportSortingDto.SORT_BY_VALUE, SortOrder.DESC))
                    .build())
            .filter(ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList())
            .businessValueReport(true)
            .build();
    reportWriter.createOrUpdateSingleProcessReport(
        COUNT_BY_PROCESS_REPORT_ID,
        null,
        reportData,
        KPI_TOP_BY_VOLUME_NAME,
        KPI_TOP_BY_VOLUME_DESCRIPTION,
        null);
    return buildTile(
        COUNT_BY_PROCESS_REPORT_ID,
        new PositionDto(9, 0),
        new DimensionDto(9, 4),
        L0_SECTION_ACTIVITY,
        null,
        PER_PROCESS_TOP_N);
  }

  // Completed-instance trend over the selected period. AUTOMATIC bucket unit — Optimize picks
  // hour/day/week/month so the x-axis stays readable across every filter preset.
  private DashboardReportTileDto buildCountByDateTile() {
    reportWriter.createOrUpdateSingleProcessReport(
        COUNT_BY_DATE_REPORT_ID,
        null,
        completedInstanceCountByDateData(),
        KPI_COMPLETED_INSTANCES_OVER_TIME_NAME,
        KPI_COMPLETED_INSTANCES_OVER_TIME_DESCRIPTION,
        null);
    return buildTile(
        COUNT_BY_DATE_REPORT_ID,
        new PositionDto(0, 4),
        new DimensionDto(GRID_WIDTH, 4),
        L0_SECTION_ACTIVITY,
        null);
  }

  private DashboardReportTileDto buildCountByDateL1Tile() {
    reportWriter.createOrUpdateSingleProcessReport(
        COUNT_BY_DATE_L1_REPORT_ID,
        null,
        completedInstanceCountByDateData(),
        KPI_MOMENTUM_NAME,
        KPI_MOMENTUM_DESCRIPTION,
        null);
    return buildTile(
        COUNT_BY_DATE_L1_REPORT_ID,
        new PositionDto(0, 22),
        new DimensionDto(GRID_WIDTH, 4),
        null,
        L1_SECTION_UNGROUPED);
  }

  private ProcessReportDataDto completedInstanceCountByDateData() {
    final EndDateGroupByDto groupBy = new EndDateGroupByDto();
    groupBy.setValue(new DateGroupByValueDto(AggregateByDateUnit.AUTOMATIC));
    return ProcessReportDataDto.builder()
        .definitions(Collections.emptyList())
        .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.FREQUENCY))
        .groupBy(groupBy)
        .distributedBy(new NoneDistributedByDto())
        .visualization(ProcessVisualization.LINE)
        .filter(ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList())
        .businessValueReport(true)
        .build();
  }

  // Top-N processes by average cycle time, sorted DESC. Horizontal BAR.
  private DashboardReportTileDto buildDurationByProcessTile() {
    final ProcessReportDataDto reportData =
        ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.DURATION))
            .groupBy(new ProcessDefinitionKeyGroupByDto())
            .distributedBy(new NoneDistributedByDto())
            .visualization(ProcessVisualization.BAR)
            .configuration(
                SingleReportConfigurationDto.builder()
                    .aggregationTypes(
                        new LinkedHashSet<>(
                            Collections.singletonList(new AggregationDto(AggregationType.AVERAGE))))
                    .horizontalBar(true)
                    .sorting(new ReportSortingDto(ReportSortingDto.SORT_BY_VALUE, SortOrder.DESC))
                    .build())
            .filter(ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList())
            .businessValueReport(true)
            .build();
    reportWriter.createOrUpdateSingleProcessReport(
        DURATION_BY_PROCESS_REPORT_ID,
        null,
        reportData,
        KPI_TOP_BY_CYCLE_TIME_NAME,
        KPI_TOP_BY_CYCLE_TIME_DESCRIPTION,
        null);
    return buildTile(
        DURATION_BY_PROCESS_REPORT_ID,
        new PositionDto(0, 8),
        new DimensionDto(9, 4),
        L0_SECTION_CYCLE_TIME,
        null,
        PER_PROCESS_TOP_N);
  }

  // Cycle time distribution: AVG, P50 and P95 in one report.
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
    return buildTile(
        DURATION_PERCENTILES_REPORT_ID,
        new PositionDto(0, 26),
        new DimensionDto(GRID_WIDTH, 4),
        null,
        L1_SECTION_UNGROUPED);
  }

  // Cycle-time history. AUTOMATIC bucket unit — see buildCountByDateTile.
  private DashboardReportTileDto buildDurationByDateTile() {
    final EndDateGroupByDto groupBy = new EndDateGroupByDto();
    groupBy.setValue(new DateGroupByValueDto(AggregateByDateUnit.AUTOMATIC));
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
    return buildTile(
        DURATION_BY_DATE_REPORT_ID,
        new PositionDto(0, 30),
        new DimensionDto(GRID_WIDTH, 4),
        null,
        L1_SECTION_UNGROUPED);
  }

  // Processes running at least one agent, using total tokens as the presence indicator. Rendered
  // as a donut.
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
        KPI_AGENTIC_PROCESSES_NAME,
        KPI_AGENTIC_PROCESSES_DESCRIPTION,
        null);
    return buildTile(
        AGENT_PRESENCE_BY_PROCESS_REPORT_ID,
        new PositionDto(0, 12),
        new DimensionDto(6, 4),
        L0_SECTION_AGENTIC_ADOPTION,
        null);
  }

  // Aggregate automation rate across all processes. Maps to
  // PROCESS_INSTANCE_AUTOMATION_RATE_GROUP_BY_NONE (NUMBER). Rendered as a single value.
  private DashboardReportTileDto buildAutomationRateAggregateTile() {
    reportWriter.createOrUpdateSingleProcessReport(
        AUTOMATION_RATE_AGGREGATE_REPORT_ID,
        null,
        automationRateAggregateData(),
        KPI_AGGREGATED_AUTOMATION_RATE_NAME,
        KPI_AUTOMATION_RATE_DESCRIPTION,
        null);
    return buildTile(
        AUTOMATION_RATE_AGGREGATE_REPORT_ID,
        new PositionDto(0, 16),
        new DimensionDto(9, 4),
        L0_SECTION_AUTOMATION,
        null);
  }

  private DashboardReportTileDto buildAutomationRateAggregateL1Tile() {
    reportWriter.createOrUpdateSingleProcessReport(
        AUTOMATION_RATE_AGGREGATE_L1_REPORT_ID,
        null,
        automationRateAggregateData(),
        KPI_AUTOMATION_RATE_NAME,
        KPI_AUTOMATION_RATE_DESCRIPTION,
        null);
    return buildTile(
        AUTOMATION_RATE_AGGREGATE_L1_REPORT_ID,
        new PositionDto(6, 20),
        new DimensionDto(6, 2),
        null,
        L1_SECTION_OVERVIEW);
  }

  private ProcessReportDataDto automationRateAggregateData() {
    return ProcessReportDataDto.builder()
        .definitions(Collections.emptyList())
        .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.AUTOMATION_RATE))
        .groupBy(new NoneGroupByDto())
        .distributedBy(new NoneDistributedByDto())
        .visualization(ProcessVisualization.NUMBER)
        .filter(ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList())
        .businessValueReport(true)
        .build();
  }

  // Top-N processes by automation rate, sorted descending. Maps to
  // PROCESS_INSTANCE_AUTOMATION_RATE_GROUP_BY_PROCESS_DEFINITION_KEY (MAP). Horizontal BAR.
  private DashboardReportTileDto buildAutomationRateByProcessTile() {
    final ProcessReportDataDto reportData =
        ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(
                new ProcessViewDto(
                    ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.AUTOMATION_RATE))
            .groupBy(new ProcessDefinitionKeyGroupByDto())
            .distributedBy(new NoneDistributedByDto())
            .visualization(ProcessVisualization.BAR)
            .configuration(
                SingleReportConfigurationDto.builder()
                    .horizontalBar(true)
                    .sorting(new ReportSortingDto(ReportSortingDto.SORT_BY_VALUE, SortOrder.DESC))
                    .build())
            .filter(ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList())
            .businessValueReport(true)
            .build();
    reportWriter.createOrUpdateSingleProcessReport(
        AUTOMATION_RATE_BY_PROCESS_REPORT_ID,
        null,
        reportData,
        KPI_AUTOMATION_RATE_BY_PROCESS_NAME,
        KPI_AUTOMATION_RATE_BY_PROCESS_DESCRIPTION,
        null);
    return buildTile(
        AUTOMATION_RATE_BY_PROCESS_REPORT_ID,
        new PositionDto(9, 16),
        new DimensionDto(9, 4),
        L0_SECTION_AUTOMATION,
        null,
        PER_PROCESS_TOP_N);
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

  // A null section means the tile is not rendered at that level. A non-null topN embeds the
  // client-side slicing hint under TILE_CONFIG_TOP_N; Hub reads it after evaluate.
  private DashboardReportTileDto buildTile(
      final String reportId,
      final PositionDto position,
      final DimensionDto dimensions,
      final String l0Section,
      final String l1Section) {
    return buildTile(reportId, position, dimensions, l0Section, l1Section, null);
  }

  private DashboardReportTileDto buildTile(
      final String reportId,
      final PositionDto position,
      final DimensionDto dimensions,
      final String l0Section,
      final String l1Section,
      final Integer topN) {
    final Map<String, String> configuration = new LinkedHashMap<>();
    if (l0Section != null) {
      configuration.put(TILE_CONFIG_L0_SECTION, l0Section);
    }
    if (l1Section != null) {
      configuration.put(TILE_CONFIG_L1_SECTION, l1Section);
    }
    if (topN != null) {
      configuration.put(TILE_CONFIG_TOP_N, String.valueOf(topN));
    }
    return DashboardReportTileDto.builder()
        .id(reportId)
        .type(DashboardTileType.OPTIMIZE_REPORT)
        .position(position)
        .dimensions(dimensions)
        .configuration(configuration)
        .build();
  }

  private DashboardDefinitionUpdateDto toUpdateDto(final DashboardDefinitionRestDto source) {
    final DashboardDefinitionUpdateDto updateDto = new DashboardDefinitionUpdateDto();
    updateDto.setName(source.getName());
    updateDto.setTiles(source.getTiles());
    return updateDto;
  }
}
