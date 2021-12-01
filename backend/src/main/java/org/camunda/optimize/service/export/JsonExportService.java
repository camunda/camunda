/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.export;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.report.AuthorizedReportEvaluationResult;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginatedDataExportDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import org.camunda.optimize.service.es.report.PlainReportEvaluationHandler;
import org.camunda.optimize.service.es.report.ReportEvaluationInfo;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

@AllArgsConstructor
@Component
@Slf4j
public class JsonExportService {
  private final PlainReportEvaluationHandler reportEvaluationHandler;
  public PaginatedDataExportDto getJsonForEvaluatedReportResult(final String reportId,
                                                                final ZoneId timezone,
                                                                final PaginationDto paginationInfo) {
    log.info("Exporting provided report " + reportId + " as JSON.");
    ReportEvaluationInfo evaluationInfo = ReportEvaluationInfo.builder(reportId)
      .timezone(timezone)
      .isCsvExport(false)
      .pagination(paginationInfo)
      .build();

    final AuthorizedReportEvaluationResult reportResult =
      reportEvaluationHandler.evaluateReport(evaluationInfo);
    final PaginatedDataExportDto resultAsJson = reportResult.getEvaluationResult().getResult();
    log.info("Report " + reportId + " exported successfully as JSON.");
    return resultAsJson;
  }
}
