/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.pub;

import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.export.report.ReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleDecisionReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleProcessReportDefinitionExportDto;
import org.camunda.optimize.service.entities.report.AbstractReportDefinitionExportIT;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportType.PROCESS;

public class PublicApiReportDefinitionExportIT extends AbstractReportDefinitionExportIT {
  private static final String ACCESS_TOKEN = "secret_export_token";

  @Override
  protected List<ReportDefinitionExportDto> exportReportDefinitionAndReturnAsList(final String reportId) {
    setAccessToken();
    return publicApiClient.exportReportDefinitionsAndReturnResponse(Collections.singletonList(reportId), ACCESS_TOKEN);
  }

  @Override
  protected Response exportReportDefinitionAndReturnResponse(final String reportId) {
    setAccessToken();
    return publicApiClient.exportReportDefinitions(Collections.singletonList(reportId), ACCESS_TOKEN);
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void exportMultipleReportDefinitions(final ReportType reportType) {
    // given
    setAccessToken();
    final List<String> reportIds = new ArrayList<>();
    final List<ReportDefinitionExportDto> expectedExportDtos = new ArrayList<>();
    if (PROCESS.equals(reportType)) {
      getTestProcessReports()
        .forEach(repDef -> {
          final String reportId = reportClient.createSingleProcessReport(repDef);
          final SingleProcessReportDefinitionExportDto expectedReportExportDto = createExportDto(repDef);
          expectedReportExportDto.setId(reportId);
          expectedExportDtos.add(expectedReportExportDto);
          reportIds.add(reportId);
        });
    } else {
      getTestDecisionReports()
        .forEach(repDef -> {
          final String reportId = reportClient.createSingleDecisionReport(repDef);
          final SingleDecisionReportDefinitionExportDto expectedReportExportDto =
            new SingleDecisionReportDefinitionExportDto(repDef);
          expectedReportExportDto.setId(reportId);
          expectedExportDtos.add(expectedReportExportDto);
          reportIds.add(reportId);
        });
    }

    // when
    final List<ReportDefinitionExportDto> actualExportDtos =
      publicApiClient.exportReportDefinitionsAndReturnResponse(reportIds, ACCESS_TOKEN);

    // then
    assertThat(actualExportDtos)
      .hasSize(expectedExportDtos.size())
      .usingRecursiveFieldByFieldElementComparator()
      .containsExactlyInAnyOrderElementsOf(expectedExportDtos);
  }

  @Test
  public void exportMultipleReportsNoDuplicates() {
    // given
    setAccessToken();
    final ProcessReportDataDto rawReport = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(DEFINITION_KEY)
      .setProcessDefinitionVersion(DEFINITION_VERSION)
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    SingleProcessReportDefinitionRequestDto reportDef = createProcessReportDefinition(rawReport);
    final String reportId = reportClient.createSingleProcessReport(reportDef);
    final SingleProcessReportDefinitionExportDto expectedReportExportDto = createExportDto(reportDef);
    expectedReportExportDto.setId(reportId);

    // when
    final List<ReportDefinitionExportDto> actualExportDtos =
      publicApiClient.exportReportDefinitionsAndReturnResponse(List.of(reportId, reportId), ACCESS_TOKEN);

    // then
    assertThat(actualExportDtos)
      .singleElement()
      .usingRecursiveComparison()
      .isEqualTo(expectedReportExportDto);
  }

  private void setAccessToken() {
    embeddedOptimizeExtension.getConfigurationService().getOptimizeApiConfiguration().setAccessToken(ACCESS_TOKEN);
  }

}
