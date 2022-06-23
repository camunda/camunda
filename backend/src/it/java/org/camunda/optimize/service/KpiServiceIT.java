/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class KpiServiceIT extends AbstractIT {

  final String PROCESS_DEFINITION_KEY = "procdef";

  @Test
  public void getKpisForDefinition() {
    // given
    String reportId1 = createKpiReport();
    String reportId2 = createKpiReport();

    // when
    final List<String> reports = embeddedOptimizeExtension.getKpiService()
      .getKpisForProcessDefinition(PROCESS_DEFINITION_KEY);

    // then
    assertThat(reports).hasSize(2).containsExactlyInAnyOrder(reportId1, reportId2);
  }

  @Test
  public void reportIsNotReturnedIfNotKpi() {
    // given
    String reportId1 = createKpiReport();
    final ProcessReportDataDto reportDataDto = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .definitions(List.of(new ReportDataDefinitionDto(PROCESS_DEFINITION_KEY)))
      .build();
    reportDataDto.getConfiguration().getTargetValue().setIsKpi(false);
    reportClient.createSingleProcessReport(reportDataDto);


    // when
    final List<String> reports = embeddedOptimizeExtension.getKpiService()
      .getKpisForProcessDefinition(PROCESS_DEFINITION_KEY);

    // then
    assertThat(reports).singleElement().isEqualTo(reportId1);
  }

  @Test
  public void otherProcessDefinitionKpiReportIsNotReturned() {
    // given
    String reportId1 = createKpiReport();
    final ProcessReportDataDto reportDataDto = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .definitions(List.of(new ReportDataDefinitionDto("someprocessdefinition")))
      .build();
    reportDataDto.getConfiguration().getTargetValue().setIsKpi(false);
    reportClient.createSingleProcessReport(reportDataDto);


    // when
    final List<String> reports = embeddedOptimizeExtension.getKpiService()
      .getKpisForProcessDefinition(PROCESS_DEFINITION_KEY);

    // then
    assertThat(reports).singleElement().isEqualTo(reportId1);
  }

  private String createKpiReport() {
    final ProcessReportDataDto reportDataDto = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .definitions(List.of(new ReportDataDefinitionDto(PROCESS_DEFINITION_KEY)))
      .build();
    reportDataDto.getConfiguration().getTargetValue().setIsKpi(true);
    return reportClient.createSingleProcessReport(reportDataDto);
  }


}
