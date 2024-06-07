/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;

import java.util.List;
import org.camunda.optimize.AbstractPlatformIT;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(OPENSEARCH_PASSING)
public class KpiServiceIT extends AbstractPlatformIT {

  private final String PROCESS_DEFINITION_KEY = "procDef";

  @Test
  public void getKpisForDefinition() {
    // given
    final String reportId1 = createKpiReport();
    final String reportId2 = createKpiReport();

    // when
    final List<SingleProcessReportDefinitionRequestDto> reports =
        embeddedOptimizeExtension
            .getKpiService()
            .getValidKpiReportsForProcessDefinition(PROCESS_DEFINITION_KEY);

    // then
    assertThat(reports).hasSize(2);
    assertThat(reports)
        .satisfiesOnlyOnce(report -> assertThat(report.getId()).isEqualTo(reportId1));
    assertThat(reports)
        .satisfiesOnlyOnce(report -> assertThat(report.getId()).isEqualTo(reportId2));
  }

  @Test
  public void reportIsNotReturnedIfNotKpi() {
    // given
    final String kpiReportId = createKpiReport();
    final ProcessReportDataDto reportDataDto =
        TemplatedProcessReportDataBuilder.createReportData()
            .setReportDataType(ProcessReportDataType.RAW_DATA)
            .definitions(List.of(new ReportDataDefinitionDto(PROCESS_DEFINITION_KEY)))
            .build();
    reportDataDto.getConfiguration().getTargetValue().setIsKpi(false);
    reportClient.createSingleProcessReport(reportDataDto);

    // when
    final List<SingleProcessReportDefinitionRequestDto> reports =
        embeddedOptimizeExtension
            .getKpiService()
            .getValidKpiReportsForProcessDefinition(PROCESS_DEFINITION_KEY);

    // then
    assertThat(reports)
        .singleElement()
        .satisfies(report -> assertThat(report.getId()).isEqualTo(kpiReportId));
  }

  @Test
  public void reportIsNotReturnedIfInvalidKpi() {
    // given
    final String validReportId = createKpiReport();
    final ProcessReportDataDto reportDataDto =
        TemplatedProcessReportDataBuilder.createReportData()
            .setReportDataType(ProcessReportDataType.RAW_DATA)
            .definitions(List.of(new ReportDataDefinitionDto(PROCESS_DEFINITION_KEY)))
            .build();
    reportDataDto.getConfiguration().getTargetValue().setIsKpi(true);
    // null view is invalid
    reportDataDto.setView(null);
    reportClient.createSingleProcessReport(reportDataDto);

    // when
    final List<SingleProcessReportDefinitionRequestDto> reports =
        embeddedOptimizeExtension
            .getKpiService()
            .getValidKpiReportsForProcessDefinition(PROCESS_DEFINITION_KEY);

    // then
    assertThat(reports)
        .singleElement()
        .satisfies(report -> assertThat(report.getId()).isEqualTo(validReportId));
  }

  @Test
  public void otherProcessDefinitionKpiReportIsNotReturned() {
    // given
    final String reportId = createKpiReport();
    final ProcessReportDataDto reportDataDto =
        TemplatedProcessReportDataBuilder.createReportData()
            .setReportDataType(ProcessReportDataType.RAW_DATA)
            .definitions(List.of(new ReportDataDefinitionDto("someProcessDefinition")))
            .build();
    reportDataDto.getConfiguration().getTargetValue().setIsKpi(false);
    reportClient.createSingleProcessReport(reportDataDto);

    // when
    final List<SingleProcessReportDefinitionRequestDto> reports =
        embeddedOptimizeExtension
            .getKpiService()
            .getValidKpiReportsForProcessDefinition(PROCESS_DEFINITION_KEY);

    // then
    assertThat(reports)
        .singleElement()
        .satisfies(report -> assertThat(report.getId()).isEqualTo(reportId));
  }

  private String createKpiReport() {
    final ProcessReportDataDto reportDataDto =
        TemplatedProcessReportDataBuilder.createReportData()
            .setReportDataType(ProcessReportDataType.RAW_DATA)
            .definitions(List.of(new ReportDataDefinitionDto(PROCESS_DEFINITION_KEY)))
            .build();
    reportDataDto.getConfiguration().getTargetValue().setIsKpi(true);
    return reportClient.createSingleProcessReport(reportDataDto);
  }
}
