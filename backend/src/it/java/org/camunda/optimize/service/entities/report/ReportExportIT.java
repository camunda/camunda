/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.entities.report;

import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleDecisionReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleProcessReportDefinitionExportDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ReportExportIT extends AbstractReportExportImportIT {

  @Test
  public void exportReportAsJsonFile_reportDoesNotExist() {
    // when
    Response response = exportClient.exportReportAsJson("fakeId", "my_file.json");

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
    assertThat(response.readEntity(String.class)).contains("fakeId");
  }

  @ParameterizedTest
  @MethodSource("getTestProcessReports")
  public void exportProcessReportAsJsonFile(final SingleProcessReportDefinitionRequestDto reportDefToExport) {
    // given
    final String reportId = reportClient.createSingleProcessReport(reportDefToExport);
    final SingleProcessReportDefinitionExportDto expectedReportExportDto = createExportDto(reportDefToExport);
    expectedReportExportDto.setId(reportId);

    // when
    Response response = exportClient.exportReportAsJson(reportId, "my_file.json");

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    // @formatter:off
    final List<SingleProcessReportDefinitionExportDto> actualExportDtos =
      response.readEntity(new GenericType<List<SingleProcessReportDefinitionExportDto>>(){});
    // @formatter:on

    assertThat(actualExportDtos)
      .singleElement()
      .usingRecursiveComparison()
      .isEqualTo(expectedReportExportDto);
  }

  @ParameterizedTest
  @MethodSource("getTestDecisionReports")
  public void exportDecisionReportAsJsonFile(final SingleDecisionReportDefinitionRequestDto reportDefToExport) {
    // given
    final String reportId = reportClient.createSingleDecisionReport(reportDefToExport);
    final SingleDecisionReportDefinitionExportDto expectedReportExportDto =
      new SingleDecisionReportDefinitionExportDto(reportDefToExport);
    expectedReportExportDto.setId(reportId);

    // when
    Response response = exportClient.exportReportAsJson(reportId, "my_file.json");

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    // @formatter:off
    final List<SingleDecisionReportDefinitionExportDto> actualExportDtos =
      response.readEntity(new GenericType<List<SingleDecisionReportDefinitionExportDto>>() {});
    // @formatter:off

    assertThat(actualExportDtos)
      .singleElement()
      .usingRecursiveComparison()
      .isEqualTo(expectedReportExportDto);
  }
}
