/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.export;

import io.camunda.optimize.dto.optimize.query.report.AuthorizedReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginatedDataExportDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.service.db.es.report.PlainReportEvaluationHandler;
import io.camunda.optimize.service.db.es.report.ReportEvaluationInfo;
import io.camunda.optimize.service.report.ReportService;
import jakarta.ws.rs.BadRequestException;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class JsonReportResultExportService {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(JsonReportResultExportService.class);
  private final PlainReportEvaluationHandler reportEvaluationHandler;
  private final ReportService reportService;

  public JsonReportResultExportService(
      final PlainReportEvaluationHandler reportEvaluationHandler,
      final ReportService reportService) {
    this.reportEvaluationHandler = reportEvaluationHandler;
    this.reportService = reportService;
  }

  public PaginatedDataExportDto getJsonForEvaluatedReportResult(
      final String reportId, final ZoneId timezone, final PaginationDto paginationInfo) {
    log.info("Exporting provided report " + reportId + " as JSON.");
    final ReportDefinitionDto<ReportDataDto> reportData =
        reportService.getReportDefinition(reportId);
    final ReportDataDto unevaluatedReportData = reportData.getData();
    // If it's a single report (not combined)
    if (unevaluatedReportData instanceof SingleReportDataDto) {
      final boolean isRawDataReport =
          ((SingleReportDataDto) unevaluatedReportData)
              .getViewProperties()
              .contains(ViewProperty.RAW_DATA);
      final ReportEvaluationInfo.ReportEvaluationInfoBuilder evaluationInfoBuilder =
          ReportEvaluationInfo.builder(reportId)
              .timezone(timezone)
              .isCsvExport(false)
              .isJsonExport(true);
      if (isRawDataReport) {
        // pagination info is only valid in the context of raw data reports
        evaluationInfoBuilder.pagination(paginationInfo);
      }
      final AuthorizedReportEvaluationResult reportResult =
          reportEvaluationHandler.evaluateReport(evaluationInfoBuilder.build());
      final PaginatedDataExportDto resultAsJson = reportResult.getEvaluationResult().getResult();
      resultAsJson.setReportId(reportId);
      // This can only possibly happen with non-raw-data Reports
      if (!isRawDataReport
          && paginationInfo.getLimit() < resultAsJson.getNumberOfRecordsInResponse()) {
        resultAsJson.setMessage(
            "All records are delivered in this response regardless of the set limit, since "
                + "result pagination is only supported for raw data reports.");
      }
      log.info("Report " + reportId + " exported successfully as JSON.");
      return resultAsJson;
    } else {
      throw new BadRequestException("Combined reports cannot be exported as Json");
    }
  }
}
