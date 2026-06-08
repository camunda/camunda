/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardFilterDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardInstanceEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardProcessScopeFilterDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardProcessScopeFilterDataDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DimensionDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.PositionDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RollingDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.NoneDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.service.db.reader.DashboardReader;
import io.camunda.optimize.service.db.writer.DashboardWriter;
import io.camunda.optimize.service.db.writer.ReportWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    // Always upsert the three KPI reports so config changes are applied on every restart.
    // Report IDs are deterministic so upserts are safe and tile references never orphan.
    final List<DashboardReportTileDto> tiles = new ArrayList<>();
    tiles.add(buildCompletedInstancesReport());
    tiles.add(buildAvgDurationReport());
    tiles.add(buildIncidentRateReport());

    // Only create the dashboard itself if it is absent — the stable tile IDs mean we never
    // need to recreate it just to keep tile→report references consistent.
    if (dashboardReader.getDashboard(AGENTIC_DASHBOARD_ID).isEmpty()) {
      dashboardWriter.saveDashboard(buildAgentDashboard(tiles));
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
            .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.PERCENTAGE))
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
    return DashboardReportTileDto.builder()
        .id(reportId)
        .type(DashboardTileType.OPTIMIZE_REPORT)
        .position(position)
        .dimensions(dimensions)
        .build();
  }
}
