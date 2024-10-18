/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardInstanceStartDateFilterDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DimensionDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.PositionDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RollingDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.StartDateGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.service.db.writer.DashboardWriter;
import io.camunda.optimize.service.db.writer.ReportWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ManagementDashboardService {

  public static final String MANAGEMENT_DASHBOARD_LOCALIZATION_CODE = "dashboardName";
  public static final String MANAGEMENT_DASHBOARD_ID = "management-dashboard";
  public static final String CURRENTLY_IN_PROGRESS_NAME_LOCALIZATION_CODE =
      "instancesCurrentlyInProgressName";
  public static final String CURRENTLY_IN_PROGRESS_DESCRIPTION_LOCALIZATION_CODE =
      "instancesCurrentlyInProgressDescription";
  public static final String STARTED_IN_LAST_SIX_MONTHS_NAME_LOCALIZATION_CODE =
      "instancesStartedInLastSixMonthsName";
  public static final String STARTED_IN_LAST_SIX_MONTHS_DESCRIPTION_LOCALIZATION_CODE =
      "instancesStartedInLastSixMonthsDescription";
  public static final String ENDED_IN_LAST_SIX_MONTHS_NAME_LOCALIZATION_CODE =
      "instancesEndedInLastSixMonthsName";
  public static final String ENDED_IN_LAST_SIX_MONTHS_DESCRIPTION_LOCALIZATION_CODE =
      "instancesEndedInLastSixMonthsDescription";
  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(ManagementDashboardService.class);

  private final DashboardWriter dashboardWriter;
  private final ReportWriter reportWriter;
  private final ConfigurationService configurationService;

  public ManagementDashboardService(
      final DashboardWriter dashboardWriter,
      final ReportWriter reportWriter,
      final ConfigurationService configurationService) {
    this.dashboardWriter = dashboardWriter;
    this.reportWriter = reportWriter;
    this.configurationService = configurationService;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void init() {
    if (configurationService.getEntityConfiguration().getCreateOnStartup()) {
      // First we delete all existing management entities
      log.info("Deleting Management entities");
      reportWriter.deleteAllManagementReports();
      dashboardWriter.deleteManagementDashboard();

      // Then recreate the management reports and dashboard
      log.info("Creating Management Reports and Management Dashboard");
      createManagementDashboardForReports(
          List.of(
              createProcessesCurrentlyInProgressReport(
                  new PositionDto(0, 0), new DimensionDto(4, 2)),
              createProcessesStartedInLastSixMonthsReport(
                  new PositionDto(4, 0), new DimensionDto(14, 4)),
              createProcessesEndedInLastSixMonthsReport(
                  new PositionDto(0, 2), new DimensionDto(4, 2))));
      log.info("Finished creating Management entities");
    }
  }

  private DashboardReportTileDto createProcessesCurrentlyInProgressReport(
      final PositionDto positionDto, final DimensionDto dimensionDto) {
    final ProcessReportDataDto processInstancesCurrentlyInProgressReport =
        ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.FREQUENCY))
            .groupBy(new NoneGroupByDto())
            .visualization(ProcessVisualization.NUMBER)
            .filter(ProcessFilterBuilder.filter().runningInstancesOnly().add().buildList())
            .managementReport(true)
            .build();
    final String reportId =
        createReportAndGetId(
            processInstancesCurrentlyInProgressReport,
            CURRENTLY_IN_PROGRESS_NAME_LOCALIZATION_CODE,
            CURRENTLY_IN_PROGRESS_DESCRIPTION_LOCALIZATION_CODE);
    return buildDashboardReportTileDto(positionDto, dimensionDto, reportId);
  }

  private DashboardReportTileDto createProcessesStartedInLastSixMonthsReport(
      final PositionDto positionDto, final DimensionDto dimensionDto) {
    final ProcessReportDataDto processInstancesStartedInLastSixMonthsReport =
        ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.FREQUENCY))
            .groupBy(new StartDateGroupByDto(new DateGroupByValueDto(AggregateByDateUnit.MONTH)))
            .distributedBy(new ProcessDistributedByDto())
            .visualization(ProcessVisualization.BAR)
            .filter(
                ProcessFilterBuilder.filter()
                    .rollingInstanceStartDate()
                    .start(6L, DateUnit.MONTHS)
                    .add()
                    .buildList())
            .configuration(
                SingleReportConfigurationDto.builder()
                    .stackedBar(true)
                    .yLabel("pi")
                    .xLabel("endDate")
                    .build())
            .managementReport(true)
            .build();
    final String reportId =
        createReportAndGetId(
            processInstancesStartedInLastSixMonthsReport,
            STARTED_IN_LAST_SIX_MONTHS_NAME_LOCALIZATION_CODE,
            STARTED_IN_LAST_SIX_MONTHS_DESCRIPTION_LOCALIZATION_CODE);
    return buildDashboardReportTileDto(positionDto, dimensionDto, reportId);
  }

  private DashboardReportTileDto createProcessesEndedInLastSixMonthsReport(
      final PositionDto positionDto, final DimensionDto dimensionDto) {
    final ProcessReportDataDto processInstancesEndedInLastSixMonthsReport =
        ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.FREQUENCY))
            .groupBy(new NoneGroupByDto())
            .visualization(ProcessVisualization.NUMBER)
            .filter(
                ProcessFilterBuilder.filter()
                    .rollingInstanceEndDate()
                    .start(6L, DateUnit.MONTHS)
                    .add()
                    .buildList())
            .managementReport(true)
            .build();
    final String reportId =
        createReportAndGetId(
            processInstancesEndedInLastSixMonthsReport,
            ENDED_IN_LAST_SIX_MONTHS_NAME_LOCALIZATION_CODE,
            ENDED_IN_LAST_SIX_MONTHS_DESCRIPTION_LOCALIZATION_CODE);
    return buildDashboardReportTileDto(positionDto, dimensionDto, reportId);
  }

  private DashboardReportTileDto buildDashboardReportTileDto(
      final PositionDto positionDto, final DimensionDto dimensionDto, final String reportId) {
    return DashboardReportTileDto.builder()
        .id(reportId)
        .type(DashboardTileType.OPTIMIZE_REPORT)
        .position(positionDto)
        .dimensions(dimensionDto)
        .build();
  }

  private void createManagementDashboardForReports(
      final List<DashboardReportTileDto> reportsForDashboard) {
    final DashboardDefinitionRestDto dashboardDefinition = new DashboardDefinitionRestDto();
    dashboardDefinition.setId(MANAGEMENT_DASHBOARD_ID);
    dashboardDefinition.setName(MANAGEMENT_DASHBOARD_LOCALIZATION_CODE);
    dashboardDefinition.setTiles(reportsForDashboard);

    final DashboardInstanceStartDateFilterDto filterDto = new DashboardInstanceStartDateFilterDto();
    final RollingDateFilterDataDto rollingFilter =
        new RollingDateFilterDataDto(new RollingDateFilterStartDto(12L, DateUnit.MONTHS));
    filterDto.setData(new DashboardDateFilterDataDto(rollingFilter));

    dashboardDefinition.setAvailableFilters(List.of(filterDto));
    dashboardDefinition.setManagementDashboard(true);
    dashboardWriter.saveDashboard(dashboardDefinition);
  }

  private String createReportAndGetId(
      final ProcessReportDataDto processReportDataDto,
      final String localisationCodeForName,
      final String localisationCodeForDescription) {
    return reportWriter
        .createNewSingleProcessReport(
            null,
            processReportDataDto,
            localisationCodeForName,
            localisationCodeForDescription,
            null)
        .getId();
  }
}
