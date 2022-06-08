/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DimensionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.PositionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardInstanceEndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardInstanceStartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.CountProgressDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.SingleReportTargetValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.FlowNodesGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.StartDateGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.UserTasksGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.service.dashboard.ManagementDashboardService;
import org.camunda.optimize.test.util.DateCreationFreezer;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.GREATER_THAN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.LESS_THAN;
import static org.camunda.optimize.service.dashboard.ManagementDashboardService.MANAGEMENT_DASHBOARD_ID;
import static org.camunda.optimize.service.dashboard.ManagementDashboardService.MANAGEMENT_DASHBOARD_NAME;
import static org.camunda.optimize.service.dashboard.ManagementDashboardService.PROCESS_INSTANCE_USAGE_REPORT_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;

public class ManagementDashboardIT extends AbstractIT {

  private static final Map<String, ExpectedReportConfigurationAndLocation> expectedReportsAndLocationsByName =
    createExpectedReportsAndLocationsByName();

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
    assertThat(managementDashboard.getName()).isEqualTo(MANAGEMENT_DASHBOARD_NAME);
    assertThat(managementDashboard.getCollectionId()).isNull();
    assertThat(managementDashboard.getLastModified()).isEqualTo(now);
    assertThat(managementDashboard.getLastModifier()).isNull();
    assertThat(managementDashboard.getOwner()).isNull();
    assertThat(managementDashboard.getCreated()).isEqualTo(now);
    assertThat(managementDashboard.getExternalResourceUrls()).isEqualTo(Collections.emptySet());
    assertThat(managementDashboard.getRefreshRateSeconds()).isNull();
    assertThat(managementDashboard.getAvailableFilters()).isEqualTo(List.of(
      new DashboardInstanceStartDateFilterDto(), new DashboardInstanceEndDateFilterDto()
    ));
    assertThat(managementDashboard.getReports())
      .hasSize(6)
      .containsExactlyInAnyOrderElementsOf(
        expectedReportsAndLocationsByName.keySet()
          .stream()
          .map(reportName -> getExpectedDashboardForReportWithName(reportName, managementReportIdsByName))
          .collect(Collectors.toList())
      );
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
        assertThat(dashboard.getName()).isEqualTo(MANAGEMENT_DASHBOARD_NAME);
        assertThat(dashboard.isManagementDashboard()).isTrue();
      });
  }

  private ReportLocationDto getExpectedDashboardForReportWithName(final String reportName,
                                                                  final Map<String, String> managementReportIdsByName) {
    final ExpectedReportConfigurationAndLocation expected = expectedReportsAndLocationsByName.get(reportName);
    return ReportLocationDto.builder()
      .id(managementReportIdsByName.get(reportName))
      .position(expected.getPositionDto())
      .dimensions(expected.getDimensionDto())
      .build();
  }

  private List<DashboardDefinitionRestDto> getAllSavedDashboards() {
    return elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(DASHBOARD_INDEX_NAME, DashboardDefinitionRestDto.class);
  }

  private List<SingleProcessReportDefinitionRequestDto> getAllSavedManagementReports() {
    return elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
      SINGLE_PROCESS_REPORT_INDEX_NAME,
      SingleProcessReportDefinitionRequestDto.class
    );
  }

  private static SingleReportTargetValueDto createTargetValueConfig(final String targetValue) {
    final SingleReportTargetValueDto targetConfig = new SingleReportTargetValueDto();
    targetConfig.setActive(true);
    final CountProgressDto countProgressConfig = new CountProgressDto();
    countProgressConfig.setTarget(targetValue);
    targetConfig.setCountProgress(countProgressConfig);
    return targetConfig;
  }

  private static Map<String, ExpectedReportConfigurationAndLocation> createExpectedReportsAndLocationsByName() {
    return Stream.of(
      new ExpectedReportConfigurationAndLocation(
        PROCESS_INSTANCE_USAGE_REPORT_NAME,
        new SingleProcessReportDefinitionRequestDto(
          ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(new ProcessViewDto(
              ProcessViewEntity.PROCESS_INSTANCE,
              ViewProperty.FREQUENCY
            ))
            .groupBy(new StartDateGroupByDto(new DateGroupByValueDto(AggregateByDateUnit.MONTH)))
            .visualization(ProcessVisualization.BAR)
            .managementReport(true)
            .build()),
        new PositionDto(0, 0),
        new DimensionDto(3, 4)
      ),
      new ExpectedReportConfigurationAndLocation(
        ManagementDashboardService.INCIDENT_FREE_RATE_REPORT_NAME,
        new SingleProcessReportDefinitionRequestDto(
          ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.PERCENTAGE))
            .groupBy(new NoneGroupByDto())
            .visualization(ProcessVisualization.NUMBER)
            .filter(ProcessFilterBuilder.filter().noIncidents().add().buildList())
            .configuration(SingleReportConfigurationDto.builder().targetValue(createTargetValueConfig("99.5")).build())
            .managementReport(true)
            .build()),
        new PositionDto(3, 0),
        new DimensionDto(3, 2)
      ),
      new ExpectedReportConfigurationAndLocation(
        ManagementDashboardService.AUTOMATION_RATE_REPORT_NAME,
        new SingleProcessReportDefinitionRequestDto(
          ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.PERCENTAGE))
            .groupBy(new NoneGroupByDto())
            .visualization(ProcessVisualization.NUMBER)
            .filter(ProcessFilterBuilder.filter()
                      .duration()
                      .operator(LESS_THAN)
                      .unit(DurationUnit.HOURS)
                      .value(1L)
                      .add()
                      .buildList())
            .configuration(SingleReportConfigurationDto.builder().targetValue(createTargetValueConfig("70")).build())
            .managementReport(true)
            .build()),
        new PositionDto(3, 2),
        new DimensionDto(3, 2)
      ),
      new ExpectedReportConfigurationAndLocation(
        ManagementDashboardService.LONG_RUNNING_INSTANCES_REPORT_NAME,
        new SingleProcessReportDefinitionRequestDto(
          ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, List.of(ViewProperty.FREQUENCY, ViewProperty.DURATION)))
            .groupBy(new NoneGroupByDto())
            .distributedBy(new ProcessDistributedByDto())
            .visualization(ProcessVisualization.PIE)
            .filter(ProcessFilterBuilder.filter()
                      .duration()
                      .operator(GREATER_THAN)
                      .unit(DurationUnit.DAYS)
                      .value(7L)
                      .add()
                      .runningInstancesOnly()
                      .add()
                      .buildList())
            .managementReport(true)
            .build()),
        new PositionDto(6, 0),
        new DimensionDto(4, 4)
      ),
      new ExpectedReportConfigurationAndLocation(
        ManagementDashboardService.AUTOMATION_CANDIDATES_REPORT_NAME,
        new SingleProcessReportDefinitionRequestDto(
          ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(new ProcessViewDto(ProcessViewEntity.USER_TASK, List.of(ViewProperty.FREQUENCY, ViewProperty.DURATION)))
            .groupBy(new UserTasksGroupByDto())
            .visualization(ProcessVisualization.PIE)
            .managementReport(true)
            .build()),
        new PositionDto(10, 0),
        new DimensionDto(4, 4)
      ),
      new ExpectedReportConfigurationAndLocation(
        ManagementDashboardService.ACTIVE_BOTTLENECKS_REPORT_NAME,
        new SingleProcessReportDefinitionRequestDto(
          ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(new ProcessViewDto(ProcessViewEntity.FLOW_NODE, List.of(ViewProperty.FREQUENCY, ViewProperty.DURATION)))
            .groupBy(new FlowNodesGroupByDto())
            .visualization(ProcessVisualization.PIE)
            .managementReport(true)
            .build()),
        new PositionDto(14, 0),
        new DimensionDto(4, 4)
      )
    ).collect(Collectors.toMap(ExpectedReportConfigurationAndLocation::getReportName, Function.identity()));
  }

  @Data
  @AllArgsConstructor
  private static class ExpectedReportConfigurationAndLocation {
    private String reportName;
    private SingleProcessReportDefinitionRequestDto reportDefinitionRequestDto;
    private PositionDto positionDto;
    private DimensionDto dimensionDto;
  }

}
