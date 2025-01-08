/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.export;

import static io.camunda.optimize.service.db.DatabaseConstants.SEARCH_CONTEXT_MISSING_EXCEPTION_TYPE;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import io.camunda.optimize.dto.optimize.query.report.AuthorizedReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginatedDataExportDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.rest.exceptions.BadRequestException;
import io.camunda.optimize.service.db.report.PlainReportEvaluationHandler;
import io.camunda.optimize.service.db.report.ReportEvaluationInfo;
import io.camunda.optimize.service.report.ReportService;
import java.time.ZoneId;
import java.util.Optional;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class JsonReportResultExportService {

  private static final Logger LOG =
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
      final String reportId, final ZoneId timezone, final PaginationDto paginationInfo)
      throws Exception {
    LOG.info("Exporting provided report " + reportId + " as JSON.");
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
      try {
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
        LOG.info("Report " + reportId + " exported successfully as JSON.");
        return resultAsJson;
      } catch (final RuntimeException e) {
        throw processAndEnrichExceptionWithMessage(e);
      }
    } else {
      throw new BadRequestException("Combined reports cannot be exported as Json");
    }
  }

  private Exception processAndEnrichExceptionWithMessage(final RuntimeException e) {
    // In case the user provides a parsable but invalid scroll id (e.g. scroll id was earlier
    // valid, but now expired) the message from the database is a bit cryptic. Therefore, extract
    // the useful information so that the user gets an appropriate response.
    if (e instanceof final ElasticsearchException elasticExc) {
      return Optional.ofNullable(elasticExc.response().error().causedBy())
          .filter(
              pag -> {
                assert pag.type() != null;
                return pag.type().contains(SEARCH_CONTEXT_MISSING_EXCEPTION_TYPE);
              })
          .map(pag -> (Exception) new BadRequestException(pag.reason()))
          // In case the exception happened for another reason, just return it as is
          .orElse(e);
    } else if (e instanceof final OpenSearchException openSearchExc) {
      return Optional.ofNullable(openSearchExc.response().error().causedBy())
          .filter(
              pag -> {
                assert pag.type() != null;
                return pag.type().contains(SEARCH_CONTEXT_MISSING_EXCEPTION_TYPE);
              })
          .map(pag -> (Exception) new BadRequestException(pag.reason()))
          // In case the exception happened for another reason, just return it as is
          .orElse(e);
    } else {
      // Just return exception unchanged
      return e;
    }
  }
}
