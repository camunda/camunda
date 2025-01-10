/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.export;

import io.camunda.optimize.dto.optimize.query.report.AuthorizedReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.rest.exceptions.NotFoundException;
import io.camunda.optimize.service.db.report.AuthorizationCheckReportEvaluationHandler;
import io.camunda.optimize.service.db.report.ReportEvaluationInfo;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class CsvExportService {

  public static final Integer DEFAULT_RECORD_LIMIT = 1_000;
  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(CsvExportService.class);

  private final AuthorizationCheckReportEvaluationHandler reportEvaluationHandler;
  private final ConfigurationService configurationService;

  public CsvExportService(
      final AuthorizationCheckReportEvaluationHandler reportEvaluationHandler,
      final ConfigurationService configurationService) {
    this.reportEvaluationHandler = reportEvaluationHandler;
    this.configurationService = configurationService;
  }

  public Optional<byte[]> getCsvBytesForEvaluatedReportResult(
      final String userId, final String reportId, final ZoneId timezone) {
    LOG.debug("Exporting report with id [{}] as csv.", reportId);
    try {
      final ReportEvaluationInfo evaluationInfo =
          ReportEvaluationInfo.builder(reportId)
              .userId(userId)
              .timezone(timezone)
              .isCsvExport(true)
              .build();
      final AuthorizedReportEvaluationResult reportResult =
          reportEvaluationHandler.evaluateReport(evaluationInfo);
      final List<String[]> resultAsCsv =
          reportResult
              .getEvaluationResult()
              .getResultAsCsv(
                  Optional.ofNullable(
                          configurationService.getCsvConfiguration().getExportCsvLimit())
                      .orElse(DEFAULT_RECORD_LIMIT),
                  0,
                  timezone);
      return Optional.ofNullable(
          CSVUtils.mapCsvLinesToCsvBytes(
              resultAsCsv, configurationService.getCsvConfiguration().getExportCsvDelimiter()));
    } catch (final NotFoundException e) {
      LOG.debug("Could not find report with id {} to export the result to csv!", reportId, e);
      return Optional.empty();
    } catch (final Exception e) {
      LOG.error("Could not evaluate report with id {} to export the result to csv!", reportId, e);
      throw e;
    }
  }

  public byte[] getCsvBytesForEvaluatedReportResult(
      final String userId, final ReportDefinitionDto<?> reportDefinition, final ZoneId timezone) {
    LOG.debug("Exporting provided report definition as csv.");
    try {
      final ReportEvaluationInfo evaluationInfo =
          ReportEvaluationInfo.builder(reportDefinition)
              .userId(userId)
              .timezone(timezone)
              .isCsvExport(true)
              .build();
      final AuthorizedReportEvaluationResult reportResult =
          reportEvaluationHandler.evaluateReport(evaluationInfo);
      final List<String[]> resultAsCsv =
          reportResult
              .getEvaluationResult()
              .getResultAsCsv(
                  Optional.ofNullable(
                          configurationService.getCsvConfiguration().getExportCsvLimit())
                      .orElse(DEFAULT_RECORD_LIMIT),
                  0,
                  timezone);
      return CSVUtils.mapCsvLinesToCsvBytes(
          resultAsCsv, configurationService.getCsvConfiguration().getExportCsvDelimiter());
    } catch (final Exception e) {
      LOG.error("Could not evaluate report to export the result to csv!", e);
      throw e;
    }
  }
}
