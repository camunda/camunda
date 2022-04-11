/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.export;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.report.AuthorizedReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginatedDataExportDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import org.camunda.optimize.service.es.report.PlainReportEvaluationHandler;
import org.camunda.optimize.service.es.report.ReportEvaluationInfo;
import org.camunda.optimize.service.report.ReportService;
import org.springframework.stereotype.Component;

import javax.ws.rs.BadRequestException;
import java.time.ZoneId;

@AllArgsConstructor
@Component
@Slf4j
public class JsonReportResultExportService {
  private final PlainReportEvaluationHandler reportEvaluationHandler;
  private final ReportService reportService;

  public PaginatedDataExportDto getJsonForEvaluatedReportResult(final String reportId,
                                                                final ZoneId timezone,
                                                                final PaginationDto paginationInfo) {
    log.info("Exporting provided report " + reportId + " as JSON.");
    ReportDefinitionDto<ReportDataDto> reportData = reportService.getReportDefinition(reportId);
    final ReportDataDto unevaluatedReportData = reportData.getData();
    // If it's a single report (not combined)
    if (unevaluatedReportData instanceof SingleReportDataDto) {
      boolean isRawDataReport =
        ((SingleReportDataDto) unevaluatedReportData).getViewProperties().contains(ViewProperty.RAW_DATA);
      final ReportEvaluationInfo.ReportEvaluationInfoBuilder evaluationInfoBuilder =
        ReportEvaluationInfo.builder(reportId)
        .timezone(timezone)
        .isCsvExport(false)
        .isJsonExport(true);
      if(isRawDataReport) {
        // pagination info is only valid in the context of raw data reports
        evaluationInfoBuilder.pagination(paginationInfo);
      }
      final AuthorizedReportEvaluationResult reportResult =
        reportEvaluationHandler.evaluateReport(evaluationInfoBuilder.build());
      final PaginatedDataExportDto resultAsJson = reportResult.getEvaluationResult().getResult();
      resultAsJson.setReportId(reportId);
      // This can only possibly happen with non-raw-data Reports
      if (!isRawDataReport && paginationInfo.getLimit() < resultAsJson.getNumberOfRecordsInResponse()) {
        resultAsJson.setMessage("All records are delivered in this response regardless of the set limit, since " +
          "result pagination is only supported for raw data reports.");
      }
      log.info("Report " + reportId + " exported successfully as JSON.");
      return resultAsJson;
    } else {
      throw new BadRequestException("Combined reports cannot be exported as Json");
    }
  }
}
