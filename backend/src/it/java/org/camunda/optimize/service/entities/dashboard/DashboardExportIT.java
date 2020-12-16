/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.entities.dashboard;

import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.export.ExportEntityType;
import org.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import org.camunda.optimize.dto.optimize.rest.export.dashboard.DashboardDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.CombinedProcessReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleProcessReportDefinitionExportDto;
import org.camunda.optimize.service.entities.AbstractExportImportIT;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class DashboardExportIT extends AbstractExportImportIT {

  @Test
  public void exportDashboard() {
    // given
    final String singleReportId1 = createSimpleReport(ReportType.PROCESS);
    final SingleProcessReportDefinitionRequestDto singleDef1 = reportClient.getSingleProcessReportById(singleReportId1);
    final SingleProcessReportDefinitionExportDto expectedSingle1 = createExportDto(singleDef1);

    final String singleReportId2 =  createSimpleReport(ReportType.PROCESS);
    final SingleProcessReportDefinitionRequestDto singleDef2 = reportClient.getSingleProcessReportById(singleReportId2);
    final SingleProcessReportDefinitionExportDto expectedSingle2 = createExportDto(singleDef2);

    final CombinedReportDefinitionRequestDto combinedDef =
      createCombinedReportDefinition(Collections.singletonList(singleDef1));
    final String combinedReportId = reportClient.createNewCombinedReport(combinedDef);
    combinedDef.setId(combinedReportId);
    final CombinedProcessReportDefinitionExportDto expectedCombined = createExportDto(combinedDef);

    final DashboardDefinitionRestDto dashboardDef = createDashboard(combinedReportId, singleReportId2);
    final String dashboardId = dashboardClient.createDashboard(dashboardDef);
    dashboardDef.setId(dashboardId);
    final DashboardDefinitionExportDto expectedDashboard = createExportDto(dashboardDef);

    // when
    final List<OptimizeEntityExportDto> exportedDtos =
      exportClient.exportDashboardAndReturnExportDtos(dashboardId, "my_file.json");

    // then
    assertThat(exportedDtos).hasSize(4);

    assertThat(exportedDtos)
      .filteredOn(dto -> ExportEntityType.DASHBOARD.equals(dto.getExportEntityType()))
      .singleElement()
      .usingRecursiveComparison()
      .isEqualTo(expectedDashboard);

    assertThat(exportedDtos)
      .filteredOn(dto -> ExportEntityType.COMBINED_REPORT.equals(dto.getExportEntityType()))
      .singleElement()
      .usingRecursiveComparison()
      .isEqualTo(expectedCombined);

    assertThat(exportedDtos)
      .filteredOn(dto -> ExportEntityType.SINGLE_PROCESS_REPORT.equals(dto.getExportEntityType()))
      .hasSize(2)
      .containsExactlyInAnyOrder(expectedSingle1, expectedSingle2);
  }

  @Test
  public void exportDashboard_noDuplicatesInExport() {
    // given a dashboard that contains the same single report twice,
    // once as single report and once as part of a combined report
    final String singleReportId = createSimpleReport(ReportType.PROCESS);
    final String combinedReportId = reportClient.createNewCombinedReport(singleReportId);
    final String dashboardId = dashboardClient.createDashboard(null, Arrays.asList(singleReportId, combinedReportId));

    // when
    final List<OptimizeEntityExportDto> exportedDtos =
      exportClient.exportDashboardAndReturnExportDtos(dashboardId, "my_file.json");

    // then the export only includes the single report once
    assertThat(exportedDtos)
      .hasSize(3)
      .filteredOn(dto -> ExportEntityType.SINGLE_PROCESS_REPORT.equals(dto.getExportEntityType()))
      .hasSize(1);
  }

  @Test
  public void exportDashboard_dashboardDoesNotExist() {
    // when
    Response response = exportClient.exportDashboard("fakeId", "my_file.json");

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
    assertThat(response.readEntity(String.class)).contains("fakeId");
  }

  @Test
  public void exportDashboard_reportMissing() {
    // given a dashboard with one of its referenced reports missing
    final String defKey =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSimpleBpmnDiagram()).getKey();
    final String reportId = reportClient.createSingleReport(
      null,
      DefinitionType.PROCESS,
      defKey,
      Collections.emptyList()
    );
    final String dashboardId = dashboardClient.createDashboard(null, Collections.singletonList(reportId));
    elasticSearchIntegrationTestExtension.deleteAllDocsInIndex(new SingleProcessReportIndex());

    // when
    Response response = exportClient.exportDashboard(dashboardId, "my_file.json");

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }
}
