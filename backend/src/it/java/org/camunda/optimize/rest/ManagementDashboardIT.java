/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import jakarta.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.camunda.optimize.AbstractPlatformIT;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardInstanceStartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DimensionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.PositionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RollingDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.StartDateGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.service.dashboard.ManagementDashboardService;
import org.camunda.optimize.test.util.DateCreationFreezer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.dashboard.ManagementDashboardService.CURRENTLY_IN_PROGRESS_DESCRIPTION_LOCALIZATION_CODE;
import static org.camunda.optimize.service.dashboard.ManagementDashboardService.CURRENTLY_IN_PROGRESS_NAME_LOCALIZATION_CODE;
import static org.camunda.optimize.service.dashboard.ManagementDashboardService.ENDED_IN_LAST_SIX_MONTHS_DESCRIPTION_LOCALIZATION_CODE;
import static org.camunda.optimize.service.dashboard.ManagementDashboardService.ENDED_IN_LAST_SIX_MONTHS_NAME_LOCALIZATION_CODE;
import static org.camunda.optimize.service.dashboard.ManagementDashboardService.MANAGEMENT_DASHBOARD_ID;
import static org.camunda.optimize.service.dashboard.ManagementDashboardService.MANAGEMENT_DASHBOARD_LOCALIZATION_CODE;
import static org.camunda.optimize.service.dashboard.ManagementDashboardService.STARTED_IN_LAST_SIX_MONTHS_DESCRIPTION_LOCALIZATION_CODE;
import static org.camunda.optimize.service.dashboard.ManagementDashboardService.STARTED_IN_LAST_SIX_MONTHS_NAME_LOCALIZATION_CODE;
import static org.camunda.optimize.service.entities.dashboard.DashboardDefinitionImportIT.getExternalResourceUrls;
import static org.camunda.optimize.service.db.DatabaseConstants.DASHBOARD_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static org.camunda.optimize.util.SuppressionConstants.UNUSED;

public class ManagementDashboardIT extends AbstractPlatformIT {

  private static final Map<String, ExpectedReportConfigurationAndLocation> expectedReportsAndLocationsByName =
    createExpectedReportsAndLocationsByName();
  private static final String ADOPTION_DASHBOARD = "Adoption";

  @Test
  public void getManagementDashboard() {
    // when
    embeddedOptimizeExtension.getManagementDashboardService().init();
    DashboardDefinitionRestDto returnedDashboard = dashboardClient.getManagementDashboard();

    // then
    assertThat(returnedDashboard).isNotNull();
    assertThat(returnedDashboard.getId()).isEqualTo(ManagementDashboardService.MANAGEMENT_DASHBOARD_ID);
    assertThat(returnedDashboard.getOwner()).isNull();
    assertThat(returnedDashboard.getLastModifier()).isNull();
  }

