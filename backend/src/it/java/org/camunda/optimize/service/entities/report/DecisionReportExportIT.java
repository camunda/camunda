/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.entities.report;

import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.export.report.ReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleDecisionReportDefinitionExportDto;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;

import static org.assertj.core.api.Assertions.assertThat;

public class DecisionReportExportIT extends AbstractReportExportImportIT {

  @ParameterizedTest
  @MethodSource("getTestDecisionReports")
  public void exportReportAsJsonFile(final SingleDecisionReportDefinitionRequestDto reportData) {
    // given
    final String reportId = reportClient.createSingleDecisionReport(reportData);
    final SingleDecisionReportDefinitionExportDto expectedReportExportDto =
      new SingleDecisionReportDefinitionExportDto(reportData);

    // when
    Response response = exportClient.exportReportAsJson(ReportType.DECISION, reportId, "my_file.json");

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    final SingleDecisionReportDefinitionExportDto actualExportDto =
      response.readEntity(SingleDecisionReportDefinitionExportDto.class);

    assertThat(actualExportDto)
      .usingRecursiveComparison()
      .ignoringFields(ReportDefinitionExportDto.Fields.id)
      .isEqualTo(expectedReportExportDto);
    assertThat(actualExportDto.getId()).isEqualTo(reportId);
  }

}
