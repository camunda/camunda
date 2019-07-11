/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.export;

import com.opencsv.CSVWriter;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.report.AuthorizationCheckReportEvaluationHandler;
import org.camunda.optimize.service.es.report.result.ReportEvaluationResult;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@AllArgsConstructor
@Component
@Slf4j
public class ExportService {

  private final AuthorizationCheckReportEvaluationHandler reportService;
  private final ConfigurationService configurationService;

  public Optional<byte[]> getCsvBytesForEvaluatedReportResult(final String userId,
                                                              final String reportId,
                                                              final Set<String> excludedColumns) {
    log.debug("Exporting report with id [{}] as csv.", reportId);
    final Integer exportCsvLimit = configurationService.getExportCsvLimit();

    try {
      final ReportEvaluationResult<?, ?> reportResult = reportService.evaluateSavedReport(
        userId, reportId, exportCsvLimit
      );
      final List<String[]> resultAsCsv = reportResult.getResultAsCsv(exportCsvLimit, 0, excludedColumns);
      return Optional.of(mapCsvLinesToCsvBytes(resultAsCsv));
    } catch (Exception e) {
      log.debug("Could not evaluate report to export the result to csv!", e);
      return Optional.empty();
    }

  }

  private byte[] mapCsvLinesToCsvBytes(final List<String[]> csvStrings) {
    final ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
    final BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(arrayOutputStream));
    final CSVWriter csvWriter = new CSVWriter(bufferedWriter);

    byte[] bytes = null;
    try {
      csvWriter.writeAll(csvStrings);
      bufferedWriter.flush();
      bufferedWriter.close();
      arrayOutputStream.flush();
      bytes = arrayOutputStream.toByteArray();
      arrayOutputStream.close();
    } catch (Exception e) {
      log.error("can't write CSV to buffer", e);
    }
    return bytes;
  }

}