  @Test
  public void getManagementDashboardWithoutAuthentication() {
    // given
    embeddedOptimizeExtension.getManagementDashboardService().init();

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetManagementDashboardRequest()
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void managementDashboardIsCreatedAsExpected() {
    // given
    final OffsetDateTime now = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    embeddedOptimizeExtension.getManagementDashboardService().init();
    final List<SingleProcessReportDefinitionRequestDto> allSavedManagementReports = getAllSavedManagementReports();
    final Map<String, String> managementReportIdsByName = allSavedManagementReports.stream().collect(Collectors.toMap(
      ReportDefinitionDto::getName,
      ReportDefinitionDto::getId
    ));

    // then the created reports have the correct configuration
    assertThat(allSavedManagementReports).hasSize(expectedReportsAndLocationsByName.size())
      .allSatisfy(report -> {
        assertThat(report.getReportType()).isEqualTo(ReportType.PROCESS);
        assertThat(report.getLastModifier()).isNull();
        assertThat(report.getLastModified()).isEqualTo(now);
        assertThat(report.getCreated()).isEqualTo(now);
        assertThat(report.getOwner()).isNull();
        assertThat(report.getCollectionId()).isNull();
        // Management Reports cannot contain KPIs because there is no "real" user when setting the data sources during evaluation
        assertThat(report.getData().getConfiguration().getTargetValue().getIsKpi()).isNull();
        assertThat(report.getDefinitionType()).isEqualTo(DefinitionType.PROCESS);
        assertThat(report.getFilterData()).isEqualTo(
          expectedReportsAndLocationsByName.get(report.getName()).getReportDefinitionRequestDto().getFilterData());
        assertThat(report.getData()).isEqualTo(
          expectedReportsAndLocationsByName.get(report.getName()).getReportDefinitionRequestDto().getData());
      });

    // and the dashboard configuration is as expected
    final DashboardDefinitionRestDto managementDashboard = dashboardClient.getManagementDashboard();
    assertThat(managementDashboard.isManagementDashboard()).isTrue();
    assertThat(managementDashboard.getId()).isEqualTo(MANAGEMENT_DASHBOARD_ID);
    assertThat(managementDashboard.getName()).isEqualTo(ADOPTION_DASHBOARD);
    assertThat(managementDashboard.getCollectionId()).isNull();
    assertThat(managementDashboard.getLastModified()).isEqualTo(now);
    assertThat(managementDashboard.getLastModifier()).isNull();
    assertThat(managementDashboard.getOwner()).isNull();
    assertThat(managementDashboard.getCreated()).isEqualTo(now);
    assertThat(getExternalResourceUrls(managementDashboard)).isEqualTo(Collections.emptySet());
    assertThat(managementDashboard.getRefreshRateSeconds()).isNull();
    assertThat(managementDashboard.getAvailableFilters()).isEqualTo(List.of(
      createDashboardStartDateFilterWithDefaultValues(
        new RollingDateFilterDataDto(new RollingDateFilterStartDto(12L, DateUnit.MONTHS))
      )
    ));
    assertThat(managementDashboard.getTiles())
      .hasSize(3)
      .containsExactlyInAnyOrderElementsOf(
        expectedReportsAndLocationsByName.keySet()
          .stream()
          .map(reportName -> getExpectedDashboardForReportWithName(reportName, managementReportIdsByName))
          .collect(Collectors.toList())
      );
  }

  @Test
  public void managementEntityCreationCanBeDisabled() {
    // given
    embeddedOptimizeExtension.getConfigurationService().getEntityConfiguration().setCreateOnStartup(false);
    embeddedOptimizeExtension.reloadConfiguration();

    // when
    embeddedOptimizeExtension.getManagementDashboardService().init();

    // then
    assertThat(getAllSavedDashboards()).isEmpty();
    assertThat(getAllSavedManagementReports()).isEmpty();
  }

  @Test
  public void managementDashboardIsOnlyCreatedOnce() {
    // given
    embeddedOptimizeExtension.getManagementDashboardService().init();
    assertThat(dashboardClient.getManagementDashboard()).isNotNull();

    // when
    embeddedOptimizeExtension.getManagementDashboardService().init();

    // then
    assertThat(getAllSavedDashboards())
      .singleElement()
      .satisfies(dashboard -> {
        assertThat(dashboard.getId()).isEqualTo(MANAGEMENT_DASHBOARD_ID);
        assertThat(dashboard.getName()).isEqualTo(MANAGEMENT_DASHBOARD_LOCALIZATION_CODE);
        assertThat(dashboard.isManagementDashboard()).isTrue();
      });
  }

  @ParameterizedTest
  @MethodSource("localizedDashboardNames")
  public void managementDashboardNameIsLocalized(final String locale, final String expectedName) {
    // when
    embeddedOptimizeExtension.getManagementDashboardService().init();

    // when
    final DashboardDefinitionRestDto returnedDashboard = dashboardClient.getManagementDashboardLocalized(locale);

    // then
    assertThat(returnedDashboard.getName()).isEqualTo(expectedName);
  }

  private DashboardReportTileDto getExpectedDashboardForReportWithName(final String reportName,
                                                                       final Map<String, String> managementReportIdsByName) {
    final ExpectedReportConfigurationAndLocation expected = expectedReportsAndLocationsByName.get(reportName);
    return DashboardReportTileDto.builder()
      .id(managementReportIdsByName.get(reportName))
      .type(DashboardTileType.OPTIMIZE_REPORT)
      .position(expected.getPositionDto())
      .dimensions(expected.getDimensionDto())
      .build();
  }

  private List<DashboardDefinitionRestDto> getAllSavedDashboards() {
    return elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(DASHBOARD_INDEX_NAME, DashboardDefinitionRestDto.class);
  }

  private static DashboardFilterDto<?> createDashboardStartDateFilterWithDefaultValues(final DateFilterDataDto<?> defaultValues) {
    DashboardInstanceStartDateFilterDto filterDto = new DashboardInstanceStartDateFilterDto();
    filterDto.setData(new DashboardDateFilterDataDto(defaultValues));
    return filterDto;
  }

  private List<SingleProcessReportDefinitionRequestDto> getAllSavedManagementReports() {
    return elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
      SINGLE_PROCESS_REPORT_INDEX_NAME,
      SingleProcessReportDefinitionRequestDto.class
    );
  }

