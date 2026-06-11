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
import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardFilterDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardInstanceEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardProcessScopeFilterDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardProcessScopeFilterDataDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DimensionDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.PositionDto;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RollingDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.NoneDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.EndDateGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
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

@Component
public class AgenticControlDashboardService {

  public static final String AGENTIC_DASHBOARD_ID = "agentic-control-plane-dashboard";

  // Localization code resolved under the "agenticControl" category by DashboardRestMapper ->
  // LocalizationService.
  public static final String AGENTIC_DASHBOARD_NAME = "agenticControlPlaneDashboardName";

  public static final String KPI_EXECUTION_COMPLETED_NAME = "agenticKpiExecutionCompletedName";
  public static final String KPI_EXECUTION_COMPLETED_DESCRIPTION =
      "agenticKpiExecutionCompletedDescription";
  public static final String KPI_EXECUTION_AVG_DURATION_NAME = "agenticKpiExecutionAvgDurationName";
  public static final String KPI_EXECUTION_AVG_DURATION_DESCRIPTION =
      "agenticKpiExecutionAvgDurationDescription";
  public static final String KPI_EXECUTION_INCIDENT_RATE_NAME =
      "agenticKpiExecutionIncidentRateName";
  public static final String KPI_EXECUTION_INCIDENT_RATE_DESCRIPTION =
      "agenticKpiExecutionIncidentRateDescription";
  public static final String KPI_EXECUTION_AVG_TOKENS_NAME = "agenticKpiExecutionAvgTokensName";
  public static final String KPI_EXECUTION_AVG_TOKENS_DESCRIPTION =
      "agenticKpiExecutionAvgTokensDescription";
  public static final String KPI_EXECUTION_MEDIAN_TOKENS_NAME =
      "agenticKpiExecutionMedianTokensName";
  public static final String KPI_EXECUTION_MEDIAN_TOKENS_DESCRIPTION =
      "agenticKpiExecutionMedianTokensDescription";
  public static final String KPI_TOKEN_TREND_INPUT_NAME = "agenticKpiTokenTrendInputName";
  public static final String KPI_TOKEN_TREND_OUTPUT_NAME = "agenticKpiTokenTrendOutputName";
  public static final String KPI_TOKEN_TREND_NAME = "agenticKpiTokenTrendName";

  // Deterministic report IDs — derived from fixed seed strings so IDs are stable across restarts
  // and DB reimports. Same seed always produces the same UUID (UUID v3 / name-based).
  public static final String KPI_COMPLETED_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-kpi-completed-instances".getBytes(StandardCharsets.UTF_8))
          .toString();
  public static final String KPI_AVG_DURATION_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-kpi-avg-duration".getBytes(StandardCharsets.UTF_8))
          .toString();
  public static final String KPI_INCIDENT_RATE_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-kpi-incident-rate".getBytes(StandardCharsets.UTF_8))
          .toString();
  public static final String KPI_AVG_TOKENS_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-kpi-avg-tokens".getBytes(StandardCharsets.UTF_8)).toString();
  public static final String KPI_MEDIAN_TOKENS_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-kpi-median-tokens".getBytes(StandardCharsets.UTF_8))
          .toString();
  public static final String TOKEN_TREND_INPUT_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-token-trend-input".getBytes(StandardCharsets.UTF_8))
          .toString();
  public static final String TOKEN_TREND_OUTPUT_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-token-trend-output".getBytes(StandardCharsets.UTF_8))
          .toString();
  public static final String TOKEN_TREND_COMBINED_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-token-trend-combined".getBytes(StandardCharsets.UTF_8))
          .toString();

  private static final long INSTANCE_END_DATE_ROLLING_DAYS = 30L;

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(AgenticControlDashboardService.class);

  private final DashboardWriter dashboardWriter;
  private final DashboardReader dashboardReader;
  private final ReportWriter reportWriter;
  private final ConfigurationService configurationService;

