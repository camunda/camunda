/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.export;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.report.AuthorizedReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.service.es.report.AuthorizationCheckReportEvaluationHandler;
import org.camunda.optimize.service.es.report.ReportEvaluationInfo;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@AllArgsConstructor
@Component
@Slf4j
public class CsvExportService {

  public static final Integer DEFAULT_RECORD_LIMIT = 1_000;

  private final AuthorizationCheckReportEvaluationHandler reportEvaluationHandler;
  private final ConfigurationService configurationService;

  public Optional<byte[]> getCsvBytesForEvaluatedReportResult(final String userId,
                                                              final String reportId,
                                                              final ZoneId timezone) {
    log.debug("Exporting report with id [{}] as csv.", reportId);
    try {
      ReportEvaluationInfo evaluationInfo = ReportEvaluationInfo.builder(reportId)
        .userId(userId)
        .timezone(timezone)
        .isCsvExport(true)
        .build();
      final AuthorizedReportEvaluationResult reportResult = reportEvaluationHandler.evaluateReport(evaluationInfo);
      final List<String[]> resultAsCsv = reportResult.getEvaluationResult()
        .getResultAsCsv(
          Optional.ofNullable(configurationService.getCsvConfiguration().getExportCsvLimit()).orElse(DEFAULT_RECORD_LIMIT),
          0,
          timezone
        );
      return Optional.ofNullable(CSVUtils.mapCsvLinesToCsvBytes(
        resultAsCsv,
        configurationService.getCsvConfiguration().getExportCsvDelimiter()
      ));
    } catch (NotFoundException e) {
      log.debug("Could not find report with id {} to export the result to csv!", reportId, e);
      return Optional.empty();
    } catch (Exception e) {
      log.error("Could not evaluate report with id {} to export the result to csv!", reportId, e);
      throw e;
    }
  }

  public byte[] getCsvBytesForEvaluatedReportResult(final String userId,
                                                    final ReportDefinitionDto<?> reportDefinition,
                                                    final ZoneId timezone) {
    log.debug("Exporting provided report definition as csv.");
    try {
      ReportEvaluationInfo evaluationInfo = ReportEvaluationInfo.builder(reportDefinition)
        .userId(userId)
        .timezone(timezone)
        .isCsvExport(true)
        .build();
      final AuthorizedReportEvaluationResult reportResult =
        reportEvaluationHandler.evaluateReport(evaluationInfo);
      final List<String[]> resultAsCsv = reportResult.getEvaluationResult()
        .getResultAsCsv(
          Optional.ofNullable(configurationService.getCsvConfiguration().getExportCsvLimit()).orElse(DEFAULT_RECORD_LIMIT),
          0,
          timezone
        );
      return CSVUtils.mapCsvLinesToCsvBytes(resultAsCsv, configurationService.getCsvConfiguration().getExportCsvDelimiter());
    } catch (Exception e) {
      log.error("Could not evaluate report to export the result to csv!", e);
      throw e;
    }
  }

}