  private static Map<String, ExpectedReportConfigurationAndLocation> createExpectedReportsAndLocationsByName() {
    return Stream.of(
      new ExpectedReportConfigurationAndLocation(
        CURRENTLY_IN_PROGRESS_NAME_LOCALIZATION_CODE,
        CURRENTLY_IN_PROGRESS_DESCRIPTION_LOCALIZATION_CODE,
        new SingleProcessReportDefinitionRequestDto(
          ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(new ProcessViewDto(
              ProcessViewEntity.PROCESS_INSTANCE,
              ViewProperty.FREQUENCY
            ))
            .groupBy(new NoneGroupByDto())
            .visualization(ProcessVisualization.NUMBER)
            .managementReport(true)
            .build()),
        new PositionDto(0, 0),
        new DimensionDto(4, 2)
      ),
      new ExpectedReportConfigurationAndLocation(
        STARTED_IN_LAST_SIX_MONTHS_NAME_LOCALIZATION_CODE,
        STARTED_IN_LAST_SIX_MONTHS_DESCRIPTION_LOCALIZATION_CODE,
        new SingleProcessReportDefinitionRequestDto(
          ProcessReportDataDto.builder()
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
            .build()),
        new PositionDto(4, 0),
        new DimensionDto(14, 4)
      ),
      new ExpectedReportConfigurationAndLocation(
        ENDED_IN_LAST_SIX_MONTHS_NAME_LOCALIZATION_CODE,
        ENDED_IN_LAST_SIX_MONTHS_DESCRIPTION_LOCALIZATION_CODE,
        new SingleProcessReportDefinitionRequestDto(
          ProcessReportDataDto.builder()
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
            .build()),
        new PositionDto(0, 2),
        new DimensionDto(4, 2)
      )
    ).collect(Collectors.toMap(ExpectedReportConfigurationAndLocation::getReportName, Function.identity()));
  }

  @Data
  @AllArgsConstructor
  private static class ExpectedReportConfigurationAndLocation {
    private String reportName;
    private String reportDescription;
    private SingleProcessReportDefinitionRequestDto reportDefinitionRequestDto;
    private PositionDto positionDto;
    private DimensionDto dimensionDto;
  }

  @SuppressWarnings(UNUSED)
  private static Stream<Arguments> localizedDashboardNames() {
    return Stream.of(
      Arguments.of("en", ADOPTION_DASHBOARD),
      Arguments.of("de", ADOPTION_DASHBOARD)
    );
  }

}