  public AgenticControlDashboardService(
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
      LOG.info("Reconciling Agentic Control Plane dashboard");
      reconcile();
      LOG.info("Finished reconciling Agentic Control Plane dashboard");
    }
  }

  public void reconcile() {
    // Always upsert the five KPI reports so config changes are applied on every restart.
    // Report IDs are deterministic so upserts are safe and tile references never orphan.
    final List<DashboardReportTileDto> tiles = new ArrayList<>();
    tiles.add(buildCompletedInstancesReport());
    tiles.add(buildAvgDurationReport());
    tiles.add(buildIncidentRateReport());
    tiles.add(buildAvgTokensReport());
    tiles.add(buildMedianTokensReport());
    tiles.add(buildTokenTrendReport());

    // Always upsert the dashboard too — tile list can grow across versions and the cold-start
    // guard would leave an existing deployment stuck on the old layout.
    final DashboardDefinitionRestDto dashboard = buildAgentDashboard(tiles);
    if (dashboardReader.getDashboard(AGENTIC_DASHBOARD_ID).isEmpty()) {
      dashboardWriter.saveDashboard(dashboard);
    } else {
      dashboardWriter.updateDashboard(toUpdateDto(dashboard), AGENTIC_DASHBOARD_ID);
    }
  }

  private DashboardReportTileDto buildCompletedInstancesReport() {
    final ProcessReportDataDto reportData =
        ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.FREQUENCY))
            .groupBy(new NoneGroupByDto())
            .distributedBy(new NoneDistributedByDto())
            .visualization(ProcessVisualization.NUMBER)
            .filter(
                ProcessFilterBuilder.filter()
                    .completedInstancesOnly()
                    .add()
                    .hasAgentInstances()
                    .add()
                    .buildList())
            .agenticControlReport(true)
            .build();
    reportWriter.createOrUpdateSingleProcessReport(
        KPI_COMPLETED_REPORT_ID,
        null,
        reportData,
        KPI_EXECUTION_COMPLETED_NAME,
        KPI_EXECUTION_COMPLETED_DESCRIPTION,
        null);
    return buildTile(KPI_COMPLETED_REPORT_ID, new PositionDto(0, 0), new DimensionDto(6, 2));
  }

  private DashboardReportTileDto buildAvgDurationReport() {
    final ProcessReportDataDto reportData =
        ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.DURATION))
            .groupBy(new NoneGroupByDto())
            .distributedBy(new NoneDistributedByDto())
            .visualization(ProcessVisualization.NUMBER)
            .filter(
                ProcessFilterBuilder.filter()
                    .completedInstancesOnly()
                    .add()
                    .hasAgentInstances()
                    .add()
                    .buildList())
            .agenticControlReport(true)
            .build();
    reportWriter.createOrUpdateSingleProcessReport(
        KPI_AVG_DURATION_REPORT_ID,
        null,
        reportData,
        KPI_EXECUTION_AVG_DURATION_NAME,
        KPI_EXECUTION_AVG_DURATION_DESCRIPTION,
        null);
    return buildTile(KPI_AVG_DURATION_REPORT_ID, new PositionDto(6, 0), new DimensionDto(6, 2));
  }

  private DashboardReportTileDto buildIncidentRateReport() {
    final ProcessReportDataDto reportData =
        ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.FREQUENCY))
            .groupBy(new NoneGroupByDto())
            .distributedBy(new NoneDistributedByDto())
            .visualization(ProcessVisualization.NUMBER)
            .filter(
                ProcessFilterBuilder.filter()
                    .completedInstancesOnly()
                    .add()
                    .hasAgentInstances()
                    .add()
                    .withResolvedIncident()
                    .add()
                    .buildList())
            .agenticControlReport(true)
            .build();
    reportWriter.createOrUpdateSingleProcessReport(
        KPI_INCIDENT_RATE_REPORT_ID,
        null,
        reportData,
        KPI_EXECUTION_INCIDENT_RATE_NAME,
        KPI_EXECUTION_INCIDENT_RATE_DESCRIPTION,
        null);
    return buildTile(KPI_INCIDENT_RATE_REPORT_ID, new PositionDto(12, 0), new DimensionDto(6, 2));
  }

  private DashboardReportTileDto buildAvgTokensReport() {
    return buildTokensReport(
        KPI_AVG_TOKENS_REPORT_ID,
        new AggregationDto(AggregationType.AVERAGE),
        new PositionDto(0, 2),
        KPI_EXECUTION_AVG_TOKENS_NAME,
        KPI_EXECUTION_AVG_TOKENS_DESCRIPTION);
  }

  private DashboardReportTileDto buildMedianTokensReport() {
    return buildTokensReport(
        KPI_MEDIAN_TOKENS_REPORT_ID,
        new AggregationDto(AggregationType.PERCENTILE, 50.0),
        new PositionDto(9, 2),
        KPI_EXECUTION_MEDIAN_TOKENS_NAME,
        KPI_EXECUTION_MEDIAN_TOKENS_DESCRIPTION);
  }

  private DashboardReportTileDto buildTokensReport(
      final String id,
      final AggregationDto aggregation,
      final PositionDto position,
      final String nameKey,
      final String descriptionKey) {
    final ProcessReportDataDto reportData =
        ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(new ProcessViewDto(ProcessViewEntity.AGENT_INSTANCE, ViewProperty.TOTAL_TOKENS))
            .groupBy(new NoneGroupByDto())
            .distributedBy(new NoneDistributedByDto())
            .visualization(ProcessVisualization.NUMBER)
            .configuration(
                SingleReportConfigurationDto.builder()
                    .aggregationTypes(new LinkedHashSet<>(Collections.singletonList(aggregation)))
                    .precision(2)
                    .valueFormat("compact")
                    .build())
            .filter(
                ProcessFilterBuilder.filter()
                    .completedInstancesOnly()
                    .add()
                    .hasAgentInstances()
                    .add()
                    .buildList())
            .agenticControlReport(true)
            .build();
    reportWriter.createOrUpdateSingleProcessReport(
        id, null, reportData, nameKey, descriptionKey, null);
    return buildTile(id, position, new DimensionDto(9, 2), Map.of("section", "token"));
  }

  private DashboardReportTileDto buildTokenTrendReport() {
    reportWriter.createOrUpdateSingleProcessReport(
        TOKEN_TREND_INPUT_REPORT_ID,
        null,
        buildTokenTrendSubReportData(ViewProperty.INPUT_TOKENS),
        KPI_TOKEN_TREND_INPUT_NAME,
        null,
        null);
    reportWriter.createOrUpdateSingleProcessReport(
        TOKEN_TREND_OUTPUT_REPORT_ID,
        null,
        buildTokenTrendSubReportData(ViewProperty.OUTPUT_TOKENS),
        KPI_TOKEN_TREND_OUTPUT_NAME,
        null,
        null);

    final CombinedReportDataDto combined = new CombinedReportDataDto();
    combined.setVisualization(ProcessVisualization.LINE);
    combined.setReports(
        List.of(
            new CombinedReportItemDto(TOKEN_TREND_INPUT_REPORT_ID, "#0062FF"),
            new CombinedReportItemDto(TOKEN_TREND_OUTPUT_REPORT_ID, "#009D9A")));
    reportWriter.createOrUpdateCombinedReport(
        TOKEN_TREND_COMBINED_REPORT_ID, null, combined, KPI_TOKEN_TREND_NAME, null, null);

    return buildTile(
        TOKEN_TREND_COMBINED_REPORT_ID,
        new PositionDto(0, 6),
        new DimensionDto(18, 4),
        Map.of("section", "token"));
  }

  private ProcessReportDataDto buildTokenTrendSubReportData(final ViewProperty tokenProperty) {
    final EndDateGroupByDto groupBy = new EndDateGroupByDto();
    groupBy.setValue(new DateGroupByValueDto(AggregateByDateUnit.WEEK));
    return ProcessReportDataDto.builder()
        .definitions(Collections.emptyList())
        .view(new ProcessViewDto(ProcessViewEntity.AGENT_INSTANCE, tokenProperty))
        .groupBy(groupBy)
        .distributedBy(new NoneDistributedByDto())
        .visualization(ProcessVisualization.LINE)
        .filter(
            ProcessFilterBuilder.filter()
                .completedInstancesOnly()
                .add()
                .hasAgentInstances()
                .add()
                .buildList())
        .agenticControlReport(true)
        .build();
  }

  private DashboardDefinitionRestDto buildAgentDashboard(final List<DashboardReportTileDto> tiles) {
    final DashboardDefinitionRestDto dashboard = new DashboardDefinitionRestDto();
    dashboard.setId(AGENTIC_DASHBOARD_ID);
    dashboard.setName(AGENTIC_DASHBOARD_NAME);
    dashboard.setAgenticControlDashboard(true);
    dashboard.setCollectionId(null);
    dashboard.setTiles(new ArrayList<>(tiles));
    dashboard.setAvailableFilters(buildAvailableFilters());
    return dashboard;
  }

  private List<DashboardFilterDto<?>> buildAvailableFilters() {
    final DashboardInstanceEndDateFilterDto endDateFilter = new DashboardInstanceEndDateFilterDto();
    final RollingDateFilterDataDto rolling =
        new RollingDateFilterDataDto(
            new RollingDateFilterStartDto(INSTANCE_END_DATE_ROLLING_DAYS, DateUnit.DAYS));
    endDateFilter.setData(new DashboardDateFilterDataDto(rolling));
    final DashboardProcessScopeFilterDto processScopeFilter = new DashboardProcessScopeFilterDto();
    processScopeFilter.setData(new DashboardProcessScopeFilterDataDto(null));
    final List<DashboardFilterDto<?>> availableFilters = new ArrayList<>();
    availableFilters.add(endDateFilter);
    availableFilters.add(processScopeFilter);
    return availableFilters;
  }

  private DashboardReportTileDto buildTile(
      final String reportId, final PositionDto position, final DimensionDto dimensions) {
    return buildTile(reportId, position, dimensions, null);
  }

  private DashboardReportTileDto buildTile(
      final String reportId,
      final PositionDto position,
      final DimensionDto dimensions,
      final Object configuration) {
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
    updateDto.setAvailableFilters(source.getAvailableFilters());
    return updateDto;
  }
}
