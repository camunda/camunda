/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.dashboard;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardInstanceStartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DimensionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.PositionDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RollingDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.StartDateGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.service.db.writer.DashboardWriter;
import org.camunda.optimize.service.db.writer.ReportWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@AllArgsConstructor
@Component
public class ManagementDashboardService {

  public static final String MANAGEMENT_DASHBOARD_LOCALIZATION_CODE = "dashboardName";
  public static final String MANAGEMENT_DASHBOARD_ID = "management-dashboard";
  public static final String CURRENTLY_IN_PROGRESS_NAME_LOCALIZATION_CODE = "instancesCurrentlyInProgressName";
  public static final String CURRENTLY_IN_PROGRESS_DESCRIPTION_LOCALIZATION_CODE = "instancesCurrentlyInProgressDescription";
  public static final String STARTED_IN_LAST_SIX_MONTHS_NAME_LOCALIZATION_CODE = "instancesStartedInLastSixMonthsName";
  public static final String STARTED_IN_LAST_SIX_MONTHS_DESCRIPTION_LOCALIZATION_CODE =
    "instancesStartedInLastSixMonthsDescription";
  public static final String ENDED_IN_LAST_SIX_MONTHS_NAME_LOCALIZATION_CODE = "instancesEndedInLastSixMonthsName";
  public static final String ENDED_IN_LAST_SIX_MONTHS_DESCRIPTION_LOCALIZATION_CODE = "instancesEndedInLastSixMonthsDescription";

  private final DashboardWriter dashboardWriter;
  private final ReportWriter reportWriter;
  private final ConfigurationService configurationService;

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
          createProcessesCurrentlyInProgressReport(new PositionDto(0, 0), new DimensionDto(4, 2)),
          createProcessesStartedInLastSixMonthsReport(new PositionDto(4, 0), new DimensionDto(14, 4)),
          createProcessesEndedInLastSixMonthsReport(new PositionDto(0, 2), new DimensionDto(4, 2))
        )
      );
    }
  }

  private DashboardReportTileDto createProcessesCurrentlyInProgressReport(final PositionDto positionDto,
                                                                          final DimensionDto dimensionDto) {
    final ProcessReportDataDto processInstancesCurrentlyInProgressReport = ProcessReportDataDto.builder()
      .definitions(Collections.emptyList())
      .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.FREQUENCY))
      .groupBy(new NoneGroupByDto())
      .visualization(ProcessVisualization.NUMBER)
      .managementReport(true)
      .build();
    final String reportId = createReportAndGetId(
      processInstancesCurrentlyInProgressReport,
      CURRENTLY_IN_PROGRESS_NAME_LOCALIZATION_CODE,
      CURRENTLY_IN_PROGRESS_DESCRIPTION_LOCALIZATION_CODE
    );
    return buildDashboardReportTileDto(positionDto, dimensionDto, reportId);
  }

  private DashboardReportTileDto createProcessesStartedInLastSixMonthsReport(final PositionDto positionDto,
                                                                             final DimensionDto dimensionDto) {
    final ProcessReportDataDto processInstancesStartedInLastSixMonthsReport = ProcessReportDataDto.builder()
      .definitions(Collections.emptyList())
      .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.FREQUENCY))
      .groupBy(new StartDateGroupByDto(new DateGroupByValueDto(AggregateByDateUnit.MONTH)))
      .distributedBy(new ProcessDistributedByDto())
      .visualization(ProcessVisualization.BAR)
      .filter(ProcessFilterBuilder.filter()
                .rollingInstanceStartDate()
                .start(6L, DateUnit.MONTHS)
                .add()
                .buildList())
      .configuration(SingleReportConfigurationDto.builder().stackedBar(true).build())
      .managementReport(true)
      .build();
    final String reportId = createReportAndGetId(
      processInstancesStartedInLastSixMonthsReport,
      STARTED_IN_LAST_SIX_MONTHS_NAME_LOCALIZATION_CODE,
      STARTED_IN_LAST_SIX_MONTHS_DESCRIPTION_LOCALIZATION_CODE
    );
    return buildDashboardReportTileDto(positionDto, dimensionDto, reportId);
  }

  private DashboardReportTileDto createProcessesEndedInLastSixMonthsReport(final PositionDto positionDto,
                                                                           final DimensionDto dimensionDto) {
    final ProcessReportDataDto processInstancesEndedInLastSixMonthsReport = ProcessReportDataDto.builder()
      .definitions(Collections.emptyList())
      .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.FREQUENCY))
      .groupBy(new NoneGroupByDto())
      .visualization(ProcessVisualization.NUMBER)
      .filter(ProcessFilterBuilder.filter()
                .rollingInstanceEndDate()
                .start(6L, DateUnit.MONTHS)
                .add()
                .buildList())
      .managementReport(true)
      .build();
    final String reportId = createReportAndGetId(
      processInstancesEndedInLastSixMonthsReport,
      ENDED_IN_LAST_SIX_MONTHS_NAME_LOCALIZATION_CODE,
      ENDED_IN_LAST_SIX_MONTHS_DESCRIPTION_LOCALIZATION_CODE
    );
    return buildDashboardReportTileDto(positionDto, dimensionDto, reportId);
  }

  private DashboardReportTileDto buildDashboardReportTileDto(final PositionDto positionDto, final DimensionDto dimensionDto,
                                                             final String reportId) {
    return DashboardReportTileDto.builder()
      .id(reportId)
      .type(DashboardTileType.OPTIMIZE_REPORT)
      .position(positionDto)
      .dimensions(dimensionDto)
      .build();
  }

  private void createManagementDashboardForReports(final List<DashboardReportTileDto> reportsForDashboard) {
    final DashboardDefinitionRestDto dashboardDefinition = new DashboardDefinitionRestDto();
    dashboardDefinition.setId(MANAGEMENT_DASHBOARD_ID);
    dashboardDefinition.setName(MANAGEMENT_DASHBOARD_LOCALIZATION_CODE);
    dashboardDefinition.setTiles(reportsForDashboard);

    DashboardInstanceStartDateFilterDto filterDto = new DashboardInstanceStartDateFilterDto();
    RollingDateFilterDataDto rollingFilter = new RollingDateFilterDataDto(new RollingDateFilterStartDto(12L, DateUnit.MONTHS));
    filterDto.setData(new DashboardDateFilterDataDto(rollingFilter));

    dashboardDefinition.setAvailableFilters(List.of(filterDto));
    dashboardDefinition.setManagementDashboard(true);
    dashboardWriter.saveDashboard(dashboardDefinition);
  }

  private String createReportAndGetId(final ProcessReportDataDto processReportDataDto,
                                      final String localisationCodeForName,
                                      final String localisationCodeForDescription) {
    return reportWriter.createNewSingleProcessReport(
      null, processReportDataDto, localisationCodeForName, localisationCodeForDescription, null
    ).getId();
  }

}
