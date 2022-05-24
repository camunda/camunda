/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.service.report.ReportService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
@AllArgsConstructor
public class KpiService {

  private final ReportService reportService;

  public List<String> getKpisForProcessDefinition(final String processDefinitionKey) {
    return reportService.getAllReportsForProcessDefinitionKeyOmitXml(processDefinitionKey).stream()
      .filter(SingleProcessReportDefinitionRequestDto.class::isInstance)
      .map(SingleProcessReportDefinitionRequestDto.class::cast)
      .filter(processReport -> processReport.getData().getConfiguration().getTargetValue().getIsKpi().equals(true))
      .map(ReportDefinitionDto::getId)
      .collect(Collectors.toList());
  }
}
