/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.entities.report;

import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.export.ReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.SingleProcessReportDefinitionExportDto;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;

import static org.assertj.core.api.Assertions.assertThat;

public class ProcessReportExportIT extends AbstractReportExportImportIT {

  @ParameterizedTest
  @MethodSource("getTestProcessReports")
  public void exportReportAsJsonFile(final SingleProcessReportDefinitionRequestDto reportDefToExport) {
    // given
    final String reportId = reportClient.createSingleProcessReport(reportDefToExport);
    final SingleProcessReportDefinitionExportDto expectedReportExportDto = createExportDto(reportDefToExport);

    // when
    Response response = exportClient.exportReportAsJson(ReportType.PROCESS, reportId, "my_file.json");

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    final SingleProcessReportDefinitionExportDto actualExportDto =
      response.readEntity(SingleProcessReportDefinitionExportDto.class);

    assertThat(actualExportDto)
      .usingRecursiveComparison()
      .ignoringFields(ReportDefinitionExportDto.Fields.id)
      .isEqualTo(expectedReportExportDto);

    assertThat(actualExportDto.getId()).isEqualTo(reportId);
  }

}
