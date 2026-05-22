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
import io.camunda.optimize.dto.optimize.query.dashboard.tile.AgentTileConfigDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DimensionDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.PositionDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RollingDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.service.db.writer.DashboardWriter;
import io.camunda.optimize.service.db.writer.ReportWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class AgentDashboardService {

  public static final String AGENTIC_DASHBOARD_ID = "agentic-control-plane-dashboard";
  public static final String AGENTIC_DASHBOARD_NAME = "dashboardName";

  private static final String DELTA_UP = "up";
  private static final String DELTA_DOWN = "down";

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(AgentDashboardService.class);

  private final DashboardWriter dashboardWriter;
  private final ReportWriter reportWriter;
  private final ConfigurationService configurationService;

  public AgentDashboardService(
      final DashboardWriter dashboardWriter,
      final ReportWriter reportWriter,
      final ConfigurationService configurationService) {
    this.dashboardWriter = dashboardWriter;
    this.reportWriter = reportWriter;
    this.configurationService = configurationService;
  }

  /**
   * Runs after {@link ManagementDashboardService} (@Order(0)) which already cleaned up all
   * managementReport=true reports and managementDashboard=true dashboards, so this method only
   * needs to create fresh agentic entities.
   */
  @EventListener(ApplicationReadyEvent.class)
  @Order(1)
  public void init() {
    if (configurationService.getEntityConfiguration().getCreateOnStartup()) {
      LOG.info("Seeding Agentic Control Plane dashboard");
      reconcile();
      LOG.info("Finished seeding Agentic Control Plane dashboard");
    }
  }

  /**
   * Seeds the agentic dashboard and its KPI reports. Called by {@link #init()} on every startup
   * after management entities have been cleaned up. Safe to call manually in tests.
   */
  public void reconcile() {
    final List<DashboardReportTileDto> tiles =
        List.of(
            buildTotalExecutionsTile(new PositionDto(0, 0), new DimensionDto(6, 2)),
            buildAvgExecutionDurationTile(new PositionDto(6, 0), new DimensionDto(6, 2)),
            buildIncidentRateTile(new PositionDto(12, 0), new DimensionDto(6, 2)));

    dashboardWriter.saveDashboard(buildAgentDashboard(tiles));
  }

  // --- Execution KPI tile builders ---

  private DashboardReportTileDto buildTotalExecutionsTile(
      final PositionDto position, final DimensionDto dimensions) {
    final ProcessReportDataDto reportData =
        ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.FREQUENCY))
            .groupBy(new NoneGroupByDto())
            .visualization(ProcessVisualization.NUMBER)
            .filter(
                ProcessFilterBuilder.filter()
                    .completedInstancesOnly()
                    .add()
                    .hasAgentInstances()
                    .add()
                    .buildList())
            .managementReport(true)
            .build();
    final String reportId =
        reportWriter
            .createNewSingleProcessReport(null, reportData, "agentic-total-executions", null, null)
            .getId();
    return DashboardReportTileDto.builder()
        .id(reportId)
        .position(position)
        .dimensions(dimensions)
        .type(DashboardTileType.OPTIMIZE_REPORT)
        .configuration(new AgentTileConfigDto(true, DELTA_UP))
        .build();
  }

  private DashboardReportTileDto buildAvgExecutionDurationTile(
      final PositionDto position, final DimensionDto dimensions) {
    final ProcessReportDataDto reportData =
        ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.DURATION))
            .groupBy(new NoneGroupByDto())
            .visualization(ProcessVisualization.NUMBER)
            .filter(
                ProcessFilterBuilder.filter()
                    .completedInstancesOnly()
                    .add()
                    .hasAgentInstances()
                    .add()
                    .buildList())
            .configuration(
                SingleReportConfigurationDto.builder()
                    .aggregationTypes(Set.of(new AggregationDto(AggregationType.AVERAGE)))
                    .build())
            .managementReport(true)
            .build();
    final String reportId =
        reportWriter
            .createNewSingleProcessReport(
                null, reportData, "agentic-avg-execution-duration", null, null)
            .getId();
    return DashboardReportTileDto.builder()
        .id(reportId)
        .position(position)
        .dimensions(dimensions)
        .type(DashboardTileType.OPTIMIZE_REPORT)
        .configuration(new AgentTileConfigDto(true, DELTA_DOWN))
        .build();
  }

  private DashboardReportTileDto buildIncidentRateTile(
      final PositionDto position, final DimensionDto dimensions) {
    final ProcessReportDataDto reportData =
        ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.PERCENTAGE))
            .groupBy(new NoneGroupByDto())
            .visualization(ProcessVisualization.NUMBER)
            .filter(
                ProcessFilterBuilder.filter()
                    .completedInstancesOnly()
                    .add()
                    .hasAgentInstances()
                    .add()
                    .buildList())
            .managementReport(true)
            .build();
    final String reportId =
        reportWriter
            .createNewSingleProcessReport(null, reportData, "agentic-incident-rate", null, null)
            .getId();
    return DashboardReportTileDto.builder()
        .id(reportId)
        .position(position)
        .dimensions(dimensions)
        .type(DashboardTileType.OPTIMIZE_REPORT)
        .configuration(new AgentTileConfigDto(true, DELTA_DOWN))
        .build();
  }

  // --- Dashboard entity builder ---

  private DashboardDefinitionRestDto buildAgentDashboard(final List<DashboardReportTileDto> tiles) {
    final DashboardDefinitionRestDto dashboard = new DashboardDefinitionRestDto();
    dashboard.setId(AGENTIC_DASHBOARD_ID);
    dashboard.setName(AGENTIC_DASHBOARD_NAME);
    dashboard.setManagementDashboard(true);
    dashboard.setCollectionId(null);
    dashboard.setTiles(tiles);
    dashboard.setAvailableFilters(buildAvailableFilters());
    return dashboard;
  }

  private List<DashboardFilterDto<?>> buildAvailableFilters() {
    final DashboardInstanceEndDateFilterDto endDateFilter = new DashboardInstanceEndDateFilterDto();
    final RollingDateFilterDataDto rolling =
        new RollingDateFilterDataDto(new RollingDateFilterStartDto(30L, DateUnit.DAYS));
    endDateFilter.setData(new DashboardDateFilterDataDto(rolling));

    return List.of(endDateFilter, new DashboardProcessScopeFilterDto());
  }
}
