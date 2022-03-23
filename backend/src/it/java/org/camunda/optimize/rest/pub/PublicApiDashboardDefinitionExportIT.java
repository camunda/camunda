/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.pub;

import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.export.ExportEntityType;
import org.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import org.camunda.optimize.dto.optimize.rest.export.dashboard.DashboardDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleDecisionReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleProcessReportDefinitionExportDto;
import org.camunda.optimize.service.entities.dashboard.AbstractDashboardDefinitionExportIT;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PublicApiDashboardDefinitionExportIT extends AbstractDashboardDefinitionExportIT {
  private static final String ACCESS_TOKEN = "secret_export_token";

  @Override
  protected List<OptimizeEntityExportDto> exportDashboardDefinitionAndReturnAsList(final String dashboardId) {
    setAccessToken();
    return publicApiClient.exportDashboardsAndReturnExportDtos(Collections.singletonList(dashboardId), ACCESS_TOKEN);
  }

  @Override
  protected Response exportDashboardDefinitionAndReturnResponse(final String dashboardId) {
    setAccessToken();
    return publicApiClient.exportDashboardDefinitions(Collections.singletonList(dashboardId), ACCESS_TOKEN);
  }

  @Test
  public void exportMultipleDashboardDefinitions() {
    // given
    setAccessToken();
    final String processReportId = createSimpleReport(ReportType.PROCESS);
    final SingleProcessReportDefinitionRequestDto processReport1 = reportClient.getSingleProcessReportById(
      processReportId);
    final SingleProcessReportDefinitionExportDto expectedProcess = createExportDto(processReport1);

    final String decisionReportId = createSimpleReport(ReportType.DECISION);
    final SingleDecisionReportDefinitionRequestDto decisionReport =
      reportClient.getSingleDecisionReportById(decisionReportId);
    final SingleDecisionReportDefinitionExportDto expectedDecision = createExportDto(decisionReport);

    final DashboardDefinitionRestDto dashboardDef1 =
      createDashboardDefinition(Collections.singletonList(processReportId));
    final String dashboardId1 = dashboardClient.createDashboard(dashboardDef1);
    dashboardDef1.setId(dashboardId1);
    final DashboardDefinitionExportDto expectedDashboard1 = createExportDto(dashboardDef1);
    final DashboardDefinitionRestDto dashboardDef2 =
      createDashboardDefinition(Collections.singletonList(decisionReportId));
    final String dashboardId2 = dashboardClient.createDashboard(dashboardDef2);
    dashboardDef2.setId(dashboardId2);
    final DashboardDefinitionExportDto expectedDashboard2 = createExportDto(dashboardDef2);

    // when
    final List<OptimizeEntityExportDto> exportedDtos =
      publicApiClient.exportDashboardsAndReturnExportDtos(List.of(dashboardId1, dashboardId2), ACCESS_TOKEN);

    // then all dashboards and reports have been exported
    assertThat(exportedDtos)
      .hasSize(4)
      .filteredOn(dto -> ExportEntityType.DASHBOARD.equals(dto.getExportEntityType()))
      .hasSize(2)
      .usingRecursiveFieldByFieldElementComparator()
      .containsExactlyInAnyOrder(expectedDashboard1, expectedDashboard2);

    assertThat(exportedDtos)
      .filteredOn(dto -> ExportEntityType.SINGLE_PROCESS_REPORT.equals(dto.getExportEntityType()))
      .singleElement()
      .usingRecursiveComparison()
      .isEqualTo(expectedProcess);

    assertThat(exportedDtos)
      .filteredOn(dto -> ExportEntityType.SINGLE_DECISION_REPORT.equals(dto.getExportEntityType()))
      .singleElement()
      .isEqualTo(expectedDecision);
  }

  @Test
  public void noDuplicateReportsAcrossMultipleDashboards() {
    // given two dashboards which both contain the same report
    setAccessToken();
    final String reportId1 = createSimpleReport(ReportType.PROCESS);
    final SingleProcessReportDefinitionRequestDto report1 = reportClient.getSingleProcessReportById(reportId1);
    final SingleProcessReportDefinitionExportDto expectedReport1 = createExportDto(report1);
    final String reportId2 = createSimpleReport(ReportType.PROCESS);
    final SingleProcessReportDefinitionRequestDto report2 = reportClient.getSingleProcessReportById(reportId2);
    final SingleProcessReportDefinitionExportDto expectedReport2 = createExportDto(report2);

    final DashboardDefinitionRestDto dashboardDef1 = createDashboardDefinition(Collections.singletonList(reportId1));
    final String dashboardId1 = dashboardClient.createDashboard(dashboardDef1);
    dashboardDef1.setId(dashboardId1);
    final DashboardDefinitionExportDto expectedDashboard1 = createExportDto(dashboardDef1);
    final DashboardDefinitionRestDto dashboardDef2 = createDashboardDefinition(List.of(reportId1, reportId2));
    final String dashboardId2 = dashboardClient.createDashboard(dashboardDef2);
    dashboardDef2.setId(dashboardId2);
    final DashboardDefinitionExportDto expectedDashboard2 = createExportDto(dashboardDef2);

    // when
    final List<OptimizeEntityExportDto> exportedDtos =
      publicApiClient.exportDashboardsAndReturnExportDtos(List.of(dashboardId1, dashboardId2), ACCESS_TOKEN);

    // then all dashboards were exported and there are no duplicate reports in the export
    assertThat(exportedDtos)
      .hasSize(4)
      .filteredOn(dto -> ExportEntityType.DASHBOARD.equals(dto.getExportEntityType()))
      .hasSize(2)
      .usingRecursiveFieldByFieldElementComparator()
      .containsExactlyInAnyOrder(expectedDashboard1, expectedDashboard2);

    assertThat(exportedDtos)
      .filteredOn(dto -> ExportEntityType.SINGLE_PROCESS_REPORT.equals(dto.getExportEntityType()))
      .hasSize(2)
      .usingRecursiveFieldByFieldElementComparator()
      .containsExactlyInAnyOrder(expectedReport1, expectedReport2);
  }

  @Test
  public void noDuplicateDashboards() {
    // given
    setAccessToken();
    final DashboardDefinitionRestDto dashboardDef =
      createDashboardDefinition(Collections.singletonList(createSimpleReport(ReportType.PROCESS)));
    final String dashboardId1 = dashboardClient.createDashboard(dashboardDef);
    dashboardDef.setId(dashboardId1);
    final DashboardDefinitionExportDto expectedDashboard = createExportDto(dashboardDef);

    // when trying to export the same dashboard twice
    final List<OptimizeEntityExportDto> exportedDtos =
      publicApiClient.exportDashboardsAndReturnExportDtos(List.of(dashboardId1, dashboardId1), ACCESS_TOKEN);

    // then there are no duplicate reports in the export
    assertThat(exportedDtos)
      .hasSize(2)
      .filteredOn(dto -> ExportEntityType.DASHBOARD.equals(dto.getExportEntityType()))
      .singleElement()
      .usingRecursiveComparison()
      .isEqualTo(expectedDashboard);
  }

  private void setAccessToken() {
    embeddedOptimizeExtension.getConfigurationService().getOptimizeApiConfiguration().setAccessToken(ACCESS_TOKEN);
  }

}
